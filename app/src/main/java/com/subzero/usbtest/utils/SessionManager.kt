package com.subzero.usbtest.utils

import android.content.Context
import android.content.SharedPreferences
import com.subzero.usbtest.Constants
import com.subzero.usbtest.R

class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE)

    companion object {
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val USER_TOKEN = "user_token"
        const val SERVER_IP = "server_ip"
    }

    /**
     * Function to save auth token
     */
    fun saveAuthToken(token: String) {
        val editor = prefs.edit()
        editor.putString(USER_TOKEN, token)
        editor.apply()
    }

    /**
     * Function to fetch auth token
     */
    fun fetchAuthToken(): String? {
        return prefs.getString(USER_TOKEN, null)
    }

    fun saveServerIp(ip: String){
        val editor = prefs.edit()
        editor.putString(SERVER_IP, ip)
        editor.apply()
    }

    fun fetchServerIp(): String? {
        return prefs.getString(SERVER_IP, null)
    }

    fun fetchWebRTCSocketUrl(): String{
        return "http://${prefs.getString(SERVER_IP, null)}:${Constants.WEBRTC_SOCKET_PORT}"
    }

    fun fetchWebRTCStunUri(): String{
        return "stun:${prefs.getString(SERVER_IP, null)}:3479"
    }

    fun fetchWebRTCTurnUri(): String{
        return "turn:${prefs.getString(SERVER_IP, null)}:3479"
    }
}