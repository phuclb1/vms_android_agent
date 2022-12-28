package com.subzero.usbtest

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import com.subzero.usbtest.api.AgentClient
import com.subzero.usbtest.models.LoginRequest
import com.subzero.usbtest.models.LoginResponse
import kotlinx.android.synthetic.main.activity_login.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {
    private val agentClient = AgentClient()
    private lateinit var sessionManager: SessionManager
    private val logService = LogService.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        agentClient.get_instance()
        sessionManager = SessionManager(this)

        et_username.setText("vsmart")
        et_password?.setText("123456aA@")
        tv_error_info.visibility = View.GONE

        bt_login.setOnClickListener {
            bt_login.background = getDrawable(R.drawable.button_background_disabled)
            val result = onClickLogin()
        }
    }

    private fun onClickLogin():Boolean{
        if(et_username.text.isNullOrBlank()){
            Toast.makeText(this, "Please enter username!", Toast.LENGTH_SHORT).show()
            et_username.requestFocus()
            bt_login.background = getDrawable(R.drawable.button_background)
            return true
        }
        if(et_password.text.isNullOrBlank()){
            Toast.makeText(this, "Please enter password!", Toast.LENGTH_SHORT).show()
            et_password.requestFocus()
            bt_login.background = getDrawable(R.drawable.button_background)
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
                        Toast.makeText(applicationContext, "Login failure: code ${response.code()}", Toast.LENGTH_LONG).show()
                        bt_login.background = getDrawable(R.drawable.button_background)
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
                    bt_login.background = getDrawable(R.drawable.button_background)
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
                    tv_error_info.visibility = View.GONE
                }
            }
        }
    }

    companion object{
        const val TAG = "Login"
    }
}