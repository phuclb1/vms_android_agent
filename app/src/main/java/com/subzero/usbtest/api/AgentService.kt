package com.subzero.usbtest.api

import com.subzero.usbtest.Constants
import com.subzero.usbtest.models.LoginRequest
import com.subzero.usbtest.models.LoginResponse
import com.subzero.usbtest.models.LogoutResponse
import retrofit2.Call
import retrofit2.http.*

interface AgentService {
    /*
       POST METHOD
    */
    @Headers(
        "Content-type:application/json"
    )
    @POST(Constants.API_LOGIN_URL)
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @Headers(
        "Content-type:application/json"
    )
    @GET(Constants.API_LOGOUT_URL)
    fun logout(): Call<LogoutResponse>
}