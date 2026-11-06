package com.starspace.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.starspace.app.data.ApiClient
import com.starspace.app.data.AppStore
import java.util.concurrent.TimeUnit

/**
 * 后台每 15 分钟看一次空间，有新动态就发通知。
 * 只在配置了后端地址且通知开启时工作，直连模式下不产生任何请求。
 */
class SpaceCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = AppStore(applicationContext).data.settings
        if (!settings.backendReady || !settings.notificationsEnabled) return Result.success()

        val prefs = applicationContext.getSharedPreferences("space_check", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen_ids", emptySet()).orEmpty()

        return try {
            val posts = ApiClient().fetchPosts(settings.serverUrl)
            // 第一次运行只记录基线，避免把历史动态全部推一遍
            if (seen.isNotEmpty()) {
                posts.filter { it.id !in seen && it.authorType == "ai" }
                    .take(3)
                    .forEach { post ->
                        Notify.showSpace(
                            applicationContext,
                            post.id,
                            "${post.authorName} 发了新动态",
                            post.content
                        )
                    }
            }
            prefs.edit().putStringSet("seen_ids", posts.map { it.id }.toSet()).apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "starspace-space-check"

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SpaceCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
