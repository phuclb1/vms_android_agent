package com.subzero.usbtest

import android.Manifest
import android.os.Build
import android.os.Environment
import java.io.File

object Constants {
    const val ENABLE_SAVE_LOG = true
    const val IS_DEFAULT_USB_CAMERA = true
    // API
    const val API_PORT = "30084"
    const val RTMP_PORT = "21935"
    const val WEBRTC_SOCKET_PORT = "30084"
    const val API_LOGIN_URL = "/api/v1/auth/sign-in"
    const val API_LOGOUT_URL = "/api/v1/auth/sign-out"
    const val API_UPLOAD_VIDEO = "/api/v1/video/upload?"
    const val TURN_USER = "admin"
    const val TURN_PASS = "admin"
    const val STUN_PORT = 3476
    const val TURN_PORT = 3476

    const val FOLDER_DOC_NAME = "VTCameraAgent"
    val DOC_DIR = Environment.getExternalStorageDirectory().absolutePath + "/$FOLDER_DOC_NAME"

    val CAMERA_REQUIRED_PERMISSIONS =
        mutableListOf (
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
//                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
}