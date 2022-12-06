package com.subzero.usbtest

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build

object Constants {
    // API
//    const val BASE_URL = "https://api-services.nextg.team/api/v1/"
//    const val API_LOGIN_URL = "auth/login"
    const val BASE_URL = "http://103.160.75.240:30085/api/v1/"
    const val API_LOGIN_URL = "auth/sign-in"

    //    const val DEFAULT_RTMP_URL = "rtmp://113.161.183.245:1935/BPC_CAMJP01_abc123?user=admin&pass=Ab2C67e2021"
    const val RTMP_URL_HEADER = "rtmp://103.160.75.240/live/"

    @SuppressLint("HardwareIds")
    var serialNumber: String = Build.SERIAL.toString()

    val CAMERA_REQUIRED_PERMISSIONS =
        mutableListOf (
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
}