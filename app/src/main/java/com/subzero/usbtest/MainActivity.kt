package com.subzero.usbtest

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import com.subzero.usbtest.streamlib.RtmpUSB
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.android.synthetic.main.activity_main.*
import net.ossrs.rtmp.ConnectCheckerRtmp

//val DEFAULT_RTMP_URL = "rtmp://113.161.183.245:1935/BPC_CAMJP01_abc123?user=admin&pass=Ab2C67e2021"
val DEFAULT_RTMP_URL = "rtmp://103.160.75.240/live/livestream"

class MainActivity : Activity(), SurfaceHolder.Callback, ConnectCheckerRtmp {
  private lateinit var usbMonitor: USBMonitor
  private var uvcCamera: UVCCamera? = null
  private var isUsbOpen = true
  private val width = 1280
  private val height = 720
  private val fps = 15
  private lateinit var rtmpUSB: RtmpUSB

  private val permissions = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.CAMERA,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
  )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    if (!hasPermissions()) {
      ActivityCompat.requestPermissions(this, permissions, 1)
    }

    rtmpUSB = RtmpUSB(openglview, this)
    usbMonitor = USBMonitor(this, onDeviceConnectListener)
    isUsbOpen = false
    usbMonitor.register()
    et_url.setText(DEFAULT_RTMP_URL)
    start_stop.setOnClickListener {
//      test_usbcam()
      if (uvcCamera != null) {
        if (!rtmpUSB.isStreaming) {
          startStream(et_url.text.toString())
          start_stop.text = "Stop stream"
        } else {
          rtmpUSB.stopStream(uvcCamera)
          start_stop.text = "Start stream"
        }
      }
    }
  }

  private fun test_usbcam(){
    rtmpUSB = RtmpUSB(openglview, this)
    usbMonitor = USBMonitor(this, onDeviceConnectListener)
    isUsbOpen = false
    usbMonitor.register()
  }

  private fun startStream(url: String) {
    if (rtmpUSB.prepareVideo(width, height, fps, 4000 * 1024, false, 0,
        uvcCamera) && rtmpUSB.prepareAudio()) {
      rtmpUSB.startStream(uvcCamera, url)
    }
  }

  private val onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
    override fun onAttach(device: UsbDevice?) {
      if (device != null) {
        Log.d("MAIN", "onAttach============== " + device.deviceName)
      }
      usbMonitor.requestPermission(device)
    }

    override fun onConnect(
      device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?,
      createNew: Boolean
    ) {
      if (device != null) {
        Log.d("MAIN", "onConnect============== " + device.deviceName)
      }else{
        Log.d("MAIN", "onConnect============== Device null")
      }
      val camera = UVCCamera()
      Log.d("MAIN", ctrlBlock.toString())
      camera.open(ctrlBlock)
      try {
        camera.setPreviewSize(width, height, UVCCamera.FRAME_FORMAT_MJPEG)
      } catch (e: IllegalArgumentException) {
        camera.destroy()
        try {
          camera.setPreviewSize(width, height, UVCCamera.DEFAULT_PREVIEW_MODE)
        } catch (e1: IllegalArgumentException) {
          return
        }
      }
      uvcCamera = camera
      rtmpUSB.startPreview(uvcCamera, width, height)
      isUsbOpen = true
    }

    override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
      Log.d("MAIN", "onDisConnect==============")
      if (uvcCamera != null) {
        uvcCamera?.close()
        uvcCamera = null
        isUsbOpen = false
      }
    }

    override fun onDettach(device: UsbDevice?) {
      Log.d("MAIN", "onDettach==============")
      if (uvcCamera != null) {
        uvcCamera?.close()
        uvcCamera = null
        isUsbOpen = false
      }
    }

    override fun onCancel(device: UsbDevice?) {
      Log.d("MAIN", "onCancel==============")
    }
  }


  override fun onAuthSuccessRtmp() {
  }

  override fun onConnectionSuccessRtmp() {
    runOnUiThread {
      Toast.makeText(this, "Rtmp connection success", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onConnectionFailedRtmp(reason: String?) {
    runOnUiThread {
      Toast.makeText(this, "Rtmp connection failed! $reason", Toast.LENGTH_SHORT).show()
      rtmpUSB.stopStream(uvcCamera)
    }
  }

  override fun onAuthErrorRtmp() {
  }

  override fun onDisconnectRtmp() {
    runOnUiThread {
      Toast.makeText(this, "Disconnect", Toast.LENGTH_SHORT).show()
    }
  }

  override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
  }

  override fun surfaceDestroyed(p0: SurfaceHolder?) {

  }

  override fun surfaceCreated(p0: SurfaceHolder?) {

  }

  override fun onDestroy() {
    super.onDestroy()
      if (rtmpUSB.isStreaming && uvcCamera != null) rtmpUSB.stopStream(uvcCamera)
    if (rtmpUSB.isOnPreview && uvcCamera != null) rtmpUSB.stopPreview(uvcCamera)
    if (isUsbOpen) {
      uvcCamera?.close()
      usbMonitor.unregister()
    }
  }

  private fun hasPermissions(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      for (permission in permissions) {
        if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, permission)) {
          return false
        }
      }
    }
    return true
  }
}
