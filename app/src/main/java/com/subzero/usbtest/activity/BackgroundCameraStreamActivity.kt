package com.subzero.usbtest.activity

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.view.View.OnClickListener
import com.serenegiant.utils.UIThreadHelper
import com.subzero.usbtest.Constants
import com.subzero.usbtest.R
import com.subzero.usbtest.rtc.WebRtcClient
import com.subzero.usbtest.service.CameraStreamService
import com.subzero.usbtest.utils.CustomizedExceptionHandler
import com.subzero.usbtest.utils.LogService
import com.subzero.usbtest.utils.SessionManager
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.webrtc.PeerConnection

class BackgroundCameraStreamActivity : AppCompatActivity(), SurfaceHolder.Callback {
  private var service: CameraStreamService? = null
  private lateinit var sessionManager: SessionManager

  private var token: String = ""
  private val logService = LogService.getInstance()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    /* Handle uncaught exception */
    val logDir = Environment.DIRECTORY_DCIM
    Thread.setDefaultUncaughtExceptionHandler(CustomizedExceptionHandler(logDir))

    if (!hasPermissions()) {
      ActivityCompat.requestPermissions(this, Constants.CAMERA_REQUIRED_PERMISSIONS, 1)
    }

    /* Service observer */
    CameraStreamService.observer.observe(this) {
      service = it
      startPreview()
    }

    layout_no_camera_found.visibility = View.GONE

    sessionManager = SessionManager(this)
    token = sessionManager.fetchAuthToken().toString()
    var rtmpUrl = "rtmp://${sessionManager.fetchServerIp().toString()}:${Constants.RTMP_PORT}/live/$token"
//    rtmpUrl = "rtmp://192.168.100.2:1935/live/livestream"
//    rtmpUrl = "rtmp://103.160.84.179:21935/live/livestream"
    logService.appendLog("RTMP url: $rtmpUrl", TAG)
    et_url.setText(rtmpUrl)

    setButtonClickListener()

    openglview.holder.addCallback(this)

    updateUIStream()

//    service?.onIceConnectionChangeCallback = fun(state){
//      runOnUiThread {
//        if (state == PeerConnection.IceConnectionState.CONNECTED) {
//          layout_calling.visibility = View.INVISIBLE
//        }
//        if (state == PeerConnection.IceConnectionState.CLOSED) {
//          layout_calling.visibility = View.GONE
//        }
//      }
//    }
//    service?.onCallingCallback = fun(){
//      Log.d(TAG, "========== activity: oncalling")
//      runOnUiThread {
//        layout_calling.visibility = View.VISIBLE
//      }
//    }
//    service?.onCallLeaveCallback = fun(){
//        runOnUiThread {
//          end_call_btn.visibility = View.GONE
//        }
//    }
  }

  override fun onBackPressed() {
    super.onBackPressed()
    val intent = Intent(applicationContext, LoginActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
  }

  private fun setButtonClickListener(){
    start_stop.setOnClickListener { onButtonStreamClick() }
//    decline_call_btn.setOnClickListener { onDeclineCall() }
//    accept_call_btn.setOnClickListener { onAcceptCall() }
//    end_call_btn.setOnClickListener { onEndCall() }
  }

  /**
   * Calling phone
   */
//  private fun onDeclineCall(){
//    service?.onDeclineCall()
//  }
//
//  private fun onAcceptCall(){
//    service?.onAcceptCall()
//    runOnUiThread {
//      end_call_btn.visibility = View.VISIBLE
//    }
//  }
//
//  private fun onEndCall(){
//    service?.onEndCall()
//    runOnUiThread {
//      end_call_btn.visibility = View.GONE
//    }
//  }

  /**
   * Option Menu
   */
  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_toolbar, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item != null) {
      when(item.itemId){
        R.id.menu_setting -> {}
        R.id.menu_about -> {}
        R.id.menu_background_phone_cam -> {
          val intent_activity = Intent(applicationContext, BackgroundCameraStreamActivity::class.java)
          intent_activity.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
          startActivity(intent_activity)
        }
        R.id.menu_background_usb_cam -> {
          val intent_activity = Intent(applicationContext, BackgroundUSBStreamActivity::class.java)
          intent_activity.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
          startActivity(intent_activity)
        }
      }
    }
    return super.onOptionsItemSelected(item)
  }

  /**
   * Surface event
   */
  override fun surfaceChanged(holder: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
    startPreview()
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    service?.setView(this)
    if (service?.isOnPreview() == true) service?.stopPreview()
  }

  override fun surfaceCreated(holder: SurfaceHolder) {

  }

  override fun onResume() {
    super.onResume()
    if (!isMyServiceRunning(CameraStreamService::class.java)) {
      val intent = Intent(applicationContext, CameraStreamService::class.java)
      startService(intent)
    }
    updateUIStream()
  }

  override fun onPause() {
    super.onPause()
    if (!isChangingConfigurations) { //stop if no rotation activity
      if (service?.isOnPreview() == true) service?.stopPreview()
      if (service?.isStreaming() != true) {
        service = null
        stopService(Intent(applicationContext, CameraStreamService::class.java))
      }
    }
  }

  @Suppress("DEPRECATION")
  private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
      if (serviceClass.name == service.service.className) {
        return true
      }
    }
    return false
  }

  private fun startPreview() {
    if (openglview.holder.surface.isValid) {
      service?.setView(openglview)
    }
    else{
      Log.e(TAG, "start preview fail")
    }
    //check if onPreview and if surface is valid
    if (service?.isOnPreview() != true && openglview.holder.surface.isValid) {
      service?.startPreview()
    }
    else{
      Log.e(TAG, "start preview fail 2")
    }
  }

  /**
   * Button event
   */

  private fun onButtonStreamClick(){
    if (!service?.isStreaming()!!) {
      callStartStream(et_url.text.toString())
    } else {
      callStopStream()
    }
    updateUIStream()
  }

  /**
   * RTMP Stream Start/Stop
   */
  private fun callStartStream(url: String) {
    logService.appendLog("call start stream", TAG)
    try {
      if (!service?.isStreaming()!!) {
        if (prepareEncoders() == true) {
          service!!.startStream(url)
        }
      }
    }catch (e: java.lang.Exception){
      Log.d(TAG, e.toString())
    }
  }

  private fun callStopStream() {
    logService.appendLog("call stop stream", TAG)
    try {
      service?.stopStream()
    }catch (e: java.lang.Exception){
      Log.d(TAG, e.toString())
    }
  }

  private fun prepareEncoders(): Boolean? {
    return service?.prepare()
  }

  /**
   * Update UI
   */
  private fun updateUIStream(){
    runOnUiThread {
      if (service?.isStreaming() == true) {
        start_stop.background = getDrawable(R.drawable.custom_oval_button_2)
        start_stop.text = getString(R.string.stop)
        rotate_btn.visibility = View.INVISIBLE
        flip_btn.visibility = View.INVISIBLE
      } else {
        start_stop.background = getDrawable(R.drawable.custom_oval_button_1)
        start_stop.text = getString(R.string.start)
        rotate_btn.visibility = View.VISIBLE
        flip_btn.visibility = View.VISIBLE
      }
    }
  }

  /**
   * Check permission
   */
  private fun hasPermissions(): Boolean {
    for (permission in Constants.CAMERA_REQUIRED_PERMISSIONS) {
      if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, permission)) {
        return false
      }
    }
    return true
  }


  companion object{
    const val TAG = "BackgroundCameraStreamActivity"
  }

}