package com.subzero.usbtest.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.widget.Toast
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.usb.UVCCamera
import com.subzero.usbtest.Constants
import com.subzero.usbtest.R
import com.subzero.usbtest.activity.LoginActivity
import com.subzero.usbtest.api.AgentClient
import com.subzero.usbtest.models.LoginRequest
import com.subzero.usbtest.models.LoginResponse
import com.subzero.usbtest.streamlib.RtmpUSB2
import com.subzero.usbtest.streamlib.USBBase2
import com.subzero.usbtest.utils.LogService
import com.subzero.usbtest.utils.SessionManager
import kotlinx.android.synthetic.main.activity_login.*
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import retrofit2.Callback
import java.io.File
import java.io.IOException
import java.util.LinkedList
import java.util.Queue

class USBStreamService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "RTP service create")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH)
            notificationManager?.createNotificationChannel(channel)
        }
        keepAliveTrick()

        sessionManager = SessionManager(this)
        token = sessionManager.fetchAuthToken().toString()
        streamServerIP = sessionManager.fetchServerIp().toString()
    }

    private fun keepAliveTrick() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val notification = NotificationCompat.Builder(this, channelId)
                .setOngoing(true)
                .setContentTitle("")
                .setContentText("").build()
            startForeground(1, notification)
        } else {
            startForeground(1, Notification())
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG, "Stream service started")
        endpoint = intent?.extras?.getString("endpoint")
        if (endpoint != null) {
            prepareStreamRtp()
            startStreamRtp(endpoint!!)
        }
        return START_STICKY
    }

    companion object {
        private val TAG = "USBStreamService"
        private val channelId = "rtpStreamChannel"
        private val notifyId = 123456
        private var notificationManager: NotificationManager? = null

        private var endpoint: String? = null

        private val width = 1920
        private val height = 1080
        val rotation = 0
        val sampleRate = 44100
        val audioBitrate = 64 * 1024
        val videoBitrate = 1200 * 1024
        val fps = 15

        val maxTimesRetryRtmp = 5
        var remainTimesRetryRtmp = maxTimesRetryRtmp
        val rtmpRetryDelay:Long = 1

        private var flagRecording = false
        private var fileRecording: String = ""
        private val videoRecordedQueue: Queue<String> = LinkedList<String>()
        private val folderRecord = File(Constants.DOC_DIR, "video_record")

        private var token: String = ""
        private var streamServerIP : String = ""
        private lateinit var sessionManager: SessionManager

        private val logService = LogService.getInstance()
        private val agentClient = AgentClient()

        @SuppressLint("StaticFieldLeak")
        private var rtmpUSB: USBBase2? = null
        private var uvcCamera: UVCCamera? = null

        private var openGlView: OpenGlView? = null
        @SuppressLint("StaticFieldLeak")
        private var contextApp: Context? = null

        fun isStreaming(): Boolean{
            return rtmpUSB!!.isStreaming
        }

        fun setUVCCamera(uvcCamera: UVCCamera){
            logService.appendLog("setUVCCamera", TAG)
            this.uvcCamera = uvcCamera
            logService.appendLog("setView ---- ${this.uvcCamera!!.previewSize}", TAG)
        }

        fun isUVCCameraAvailable(): Boolean {
            if(this.uvcCamera == null)
                return false
            return true
        }

        fun setView(openGlView: OpenGlView) {
            logService.appendLog("setView openGlView", TAG)
            this.openGlView = openGlView
            rtmpUSB?.replaceView(uvcCamera, openGlView)
        }

        fun setView(context: Context) {
            logService.appendLog("setView context", TAG)
            contextApp = context
            this.openGlView = null

            logService.appendLog("setView ---- ${uvcCamera?.previewSize}", TAG)
            rtmpUSB?.replaceView(uvcCamera, context)
        }

        fun startPreview() {
            logService.appendLog("startPreview", TAG)
            val rotation = this.openGlView?.rotation
            logService.appendLog("startPreview   rotation=$rotation", TAG)
            if (rotation != null) {
                rtmpUSB?.startPreview(uvcCamera, width, height, fps, rotation.toInt())
            }

//            rtmpUSB?.startPreview(uvcCamera, width, height, fps, 90)
        }

        fun init(context: Context, openGlView: OpenGlView) {
            logService.appendLog("init", TAG)
            this.openGlView = openGlView
            contextApp = context
            if (rtmpUSB == null)
                rtmpUSB = RtmpUSB2(this.contextApp, connectCheckerRtp)
        }

        fun stopStream() {
            logService.appendLog("stopStream", TAG)
            if (rtmpUSB != null) {
                if (rtmpUSB!!.isStreaming) rtmpUSB!!.stopStream(uvcCamera)
            }
            if(flagRecording)
                callStopRecord()
        }

        fun stopPreview() {
            logService.appendLog("stopPreview", TAG)
            if (rtmpUSB != null) {
                if (rtmpUSB!!.isOnPreview) {
                    rtmpUSB!!.stopPreview(uvcCamera)
                }
            }
        }

        fun flipView(isHorizonFlip: Boolean, isVerticalFlip: Boolean){
            openGlView?.setCameraFlip(isHorizonFlip, isVerticalFlip)
        }

        fun rotateView(rotate: Int){
            openGlView?.setRotation(rotate)
            rtmpUSB?.stopPreview(uvcCamera)
            rtmpUSB?.startPreview(uvcCamera, width, height, fps, rotate)
        }

        private val connectCheckerRtp = object : ConnectCheckerRtmp {
            override fun onConnectionSuccessRtmp() {
                showNotification("Stream started")
                logService.appendLog("onConnectionSuccessRtmp", TAG)
                if(flagRecording)
                    callStopRecord()
                remainTimesRetryRtmp = 0
            }

            override fun onNewBitrateRtmp(bitrate: Long) {

            }

            override fun onConnectionFailedRtmp(reason: String) {
                if(remainTimesRetryRtmp % 5 == 0)
                    showNotification("Stream connection failed")

                logService.appendLog("onConnectionFailedRtmp", TAG)

                remainTimesRetryRtmp+=1

                if(!rtmpUSB?.isRecording!!){
                    val currentTimestamp = System.currentTimeMillis()
                    val fileRecord = "$folderRecord/$currentTimestamp.mp4"
                    val record = callStartRecord(fileRecord)
                    if(record){
                        fileRecording = fileRecord
                    }
                }

                rtmpUSB?.setReTries(100)
                rtmpUSB!!.reTry(rtmpRetryDelay * 1000, reason)
            }

            override fun onConnectionStartedRtmp(rtmpUrl: String) {
            }

            override fun onDisconnectRtmp() {
                showNotification("Stream stopped")
            }

            override fun onAuthErrorRtmp() {
                showNotification("Stream auth error")
            }

            override fun onAuthSuccessRtmp() {
                showNotification("Stream auth success")
            }
        }

        private fun showNotification(text: String) {
            contextApp?.let {
                val notification = NotificationCompat.Builder(it, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher_foreground)
                    .setContentTitle("VT Camera Agent Stream")
                    .setTimeoutAfter(1000)
                    .setContentText(text).build()
                notificationManager?.notify(notifyId, notification)
            }
        }

        fun closeUVCCamera(){
            logService.appendLog("closeUVCCamera", TAG)
            if(uvcCamera != null) {
                uvcCamera?.close()
                uvcCamera = null
            }
        }

        /**
         * Record Start/Stop
         */
        private fun callStartRecord(url: String):Boolean{
//            showNotification("Start Record")
            if (!folderRecord.exists()){
                folderRecord.mkdirs()
            }
            logService.appendLog("call start record", TAG)
            if(rtmpUSB?.isStreaming == true && !rtmpUSB?.isRecording!! && !flagRecording){
                flagRecording = true
                try{
                    logService.appendLog("started record: $url", TAG)
                    try {
                        rtmpUSB!!.startRecord(uvcCamera, url)
                        return true
                    }catch (e: IOException){
                        flagRecording = false
                    }
                }
                catch (e: java.lang.Exception){
                    flagRecording = false
                    logService.appendLog("record error: ${e.message}", TAG)
                }
            }
            return false
        }

        private fun callStopRecord(){
//            showNotification("Stop Record")
            logService.appendLog("call stop record", TAG)
            if(rtmpUSB?.isRecording == true){
                rtmpUSB?.stopRecord(uvcCamera)
                flagRecording = false

                // Upload if video's duration > 1 seconds
                if(remainTimesRetryRtmp > 1) {
                    videoRecordedQueue.add(fileRecording)
                    uploadAllVideoRecorded()
                }else{
                    val file = File(fileRecording)
                    if(file.exists()){
                        file.delete()
                    }
                }
            }
        }

        private fun uploadAllVideoRecorded(){
            if(videoRecordedQueue.isEmpty())
                return

            while(!videoRecordedQueue.isEmpty()){
                val videoname = videoRecordedQueue.poll()
                uploadVideo(File(videoname))
            }
        }

        private fun uploadVideo(file: File){
            val baseURL = "http://${sessionManager.fetchServerIp().toString()}:${Constants.API_PORT}${Constants.API_UPLOAD_VIDEO}"
            logService.appendLog("=== upload video ${file.absolutePath}    ${file.name}", TAG)

            val mediaType = MediaType.parse("text/plain")
            val requestBody = RequestBody.create(MediaType.parse("application/octet-stream"), file)
            val body = MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("video_file", file.name, requestBody)
                .build()
            val request = Request.Builder()
                .url(baseURL)
                .method("POST", body)
                .addHeader("Authorization", "Bearer $token")
                .build()
//            logService.appendLog("=== upload video request: $request", TAG)
            agentClient.getClientOkhttpInstance().newCall(request).enqueue(object: okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) {
                    logService.appendLog("upload video failed: ${e.message.toString()}", TAG)
                    showNotification("Upload video failed")

                    if(file.exists()){
                        file.delete()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseData = response.body().toString()
                    logService.appendLog("upload video success: ${file.name}", TAG)
                    showNotification("Upload video success")

                    if(file.exists()){
                        file.delete()
                    }
                }

            })
        }

        fun startStreamRtpWithAudio() {
            logService.appendLog("startStreamRtpWithAudio", TAG)
            if(rtmpUSB!!.isStreaming) {
                stopStream()
                stopPreview()

                if (!rtmpUSB!!.isStreaming) {
                    val prepareVideo = rtmpUSB!!.prepareVideo(
                        uvcCamera,
                        width, height, fps, videoBitrate, rotation
                    )
                    val prepareAudio = rtmpUSB!!.prepareAudio(
                        MediaRecorder.AudioSource.DEFAULT,
                        audioBitrate, sampleRate, true, false, false
                    )
                    if (prepareVideo && prepareAudio) {
                        rtmpUSB!!.startStream(uvcCamera, endpoint)
                    }
                } else {
                    showNotification("You are already streaming :(")
                }
            }

        }

        fun startStreamRtpWithoutAudio() {
            logService.appendLog("startStreamRtpWithoutAudio", TAG)
            if(rtmpUSB!!.isStreaming) {
                stopStream()
                stopPreview()

                if (!rtmpUSB!!.isStreaming) {
                    val prepareVideo = rtmpUSB!!.prepareVideo(
                        uvcCamera,
                        width, height, fps, videoBitrate, rotation
                    )
                    rtmpUSB?.setAudioInit(false);
                    if (prepareVideo) {
                        rtmpUSB!!.startStream(uvcCamera, endpoint)
                    }
                } else {
                    showNotification("You are already streaming :(")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logService.appendLog("Stream Service destroy", TAG)
        stopStream()
        if(flagRecording)
            callStopRecord()
    }

    private fun prepareStreamRtp() {
        logService.appendLog("prepareStreamRtp", TAG)
        stopStream()
        stopPreview()
        rtmpUSB = if (openGlView == null) {
            RtmpUSB2(baseContext, connectCheckerRtp)
        } else {
            RtmpUSB2(openGlView, connectCheckerRtp)
        }
    }

    private fun startStreamRtp(endpoint: String) {
        logService.appendLog("startStreamRtp", TAG)
        if (!rtmpUSB!!.isStreaming) {
            if(rtmpUSB!!.prepareVideo(uvcCamera,
                    width, height, fps, videoBitrate, rotation
                )
                && rtmpUSB!!.prepareAudio(MediaRecorder.AudioSource.DEFAULT,
                    audioBitrate, sampleRate, true, false, false
                )){
                rtmpUSB!!.startStream(uvcCamera, endpoint)
            }
        } else {
            showNotification("You are already streaming :(")
        }
    }
}