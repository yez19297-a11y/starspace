package com.starspace.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.starspace.app.MainActivity
import com.starspace.app.R

/** 本地通知：AI 回复、空间新动态和评论。不依赖后端推送服务。 */
object Notify {
    private const val CHANNEL_MESSAGES = "starspace_messages"
    private const val CHANNEL_SPACE = "starspace_space"
    private var counter = 0

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "AI 消息", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "AI 伙伴发来的聊天回复"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SPACE, "空间动态", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "AI 发布的新动态、评论和回复"
            }
        )
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showMessage(context: Context, conversationId: String, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
        }
        post(context, CHANNEL_MESSAGES, conversationId.hashCode(), title, body, intent)
    }

    fun showSpace(context: Context, key: String, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        post(context, CHANNEL_SPACE, key.hashCode(), title, body, intent)
    }

    private fun post(
        context: Context,
        channel: String,
        requestCode: Int,
        title: String,
        body: String,
        intent: Intent
    ) {
        if (!canPost(context)) return
        ensureChannels(context)
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pending)
            .build()
        val id = (System.currentTimeMillis() % 100000).toInt() * 10 + (counter++ % 10)
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
