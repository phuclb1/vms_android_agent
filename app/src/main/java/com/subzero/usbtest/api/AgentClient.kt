package com.subzero.usbtest.api

import com.subzero.usbtest.Constants
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AgentClient {
    private lateinit var instance: AgentService
    private var baseURL = Constants.BASE_FS_URL
    fun get_instance(): AgentService {

        // Initialize ApiService if not initialized yet
        if (!::instance.isInitialized) {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseURL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            instance = retrofit.create(AgentService::class.java)
        }

        return instance
    }
}