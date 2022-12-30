package com.subzero.usbtest

import com.subzero.usbtest.rtc.RtcSdkManager
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.subzero.usbtest.api.AgentClient
import com.subzero.usbtest.streamlib.RtmpUSB
import com.subzero.usbtest.utils.CustomizedExceptionHandler
import kotlinx.android.synthetic.main.activity_main.*
import net.ossrs.rtmp.ConnectCheckerRtmp
//import com.pedro.rtmp.utils.ConnectCheckerRtmp
import okhttp3.*
import java.io.File
import java.io.IOException

class MainActivity : Activity(), SurfaceHolder.Callback, ConnectCheckerRtmp {
//  private val rtcManager by lazy { RtcSdkManager.instance }
  private val agentClient = AgentClient()

  private lateinit var sessionManager: SessionManager
  private lateinit var usbMonitor: USBMonitor
  private lateinit var rtmpUSB: RtmpUSB

  private var uvcCamera: UVCCamera? = null
  private var isUsbOpen = false

  private val width = 1280
  private val height = 720
  private val fps = 15
  private val folderRecord = Constants.RECORD_FOLDER

  private var isRotated = false
  private var isFlipped = false

  private var token: String = ""
  private var flagRecording = false
  private var fileRecording: String = ""
  private var isCalling = false

  private val logService = LogService.getInstance()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    /* Handle uncaught exception */
    val logDir = Environment.DIRECTORY_DCIM
    Thread.setDefaultUncaughtExceptionHandler(CustomizedExceptionHandler(logDir))

    sessionManager = SessionManager(this)
    token = sessionManager.fetchAuthToken().toString()
    val rtmpUrl = Constants.RTMP_URL_HEADER + token
    logService.appendLog("RTMP url: $rtmpUrl", TAG)

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

//    rtcManager.init(this)
//    rtcManager.connect(Constants.WEBRTC_SOCKET_SERVER)

    if (!folderRecord.exists()){
      folderRecord.mkdirs()
    }

    start_stop.setOnClickListener {
      if (uvcCamera != null) {
        if (!rtmpUSB.isStreaming) {
          callStartStream(et_url.text.toString())
          updateUIStream()
        } else {
          callStopStream()
          updateUIStream()
        }
      }
    }

    rotate_btn.setOnClickListener{ onRotateClick() }
    flip_btn.setOnClickListener{ onFlipClick() }


//    btn_answer.setOnClickListener {
//      if(isCalling){
//        // End call
//        onEndCall()
//      }else{
//        // Answer call
//        onAnswerCall()
//      }
//    }

    updateUIStream()
    updateRecordStatus()
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
//    rtcManager.onDestroy()
  }

  override fun onResume() {
    super.onResume()
//    rtcManager.onResume()
  }

  override fun onPause() {
    super.onPause()
//    rtcManager.onPause()
  }

  private fun onRotateClick(){
    isRotated = if(!isRotated){
      rtmpUSB.glInterface.setRotation(90)
      true
    }else{
      rtmpUSB.glInterface.setRotation(0)
      false
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

  /**
   * Calling phone
   */
  private fun onAnswerCall(){
//    rtcManager.startAnswer()
  }

  private fun onEndCall(){
//    rtcManager.endCall()
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

  /*
  Auto record video
  Try reconnect
  Upload recorded video when reconnected
  * */
  override fun onConnectionFailedRtmp(reason: String?) {
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
//    if (!reconnect){
//      callStopStream()
//    }
//
//    updateRecordStatus()
//    runOnUiThread {
//      if (!reconnect){
//        Toast.makeText(this, "Rtmp connection failed! $reason", Toast.LENGTH_SHORT).show()
//      }
//    }
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
    runOnUiThread {
      updateUIStream()
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
    if (rtmpUSB.prepareVideo(width, height, fps, 4000 * 1024, false, 0,
        uvcCamera) && rtmpUSB.prepareAudio()) {
      rtmpUSB.startStream(uvcCamera, url)
    }
  }

  private fun callStopStream(){
    logService.appendLog("call stop stream", TAG)
    if(rtmpUSB.isStreaming && uvcCamera != null){
      rtmpUSB.stopStream(uvcCamera)
      callStopRecord()
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
    if(rtmpUSB.isStreaming){
      start_stop.background = getDrawable(R.drawable.custom_oval_button_2)
      start_stop.text = getString(R.string.stop)
//      et_url.visibility = View.INVISIBLE
    }else{
      start_stop.background = getDrawable(R.drawable.custom_oval_button_1)
      start_stop.text = getString(R.string.start)
//      et_url.visibility = View.VISIBLE
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
      .url("${Constants.BASE_URL}${Constants.API_UPLOAD_VIDEO}")
      .method("POST", body)
      .addHeader("Authorization", "Bearer $token")
      .build()
    agentClient.getClientOkhttpInstance().newCall(request).enqueue(object: okhttp3.Callback {
      override fun onFailure(call: Call, e: IOException) {
        logService.appendLog("upload video failed: ${e.message.toString()}", TAG)
        runOnUiThread {
          Toast.makeText(this@MainActivity, "Upload failed: ${e.message.toString()}", Toast.LENGTH_SHORT).show()
        }
      }

      override fun onResponse(call: Call, response: Response) {
        val responseData = response.body().toString()
        logService.appendLog("upload video success: $responseData", TAG)
        if(file.exists()){
          file.delete()
        }
        runOnUiThread {
          Toast.makeText(this@MainActivity, "Upload success $responseData", Toast.LENGTH_SHORT).show()
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
