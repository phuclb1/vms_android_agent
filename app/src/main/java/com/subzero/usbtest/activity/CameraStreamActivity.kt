package com.subzero.usbtest.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.*
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.*
import android.widget.Toast
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.subzero.usbtest.Constants
import com.subzero.usbtest.R
import com.subzero.usbtest.api.AgentClient
import com.subzero.usbtest.rtc.WebRtcClient
import com.subzero.usbtest.utils.CustomizedExceptionHandler
import com.subzero.usbtest.utils.LogService
import com.subzero.usbtest.utils.SessionManager
import kotlinx.android.synthetic.main.activity_main.*
import net.ossrs.rtmp.ConnectCheckerRtmp
import okhttp3.*
import org.webrtc.PeerConnection
import java.io.File
import java.io.IOException

class CameraStreamActivity : Activity(), SurfaceHolder.Callback, ConnectCheckerRtmp {
  private val webRtcManager by lazy { WebRtcClient.instance }

  private val agentClient = AgentClient()

  private lateinit var sessionManager: SessionManager
  private lateinit var rtmpCamera: RtmpCamera2

  private val folderRecord = File(Constants.DOC_DIR, "video_record")

  private var isFlipped = false

  private var token: String = ""
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
    var rtmpUrl = Constants.RTMP_URL_HEADER + token
//    rtmpUrl = "rtmp://127.0.0.1:21935/live/livestream"
    logService.appendLog("RTMP url: $rtmpUrl", TAG)

    vibrator = this.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    if (!hasPermissions()) {
      ActivityCompat.requestPermissions(this, Constants.CAMERA_REQUIRED_PERMISSIONS, 1)
    }else{
      initStreamCamera()
    }

    et_url.setText(rtmpUrl)
    layout_no_camera_found.visibility = View.GONE

    webRtcManager.init(this)
    webRtcManager.connect(Constants.WEBRTC_SOCKET_SERVER, token)

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

  private fun initStreamCamera(){
    try {
      openglview.holder.addCallback(this)
      rtmpCamera = RtmpCamera2(openglview, this)
    } catch (e: Exception) {
      Log.e("error =", e.toString())
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
      }
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onDestroy() {
    super.onDestroy()
    logService.appendLog("MainActivity ---- onDestroy", TAG)
    try{
      if(rtmpCamera.isStreaming){
        callStopStream()
      }
      rtmpCamera.stopPreview()
      webRtcManager.onDestroy()
    }catch (e: java.lang.Exception){
      Log.e("error =", e.toString())
    }
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
      if (!rtmpCamera.isStreaming) {
        callStartStream(et_url.text.toString())
      } else {
        callStopStream()
      }
      updateUIStream()
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
    if(!rtmpCamera.isRecording){
      val currentTimestamp = System.currentTimeMillis()
      val fileRecord = "$folderRecord/$currentTimestamp.mp4"
      val record = callStartRecord(fileRecord)
      if(record){
        fileRecording = fileRecord
      }
    }
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
    try {
      if (!rtmpCamera.isStreaming) {
        if (prepareEncoders()) {
          rtmpCamera.startStream(url)
        }
      }
    }catch (e: java.lang.Exception){
      Log.d(TAG, e.toString())
    }
  }

  private fun callStopStream() {
    logService.appendLog("call stop stream", TAG)
    try {
      rtmpCamera.stopStream()
      callStopRecord()
    }catch (e: java.lang.Exception){
      Log.d(TAG, e.toString())
    }
  }

  private fun prepareEncoders():Boolean{
    try {
      val sampleRate = 44100
      val audioBitrate = 64000
      val videoBitrate = 4000 * 1024
      return rtmpCamera.prepareVideo(
//        width, height, fps, videoBitrate, false, 0
      )
              && rtmpCamera.prepareAudio(
        audioBitrate,
        sampleRate,
        true,
        false,
        false
      )
    } catch (e: java.lang.Exception){
      return rtmpCamera.prepareVideo() && rtmpCamera.prepareAudio()
    }
  }

  /**
   * Record Start/Stop
   */
  private fun callStartRecord(url: String):Boolean{
    logService.appendLog("call start record", TAG)
    if(rtmpCamera.isStreaming && !rtmpCamera.isRecording && !flagRecording){
      flagRecording = true
      try{
        logService.appendLog("started record: $url", TAG)
        try {
          rtmpCamera.startRecord(url)
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
    if(rtmpCamera.isRecording){
      rtmpCamera.stopRecord()
      flagRecording = false
      uploadVideo(File(fileRecording))
    }
  }


  /**
   * Update UI
   */
  private fun updateUIStream(){
    runOnUiThread {
      if (rtmpCamera.isStreaming) {
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

  private fun updateRecordStatus(){
    runOnUiThread{
      if (rtmpCamera.isRecording){
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
          Toast.makeText(this@CameraStreamActivity, "Upload failed: ${e.message.toString()}", Toast.LENGTH_SHORT).show()
        }
      }

      override fun onResponse(call: Call, response: Response) {
        val responseData = response.body().toString()
        logService.appendLog("upload video success: $responseData", TAG)
        if(file.exists()){
          file.delete()
        }
        runOnUiThread {
          Toast.makeText(this@CameraStreamActivity, "Upload success $responseData", Toast.LENGTH_SHORT).show()
        }
      }

    })
  }

  private fun flipCamera(isHorizonFlip: Boolean, isVerticalFlip: Boolean){
    openglview.setCameraFlip(isHorizonFlip, isVerticalFlip)
  }

  override fun surfaceCreated(p0: SurfaceHolder) {
  }

  override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
    Log.d(TAG, "surfaceChanged")
    try {
        rtmpCamera.startPreview()
        // rtmpCamera2.startPreview(CameraHelper.Facing.BACK, screenWidth, screenHeight, 90);
    } catch (e: java.lang.Exception) {
      Log.e("error =", e.toString())
    }
  }

  override fun surfaceDestroyed(p0: SurfaceHolder) {
    try{
      if(rtmpCamera.isStreaming){
        callStopStream()
      }
      rtmpCamera.stopPreview()
    }catch (e: java.lang.Exception){
      Log.e("error =", e.toString())
    }
  }

  companion object{
    const val TAG = "Main"
  }
}
