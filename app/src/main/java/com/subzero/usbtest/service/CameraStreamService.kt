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
import com.pedro.rtplibrary.base.Camera2Base
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.pedro.rtplibrary.view.OpenGlView
import com.subzero.usbtest.R
import net.ossrs.rtmp.ConnectCheckerRtmp

class CameraStreamService : Service() {
    companion object {
        private const val TAG = "CameraStreamService"
        private const val channelId = "rtpStreamChannel"
        private const val notifyId = 123456
        private var notificationManager: NotificationManager? = null
        val observer = MutableLiveData<CameraStreamService?>()
    }

    private var camera2Base: Camera2Base? = null

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
        camera2Base = RtmpCamera2(this, true, connectCheckerRtp)
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
        return camera2Base?.prepareVideo() ?: false && camera2Base?.prepareAudio() ?: false
    }

    fun startPreview() {
        Log.d(TAG, "--------- start preview")
        camera2Base?.startPreview()
    }

    fun stopPreview() {
        camera2Base?.stopPreview()
    }

    fun switchCamera() {
        camera2Base?.switchCamera()
    }

    fun isStreaming(): Boolean = camera2Base?.isStreaming ?: false

    fun isRecording(): Boolean = camera2Base?.isRecording ?: false

    fun isOnPreview(): Boolean = camera2Base?.isOnPreview ?: false

    fun startStream(endpoint: String) {
        camera2Base?.startStream(endpoint)
    }

    fun stopStream() {
        camera2Base?.stopStream()
    }

    fun startRecord(path: String) {
        camera2Base?.startRecord(path) {
            Log.i(TAG, "record state: ${it.name}")
        }
    }

    fun stopRecord() {
        camera2Base?.stopRecord()
    }

    fun setView(openGlView: OpenGlView) {
//        camera2Base?.replaceView(openGlView)
    }

    fun setView(context: Context) {
//        camera2Base?.replaceView(context)
    }

    private val connectCheckerRtp = object : ConnectCheckerRtmp {

        override fun onConnectionSuccessRtmp() {
            showNotification("Stream started")
            Log.e(TAG, "RTP service destroy")
        }

        override fun onConnectionFailedRtmp(reason: String) {
            showNotification("Stream connection failed")
            Log.e(TAG, "RTP service destroy")
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
}