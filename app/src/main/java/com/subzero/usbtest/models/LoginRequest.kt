package com.subzero.usbtest.models

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("account")
    var account: String,
    @SerializedName("password")
    var password: String,
    @SerializedName("call_token")
    var call_token: String
)
