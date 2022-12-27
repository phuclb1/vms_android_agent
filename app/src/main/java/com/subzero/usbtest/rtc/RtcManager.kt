package com.subzero.usbtest.rtc

import android.content.Context
import org.webrtc.EglBase

/**
 * Create by kgxl on 2019-08-08
 */
interface RtcManager {
    fun init(
        context: Context
    )

    fun connect(address: String)

    fun startCall()
    fun startAnswer()
    fun closeCall()
    fun switchAudioMute()
    fun switchAudioMode()
    fun getIsCall(): Boolean
    fun onPause()
    fun onResume()
    fun onDestroy()
    fun onStop()
}