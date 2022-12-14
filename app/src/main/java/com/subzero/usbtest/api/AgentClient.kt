package com.subzero.usbtest.api

import android.util.Log
import com.subzero.usbtest.Constants
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AgentClient {
    private lateinit var instance: AgentService
    private lateinit var clientOkhttp: OkHttpClient
    private var baseURL = Constants.BASE_URL

    fun getClientOkhttpInstance(): OkHttpClient{
        if(!::clientOkhttp.isInitialized){
            clientOkhttp = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }

        return clientOkhttp
    }

    fun get_instance(): AgentService {
        // Initialize ApiService if not initialized yet
        if (!::instance.isInitialized) {
            val client = getClientOkhttpInstance()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseURL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()

            instance = retrofit.create(AgentService::class.java)
        }

        return instance
    }
}