package com.subzero.usbtest

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import com.subzero.usbtest.api.AgentClient
import com.subzero.usbtest.models.LoginRequest
import com.subzero.usbtest.models.LoginResponse
import com.subzero.usbtest.rtc.WebRtcClient
import kotlinx.android.synthetic.main.activity_login.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {
    private val agentClient = AgentClient()
    private lateinit var sessionManager: SessionManager
    private val logService = LogService.getInstance()
    private var doubleBackToExitPressedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        agentClient.get_instance()
        sessionManager = SessionManager(this)

        et_username.setText("demoevn")
        et_password?.setText("123456aA@")
        tv_error_info.visibility = View.GONE

        bt_login.setOnClickListener {
//            bt_login.background = getDrawable(R.drawable.button_background_disabled)
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
//            bt_login.background = getDrawable(R.drawable.custom_button)
            return true
        }
        if(et_password.text.isNullOrBlank()){
            displayLoginErrorInfo(401)
            et_password.requestFocus()
//            bt_login.background = getDrawable(R.drawable.custom_button)
            return true
        }

        agentClient.get_instance().login(
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
//                        Toast.makeText(applicationContext, "Login failure: code ${response.code()}", Toast.LENGTH_LONG).show()
//                        bt_login.background = getDrawable(R.drawable.custom_button)
                        logService.appendLog(text = "Login failure: code ${response.code()}", tag = TAG)
                        displayLoginErrorInfo(response.code())
                    }else{
                        val authToken = loginResponse?.authToken.toString()
                        sessionManager.saveAuthToken(authToken)
                        val intent = Intent(applicationContext, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        logService.appendLog("Login success", TAG)
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Toast.makeText(applicationContext, "Login failure", Toast.LENGTH_LONG).show()
//                    bt_login.background = getDrawable(R.drawable.custom_button)
                    logService.appendLog("Login response fail", TAG)
                    displayLoginErrorInfo(1)
                }
            })

        return true
    }

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
                else -> {
                    tv_error_info.setText(R.string.err_happen)
                    tv_error_info.visibility = View.VISIBLE
                }
            }
        }
    }

    companion object{
        const val TAG = "Login"
    }
}