package com.subzero.usbtest.models

import com.google.gson.annotations.SerializedName

data class LogoutResponse(
    @SerializedName("status")
    var status: Boolean
)
