package com.subzero.usbtest.webrtc

import android.content.Context

/**
 * Create by kgxl on 2019-08-08
 */
class RtcSdkManager : RtcManager {
companion object{
    val instance: RtcSdkManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RtcSdkManager() }
}
    override fun init(
        context: Context
    ) {
        WebRtcClient.instance.init(context)
    }

    override fun connect(address: String) {
        WebRtcClient.instance.connect(address)
    }

    override fun startCall() {
        WebRtcClient.instance.startCall()
    }

    override fun startAnswer() {
        WebRtcClient.instance.startAnswer()
    }

    override fun endCall() {
        WebRtcClient.instance.endCall()
    }

    override fun switchAudioMute() {
        WebRtcClient.instance.switchAudioMute()
    }

    override fun switchAudioMode() {
        WebRtcClient.instance.switchAudioMode()
    }

    override fun getIsCall(): Boolean {
        return WebRtcClient.instance.getIsCall()
    }

    override fun onPause() {
        WebRtcClient.instance.onPause()
    }

    override fun onResume() {
        WebRtcClient.instance.onResume()
    }

    override fun onDestroy() {
        WebRtcClient.instance.onDestroy()
    }

    override fun onStop() {
        WebRtcClient.instance.onStop()
    }
}