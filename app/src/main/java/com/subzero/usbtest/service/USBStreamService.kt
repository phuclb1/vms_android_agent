package com.subzero.usbtest.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.arch.lifecycle.MutableLiveData
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.view.View
import android.widget.Toast
import com.pedro.rtplibrary.base.Camera2Base
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.pedro.rtplibrary.rtsp.RtspCamera2
import com.pedro.rtplibrary.view.OpenGlView
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.utils.UIThreadHelper
import com.serenegiant.utils.UIThreadHelper.runOnUiThread
import com.subzero.usbtest.Constants
import com.subzero.usbtest.R
import com.subzero.usbtest.activity.BackgroundCameraStreamActivity
import com.subzero.usbtest.activity.CameraStreamActivity
import com.subzero.usbtest.activity.USBStreamActivity
import com.subzero.usbtest.api.AgentClient
import com.subzero.usbtest.rtc.WebRtcClient
import com.subzero.usbtest.streamlib.RtmpUSB
import com.subzero.usbtest.streamlib.RtmpUSB2
import com.subzero.usbtest.streamlib.USBBase2
import com.subzero.usbtest.utils.LogService
import com.subzero.usbtest.utils.SessionManager
import kotlinx.android.synthetic.main.activity_main.*
import net.ossrs.rtmp.ConnectCheckerRtmp
import okhttp3.*
import org.webrtc.PeerConnection
import java.io.File
import java.io.IOException

class USBStreamService : Service() {
    private var endpoint: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "RTP service create")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH)
            notificationManager?.createNotificationChannel(channel)
        }
        keepAliveTrick()
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

        private val width = 1280
        private val height = 720
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
            this.uvcCamera = uvcCamera
        }

        fun isUVCCameraAvailable(): Boolean {
            if(this.uvcCamera == null)
                return false
            return true
        }

        fun setView(openGlView: OpenGlView) {
            this.openGlView = openGlView
            rtmpUSB?.replaceView(uvcCamera, openGlView)
        }

        fun setView(context: Context) {
            contextApp = context
            this.openGlView = null
            rtmpUSB?.replaceView(uvcCamera, context)
        }

        fun startPreview() {
            rtmpUSB?.startPreview(uvcCamera, width, height, 0)
        }

        fun init(context: Context, openGlView: OpenGlView) {
            this.openGlView = openGlView
            contextApp = context
            if (rtmpUSB == null)
                rtmpUSB = RtmpUSB2(this.contextApp, connectCheckerRtp)
        }

        fun stopStream() {
            if (rtmpUSB != null) {
                if (rtmpUSB!!.isStreaming) rtmpUSB!!.stopStream(uvcCamera)
            }
        }

        fun stopPreview() {
            if (rtmpUSB != null) {
                if (rtmpUSB!!.isOnPreview) rtmpUSB!!.stopPreview(uvcCamera)
            }
        }


        private val connectCheckerRtp = object : ConnectCheckerRtmp {
            override fun onConnectionSuccessRtmp() {
                showNotification("Stream started")
                Log.e(TAG, "RTP service destroy")
            }

            override fun onNewBitrateRtmp(bitrate: Long) {

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

        private fun showNotification(text: String) {
            contextApp?.let {
                val notification = NotificationCompat.Builder(it, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher_foreground)
                    .setContentTitle("VT Camera Agent Stream")
                    .setContentText(text).build()
                notificationManager?.notify(notifyId, notification)
            }
        }

        fun closeUVCCamera(){
            if(uvcCamera != null) {
                uvcCamera?.close()
                uvcCamera = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "Stream Service destroy")
        stopStream()
    }

    private fun prepareStreamRtp() {
        stopStream()
        stopPreview()
        if (endpoint!!.startsWith("rtmp")) {
            rtmpUSB = if (openGlView == null) {
                RtmpUSB2(baseContext, connectCheckerRtp)
            } else {
                RtmpUSB2(openGlView, connectCheckerRtp)
            }
        } else {
            rtmpUSB = if (openGlView == null) {
                RtmpUSB2(baseContext, connectCheckerRtp)
            } else {
                RtmpUSB2(openGlView, connectCheckerRtp)
            }
        }
    }

    private fun startStreamRtp(endpoint: String) {
        val sampleRate = 44100
        val audioBitrate = 64 * 1024
        val videoBitrate = 1200 * 1024
        val fps = 15
        val rotation = 0
        if (!rtmpUSB!!.isStreaming) {
            if(rtmpUSB!!.prepareVideo(
                    width, height, fps, videoBitrate, false, rotation, uvcCamera
                )
                && rtmpUSB!!.prepareAudio(
                    audioBitrate, sampleRate, true, false, false
                )){
                rtmpUSB!!.startStream(uvcCamera, endpoint)
            }
        } else {
            showNotification("You are already streaming :(")
        }
    }
}