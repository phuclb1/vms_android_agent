package com.subzero.usbtest.models

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("username")
    var user: String,
    @SerializedName("password")
    var password: String
)
