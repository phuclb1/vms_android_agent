package com.subzero.usbtest.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.*
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.view.View.OnClickListener
import android.widget.Toast
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.utils.UIThreadHelper
import com.subzero.usbtest.Constants
import com.subzero.usbtest.R
import com.subzero.usbtest.rtc.WebRtcClient
import com.subzero.usbtest.service.USBStreamService
import com.subzero.usbtest.utils.CustomizedExceptionHandler
import com.subzero.usbtest.utils.LogService
import com.subzero.usbtest.utils.SessionManager
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.webrtc.PeerConnection

class BackgroundUSBStreamActivity : Activity(), SurfaceHolder.Callback {
  private lateinit var sessionManager: SessionManager

  private var token: String = ""
  private val logService = LogService.getInstance()

  private lateinit var usbMonitor: USBMonitor
  private val width = 1280
  private val height = 720
  private var isUsbOpen = false

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

    usbMonitor = USBMonitor(this, onDeviceConnectListener)
    usbMonitor.register()

    USBStreamService.init(this, openglview)

    layout_no_camera_found.visibility = View.VISIBLE

    openglview.holder.addCallback(this)

    sessionManager = SessionManager(this)
    token = sessionManager.fetchAuthToken().toString()
    var rtmpUrl = "rtmp://${sessionManager.fetchServerIp().toString()}:${Constants.RTMP_PORT}/live/$token"
    rtmpUrl = "rtmp://103.160.84.179:21935/live/livestream"
    logService.appendLog("RTMP url: $rtmpUrl", BackgroundCameraStreamActivity.TAG)
    et_url.setText(rtmpUrl)

    setButtonClickListener()

    updateUIStream(USBStreamService.isStreaming())
  }

  override fun onDestroy() {
    super.onDestroy()
    usbMonitor.unregister()
  }

  override fun onBackPressed() {
    super.onBackPressed()
    val intent = Intent(applicationContext, LoginActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
    stopService(Intent(applicationContext, USBStreamService::class.java))
  }

  private fun setButtonClickListener(){
    start_stop.setOnClickListener { onButtonStreamClick() }
//    decline_call_btn.setOnClickListener { onDeclineCall() }
//    accept_call_btn.setOnClickListener { onAcceptCall() }
//    end_call_btn.setOnClickListener { onEndCall() }
  }

  /**
   * Button event
   */

  private fun onButtonStreamClick(){
    if (isMyServiceRunning(USBStreamService::class.java)) {
      stopService(Intent(applicationContext, USBStreamService::class.java))
      updateUIStream(false)
    } else {
      val intent = Intent(applicationContext, USBStreamService::class.java)
      intent.putExtra("endpoint", et_url.text.toString())
      startService(intent)
      updateUIStream(true)
    }
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
    USBStreamService.setView(openglview)
//    USBStreamService.startPreview()
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    USBStreamService.setView(applicationContext)
    USBStreamService.stopPreview()
  }

  override fun surfaceCreated(holder: SurfaceHolder) {

  }

  override fun onResume() {
    super.onResume()
    if (isMyServiceRunning(USBStreamService::class.java)) {
      updateUIStream(USBStreamService.isStreaming())
    } else {
      updateUIStream(false)
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

  /**
   * USB Monitor
   */
  private val onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
    override fun onAttach(device: UsbDevice?) {
      logService.appendLog("onDeviceConnectListener ---- onAttach", TAG)
      if (device != null) {
        layout_no_camera_found.visibility = View.INVISIBLE
        usbMonitor.requestPermission(device)
      }
    }

    override fun onConnect(
      device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?,
      createNew: Boolean
    ) {
      if (device != null) {
        logService.appendLog("onDeviceConnectListener ---- ${device.deviceName}", TAG)
      }else{
        logService.appendLog("onDeviceConectListener ---- device null", TAG)
        layout_no_camera_found.visibility = View.VISIBLE
        return
      }

      if(!USBStreamService.isUVCCameraAvailable()) {
        val camera = UVCCamera()
        logService.appendLog("onDeviceConectListener ---- ${ctrlBlock.toString()}", TAG)
        camera.open(ctrlBlock)
        try {
          camera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG)
        } catch (e: IllegalArgumentException) {
          logService.appendLog(
            "onDeviceConectListener --- setPreviewSize camera ---- ${e.toString()}",
            TAG
          )
          camera.destroy()
          try {
            camera.setPreviewSize(width, height, UVCCamera.DEFAULT_PREVIEW_MODE)
          } catch (e1: IllegalArgumentException) {
            logService.appendLog(
              "onDeviceConectListener --- setPreviewSize camera ---- ${e1.toString()}",
              TAG
            )
            return
          }
        }
        USBStreamService.setView(openglview)
        USBStreamService.setUVCCamera(camera)
        USBStreamService.startPreview()
      }

      isUsbOpen = true
      logService.appendLog("onDeviceConectListener --- success ", TAG)
      layout_no_camera_found.visibility = View.INVISIBLE
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
      logService.appendLog("MainActivity onDisconnect", TAG)
      layout_no_camera_found.visibility = View.VISIBLE
//      if (uvcCamera != null) {
        updateUIStream(false)
//        callStopStream()

        USBStreamService.closeUVCCamera()
        isUsbOpen = false
//      }
    }

    override fun onDettach(device: UsbDevice?) {
      logService.appendLog("MainActivity onDetach", TAG)
      USBStreamService.closeUVCCamera()
      isUsbOpen = false
    }

    override fun onCancel(device: UsbDevice?) {
      logService.appendLog("MainActivity onCancel", TAG)
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

  /**
   * Update UI
   */
  private fun updateUIStream(isStreaming: Boolean){
    runOnUiThread {
      if (isStreaming) {
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


  companion object{
    const val TAG = "BackgroundUSBStreamActivity"
  }

}