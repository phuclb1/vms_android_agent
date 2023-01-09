package com.subzero.usbtest.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.arch.lifecycle.MutableLiveData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.widget.Toast
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.utils.UIThreadHelper.runOnUiThread
import com.subzero.usbtest.Constants
import com.subzero.usbtest.R
import com.subzero.usbtest.activity.CameraStreamActivity
import com.subzero.usbtest.api.AgentClient
import com.subzero.usbtest.utils.LogService
import com.subzero.usbtest.utils.SessionManager
import net.ossrs.rtmp.ConnectCheckerRtmp
import okhttp3.*
import java.io.File
import java.io.IOException

class CameraStreamService : Service() {
    companion object {
        private const val TAG = "CameraStreamService"
        private const val channelId = "rtpStreamChannel"
        private const val notifyId = 123456
        private var notificationManager: NotificationManager? = null
        val observer = MutableLiveData<CameraStreamService?>()
    }

    private lateinit var sessionManager: SessionManager
    private val agentClient = AgentClient()
    private var rtmpCamera: RtmpCamera2? = null
    private val logService = LogService.getInstance()
    private var token: String = ""
    private var streamServerIP : String = ""
    private var flagRecording = false
    private var fileRecording: String = ""
    private val folderRecord = File(Constants.DOC_DIR, "video_record")

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "RTP service started")
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "$TAG create")

        sessionManager = SessionManager(this)
        streamServerIP = sessionManager.fetchServerIp().toString()

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH)
            notificationManager?.createNotificationChannel(channel)
        }
        keepAliveTrick()
        rtmpCamera = RtmpCamera2(this, true, connectCheckerRtp)
        rtmpCamera!!.setReTries(1000)

        observer.postValue(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "RTP service destroy")
        callStopRecord()
        stopStream()
        stopPreview()
        observer.postValue(null)
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

    private fun showNotification(text: String) {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle("RTP Stream")
            .setContentText(text).build()
        notificationManager?.notify(notifyId, notification)
    }

    fun prepare(): Boolean {
        return rtmpCamera?.prepareVideo() ?: false && rtmpCamera?.prepareAudio() ?: false
    }

    fun prepareVideo(width: Int, height: Int, bitrate: Int): Boolean{
        return rtmpCamera?.prepareVideo(width, height, bitrate) ?: false
    }

    fun prepareAudio(bitrate: Int, sampleRate: Int, isStereo: Boolean): Boolean{
        return rtmpCamera?.prepareAudio(bitrate, sampleRate, isStereo) ?: false
    }

    fun startPreview() {
        Log.d(TAG, "--------- start preview")
        rtmpCamera?.startPreview()
    }

    fun stopPreview() {
        rtmpCamera?.stopPreview()
    }

    fun switchCamera() {
        rtmpCamera?.switchCamera()
    }

    fun isStreaming(): Boolean = rtmpCamera?.isStreaming ?: false

    fun isRecording(): Boolean = rtmpCamera?.isRecording ?: false

    fun isOnPreview(): Boolean = rtmpCamera?.isOnPreview ?: false

    fun startStream(endpoint: String) {
        if(!isStreaming()) {
            rtmpCamera?.startStream(endpoint)
        }
    }

    fun stopStream() {
        rtmpCamera?.stopStream()
        callStopRecord()
    }

    fun setView(openGlView: OpenGlView) {
        rtmpCamera?.replaceView(openGlView)
    }

    fun setView(context: Context) {
        rtmpCamera?.replaceView(context)
    }

    private val connectCheckerRtp = object : ConnectCheckerRtmp {

        override fun onConnectionSuccessRtmp() {
            showNotification("Stream started")
            logService.appendLog("connect rtmp success", TAG)
            callStopRecord()
        }

        override fun onConnectionFailedRtmp(reason: String) {
            showNotification("Stream connection failed")
            logService.appendLog("connect rtmp fail", TAG)

            logService.appendLog("connect rtmp fail", TAG)
            if(!rtmpCamera?.isRecording!!){
                val currentTimestamp = System.currentTimeMillis()
                val fileRecord = "$folderRecord/$currentTimestamp.mp4"
                val record = callStartRecord(fileRecord)
                if(record){
                    fileRecording = fileRecord
                }
            }

            if(rtmpCamera!!.shouldRetry(reason)){
                rtmpCamera!!.reConnect(1000)
            }else{
                rtmpCamera!!.setReTries(1000)
            }
        }

        override fun onNewBitrateRtmp(bitrate: Long) {
        }


        override fun onDisconnectRtmp() {
            showNotification("Stream stopped")
            stopStream()

//            updateUIStream()
        }

        override fun onAuthErrorRtmp() {
            showNotification("Stream auth error")
        }

        override fun onAuthSuccessRtmp() {
            showNotification("Stream auth success")
        }
    }

    /**
     * Record Start/Stop
     */
    private fun callStartRecord(url: String):Boolean{
        logService.appendLog("call start record", TAG)
        if(rtmpCamera?.isStreaming == true && !rtmpCamera?.isRecording!! && !flagRecording){
            flagRecording = true
            try{
                logService.appendLog("started record: $url", TAG)
                try {
                    rtmpCamera!!.startRecord(url)
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
        if(rtmpCamera?.isRecording == true){
            rtmpCamera?.stopRecord()
            flagRecording = false
            uploadVideo(File(fileRecording))
        }
    }

    private fun uploadVideo(file: File){
        logService.appendLog("======== upload video ${file.absolutePath}    ${file.name}", TAG)
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
                logService.appendLog("upload video failed: ${e.message.toString()}", TAG
                )
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body().toString()
                logService.appendLog("upload video success: ${file.name}", TAG)

                if(file.exists()){
                    file.delete()
                }
            }

        })
    }
}