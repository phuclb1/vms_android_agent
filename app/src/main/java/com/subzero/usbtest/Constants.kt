package com.subzero.usbtest

import android.Manifest
import android.os.Build
import android.os.Environment
import java.io.File

object Constants {
    const val ENABLE_SAVE_LOG = true
    const val IS_DEFAULT_USB_CAMERA = true
    // API
    const val BASE_URL = "http://103.174.213.14:30084/api/v1/"
    const val API_LOGIN_URL = "auth/sign-in"
    const val API_UPLOAD_VIDEO = "video/upload?"

    const val RTMP_URL_HEADER = "rtmp://103.174.213.14:21935/live/"

    const val WEBRTC_SOCKET_SERVER = "http://103.174.213.14:30084"
    const val STUN_URI = "stun:103.160.84.179:3479"
    const val TURN_URI = "turn:103.160.84.179:3479"
    const val TURN_USER = "admin1"
    const val TURN_PASS = "admin1"

    const val FOLDER_DOC_NAME = "VTCameraAgent"
    val DOC_DIR = Environment.getExternalStorageDirectory().absolutePath + "/$FOLDER_DOC_NAME"
    val RECORD_FOLDER = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + File.separator + "UsbStream")

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