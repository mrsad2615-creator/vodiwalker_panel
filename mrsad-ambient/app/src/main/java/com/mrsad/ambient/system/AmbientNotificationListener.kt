package com.mrsad.ambient.system

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mrsad.ambient.domain.model.NotificationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AmbientNotificationListener : NotificationListenerService() {

    companion object {
        private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
        val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
        refreshActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshActiveNotifications()
    }

    private fun refreshActiveNotifications() {
        try {
            val list = activeNotifications?.mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return@mapNotNull null
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                NotificationItem(
                    id = sbn.key,
                    packageName = sbn.packageName,
                    appName = sbn.packageName.substringAfterLast('.'),
                    title = title,
                    text = text,
                    timestamp = sbn.postTime,
                    category = sbn.notification.category ?: "general"
                )
            }?.take(10) ?: emptyList()
            _notifications.value = list
        } catch (_: SecurityException) {
            _notifications.value = emptyList()
        }
    }
}
