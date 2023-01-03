package com.subzero.usbtest.rtc

import android.util.Log
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/***
 * Create by kgxl on 2019/7/23
 */
class SocketManager {
    private var mSocket: Socket? = null
    private var mOnConnectStateListener: onConnectStateListener? = null
    private var mOnRtcListener: onRtcListener? = null

    private constructor()

    companion object {
        val instance: SocketManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SocketManager() }
    }

    interface onConnectStateListener {
        fun connectSuccess()
        fun connectFailure(errorMsg: String)
        fun disconnect()
        fun connecting()
    }

    interface onRtcListener {
        fun userJoin(signal: String)
        fun userLeave(signal: String)
        fun receiveMsg(msg: String)
        fun result(msg: String)
    }

    fun setOnConnectStateListener(onConnectStateListener: onConnectStateListener) {
        mOnConnectStateListener = onConnectStateListener
    }

    fun setOnReceiveMsgListener(onRtcListener: onRtcListener) {
        mOnRtcListener = onRtcListener
    }

    @Throws(JSONException::class)
    fun sendMessage(to: String, type: String, payload: JSONObject?) {
        val message = JSONObject()
        message.put("to", to)
        message.put("type", type)
        message.put("payload", payload)
        mSocket?.emit("message", message)
    }

    fun sendLeaveMessage(to: String, type: String) {
        val message = JSONObject()
        message.put("to", to)
        message.put("type", type)
        mSocket?.emit("message", message)
    }

    fun connectSocket(address: String, token: String) {
        if (mSocket == null) {
            Log.e("SocketManager", token)
            var options = IO.Options()
            options.path = "/ws/socket.io"
            options.transports = arrayOf("websocket", "polling")
            options.query= "token=$token&foo=bar"

            mSocket = IO.socket(address, options)
        }

        mSocket?.on(Socket.EVENT_CONNECT) {
            mOnConnectStateListener?.connectSuccess()
        }?.on(Socket.EVENT_CONNECT_ERROR) {
            mOnConnectStateListener?.connectFailure(it[0].toString())
        }?.on(Socket.EVENT_DISCONNECT) {
            mOnConnectStateListener?.disconnect()
        }?.on("message") {
//            for(i in it){
//                Log.e("${it.size} Socket manager: message: ","${i}")
//            }

            var message = JSONArray(it)
            Log.e("Socketmanager message", message[0].toString())
            mOnRtcListener?.receiveMsg(message[0].toString())

//            if(message["type"] == "id"){
//                mOnRtcListener?.userJoin(message["payload"].toString())
//            }
//            else{
//                mOnRtcListener?.receiveMsg(message.toString())
//            }
//            Log.e("${it.size} Socket manager: message: ","${message["type"]}")

//
//            mOnRtcListener?.receiveMsg(it[0].toString())
        }?.on("id") {
//            mOnRtcListener?.userJoin(content.toString())
        }?.on("leave"){
            mOnRtcListener?.userLeave(it[0].toString())
        }?.on("newUserJoin"){
            mOnRtcListener?.receiveMsg(it[0].toString())
        }?.on("errorMsg"){
            mOnRtcListener?.result(it[0].toString())
        }
        mSocket?.connect()
    }

    fun getSocket(): Socket? {
        return mSocket
    }

    fun disconnectSocket() {
        mSocket?.disconnect()
    }
}