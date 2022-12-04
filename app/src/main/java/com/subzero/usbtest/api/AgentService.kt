package com.subzero.usbtest.api

import com.subzero.usbtest.models.LoginRequest
import com.subzero.usbtest.models.LoginResponse
import com.subzero.usbtest.Constants
import retrofit2.Call
import retrofit2.http.*

interface AgentService {
    /*
       POST METHOD
    */
    @POST(Constants.API_FS_LOGIN_URL)
    fun login(@Body request: LoginRequest): Call<LoginResponse>
}