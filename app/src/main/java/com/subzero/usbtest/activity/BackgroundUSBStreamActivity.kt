package com.subzero.usbtest.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.*
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.widget.Toast
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.subzero.usbtest.Constants
import com.subzero.usbtest.R
import com.subzero.usbtest.api.AgentClient
import com.subzero.usbtest.models.LoginRequest
import com.subzero.usbtest.models.LoginResponse
import com.subzero.usbtest.rtc.WebRtcClient
import com.subzero.usbtest.service.CameraStreamService
import com.subzero.usbtest.service.USBStreamService
import com.subzero.usbtest.utils.CustomizedExceptionHandler
import com.subzero.usbtest.utils.LogService
import com.subzero.usbtest.utils.SessionManager
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.webrtc.PeerConnection
import retrofit2.Callback
import java.io.IOException
import java.util.*


class BackgroundUSBStreamActivity : AppCompatActivity(), SurfaceHolder.Callback {
  private lateinit var sessionManager: SessionManager

  private val webRtcManager by lazy { WebRtcClient.instance }
  private val agentClient = AgentClient()
  private lateinit var vibrator: Vibrator

  private var token: String = ""
  private val logService = LogService.getInstance()

  private lateinit var usbMonitor: USBMonitor
  private val width = 1920
  private val height = 1080
  private var isUsbOpen = false
  private var isFlipped = false
  private var isRotated = false

  private var doubleBackToExitPressedOnce = false

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

    /* Usb monitor */
    usbMonitor = USBMonitor(this, onDeviceConnectListener)
    usbMonitor.register()

    USBStreamService.init(this, openglview)

    layout_no_camera_found.visibility = View.VISIBLE
    openglview.holder.addCallback(this)

    sessionManager = SessionManager(this)
    token = sessionManager.fetchAuthToken().toString()

    generateStreamRtmpUrl()

    setButtonClickListener()

    updateUIStream(USBStreamService.isStreaming())

