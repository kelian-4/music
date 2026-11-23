package com.music.amber.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.amber.data.settings.AppSettings

class DownloadResumeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owed = DownloadQueueStore.all()
        if (owed.isEmpty()) return Result.success()
        Downloads.downloadNow(applicationContext, owed.map { it.toSong() }, null)

        return if (DownloadQueueStore.all().isEmpty()) Result.success() else Result.retry()
    }
}

internal object DownloadResumeWork {
    private const val UNIQUE_NAME = "download-resume"

    fun schedule(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (AppSettings.wifiOnlyDownloads.value) NetworkType.UNMETERED else NetworkType.CONNECTED,
                )
                .build()
            val request = OneTimeWorkRequestBuilder<DownloadResumeWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
