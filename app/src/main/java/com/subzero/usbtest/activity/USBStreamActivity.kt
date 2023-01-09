package com.subzero.usbtest.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.*
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.*
import android.widget.Toast
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.subzero.usbtest.Constants
import com.subzero.usbtest.R
import com.subzero.usbtest.api.AgentClient
import com.subzero.usbtest.rtc.WebRtcClient
import com.subzero.usbtest.streamlib.RtmpUSB
import com.subzero.usbtest.utils.CustomizedExceptionHandler
import com.subzero.usbtest.utils.LogService
import com.subzero.usbtest.utils.SessionManager
import kotlinx.android.synthetic.main.activity_main.*
import net.ossrs.rtmp.ConnectCheckerRtmp
import okhttp3.*
import org.webrtc.PeerConnection
import java.io.File
import java.io.IOException

class USBStreamActivity : Activity(), SurfaceHolder.Callback, ConnectCheckerRtmp {
  private val webRtcManager by lazy { WebRtcClient.instance }

  private val agentClient = AgentClient()

  private lateinit var sessionManager: SessionManager
  private lateinit var usbMonitor: USBMonitor
  private lateinit var rtmpUSB: RtmpUSB

  private var uvcCamera: UVCCamera? = null
  private var isUsbOpen = false

  private val width = 1280
  private val height = 720
  private val fps = 15
  private val folderRecord = File(Constants.DOC_DIR, "video_record")

  private var isFlipped = false

  private var token: String = ""
  private var streamServerIP : String = ""
  private var flagRecording = false
  private var fileRecording: String = ""

  private val logService = LogService.getInstance()

  private lateinit var vibrator: Vibrator

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    /* Handle uncaught exception */
    val logDir = Environment.DIRECTORY_DCIM
    Thread.setDefaultUncaughtExceptionHandler(CustomizedExceptionHandler(logDir))

    sessionManager = SessionManager(this)
    token = sessionManager.fetchAuthToken().toString()
    streamServerIP = sessionManager.fetchServerIp().toString()
    var rtmpUrl = "rtmp://${sessionManager.fetchServerIp().toString()}:${Constants.RTMP_PORT}/live/$token"
//    rtmpUrl = "rtmp://103.160.84.179:21935/live/livestream"

    vibrator = this.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    if (!hasPermissions()) {
      ActivityCompat.requestPermissions(this, Constants.CAMERA_REQUIRED_PERMISSIONS, 1)
    }

    et_url.setText(rtmpUrl)
    layout_no_camera_found.visibility = View.VISIBLE

    isUsbOpen = false
    rtmpUSB = RtmpUSB(openglview, this)
    usbMonitor = USBMonitor(this, onDeviceConnectListener)
    usbMonitor.register()
    rtmpUSB.setNumRetriesConnect(1000)

    val stunUri = sessionManager.fetchWebRTCStunUri()
    val turnUri = sessionManager.fetchWebRTCTurnUri()
    webRtcManager.init(this, stunUri, turnUri)
    webRtcManager.connect(sessionManager.fetchWebRTCSocketUrl(), token)

    if (!folderRecord.exists()){
      folderRecord.mkdirs()
    }

    start_stop.setOnClickListener { onButtonStreamClick() }
    rotate_btn.setOnClickListener{ onRotateClick() }
    flip_btn.setOnClickListener{ onFlipClick() }

    decline_call_btn.setOnClickListener { onDeclineCall() }
    accept_call_btn.setOnClickListener { onAcceptCall() }
    end_call_btn.setOnClickListener { onEndCall() }

    btn_switch_audio.setOnClickListener {
      webRtcManager.switchAudioMode()
    }

    updateUIStream()
    updateRecordStatus()

