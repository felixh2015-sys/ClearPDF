package com.chethan616.clearpdf.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.chethan616.clearpdf.R

/**
 * Hands a finished file to Google Drive (or any other app) through Android's own share
 * mechanism. No Google account keys are needed inside this app: the Drive app does the
 * upload with the account already on the phone, and its own screen lets the user pick the
 * folder and then share the file with other people.
 */
object ShareToCloud {

    const val MIME_PDF = "application/pdf"
    const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private const val DRIVE_PACKAGE = "com.google.android.apps.docs"

    private fun sendIntent(uri: Uri, mime: String, title: String?): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!title.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            clipData = ClipData.newRawUri(title ?: "", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /**
     * Opens Google Drive's "Upload to Drive" screen for [uri]. When Drive isn't installed,
     * falls back to the normal share sheet so the user can still choose another app.
     */
    fun uploadToDrive(context: Context, uri: Uri, mime: String, title: String? = null) {
        val drive = sendIntent(uri, mime, title).setPackage(DRIVE_PACKAGE)
        try {
            context.startActivity(drive)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.drive_not_installed), Toast.LENGTH_LONG).show()
            share(context, uri, mime, title)
        } catch (_: SecurityException) {
            share(context, uri, mime, title)
        }
    }

    /** Standard Android share sheet (WhatsApp, Gmail, Drive, …). */
    fun share(context: Context, uri: Uri, mime: String, title: String? = null) {
        val chooser = Intent.createChooser(sendIntent(uri, mime, title), context.getString(R.string.share))
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.no_app_to_share), Toast.LENGTH_SHORT).show()
        }
    }
}
