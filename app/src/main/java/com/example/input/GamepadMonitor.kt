package com.example.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Something a game controller can ask the Game Space menu to do. */
enum class GamepadAction {
    PREV_TAB,
    NEXT_TAB,
    SEARCH,
    MENU,
    LAUNCH,
    LEFT,
    RIGHT,
    BACK
}

/** True when a real game controller (USB/OTG or Bluetooth) is plugged in. */
class GamepadMonitor(context: Context) : InputManager.InputDeviceListener {

    private val inputManager = context.applicationContext.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val _isConnected = MutableStateFlow(detect())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun start() {
        try {
            inputManager.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
        }
        refresh()
    }

    fun stop() {
        try {
            inputManager.unregisterInputDeviceListener(this)
        } catch (_: Exception) {
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) = refresh()

    override fun onInputDeviceRemoved(deviceId: Int) = refresh()

    override fun onInputDeviceChanged(deviceId: Int) = refresh()

    private fun refresh() {
        _isConnected.value = detect()
    }

    private fun detect(): Boolean {
        return try {
            InputDevice.getDeviceIds().any { id ->
                val device = InputDevice.getDevice(id)
                device != null && !device.isVirtual && isGamepadSource(device.sources)
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun isGamepadSource(sources: Int): Boolean =
            (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }
}
