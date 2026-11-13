package com.lightreader.app.feature.download

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lightreader.app.ReaderApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Notification controls call the exact same durable business action as the download screen. */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PAUSE) return
        val id = intent.getStringExtra(TASK_ID) ?: return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                (context.applicationContext as ReaderApplication).container.downloadRepository.pause(id)
            } finally { result.finish() }
        }
    }

    companion object {
        private const val PAUSE = "com.lightreader.app.PAUSE_DOWNLOAD"
        private const val TASK_ID = "task_id"
        fun pauseIntent(context: Context, taskId: String): PendingIntent = PendingIntent.getBroadcast(
            context, taskId.hashCode(), Intent(context, DownloadActionReceiver::class.java)
                .setAction(PAUSE).setData(android.net.Uri.parse("reader-download://pause/$taskId")).putExtra(TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
