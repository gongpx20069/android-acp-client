package com.gongpx.androidacpclient.data.notification

internal data class ChatCompletionAttention(
    val markUnread: Boolean,
    val showNotification: Boolean,
)

internal fun chatCompletionAttention(appInForeground: Boolean, chatIsOpen: Boolean): ChatCompletionAttention {
    return ChatCompletionAttention(
        markUnread = !appInForeground || !chatIsOpen,
        showNotification = !appInForeground,
    )
}
