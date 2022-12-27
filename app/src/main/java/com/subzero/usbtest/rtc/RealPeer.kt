package com.subzero.usbtest.rtc

import android.util.Log
import com.subzero.usbtest.rtc.SocketManager
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*

/***
 * Create by kgxl on 2019/7/23
 */
class RealPeer : PeerConnection.Observer, SdpObserver {
    val TAG = "Realpeer"
    var id: String = ""
    var pc: PeerConnection? = null

    constructor(id: String, pc: PeerConnection) {
        this.id = id
        this.pc = pc
    }

    override fun onIceCandidate(p0: IceCandidate?) {
        Log.d(TAG, "onIceCandidate: $p0")
    }

    override fun onDataChannel(p0: DataChannel?) {
        Log.d(TAG, "onDataChannel: $p0")
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        Log.d(TAG, "onIceConnectionReceivingChange: $p0")
    }

    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        Log.d(TAG, "onIceConnectionChange: $p0")
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        Log.d(TAG, "onIceGatheringChange: $p0")
    }

    override fun onAddStream(p0: MediaStream?) {
        Log.d(TAG, "onAddStream: $p0")
    }






    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        Log.d(TAG, "onSignalingChange: $p0")
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        Log.d(TAG, "onIceCandidatesRemoved")
    }

    override fun onRemoveStream(p0: MediaStream?) {
        Log.d(TAG, "onRemoveStream")
    }

    override fun onRenegotiationNeeded() {
        Log.d(TAG, "onRenegotiationNeeded")
    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        Log.d(TAG, "onAddTrack")
    }

    override fun onSetFailure(p0: String?) {
        Log.d(TAG, "onSetFailure: $p0")
    }

    override fun onSetSuccess() {
        Log.d(TAG, "onSetSuccess")
    }

    override fun onCreateSuccess(sdp: SessionDescription) {
        Log.e(TAG, "sdp-->$id")
        Log.e(TAG, "sdp-->${sdp.type.canonicalForm()}")
        try {
            val payload = JSONObject()
            payload.put("type", sdp.type.canonicalForm())
            payload.put("sdp", sdp.description)
            SocketManager.instance.sendMessage(id, sdp.type.canonicalForm(), payload)
            pc?.setLocalDescription(this, sdp)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override fun onCreateFailure(p0: String?) {
        Log.e(TAG, "realpeer: onCreateFailure -->$p0")
    }

}