package com.toktiklite.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.toktiklite.Constants

/**
 * Implements [DownloadListener] so that anything the page triggers as a download (TikTok's
 * "Save video" action, an attachment link, etc.) is handed to the system [DownloadManager]
 * instead of silently doing nothing, which is what happens if no listener is registered at all.
 */
class DownloadHandler(private val context: Context) : DownloadListener {

    override fun onDownloadStart(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        try {
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri).apply {
                // Forward the session cookie; TikTok's media CDN URLs are frequently
                // short-lived/signed and cookie-gated, so a cookie-less request from
                // DownloadManager's own HTTP client would simply 403.
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
                addRequestHeader("User-Agent", userAgent)
                setMimeType(resolveMimeType(mimeType, url))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                val fileName = guessFileName(contentDisposition, url, mimeType)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "${Constants.DOWNLOAD_SUBDIRECTORY}/$fileName"
                )
                setTitle(fileName)
            }

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(context, "Downloading…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't start the download", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveMimeType(mimeType: String, url: String): String {
        if (mimeType.isNotBlank() && mimeType != "application/octet-stream") return mimeType
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "video/mp4"
    }

    private fun guessFileName(contentDisposition: String, url: String, mimeType: String): String {
        val fromDisposition = Regex("filename=\"?([^\";]+)\"?").find(contentDisposition)?.groupValues?.get(1)
        if (!fromDisposition.isNullOrBlank()) return sanitize(fromDisposition)

        val lastSegment = Uri.parse(url).lastPathSegment
        if (!lastSegment.isNullOrBlank() && lastSegment.contains('.')) return sanitize(lastSegment)

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "mp4"
        return "tiktok_${System.currentTimeMillis()}.$extension"
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
