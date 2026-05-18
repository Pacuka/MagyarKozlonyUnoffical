package com.pacuka.magyarkozlony.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object Downloader {
    fun downloadPdf(context: Context, url: String, title: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Közlöny letöltése...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$title.pdf")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }
}
