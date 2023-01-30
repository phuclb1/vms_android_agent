package com.subzero.usbtest.rtc

import android.content.Context
import android.media.AudioManager
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import com.subzero.usbtest.Constants
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import java.util.*


class WebRtcClient private constructor() {
    private val AUDIO_TRACK_ID = "ARDAMSa0"
    private val TAG = "WebRtcClient"
    private var isCall = false
    private val iceServers = LinkedList<PeerConnection.IceServer>()
    private val peers: HashMap<String, RealPeer> = hashMapOf()
    private var defaultMute = false

    private var fromCalling = ""

    companion object {
        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { WebRtcClient() }
    }

    private var context: Context? = null
    private var peerFactory: PeerConnectionFactory? = null
    private var mPeerConnection: PeerConnection? = null
    private var mAudioTrack: AudioTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var mediaConstraints: MediaConstraints? = null
    private var localMS: MediaStream? = null
    private val commandMap: HashMap<String, Command> = hashMapOf()
    var mPayload: JSONObject? = null

    var onIceConnectionChangeCallback = fun(x: PeerConnection.IceConnectionState) {}
    var onCallingCallback = fun(){}
    var onCallLeaveCallback = fun(){}

    private val mPeerConnectionObserver: PeerConnection.Observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            try {
                val payload = JSONObject()
                payload.put("label", candidate.sdpMLineIndex)
                payload.put("id", candidate.sdpMid)
                payload.put("candidate", candidate.sdp)

                Log.d(TAG, "fromCalling=$fromCalling   \npayload=$payload")

                SocketManager.instance.sendMessage(fromCalling, "candidate", payload)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        override fun onDataChannel(p0: DataChannel?) {
            Log.d(TAG, "------- onDataChannel")
        }

        override fun onIceConnectionReceivingChange(p0: Boolean) {
            Log.d(TAG, "------- onIceConnectionReceivingChange")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            if (state == PeerConnection.IceConnectionState.FAILED) {
                isCall = false
            }
            if (state == PeerConnection.IceConnectionState.CONNECTED) {
                isCall = true
            }
            if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
//                closePeerConnection()
//                closeCall()
            }
            if (state == PeerConnection.IceConnectionState.CLOSED) {
//                peers.clear()
//                isCall = false
//                closeCall()
            }
            Log.e(TAG, "onIceConnectionChange-->$state")

            if (state != null) {
                onIceConnectionChangeCallback(state)
            }
        }

        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
            Log.d(TAG, "------- onIceGatheringChange")
        }

        override fun onAddStream(p0: MediaStream?) {
            Log.e(TAG, "onAddStream")
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
            Log.d(TAG, "------- onSignalingChange")
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            mPeerConnection?.removeIceCandidates(candidates)
            Log.d(TAG, "------- onIceCandidatesRemoved")
        }

        override fun onRemoveStream(p0: MediaStream?) {
            mPeerConnection?.close()
            Log.d(TAG, "------- onRemoveStream")
        }

        override fun onRenegotiationNeeded() {
            Log.d(TAG, "------- onRenegotiationNeeded")
        }

        override fun onAddTrack(rtpReceiver: RtpReceiver?, p1: Array<out MediaStream>?) {
            isCall = true
            Log.d(TAG, "------- onAddTrack")
        }
    }

    fun onPause() {
    }

    fun onDestroy() {
        surfaceTextureHelper?.dispose()
        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()
    }

    fun onStop() {
    }

    fun onResume() {
    }

    fun connect(address: String, token: String) {
        commandMap["init"] = CreateOfferCommand()
        commandMap["offer"] = CreateAnswerCommand()
        commandMap["answer"] = SetRemoteSDPCommand()
        commandMap["candidate"] = AddIceCandidateCommand()
        SocketManager.instance.setOnConnectStateListener(object :
            SocketManager.onConnectStateListener {
            override fun connectSuccess() {
                Log.e(TAG, "connectSuccess")
            }

            override fun connectFailure(errorMsg: String) {
                Log.e(TAG, "connectFailure-->$errorMsg")

            }

            override fun disconnect() {
                Log.e(TAG, "disconnect")
            }

            override fun connecting() {
                Log.e(TAG, "connecting")
            }
        })
        SocketManager.instance.setOnReceiveMsgListener(object : SocketManager.onRtcListener {
            override fun result(msg: String) {
                Log.e(TAG, "result-->$msg")
            }

            override fun receiveMsg(msg: String) {
                val data = JSONObject(msg)
                Log.d(TAG, "receiveMsg: $data")
                try {
                    val type = data.optString("type")
                    val to = data.optString("to")

                    if (type == "leave"){
                        if(!to.isNullOrBlank() && to !="null") {
                            Log.e(TAG, "------> leave call: $to")
                            onRecieveMsgLeave()
                        }
                    }
                    else {
                        val from = data.optString("from")
                        var payload: JSONObject = data.getJSONObject("payload")
                        mPayload = payload
                        fromCalling = from
                        if (!peers.containsKey(from)) {
                            mPeerConnection = createPeerConnect()
                            val rt_addstream = mPeerConnection!!.addStream(localMS)
                            Log.e(TAG, "rt_addstream: $rt_addstream")
                            Log.e(
                                TAG, "mPeerConnection state ${
                                    mPeerConnection!!.signalingState().toString()
                                }"
                            )
                            val peer = RealPeer(from, mPeerConnection!!)
                            peers[from] = peer
                        }
                        if (type == "offer") {
                            onCallingCallback()
                        }
                        else {
                            commandMap[type]?.execute(from, payload)
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

            }
        })
        SocketManager.instance.connectSocket(address, token)
    }

    fun onRecieveMsgLeave(){
        onCallLeaveCallback()
        closeCall()
    }

    /**
     *
     */
    fun init(
        context: Context,
        stunUri: String,
        turnUri: String
    ) {
        this.context = context
//        iceServers.add(PeerConnection.IceServer.builder("stun:23.21.150.121").createIceServer())
//        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        iceServers.add(PeerConnection.IceServer.builder(stunUri)
            .createIceServer())
        iceServers.add(PeerConnection.IceServer.builder(turnUri)
            .setUsername(Constants.TURN_USER)
            .setPassword(Constants.TURN_PASS)
            .createIceServer())

        //peerConnectFactory
        peerFactory = createPeerFactory(context)
        Log.e(TAG, Logging.Severity.LS_VERBOSE.toString())

        val audioSource = peerFactory?.createAudioSource(createAudioConstraints())
        mAudioTrack = peerFactory?.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        mAudioTrack?.setEnabled(true)

        mediaConstraints = MediaConstraints()
        mediaConstraints?.apply {
            this.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            this.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }
        initLocalStream()
    }

    private fun initLocalStream() {
        localMS = peerFactory?.createLocalMediaStream("ARDAMS")
        localMS?.addTrack(mAudioTrack)
    }

    private fun createAudioConstraints(): MediaConstraints {
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        return audioConstraints
    }

    fun startAnswer(){
        if (peers.containsKey(fromCalling))
        {
            commandMap["offer"]?.execute(fromCalling, mPayload)
        }else{
            Log.d(TAG, "====== not init cause peer contained key $fromCalling")
        }
    }

    fun closeCall(){
        if (peers.containsKey(fromCalling)){
            SocketManager.instance.sendLeaveMessage(fromCalling, "leave")
            closePeerConnection()
        }
    }

    private fun closePeerConnection(){
        Log.e(TAG, "------- closePeerConnection")
        peers.clear()
        isCall = false
        mPeerConnection?.close()
    }

    private fun createPeerConnect(): PeerConnection {
        val configuration = PeerConnection.RTCConfiguration(iceServers)
        val connection =
            peerFactory?.createPeerConnection(configuration, mPeerConnectionObserver)
        connection!!.addTrack(mAudioTrack)
        Log.i(TAG, "Create PeerConnection ...${connection.toString()}")
        return connection
    }

    private fun createPeerFactory(context: Context): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        val builder = PeerConnectionFactory.builder()
        builder.setOptions(null)
        return builder.createPeerConnectionFactory()
    }

    private interface Command {
        @Throws(JSONException::class)
        fun execute(peerId: String, payload: JSONObject?)
    }

    private inner class CreateOfferCommand : Command {

        @Throws(JSONException::class)
        override fun execute(peerId: String, payload: JSONObject?) {
            Log.d(TAG, "CreateOfferCommand")
            val peer = peers[peerId]
            peer?.pc?.createOffer(peer, mediaConstraints)
        }
    }

    private inner class CreateAnswerCommand : Command {
        @Throws(JSONException::class)
        override fun execute(peerId: String, payload: JSONObject?) {
            Log.d(TAG, "CreateAnswerCommand: peerId=$peerId  \n payload=$payload")
            val peer = peers[peerId]
            val sdp = SessionDescription(
                SessionDescription.Type.fromCanonicalForm(payload?.getString("type")),
                payload?.getString("sdp")
            )
            Log.e(TAG, "set remote des: type=${sdp.type}   sdp=${sdp.description}")
            peer?.pc?.setRemoteDescription(peer, sdp)
            peer?.pc?.createAnswer(peer, mediaConstraints)
        }
    }

    private inner class SetRemoteSDPCommand : Command {
        @Throws(JSONException::class)
        override fun execute(peerId: String, payload: JSONObject?) {
            Log.d(TAG, "SetRemoteSDPCommand")
            val peer = peers[peerId]
            val sdp = SessionDescription(
                SessionDescription.Type.fromCanonicalForm(payload?.getString("type")),
                payload?.getString("sdp")
            )
            peer?.pc?.setRemoteDescription(peer, sdp)
        }
    }

    private inner class AddIceCandidateCommand : Command {
        @Throws(JSONException::class)
        override fun execute(peerId: String, payload: JSONObject?) {
            Log.d(TAG, "AddIceCandidateCommand")
            val pc = peers[peerId]?.pc
            if (pc?.remoteDescription != null) {
                val candidate = IceCandidate(
                    payload?.getString("id"),
                    payload?.getInt("label") ?: 0,
                    payload?.getString("candidate")
                )
                pc.addIceCandidate(candidate)
            }else{
                Log.e(TAG, "AddIceCandidateCommand fail, cause pc?.getRemoteDescription() == null")
            }
        }
    }

    fun switchAudioMute() {
        defaultMute = !defaultMute
        mAudioTrack?.setEnabled(!defaultMute)
    }

    fun switchAudioMode() {
        val am = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d(TAG,"before audio mode--->" + am.isWiredHeadsetOn + "---" + am.mode + "----" + am.isSpeakerphoneOn)
//        if (!am.isWiredHeadsetOn) {
//            am.isSpeakerphoneOn = !am.isSpeakerphoneOn
//            am.mode = if (am.mode == AudioManager.MODE_NORMAL) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        if(am.mode == AudioManager.MODE_NORMAL){
            am.mode = AudioManager.MODE_IN_COMMUNICATION
        }else{
            am.mode = AudioManager.MODE_NORMAL
        }

//        }

        Log.d(TAG, "after audio mode--->" + am.isWiredHeadsetOn + "---" + am.mode + "----" + am.isSpeakerphoneOn)
    }

    fun getIsCall(): Boolean{
        return isCall
    }
}