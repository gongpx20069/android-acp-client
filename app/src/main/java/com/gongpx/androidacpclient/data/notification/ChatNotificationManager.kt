package com.gongpx.androidacpclient.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.gongpx.androidacpclient.MainActivity
import com.gongpx.androidacpclient.R

const val EXTRA_CHAT_ID = "com.gongpx.androidacpclient.extra.CHAT_ID"

class ChatNotificationManager(private val context: Context) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.chat_completion_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.chat_completion_channel_description)
            },
        )
    }

    fun showCompletion(chatId: String, chatTitle: String, preview: String) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val openChatIntent = Intent(context, MainActivity::class.java)
            .setAction("$ACTION_OPEN_CHAT.$chatId")
            .putExtra(EXTRA_CHAT_ID, chatId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            openChatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notificationManager.notify(
            chatId.hashCode(),
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_agentlink)
                .setContentTitle(chatTitle)
                .setContentText(preview)
                .setStyle(Notification.BigTextStyle().bigText(preview))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancel(chatId: String) {
        notificationManager.cancel(chatId.hashCode())
    }

    private companion object {
        const val CHANNEL_ID = "chat_completions"
        const val ACTION_OPEN_CHAT = "com.gongpx.androidacpclient.action.OPEN_CHAT"
    }
}
