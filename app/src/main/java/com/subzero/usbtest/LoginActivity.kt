package com.subzero.usbtest

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        agentClient.get_instance()
        sessionManager = SessionManager(this)

        et_username.setText("vms@xmas.team")
        et_password?.setText("123456a@")

        bt_login.setOnClickListener {
            bt_login.background = getDrawable(R.drawable.button_background_disabled)
            val result = onClickLogin()

//            val intent = Intent(applicationContext, MainActivity::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//            startActivity(intent)
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
            LoginRequest(user = et_username.text.toString().trim(),
                password = et_password.text.toString().trim()
            )
        )
            .enqueue(object : Callback<LoginResponse> {
                override fun onResponse(
                    call: Call<LoginResponse>,
                    response: Response<LoginResponse>
                ) {
                    Log.d(TAG,response.toString())
                    val loginResponse = response.body()
                    Log.d(TAG, loginResponse?.authToken.toString())
                    if(loginResponse?.authToken.isNullOrEmpty()){
                        Toast.makeText(applicationContext, "Login failure: code ${response.code()}", Toast.LENGTH_LONG).show()
                        bt_login.background = getDrawable(R.drawable.button_background)
                    }else{
                        val authToken = loginResponse?.authToken.toString()
                        sessionManager.saveAuthToken(authToken)
                        val intent = Intent(applicationContext, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Log.d(TAG, "Login failure ${t.message}")
                    Toast.makeText(applicationContext, "Login failure", Toast.LENGTH_LONG).show()
                    bt_login.background = getDrawable(R.drawable.button_background)
                }
            })

        return true
    }

    companion object{
        const val TAG = "Login"
    }
}