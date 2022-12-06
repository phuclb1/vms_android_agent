package com.subzero.usbtest

import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import com.subzero.usbtest.streamlib.RtmpUSB
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.android.synthetic.main.activity_main.*
import net.ossrs.rtmp.ConnectCheckerRtmp

class MainActivity : Activity(), SurfaceHolder.Callback, ConnectCheckerRtmp {
  private lateinit var sessionManager: SessionManager
  private lateinit var usbMonitor: USBMonitor
  private lateinit var rtmpUSB: RtmpUSB
  private var uvcCamera: UVCCamera? = null
  private var isUsbOpen = false
  private val width = 1280
  private val height = 720
  private val fps = 15
  private var token: String = ""

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    sessionManager = SessionManager(this)
    token = sessionManager.fetchAuthToken().toString()
    val rtmpUrl = Constants.RTMP_URL_HEADER + token
    Log.d(TAG, "RTMP url: $rtmpUrl")

    if (!hasPermissions()) {
      ActivityCompat.requestPermissions(this, Constants.CAMERA_REQUIRED_PERMISSIONS, 1)
    }

    rtmpUSB = RtmpUSB(openglview, this)
    usbMonitor = USBMonitor(this, onDeviceConnectListener)
    isUsbOpen = false
    usbMonitor.register()

    et_url.setText(rtmpUrl)
    start_stop.setOnClickListener {
      if (uvcCamera != null) {
        if (!rtmpUSB.isStreaming) {
          updateUI(isStream = true)
          startStream(et_url.text.toString())
        } else {
          updateUI(isStream = false)
          rtmpUSB.stopStream(uvcCamera)
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.d(TAG, "Main activity ======== onDestroy")
    if (rtmpUSB.isStreaming && uvcCamera != null) rtmpUSB.stopStream(uvcCamera)
    if (rtmpUSB.isOnPreview && uvcCamera != null) rtmpUSB.stopPreview(uvcCamera)
    if (isUsbOpen) {
      uvcCamera?.close()
      usbMonitor.unregister()
    }
  }


  /**
   * USB Monitor
   */
  private val onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
    override fun onAttach(device: UsbDevice?) {
      if (device != null) {
        Log.d(TAG, "onAttach============== " + device.deviceName)
        usbMonitor.requestPermission(device)
      }
    }

    override fun onConnect(
      device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?,
      createNew: Boolean
    ) {
      if (device != null) {
        Log.d(TAG, "onConnect============== " + device.deviceName)
      }else{
        Log.d(TAG, "onConnect============== Device null")
        return
      }
      val camera = UVCCamera()
      Log.d(TAG, ctrlBlock.toString())
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
      Log.d(TAG, "onDisConnect==============")
      if (uvcCamera != null) {
        updateUI(isStream = false)
        if(rtmpUSB.isStreaming){
          rtmpUSB.stopStream(uvcCamera)
        }

        uvcCamera?.close()
//        usbMonitor.unregister()
        uvcCamera = null
        isUsbOpen = false
      }
    }

    override fun onDettach(device: UsbDevice?) {
      Log.d(TAG, "onDettach==============")
      if (uvcCamera != null) {
        uvcCamera?.close()
        uvcCamera = null
        isUsbOpen = false
      }
    }

    override fun onCancel(device: UsbDevice?) {
      Log.d(TAG, "onCancel==============")
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

  override fun onDisconnectRtmp() {
    runOnUiThread {
      updateUI(isStream = false)
      rtmpUSB.stopStream(uvcCamera)
      Toast.makeText(this, "Disconnect", Toast.LENGTH_SHORT).show()
    }
  }


  /**
   * Surface
   */
  override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
  }

  override fun surfaceDestroyed(p0: SurfaceHolder?) {

  }

  override fun surfaceCreated(p0: SurfaceHolder?) {

  }


  private fun hasPermissions(): Boolean {
    for (permission in Constants.CAMERA_REQUIRED_PERMISSIONS) {
      if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, permission)) {
        return false
      }
    }
    return true
  }

  private fun startStream(url: String) {
    if (rtmpUSB.prepareVideo(width, height, fps, 4000 * 1024, false, 0,
        uvcCamera) && rtmpUSB.prepareAudio()) {
      rtmpUSB.startStream(uvcCamera, url)
    }
  }

  private fun updateUI(isStream: Boolean){
    if(isStream){
      start_stop.text = getString(R.string.stop)
      et_url.visibility = View.INVISIBLE
    }else{
      start_stop.text = getString(R.string.start)
      et_url.visibility = View.VISIBLE
    }
  }

  companion object{
    const val TAG = "Main"
  }
}
