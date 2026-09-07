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
        fn try_create_and_start(
            sample_rate: Option<i32>,
            sharing_mode: SharingMode,
            perf_mode: PerformanceMode,
            device_id: Option<i32>,
            buffer_size: Option<u32>,
            callback: Arc<dyn AudioRenderCallback>,
        ) -> Result<AudioStreamAsync<Output, OboeCallbackWrapper>, OutputError> {
            let cb_wrapper = OboeCallbackWrapper {
                callback,
            };

            let mut builder = AudioStreamBuilder::default()
                .set_performance_mode(perf_mode)
                .set_sharing_mode(sharing_mode);

            if let Some(sr) = sample_rate {
                builder = builder.set_sample_rate(sr);
            }

            if let Some(dev_id) = device_id {
                builder = builder.set_device_id(dev_id);
            }

            if let Some(buf_size) = buffer_size {
                builder = builder.set_buffer_capacity_in_frames(buf_size as i32);
            }

            let async_builder = builder
                .set_channel_count::<Stereo>()
                .set_format::<f32>()
                .set_callback(cb_wrapper);

            let mut stream = async_builder
                .open_stream()
                .map_err(|e| OutputError::OpenFailed(format!("{:?}", e)))?;

            stream
                .start()
                .map_err(|e| OutputError::StartFailed(format!("{:?}", e)))?;

            Ok(stream)
        }

        pub fn open(
            config: AudioOutputConfig,
            callback: Arc<dyn AudioRenderCallback>,
        ) -> Result<Self, OutputError> {
            let target_rate = config.sample_rate as i32;

            // Tier 1: If exclusive mode was explicitly requested, try Exclusive with LowLatency
            if config.sharing_mode == OutputSharingMode::Exclusive {
                log::info!("Attempting to open AAudio Exclusive output stream at {} Hz (Device: {:?})", target_rate, config.device_id);
                if let Ok(stream) = Self::try_create_and_start(
                    Some(target_rate),
                    SharingMode::Exclusive,
                    PerformanceMode::LowLatency,
                    config.device_id,
                    config.buffer_size_frames,
                    Arc::clone(&callback),
                ) {
                    let is_exclusive = stream.get_sharing_mode() == SharingMode::Exclusive;
                    log::info!("Successfully opened AAudio stream (Exclusive: {})", is_exclusive);
                    return Ok(Self { stream, is_exclusive });
                }
                log::warn!("AAudio Exclusive stream failed, falling back to Shared mode");
            }

            // Tier 2: Shared mode with target sample rate and LowLatency
            if let Ok(stream) = Self::try_create_and_start(
                Some(target_rate),
                SharingMode::Shared,
                PerformanceMode::LowLatency,
                config.device_id,
                None,
                Arc::clone(&callback),
            ) {
                return Ok(Self { stream, is_exclusive: false });
            }

            // Tier 3: Shared mode at standard 48000 Hz with LowLatency (native Android HAL rate)
            log::warn!("AAudio Shared mode at {} Hz failed, trying standard 48000 Hz Shared", target_rate);
            if let Ok(stream) = Self::try_create_and_start(
                Some(48000),
                SharingMode::Shared,
                PerformanceMode::LowLatency,
                config.device_id,
                None,
                Arc::clone(&callback),
            ) {
                return Ok(Self { stream, is_exclusive: false });
            }

            // Tier 4: Shared mode at 48000 Hz with standard latency (PerformanceMode::None)
            log::warn!("AAudio LowLatency failed, trying PerformanceMode::None at 48000 Hz");
            if let Ok(stream) = Self::try_create_and_start(
                Some(48000),
                SharingMode::Shared,
                PerformanceMode::None,
                None,
                None,
                Arc::clone(&callback),
            ) {
                return Ok(Self { stream, is_exclusive: false });
            }

            // Tier 5: Safe universal fallback (auto-detect all parameters from Oboe/AAudio)
            log::warn!("AAudio 48000 Hz failed, attempting universal auto-config fallback");
            let stream = Self::try_create_and_start(
                None,
                SharingMode::Shared,
                PerformanceMode::None,
                None,
                None,
                callback,
            )?;

            Ok(Self { stream, is_exclusive: false })
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
