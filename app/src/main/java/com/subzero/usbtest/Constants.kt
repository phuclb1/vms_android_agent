package com.subzero.usbtest

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build

object Constants {
    // Face search API
    const val BASE_FS_URL = "https://api-services.nextg.team/api/v1/"
//    const val API_FS_USER = "vibdemo"
//    const val API_FS_PASSWORD = "123456aA@"
    const val API_FS_LOGIN_URL = "auth/login"

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

    val RECORD_AUDIO_REQUIRED_PERMISSIONS =
        mutableListOf (
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
}