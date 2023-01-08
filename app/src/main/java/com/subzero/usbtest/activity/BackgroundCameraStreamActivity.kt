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
//  private var token: String = ""
//  private var flagRecording = false
//  private var fileRecording: String = ""
//
//  private val logService = LogService.getInstance()
//
//  private lateinit var vibrator: Vibrator

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    /* Handle uncaught exception */
    val logDir = Environment.DIRECTORY_DCIM
    Thread.setDefaultUncaughtExceptionHandler(CustomizedExceptionHandler(logDir))

    CameraStreamService.observer.observe(this) {
      service = it
      startPreview()
    }

    if(service == null){
      Log.e(TAG, "----- service is null")
    }

    var url_rtmp = "rtmp://103.160.84.179:21935/live/livestream"
    start_stop.setOnClickListener {
      if (service?.isStreaming() != true) {
        if (service?.prepare() == true) {
          service?.startStream(url_rtmp)
          start_stop.setText("Stop")
        }
      } else {
        service?.stopStream()
        start_stop.setText("Start")
      }
    }
    openglview.holder.addCallback(this)
  }
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
    if (service?.isStreaming() == true) {
      start_stop.setText("Stop")
    } else {
      start_stop.setText("Start")
    }
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

  companion object{
    const val TAG = "Main"
  }
}