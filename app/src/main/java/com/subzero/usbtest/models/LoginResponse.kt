package com.subzero.usbtest.models

import com.google.gson.annotations.SerializedName

data class  LoginResponse(
    @SerializedName("access_token")
    var authToken: String
)

