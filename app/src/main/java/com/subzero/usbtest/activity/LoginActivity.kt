package com.subzero.usbtest.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import com.subzero.usbtest.Constants
import com.subzero.usbtest.R
import com.subzero.usbtest.api.AgentClient
import com.subzero.usbtest.models.LoginRequest
import com.subzero.usbtest.models.LoginResponse
import com.subzero.usbtest.utils.LogService
import com.subzero.usbtest.utils.SessionManager
import kotlinx.android.synthetic.main.activity_login.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class LoginActivity : AppCompatActivity() {
    private val agentClient = AgentClient()
    private lateinit var sessionManager: SessionManager
    private val logService = LogService.getInstance()
    private var doubleBackToExitPressedOnce = false
    private lateinit var intent_activity : Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val dir = File(
            Environment.getExternalStorageDirectory(),
            Constants.FOLDER_DOC_NAME
        )
        if (!dir.exists()) {
            dir.mkdirs()
        }

        sessionManager = SessionManager(this)

        intent_activity = if(Constants.IS_DEFAULT_USB_CAMERA) {
            Intent(applicationContext, BackgroundUSBStreamActivity::class.java)
        }
        else {
            Intent(applicationContext, BackgroundCameraStreamActivity::class.java)
        }

        et_username.setText(sessionManager.fetchUserLogin())
        et_password?.setText(sessionManager.fetchPassLogin())

        et_ip_stream_server.setText(sessionManager.fetchServerIp())
        tv_error_info.visibility = View.GONE

        bt_login.setOnClickListener {
            val result = onClickLogin()
        }
    }

    override fun onBackPressed() {
        if(this.doubleBackToExitPressedOnce) {
            super.onBackPressed()
            finishAndRemoveTask()
            return
        }
        this.doubleBackToExitPressedOnce = true
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed(Runnable { doubleBackToExitPressedOnce = false }, 2000)
    }

    private fun onClickLogin():Boolean{
        if(et_username.text.isNullOrBlank()){
            displayLoginErrorInfo(404)
            et_username.requestFocus()
            return true
        }
        if(et_password.text.isNullOrBlank()){
            displayLoginErrorInfo(401)
            et_password.requestFocus()
            return true
        }
        if(et_ip_stream_server.text.isNullOrBlank()){
            displayLoginErrorInfo(2)
            et_ip_stream_server.requestFocus()
            return true
        }

        agentClient.setUrl(et_ip_stream_server.text.toString())
        agentClient.create().login(
            LoginRequest(account = et_username.text.toString().trim(),
                password = et_password.text.toString().trim()
            )
        )
            .enqueue(object : Callback<LoginResponse> {
                override fun onResponse(
                    call: Call<LoginResponse>,
                    response: Response<LoginResponse>
                ) {
                    logService.appendLog(text = response.toString(), tag = TAG)
                    val loginResponse = response.body()
                    logService.appendLog(text = loginResponse?.authToken.toString(), tag = TAG)
                    if(loginResponse?.authToken.isNullOrEmpty()){
                        logService.appendLog(text = "Login failure: code ${response.code()}", tag = TAG)
                        displayLoginErrorInfo(response.code())
                    }else{
                        val authToken = loginResponse?.authToken.toString()
                        sessionManager.saveAuthToken(authToken)
                        val serverIp = et_ip_stream_server.text
                        sessionManager.saveServerIp(serverIp.toString())
                        sessionManager.saveUserLogin(et_username.text.toString().trim())
                        sessionManager.savePassLogin(et_password.text.toString().trim())

                        intent_activity.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent_activity)

                        logService.appendLog("Login success", TAG)
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Toast.makeText(applicationContext, "Login failure", Toast.LENGTH_LONG).show()
                    logService.appendLog("Login response fail", TAG)
                    displayLoginErrorInfo(1)
                }
            })

        return true
    }

    @SuppressLint("SetTextI18n")
    private fun displayLoginErrorInfo(code: Int){
        runOnUiThread {
            when(code){
                401 -> {
                    tv_error_info.setText(R.string.wrong_password)
                    tv_error_info.visibility = View.VISIBLE
                }
                404 -> {
                    tv_error_info.setText(R.string.wrong_username)
                    tv_error_info.visibility = View.VISIBLE
                }
                1 -> {
                    tv_error_info.setText(R.string.lost_connect_err)
                    tv_error_info.visibility = View.VISIBLE
                }
                2 -> {
                    tv_error_info.setText(R.string.err_ip_stream_server_empty)
                    tv_error_info.visibility = View.VISIBLE
                }
                else -> {
                    tv_error_info.setText("Đã xảy ra lỗi: $code")
                    tv_error_info.visibility = View.VISIBLE
                }
            }
        }
    }

    companion object{
        const val TAG = "Login"
    }
}