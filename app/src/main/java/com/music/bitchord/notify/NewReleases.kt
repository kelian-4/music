package com.music.bitchord.notify

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit

object NewReleases {
    private const val PERIODIC_WORK = "new_releases"
    private const val ONE_OFF_WORK = "new_releases_now"
    private const val PREFS = "bitchord_new_releases"
    private const val KEY_SEEN = "seen"
    private const val KEY_LAST_CHECK = "last_complete_check"
    private const val CHANNEL_ID = "new_releases"
    private const val PERIOD_HOURS = 12L
    private const val FLEX_HOURS = 4L
    private const val CATCH_UP_MILLIS = 12L * 60 * 60 * 1000
    private const val PAUSE_MILLIS = 500L
    private const val MAX_ARTISTS_PER_CHECK = 60

    private val running = Mutex()

    fun init(app: Application) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            var firstLook = true
            AppSettings.newReleaseNotifications.distinctUntilChanged().collect { on ->
                if (on) {
                    schedule(app, replace = !firstLook)
                    if (!firstLook) checkNow(app)
                } else if (!firstLook) {
                    cancel(app)
                }
                firstLook = false
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (AppSettings.newReleaseNotifications.value && catchUpDue(app)) checkNow(app)
            }
        })
    }

    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<NewReleasesWorker>().setConstraints(constraints()).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF_WORK, ExistingWorkPolicy.KEEP, request)
    }

    internal fun cancel(context: Context) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(PERIODIC_WORK)
        work.cancelUniqueWork(ONE_OFF_WORK)
    }

    private fun schedule(context: Context, replace: Boolean) {
        val request = PeriodicWorkRequestBuilder<NewReleasesWorker>(
            PERIOD_HOURS,
            TimeUnit.HOURS,
            FLEX_HOURS,
            TimeUnit.HOURS,
        ).setConstraints(constraints()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun catchUpDue(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(KEY_LAST_CHECK, 0L) >= CATCH_UP_MILLIS

    internal suspend fun check(context: Context) {
        if (!running.tryLock()) return
        try {
            val followed = YtMusicRepository.subscribedArtists().getOrNull()
                ?.filter { it.browseId != null }
                ?.take(MAX_ARTISTS_PER_CHECK)
                ?: return
            if (followed.isEmpty()) return
            val store = prefs(context)
            var seen = NewReleasePolicy.decode(store.getString(KEY_SEEN, null))
            val findings = ArrayList<Pair<String, List<ReleaseRef>>>()
            var whole = true
            for (artist in followed) {
                if (!AppSettings.newReleaseNotifications.value) return
                val id = artist.browseId ?: continue
                val page = YtMusicRepository.artistPage(id).getOrNull()
                if (page == null) {
                    whole = false
                    continue
                }
                val current = page.sections.flatMap { it.items }
                    .filter { NewReleasePolicy.isRelease(it.browseId) }
                    .map { ReleaseRef(it.browseId.orEmpty(), it.title) }
                val diff = NewReleasePolicy.diff(seen[id], current)
                seen = seen + (id to diff.seenAfter)
                if (diff.fresh.isNotEmpty()) findings += artist.title to diff.fresh
                delay(PAUSE_MILLIS)
            }
            seen = NewReleasePolicy.forgetUnfollowed(seen, followed.mapNotNull { it.browseId })
            store.edit().putString(KEY_SEEN, NewReleasePolicy.encode(seen)).apply()
            NewReleasePolicy.toAnnounce(findings).forEach { (artist, releases) -> post(context, artist, releases) }
            if (whole) store.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        } finally {
            running.unlock()
        }
    }

    private fun post(context: Context, artist: String, releases: List<ReleaseRef>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.new_releases_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(context.getString(R.string.new_release_title, artist))
            .setContentText(releases.joinToString(", ") { it.title })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(artist.hashCode(), notification) }
    }
}

class NewReleasesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!AppSettings.newReleaseNotifications.value) {
            NewReleases.cancel(applicationContext)
            return Result.success()
        }
        NewReleases.check(applicationContext)
        return Result.success()
    }
}
