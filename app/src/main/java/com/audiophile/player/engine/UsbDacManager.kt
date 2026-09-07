package com.audiophile.player.engine

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbDacInfo(
    val id: Int,
    val name: String,
    val isUsbHeadset: Boolean,
    val supportedSampleRates: List<Int>,
    val supportedChannelCounts: List<Int>,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val isDirectUsbExclusiveSupported: Boolean = true
)

class UsbDacManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _connectedDac = MutableStateFlow<UsbDacInfo?>(null)
    val connectedDac: StateFlow<UsbDacInfo?> = _connectedDac.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.i("UsbDacManager", "Audio devices added: ${addedDevices?.size ?: 0}")
            refreshConnectedDac()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.i("UsbDacManager", "Audio devices removed: ${removedDevices?.size ?: 0}")
            refreshConnectedDac()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        refreshConnectedDac()
    }

    fun refreshConnectedDac() {
        try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val usbDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                it.type == AudioDeviceInfo.TYPE_DOCK
            }

            if (usbDevice != null) {
                val dacInfo = inspectAudioDevice(usbDevice)
                Log.i("UsbDacManager", "Detected USB DAC: ${dacInfo.name} (ID: ${dacInfo.id}), Supported Rates: ${dacInfo.supportedSampleRates}")
                _connectedDac.value = dacInfo
            } else {
                if (_connectedDac.value != null) {
                    Log.i("UsbDacManager", "USB DAC disconnected")
                }
                _connectedDac.value = null
            }
        } catch (e: Exception) {
            Log.e("UsbDacManager", "Error inspecting USB DAC devices", e)
        }
    }

    private fun inspectAudioDevice(device: AudioDeviceInfo): UsbDacInfo {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            device.productName?.toString()?.takeIf { it.isNotBlank() }
                ?: if (device.type == AudioDeviceInfo.TYPE_USB_HEADSET) "USB Audio Headset" else "USB DAC"
        } else {
            if (device.type == AudioDeviceInfo.TYPE_USB_HEADSET) "USB Audio Headset" else "USB DAC"
        }

        val rates = device.sampleRates.toList().filter { it > 0 }.sorted()
        val standardHiResRates = listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000, 705600, 768000)

        // If OS returns empty array (common on some OEM drivers), provide standard USB Audio Class 2.0 rates
        val finalRates = if (rates.isEmpty()) {
            standardHiResRates
        } else {
            rates
        }

        val channels = device.channelCounts.toList().filter { it > 0 }.sorted()
        val finalChannels = if (channels.isEmpty()) listOf(2) else channels

        // Match with UsbManager device list for Vendor/Product IDs
        var vendorId: Int? = null
        var productId: Int? = null

        usbManager?.deviceList?.values?.forEach { usb ->
            if (isAudioUsbDevice(usb)) {
                vendorId = usb.vendorId
                productId = usb.productId
            }
        }

        return UsbDacInfo(
            id = device.id,
            name = name,
            isUsbHeadset = device.type == AudioDeviceInfo.TYPE_USB_HEADSET,
            supportedSampleRates = finalRates,
            supportedChannelCounts = finalChannels,
            vendorId = vendorId,
            productId = productId,
            isDirectUsbExclusiveSupported = true
        )
    }

    private fun isAudioUsbDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            // USB Audio Class is 0x01
            if (iface.interfaceClass == 1) {
                return true
            }
        }
        return false
    }

    /**
     * Resolves the optimal sample rate for the connected USB DAC given a track's native sample rate.
     * If the DAC supports the exact track sample rate, it returns it for bit-perfect output.
     * Otherwise returns the closest supported integer multiple or highest supported sample rate.
     */
    fun getOptimalSampleRate(requestedRate: Int): Int {
        val dac = _connectedDac.value ?: return requestedRate.coerceAtLeast(44100)

        // Exact match
        if (dac.supportedSampleRates.contains(requestedRate)) {
            return requestedRate
        }

        // Integer multiple match (e.g. 44.1k -> 88.2k or 176.4k; 48k -> 96k or 192k)
        val multiples = dac.supportedSampleRates.filter { it % requestedRate == 0 || requestedRate % it == 0 }
        if (multiples.isNotEmpty()) {
            return multiples.maxOrNull() ?: requestedRate
        }

        // Fallback to highest supported or 48k
        return dac.supportedSampleRates.maxOrNull() ?: 48000
    }

    fun release() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }
}
