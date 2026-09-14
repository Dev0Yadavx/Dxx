package com.allsocial.dx

import android.os.Environment
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class BackendBridge {
    private val pyInstance: Python
        get() = Python.getInstance()

    private val backendModule
        get() = pyInstance.getModule("backend_engine")

    // 1. Metadata & Stream Fetch
    suspend fun fetchMedia(url: String): JSONObject = withContext(Dispatchers.IO) {
        val resultStr = backendModule.callAttr("get_stream_details", url).toString()
        JSONObject(resultStr)
    }

    // 2. Local Download
    suspend fun executeDownload(url: String, formatId: String, title: String) = withContext(Dispatchers.IO) {
        val destFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        
        backendModule.callAttr("download_media_file", url, formatId, destFolder, safeTitle)
    }
}
