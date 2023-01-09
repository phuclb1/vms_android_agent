package com.subzero.usbtest.activity

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.*
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.widget.Toast
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.subzero.usbtest.Constants
import com.subzero.usbtest.R
import com.subzero.usbtest.api.AgentClient
import com.subzero.usbtest.rtc.WebRtcClient
import com.subzero.usbtest.service.CameraStreamService
import com.subzero.usbtest.utils.CustomizedExceptionHandler
import com.subzero.usbtest.utils.LogService
import com.subzero.usbtest.utils.SessionManager
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.webrtc.PeerConnection
import java.io.File
import java.io.IOException

class BackgroundCameraStreamActivity : AppCompatActivity(), SurfaceHolder.Callback {
  private var service: CameraStreamService? = null
//
//  private val webRtcManager by lazy { WebRtcClient.instance }
//
//  private val agentClient = AgentClient()
//
  private lateinit var sessionManager: SessionManager
//  private lateinit var rtmpCamera: RtmpCamera2
//
//  private val folderRecord = File(Constants.DOC_DIR, "video_record")
//
//  private var isFlipped = false
//
  private var token: String = ""
//  private var flagRecording = false
//  private var fileRecording: String = ""
//
  private val logService = LogService.getInstance()
//
//  private lateinit var vibrator: Vibrator

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
    logService.appendLog("RTMP url: $rtmpUrl", CameraStreamActivity.TAG)
    et_url.setText(rtmpUrl)

    start_stop.setOnClickListener {
      onButtonStreamClick()
    }

    openglview.holder.addCallback(this)

    updateUIStream()
  }

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
        R.id.menu_phone_cam -> {
          val intent_activity = Intent(applicationContext, CameraStreamActivity::class.java)
          intent_activity.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
          startActivity(intent_activity)
        }
        R.id.menu_usb_cam -> {
          val intent_activity = Intent(applicationContext, USBStreamActivity::class.java)
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
    logService.appendLog("call start stream", CameraStreamActivity.TAG)
    try {
      if (!service?.isStreaming()!!) {
        if (prepareEncoders() == true) {
          service!!.startStream(url)
        }
      }
    }catch (e: java.lang.Exception){
      Log.d(CameraStreamActivity.TAG, e.toString())
    }
  }

  private fun callStopStream() {
    logService.appendLog("call stop stream", CameraStreamActivity.TAG)
    try {
      service?.stopStream()
//      callStopRecord()
    }catch (e: java.lang.Exception){
      Log.d(CameraStreamActivity.TAG, e.toString())
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
    const val TAG = "Main"
  }
}