    /* Voice Call */
    vibrator = this.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    val stunUri = sessionManager.fetchWebRTCStunUri()
    val turnUri = sessionManager.fetchWebRTCTurnUri()
    webRtcManager.init(this, stunUri, turnUri)
    webRtcManager.connect(sessionManager.fetchWebRTCSocketUrl(), token)
    webRtcManager.onIceConnectionChangeCallback = fun(state) { onIceConnectionChangeCallback(state) }
    webRtcManager.onCallingCallback = fun(){ onCallingCallback() }
    webRtcManager.onCallLeaveCallback = fun(){ onCallLeaveCallback() }
  }

  override fun onDestroy() {
    super.onDestroy()
    usbMonitor.unregister()
    callLogoutApi(false)
  }

  override fun onBackPressed() {
    if(this.doubleBackToExitPressedOnce) {
      super.onBackPressed()
      callLogoutApi(true)
      return
    }
    this.doubleBackToExitPressedOnce = true
    Toast.makeText(this, "Please click BACK again to LOGOUT", Toast.LENGTH_SHORT).show()
    Handler(Looper.getMainLooper()).postDelayed(Runnable { doubleBackToExitPressedOnce = false }, 2000)
  }

  private fun callLogoutApi(isBackIntent: Boolean){
    val baseURL = "http://${sessionManager.fetchServerIp().toString()}:${Constants.API_PORT}${Constants.API_LOGOUT_URL}"
    val request = Request.Builder()
      .url(baseURL)
      .method("GET", null)
      .addHeader("Authorization", "Bearer $token")
      .build()

    agentClient.getClientOkhttpInstance().newCall(request).enqueue(object: okhttp3.Callback {
      override fun onFailure(call: okhttp3.Call, e: IOException) {
        Toast.makeText(applicationContext, "Logout failure", Toast.LENGTH_LONG).show()
        logService.appendLog("Logout response fail", TAG)
      }
      override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
        val responseData = response.body().toString()
        Log.d(TAG, "------ logout: $responseData")

        if(isBackIntent){
          backToLoginActivity()
        }
      }
    })
  }

  private fun backToLoginActivity(){
    val intent = Intent(applicationContext, LoginActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    stopService(Intent(applicationContext, USBStreamService::class.java))
    startActivity(intent)
  }

  private fun generateStreamRtmpUrl(){
    var rtmpUrl = "rtmp://${sessionManager.fetchServerIp().toString()}:${Constants.RTMP_PORT}/live/$token"
    logService.appendLog("RTMP url: $rtmpUrl", TAG)
    et_url.setText(rtmpUrl)
  }

  private fun setButtonClickListener(){
    start_stop.setOnClickListener { onButtonStreamClick() }
    rotate_btn.setOnClickListener { onRotateClick() }
    flip_btn.setOnClickListener { onFlipClick() }

    decline_call_btn.setOnClickListener { onDeclineCall() }
    accept_call_btn.setOnClickListener { onAcceptCall() }
    end_call_btn.setOnClickListener { onEndCall() }
    speaker_btn.setOnClickListener{ onSwitchSpeaker()}
  }

  /**
   * Calling phone
   */
  private fun onDeclineCall(){
    webRtcManager.closeCall()
    if(vibrator.hasVibrator()){
      vibrator.cancel()
    }
  }

  private fun onAcceptCall(){
    webRtcManager.startAnswer()
    end_call_btn.visibility = View.VISIBLE
    speaker_btn.visibility = View.VISIBLE
    if(vibrator.hasVibrator()){
      vibrator.cancel()
    }

    webRtcManager.setSpeakerOn(true)
    speaker_btn.setBackgroundResource(R.drawable.speaker_up_24)

    USBStreamService.startStreamRtpWithoutAudio()
  }

  private fun onSwitchSpeaker(){
    webRtcManager.switchSpeakerMode()
    if(webRtcManager.getSpeakerStatus()){
      speaker_btn.setBackgroundResource(R.drawable.speaker_up_24)
    }else{
      speaker_btn.setBackgroundResource(R.drawable.speaker_down_24)
    }
  }

  private fun onEndCall(){
    webRtcManager.closeCall()
    runOnUiThread {
      end_call_btn.visibility = View.GONE
      speaker_btn.visibility = View.GONE
    }

    USBStreamService.startStreamRtpWithAudio()
  }

  private fun onIceConnectionChangeCallback(state: PeerConnection.IceConnectionState){
    Log.d(TAG, "------ callback: onPeerConectionChange $state")
    runOnUiThread {
      if (state == PeerConnection.IceConnectionState.CONNECTED) {
        layout_calling.visibility = View.INVISIBLE
      }
      if (state == PeerConnection.IceConnectionState.CLOSED){
        layout_calling.visibility = View.GONE
        if(vibrator.hasVibrator()){
          vibrator.cancel()
        }
      }
      else {

      }
    }
  }

  private fun onCallingCallback(){
    Log.d(TAG, "------ callback: onCalling")
    val pattern = longArrayOf(1500, 800, 800, 800)
    runOnUiThread {
      layout_calling.visibility = View.VISIBLE
      if (Build.VERSION.SDK_INT >= 26) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
      } else {
        vibrator.vibrate(200)
      }
    }
  }

  private fun onCallLeaveCallback(){
    Log.d(TAG, "------ callback: onCallLeaveCallback")
    runOnUiThread {
      end_call_btn.visibility = View.GONE
      speaker_btn.visibility = View.GONE
    }
    if(vibrator.hasVibrator()){
      vibrator.cancel()
    }

    USBStreamService.startStreamRtpWithAudio()
  }

  /**
   * Button event
   */

  private fun onButtonStreamClick(){
    if (isMyServiceRunning(USBStreamService::class.java)) {
      stopService()
    } else {
      startService()
    }
  }


  /**
   * Stop/start service
   */
  private fun stopService(){
    stopService(Intent(applicationContext, USBStreamService::class.java))
    updateUIStream(false)
  }

  private fun startService(){
    val intent = Intent(applicationContext, USBStreamService::class.java)
    intent.putExtra("endpoint", et_url.text.toString())
    startService(intent)
    updateUIStream(true)
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
        R.id.menu_logout -> {
          callLogoutApi(true)
        }
        R.id.menu_setting -> {}
        R.id.menu_about -> { showAboutDialog() }
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

  fun showAboutDialog() {
    runOnUiThread {
      val positiveButtonClick = { dialog: DialogInterface, which: Int ->
      }

      val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogCustom))

      with(builder)
      {
        setTitle("About")
        val version_info = "Phiên bản: ${Constants.APP_VER}"
        val release_info = "Thời gian: ${Constants.RELEASE_TIME}"
        setMessage("$version_info \n$release_info")
        setPositiveButton(android.R.string.yes, positiveButtonClick)
        show()
      }
    }
  }

  /**
   * Surface event
   */
  override fun surfaceChanged(holder: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
    logService.appendLog("surfaceChanged", TAG)
    USBStreamService.setView(openglview)
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    logService.appendLog("surfaceDestroyed", TAG)
    USBStreamService.setView(applicationContext)
  }

  override fun surfaceCreated(holder: SurfaceHolder) {
    logService.appendLog("surfaceCreated", TAG)
  }

  override fun onResume() {
    logService.appendLog("surfaceCreated", TAG)
    super.onResume()
    if (isMyServiceRunning(USBStreamService::class.java)) {
      updateUIStream(USBStreamService.isStreaming())
    } else {
      updateUIStream(false)
    }
  }

  @Suppress("DEPRECATION")
  private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
    logService.appendLog("surfaceCreated", TAG)
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

      if(!USBStreamService.isUVCCameraAvailable())
      {
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

        USBStreamService.setUVCCamera(camera)
        if(USBStreamService.isUVCCameraAvailable()) {
          USBStreamService.setView(openglview)
          USBStreamService.startPreview()
        }
      }

      isUsbOpen = true
      logService.appendLog("onDeviceConectListener --- success ", TAG)
      layout_no_camera_found.visibility = View.INVISIBLE
      runOnUiThread {
        start_stop.isEnabled = isUsbOpen
      }
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
      logService.appendLog("onDeviceConectListener onDisconnect", TAG)
      layout_no_camera_found.visibility = View.VISIBLE
      updateUIStream(false)
      stopService()

      USBStreamService.closeUVCCamera()
      isUsbOpen = false
    }

    override fun onDettach(device: UsbDevice?) {
      logService.appendLog("onDeviceConectListener onDetach", TAG)
      USBStreamService.closeUVCCamera()
      isUsbOpen = false
      stopService()
      updateUIStream(false)
    }

    override fun onCancel(device: UsbDevice?) {
      logService.appendLog("onDeviceConectListener onCancel", TAG)
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
      start_stop.isEnabled = isUsbOpen
    }
  }

  private fun onRotateClick(){
    if(isRotated){
      USBStreamService.rotateView(0)
      isRotated = false
    }
    else {
      USBStreamService.rotateView(90)
      isRotated = true
    }
  }

  private fun onFlipClick(){
    isFlipped = if(!isFlipped){
      flipCamera(true, false)
      true
    }else{
      flipCamera(false, false)
      false
    }
  }

  private fun flipCamera(isHorizonFlip: Boolean, isVerticalFlip: Boolean){
    USBStreamService.flipView(isHorizonFlip, isVerticalFlip)
  }


  companion object{
    const val TAG = "BackgroundUSBStreamActivity"
  }

}