    webRtcManager.onIceConnectionChangeCallback = fun(state)
    {
      Log.d(TAG, "------ callback: onPeerConectionChange $state")
      runOnUiThread {
        if (state == PeerConnection.IceConnectionState.CONNECTED) {
          layout_calling.visibility = View.INVISIBLE
        }
        if (state == PeerConnection.IceConnectionState.CLOSED){
          layout_calling.visibility = View.GONE
        }
        else {

        }
      }
    }
    webRtcManager.onCallingCallback = fun(){
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
    webRtcManager.onCallLeaveCallback = fun(){
      Log.d(TAG, "------ callback: onCallLeaveCallback")
      runOnUiThread {
        end_call_btn.visibility = View.GONE
      }
    }
  }

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
      }
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onDestroy() {
    super.onDestroy()
    logService.appendLog("MainActivity ---- onDestroy", TAG)
    if (rtmpUSB.isStreaming && uvcCamera != null) callStopStream()
    if (rtmpUSB.isOnPreview && uvcCamera != null) rtmpUSB.stopPreview(uvcCamera)
    if (isUsbOpen) {
      uvcCamera?.close()
    }
    usbMonitor.unregister()
    webRtcManager.onDestroy()
  }

  override fun onResume() {
    super.onResume()
    webRtcManager.onResume()
  }

  override fun onPause() {
    super.onPause()
    webRtcManager.onPause()
  }

  private fun onRotateClick(){
    requestedOrientation = if(resources.configuration.orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    else
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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

  private fun onButtonStreamClick(){
    if (uvcCamera != null) {
      if (!rtmpUSB.isStreaming) {
        callStartStream(et_url.text.toString())
      } else {
        callStopStream()
      }
      updateUIStream()
    }
  }

  /**
   * Calling phone
   */
  private fun onDeclineCall(){
    webRtcManager.closeCall()
    vibrator.cancel()
  }

  private fun onAcceptCall(){
    webRtcManager.startAnswer()
    end_call_btn.visibility = View.VISIBLE
    vibrator.cancel()
  }

  private fun onEndCall(){
    webRtcManager.closeCall()
    runOnUiThread {
      end_call_btn.visibility = View.GONE
    }
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
      val camera = UVCCamera()
      logService.appendLog("onDeviceConectListener ---- ${ctrlBlock.toString()}", TAG)
      camera.open(ctrlBlock)
      try {
        camera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG)
      } catch (e: IllegalArgumentException) {
        logService.appendLog("onDeviceConectListener --- setPreviewSize camera ---- ${e.toString()}", TAG)
        camera.destroy()
        try {
          camera.setPreviewSize(width, height, UVCCamera.DEFAULT_PREVIEW_MODE)
        } catch (e1: IllegalArgumentException) {
          logService.appendLog("onDeviceConectListener --- setPreviewSize camera ---- ${e1.toString()}", TAG)
          return
        }
      }
      uvcCamera = camera
      rtmpUSB.startPreview(uvcCamera, width, height)

      isUsbOpen = true
      logService.appendLog("onDeviceConectListener --- success ", TAG)
      layout_no_camera_found.visibility = View.INVISIBLE
    }

    override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
      logService.appendLog("MainActivity onDisconnect", TAG)
      layout_no_camera_found.visibility = View.VISIBLE
      if (uvcCamera != null) {
        updateUIStream()
        callStopStream()

        uvcCamera?.close()
        uvcCamera = null
        isUsbOpen = false
      }
    }

    override fun onDettach(device: UsbDevice?) {
      logService.appendLog("MainActivity onDetach", TAG)
      if (uvcCamera != null) {
        uvcCamera?.close()
        uvcCamera = null
        isUsbOpen = false
      }
    }

    override fun onCancel(device: UsbDevice?) {
      logService.appendLog("MainActivity onCancel", TAG)
    }
  }


  /**
   * Rtmp
   */
  override fun onAuthSuccessRtmp() {
  }


  override fun onAuthErrorRtmp() {
  }

  override fun onConnectionSuccessRtmp() {
    logService.appendLog("connect rtmp success", TAG)
    callStopRecord()
    updateRecordStatus()
    runOnUiThread {
      Toast.makeText(this, "Rtmp connection success", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onConnectionFailedRtmp(reason: String) {
    logService.appendLog("connect rtmp fail", TAG)
    if(!rtmpUSB.isRecording){
      val currentTimestamp = System.currentTimeMillis()
      val fileRecord = "$folderRecord/$currentTimestamp.mp4"
      val record = callStartRecord(fileRecord)
      if(record){
        fileRecording = fileRecord
      }
    }

    val reconnect = rtmpUSB.reconnectRtp(reason, 1000)
    if (!reconnect){
      rtmpUSB.setNumRetriesConnect(1000)
    }
  }

  override fun onNewBitrateRtmp(bitrate: Long) {
  }

  override fun onBackPressed() {
    super.onBackPressed()
    val intent = Intent(applicationContext, LoginActivity::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
  }

  override fun onDisconnectRtmp() {
    logService.appendLog("disconnect rtmp", TAG)
    callStopStream()
    updateUIStream()
    runOnUiThread {
      Toast.makeText(this, "Disconnect", Toast.LENGTH_SHORT).show()
    }
  }


  private fun hasPermissions(): Boolean {
    for (permission in Constants.CAMERA_REQUIRED_PERMISSIONS) {
      if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, permission)) {
        return false
      }
    }
    return true
  }


  /**
   * RTMP Stream Start/Stop
   */
  private fun callStartStream(url: String) {
    logService.appendLog("call start stream", TAG)
    if(prepareEncoders()){
      rtmpUSB.startStream(uvcCamera, url)
      rtmpUSB.enableAudio()
    }
  }

  private fun callStopStream(){
    logService.appendLog("call stop stream", TAG)
    if(rtmpUSB.isStreaming && uvcCamera != null){
      rtmpUSB.stopStream(uvcCamera)
      callStopRecord()
    }
  }

  private fun prepareEncoders():Boolean{
    try {
      val sampleRate = 44100
      val audioBitrate = 64000
      val videoBitrate = 4000 * 1024
      return rtmpUSB.prepareVideo(
        width, height, fps, videoBitrate, false, 0, uvcCamera
      )
              && rtmpUSB.prepareAudio(
        audioBitrate,
        sampleRate,
        true,
        false,
        false
      )
    } catch (e: java.lang.Exception){
      return rtmpUSB.prepareVideo(uvcCamera) && rtmpUSB.prepareAudio()
    }
  }

  /**
   * Record Start/Stop
   */
  private fun callStartRecord(url: String):Boolean{
//    logService.appendLog("call start record", TAG)
    if(rtmpUSB.isStreaming && !rtmpUSB.isRecording && uvcCamera != null && !flagRecording){
      flagRecording = true
      try{
        logService.appendLog("started record: $url", TAG)
        try {
          rtmpUSB.startRecord(uvcCamera, url)
          return true
        }catch (e: IOException){
          flagRecording = false
        }
      }
      catch (e: java.lang.Exception){
        flagRecording = false
        logService.appendLog("record error: ${e.message}", TAG)
        Toast.makeText(this, "record exception: ${e.message}", Toast.LENGTH_SHORT).show()
      }
    }
    return false
  }

  private fun callStopRecord(){
    if(rtmpUSB.isRecording && uvcCamera != null){
      rtmpUSB.stopRecord(uvcCamera)
      flagRecording = false
      uploadVideo(File(fileRecording))
    }
  }

  /**
   * Update UI
   */
  private fun updateUIStream(){
    runOnUiThread {
      if (rtmpUSB.isStreaming) {
        start_stop.background = getDrawable(R.drawable.custom_oval_button_2)
        start_stop.text = getString(R.string.stop)
        rotate_btn.visibility = View.INVISIBLE
        flip_btn.visibility = View.INVISIBLE
//      et_url.visibility = View.INVISIBLE
      } else {
        start_stop.background = getDrawable(R.drawable.custom_oval_button_1)
        start_stop.text = getString(R.string.start)
        rotate_btn.visibility = View.VISIBLE
        flip_btn.visibility = View.VISIBLE
//      et_url.visibility = View.VISIBLE
      }
    }
  }

  private fun updateRecordStatus(){
    runOnUiThread{
      if (rtmpUSB.isRecording){
        tv_record.visibility = View.VISIBLE
      }else{
        tv_record.visibility = View.INVISIBLE
      }
    }
  }

  private fun uploadVideo(file: File){
    logService.appendLog("======== upload video ${file.absolutePath}    ${file.name}")
    val mediaType = MediaType.parse("text/plain")
    val requestBody = RequestBody.create(MediaType.parse("application/octet-stream"), file)
    val body = MultipartBody
      .Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("video_file", file.name, requestBody)
      .build()
    val request = Request.Builder()
      .url("http://$streamServerIP${Constants.API_UPLOAD_VIDEO}")
      .method("POST", body)
      .addHeader("Authorization", "Bearer $token")
      .build()
    agentClient.getClientOkhttpInstance().newCall(request).enqueue(object: okhttp3.Callback {
      override fun onFailure(call: Call, e: IOException) {
        logService.appendLog("upload video failed: ${e.message.toString()}", TAG)
        runOnUiThread {
          Toast.makeText(this@USBStreamActivity, "Upload failed: ${e.message.toString()}", Toast.LENGTH_SHORT).show()
        }
      }

      override fun onResponse(call: Call, response: Response) {
        val responseData = response.body().toString()
        logService.appendLog("upload video success: $responseData", TAG)
        runOnUiThread {
          Toast.makeText(this@USBStreamActivity, "Upload success ${file.name}", Toast.LENGTH_SHORT).show()
        }
        if(file.exists()){
          file.delete()
        }
      }

    })
  }

  private fun onStreamMute(){
    if(rtmpUSB.isAudioMuted){
      rtmpUSB.enableAudio()
    }else{
      rtmpUSB.disableAudio()
    }
  }

  private fun flipCamera(isHorizonFlip: Boolean, isVerticalFlip: Boolean){
    openglview.setCameraFlip(isHorizonFlip, isVerticalFlip)
  }

  override fun surfaceCreated(p0: SurfaceHolder) {
  }

  override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
  }

  override fun surfaceDestroyed(p0: SurfaceHolder) {
  }

  companion object{
    const val TAG = "Main"
  }
}
