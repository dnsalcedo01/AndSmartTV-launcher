package com.andsmarttv.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class OnlineBannerManager(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "online_banners").apply {
        if (!exists()) mkdirs()
    }

    private val bannerEndpoints = listOf(
        "https://raw.githubusercontent.com/theothernt/androidtv-banners/master/banners/%s.png",
        "https://raw.githubusercontent.com/theothernt/androidtv-banners/master/banners/%s.jpg",
        "https://raw.githubusercontent.com/shmykelsa/AA-Banners/main/banners/%s.png",
        "https://raw.githubusercontent.com/shmykelsa/AA-Banners/main/banners/%s.jpg",
        "https://raw.githubusercontent.com/yuliskov/LeanbackBanners/master/banners/%s.png",
        "https://raw.githubusercontent.com/yuliskov/LeanbackBanners/master/banners/%s.jpg",
        "https://raw.githubusercontent.com/benfoxley/atv-banners/master/banners/%s.png",
        "https://raw.githubusercontent.com/benfoxley/atv-banners/master/banners/%s.jpg"
    )

    fun getCachedBanner(packageName: String): Drawable? {
        val pngFile = File(cacheDir, "$packageName.png")
        if (pngFile.exists()) {
            return try {
                val bitmap = BitmapFactory.decodeFile(pngFile.absolutePath)
                if (bitmap != null) BitmapDrawable(context.resources, bitmap) else null
            } catch (e: Exception) {
                null
            }
        }
        val jpgFile = File(cacheDir, "$packageName.jpg")
        if (jpgFile.exists()) {
            return try {
                val bitmap = BitmapFactory.decodeFile(jpgFile.absolutePath)
                if (bitmap != null) BitmapDrawable(context.resources, bitmap) else null
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    suspend fun fetchBannerOnline(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        val cached = getCachedBanner(packageName)
        if (cached != null) return@withContext cached

        for (endpointFormat in bannerEndpoints) {
            val urlString = String.format(endpointFormat, packageName)
            try {
                val url = URL(urlString)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 3500
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    connection.disconnect()

                    if (bitmap != null) {
                        try {
                            val cachedFile = File(cacheDir, "$packageName.png")
                            val out = FileOutputStream(cachedFile)
                            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                            out.flush()
                            out.close()
                        } catch (e: Exception) {
                            // Ignore cache write failure
                        }
                        return@withContext BitmapDrawable(context.resources, bitmap)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                // Try next endpoint
            }
        }
        null
    }
}
