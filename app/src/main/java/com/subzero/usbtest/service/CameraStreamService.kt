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
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.pedro.rtplibrary.view.OpenGlView
import com.subzero.usbtest.R
import com.subzero.usbtest.activity.CameraStreamActivity
import com.subzero.usbtest.utils.LogService
import net.ossrs.rtmp.ConnectCheckerRtmp

class CameraStreamService : Service() {
    companion object {
        private const val TAG = "CameraStreamService"
        private const val channelId = "rtpStreamChannel"
        private const val notifyId = 123456
        private var notificationManager: NotificationManager? = null
        val observer = MutableLiveData<CameraStreamService?>()
    }

    private var rtmpCamera: RtmpCamera2? = null
    private val logService = LogService.getInstance()

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
        stopRecord()
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
        rtmpCamera?.startStream(endpoint)
    }

    fun stopStream() {
        rtmpCamera?.stopStream()
    }

    fun startRecord(path: String) {
        rtmpCamera?.startRecord(path) {
            Log.i(TAG, "record state: ${it.name}")
        }
    }

    fun stopRecord() {
        rtmpCamera?.stopRecord()
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
        }

        override fun onConnectionFailedRtmp(reason: String) {
            showNotification("Stream connection failed")
            logService.appendLog("connect rtmp fail", TAG)

            if(rtmpCamera?.shouldRetry(reason) == true){
                rtmpCamera!!.reConnect(1000)
            }else{
                rtmpCamera?.setReTries(1000)
            }
        }

        override fun onNewBitrateRtmp(bitrate: Long) {
        }


        override fun onDisconnectRtmp() {
            showNotification("Stream stopped")
            stopStream()
        }

        override fun onAuthErrorRtmp() {
            showNotification("Stream auth error")
        }

        override fun onAuthSuccessRtmp() {
            showNotification("Stream auth success")
        }
    }
}