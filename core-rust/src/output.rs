use thiserror::Error;

#[derive(Error, Debug)]
pub enum OutputError {
    #[error("Failed to open audio output stream: {0}")]
    OpenFailed(String),
    #[error("Failed to start audio output stream: {0}")]
    StartFailed(String),
    #[error("Audio output unsupported format")]
    UnsupportedFormat,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OutputSharingMode {
    Exclusive, // Bit-perfect direct hardware stream / MMAP
    Shared,    // Shared mixer with Android AudioFlinger
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OutputSampleFormat {
    F32,
    I24,
    I16,
}

pub trait AudioRenderCallback: Send + Sync + 'static {
    /// Request `frames` of interleaved audio samples into `buffer`.
    /// Buffer length = frames * channels.
    fn render_audio(&self, buffer: &mut [f32], frames: usize, channels: usize);
}

#[derive(Debug, Clone)]
pub struct AudioOutputConfig {
    pub sample_rate: u32,
    pub channels: u32,
    pub sharing_mode: OutputSharingMode,
    pub sample_format: OutputSampleFormat,
    pub buffer_size_frames: Option<u32>,
    /// Android AudioDeviceInfo ID for direct USB DAC targeting (e.g. from AudioDeviceInfo.getId())
    pub device_id: Option<i32>,
}

#[cfg(target_os = "android")]
pub mod android_oboe {
    use super::*;
    use oboe::{
        AudioOutputCallback, AudioOutputStream, AudioOutputStreamSafe, AudioStream, AudioStreamAsync,
        AudioStreamBase, AudioStreamBuilder, DataCallbackResult, Output, PerformanceMode, SharingMode,
        Stereo,
    };
    use std::sync::Arc;

    struct OboeCallbackWrapper {
        callback: Arc<dyn AudioRenderCallback>,
    }

    impl AudioOutputCallback for OboeCallbackWrapper {
        type FrameType = (f32, Stereo);

        fn on_audio_ready(
            &mut self,
            _stream: &mut dyn AudioOutputStreamSafe,
            data: &mut [(f32, f32)],
        ) -> DataCallbackResult {
            let frames = data.len();
            let slice = unsafe {
                std::slice::from_raw_parts_mut(data.as_mut_ptr() as *mut f32, frames * 2)
            };
            self.callback.render_audio(slice, frames, 2);
            DataCallbackResult::Continue
        }

        fn on_error_before_close(
            &mut self,
            _stream: &mut dyn AudioOutputStreamSafe,
            error: oboe::Error,
        ) {
            log::warn!("Oboe audio stream error before close: {:?}", error);
        }

        fn on_error_after_close(
            &mut self,
            _stream: &mut dyn AudioOutputStreamSafe,
            error: oboe::Error,
        ) {
            log::warn!("Oboe audio stream error after close: {:?}", error);
        }
    }

    pub struct AndroidAudioOutput {
        stream: AudioStreamAsync<Output, OboeCallbackWrapper>,
        is_exclusive: bool,
    }

    impl AndroidAudioOutput {
        pub fn open(
            config: AudioOutputConfig,
            callback: Arc<dyn AudioRenderCallback>,
        ) -> Result<Self, OutputError> {
            let sharing_mode = match config.sharing_mode {
                OutputSharingMode::Exclusive => SharingMode::Exclusive,
                OutputSharingMode::Shared => SharingMode::Shared,
            };

            let cb_wrapper = OboeCallbackWrapper {
                callback: Arc::clone(&callback),
            };

            let mut builder = AudioStreamBuilder::default()
                .set_performance_mode(PerformanceMode::LowLatency)
                .set_sharing_mode(sharing_mode)
                .set_sample_rate(config.sample_rate as i32);

            if let Some(dev_id) = config.device_id {
                builder = builder.set_device_id(dev_id);
            }

            if let Some(buf_size) = config.buffer_size_frames {
                builder = builder.set_buffer_capacity_in_frames(buf_size as i32);
            }

            let async_builder = builder
                .set_channel_count::<Stereo>()
                .set_format::<f32>()
                .set_callback(cb_wrapper);

            let mut stream = match async_builder.open_stream() {
                Ok(s) => s,
                Err(e) if config.sharing_mode == OutputSharingMode::Exclusive => {
                    log::warn!("Failed to open AAudio Exclusive stream ({:?}), falling back to Shared mode", e);
                    let cb_wrapper_fallback = OboeCallbackWrapper { callback };
                    let mut fallback_builder = AudioStreamBuilder::default()
                        .set_performance_mode(PerformanceMode::LowLatency)
                        .set_sharing_mode(SharingMode::Shared)
                        .set_sample_rate(config.sample_rate as i32);

                    if let Some(dev_id) = config.device_id {
                        fallback_builder = fallback_builder.set_device_id(dev_id);
                    }

                    let fallback_async = fallback_builder
                        .set_channel_count::<Stereo>()
                        .set_format::<f32>()
                        .set_callback(cb_wrapper_fallback);

                    fallback_async
                        .open_stream()
                        .map_err(|err| OutputError::OpenFailed(format!("{:?}", err)))?
                }
                Err(e) => return Err(OutputError::OpenFailed(format!("{:?}", e))),
            };

            let is_exclusive = stream.get_sharing_mode() == SharingMode::Exclusive;

            stream.start().map_err(|e| OutputError::StartFailed(format!("{:?}", e)))?;

            Ok(Self {
                stream,
                is_exclusive,
            })
        }

