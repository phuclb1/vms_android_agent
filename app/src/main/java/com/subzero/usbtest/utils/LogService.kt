package com.subzero.usbtest.utils

import android.annotation.SuppressLint
import android.util.Log
import com.subzero.usbtest.Constants
//import okhttp3.MediaType.Companion.toMediaTypeOrNull
//import okhttp3.MultipartBody
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import okhttp3.RequestBody
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

public class LogService private constructor(){
    private var previous_log_file: String = ""
    private var readyUploadFile: String = ""
    private var dir_log: String = ""
//    private var httpClient = OkHttpClient()

    init {
        val folder_log = File(Constants.DOC_DIR, "log")
        if(!folder_log.exists()){
            folder_log.mkdirs()
        }
        dir_log = folder_log.absolutePath
    }

//    fun getFileLog2Upload(): String {
//        val tmp = readyUploadFile
//        readyUploadFile = ""
//        return tmp
//    }

    @SuppressLint("SimpleDateFormat")
    fun appendLog(text: String, tag: String = "LOGSERVICE") {
        Log.d(tag, text)

        if (!Constants.ENABLE_SAVE_LOG) return

//        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = dir_log
        val timestamp = SimpleDateFormat("yyyyMMdd_HH").format(Date())
        val fileName = "logfile_$timestamp.txt"
        val logfile = File(dir, fileName)
        if (!logfile.exists()) {
            try {
                logfile.createNewFile()
                if (previous_log_file.isEmpty()) {
                    previous_log_file = fileName
                } else if (previous_log_file != fileName) {
                    readyUploadFile = previous_log_file
                    previous_log_file = fileName
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        try {
            val buf = BufferedWriter(FileWriter(logfile, true))
            val timeLog = SimpleDateFormat("yyyy/MM/dd-HH:mm:ss ").format(Date()).toString()
            buf.append("$timeLog   $tag : $text")
            buf.newLine()
            buf.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

//    fun uploadFileLog(file: File, fileName: String, authToken: String) {
//        val body = MultipartBody.Builder()
//            .setType(MultipartBody.FORM)
//            .addFormDataPart(
//                "file",
//                fileName,
//                RequestBody.create("application/octet-stream".toMediaTypeOrNull(), file)
//            )
//            .build()
//        val request = Request.Builder()
//            .url(Constants.BASE_FS_URL + Constants.API_SAVE_LOG_FILE + Constants.serialNumber)
//            .method("POST", body)
//            .header("Authorization", "Bearer $authToken")
//            .build()
//
//        try {
//            httpClient.newCall(request).execute().use { response ->
//                if (!response.isSuccessful) throw IOException("Unexpected code $response")
//                appendLog("Saved file log success: ${response.body!!.string()}")
//                file.delete()
//            }
//        } catch (e: Exception) {
//            appendLog("Save file log failure: ${e.toString()}")
//        }
//    }

    private object Holder {
        val INSTANCE = LogService()
    }
    companion object {
        fun getInstance(): LogService {
            return Holder.INSTANCE
        }
    }
}