        pub fn is_exclusive(&self) -> bool {
            self.is_exclusive
        }

        pub fn sample_rate(&self) -> i32 {
            self.stream.get_sample_rate()
        }

        pub fn stop(&mut self) -> Result<(), OutputError> {
            self.stream.stop().map_err(|e| OutputError::StartFailed(format!("{:?}", e)))
        }

        pub fn pause(&mut self) -> Result<(), OutputError> {
            self.stream.pause().map_err(|e| OutputError::StartFailed(format!("{:?}", e)))
        }
    }
}

#[cfg(not(target_os = "android"))]
pub mod desktop_cpal {
    use super::*;
    use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::sync::Arc;
    use std::thread;

    pub struct DesktopAudioOutput {
        is_running: Arc<AtomicBool>,
        sample_rate: u32,
        channels: u32,
    }

    // Safety: The CPAL stream lifecycle is contained inside a dedicated thread.
    unsafe impl Send for DesktopAudioOutput {}
    unsafe impl Sync for DesktopAudioOutput {}

    impl DesktopAudioOutput {
        pub fn open(
            config: AudioOutputConfig,
            callback: Arc<dyn AudioRenderCallback>,
        ) -> Result<Self, OutputError> {
            let is_running = Arc::new(AtomicBool::new(true));
            let is_running_clone = Arc::clone(&is_running);
            let sample_rate = config.sample_rate;
            let channels = config.channels;

            let (tx_ready, rx_ready) = crossbeam_channel::bounded(1);

            thread::Builder::new()
                .name("audiophile-cpal-thread".to_string())
                .spawn(move || {
                    let host = cpal::default_host();
                    let device = match host.default_output_device() {
                        Some(d) => d,
                        None => {
                            let _ = tx_ready.send(Err(OutputError::OpenFailed("No audio device".to_string())));
                            return;
                        }
                    };

                    let stream_config = cpal::StreamConfig {
                        channels: channels as u16,
                        sample_rate: cpal::SampleRate(sample_rate),
                        buffer_size: cpal::BufferSize::Default,
                    };

                    let cb = Arc::clone(&callback);
                    let ch = channels as usize;

                    let stream = match device.build_output_stream(
                        &stream_config,
                        move |data: &mut [f32], _: &cpal::OutputCallbackInfo| {
                            let frames = data.len() / ch;
                            cb.render_audio(data, frames, ch);
                        },
                        move |err| {
                            log::warn!("Audio output error: {}", err);
                        },
                        None,
                    ) {
                        Ok(s) => s,
                        Err(e) => {
                            let _ = tx_ready.send(Err(OutputError::OpenFailed(e.to_string())));
                            return;
                        }
                    };

                    if let Err(e) = stream.play() {
                        let _ = tx_ready.send(Err(OutputError::StartFailed(e.to_string())));
                        return;
                    }

                    let _ = tx_ready.send(Ok(()));

                    while is_running_clone.load(Ordering::Relaxed) {
                        thread::sleep(std::time::Duration::from_millis(100));
                    }
                })
                .map_err(|e| OutputError::OpenFailed(e.to_string()))?;

            match rx_ready.recv() {
                Ok(Ok(())) => Ok(Self {
                    is_running,
                    sample_rate,
                    channels,
                }),
                Ok(Err(e)) => Err(e),
                Err(_) => Err(OutputError::OpenFailed("CPAL thread terminated prematurely".to_string())),
            }
        }

        pub fn is_exclusive(&self) -> bool {
            false
        }

        pub fn sample_rate(&self) -> u32 {
            self.sample_rate
        }

        pub fn channels(&self) -> u32 {
            self.channels
        }
    }

    impl Drop for DesktopAudioOutput {
        fn drop(&mut self) {
            self.is_running.store(false, Ordering::Relaxed);
        }
    }
}
