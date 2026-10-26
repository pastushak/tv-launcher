package ua.pastushak.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Необов'язкова служба спеціальних можливостей.
 * Коли система відкриває стандартний головний екран Android TV (наприклад, після кнопки Home),
 * служба одразу відкриває наш лаунчер. Інші вікна не відстежуються — список пакетів
 * обмежено у res/xml/home_service.xml.
 */
class HomeButtonService : AccessibilityService() {

    private var lastStart = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in STOCK_LAUNCHERS) return

        // Реагуємо лише на головне вікно, а не на панелі (наприклад, вибір джерела HDMI)
        val cls = event.className?.toString() ?: ""
        val isHomeScreen = cls.isEmpty() || cls in HOME_ACTIVITIES ||
            cls.endsWith(".MainActivity") || cls.endsWith(".HomeActivity")
        if (!isHomeScreen) return

        val now = SystemClock.uptimeMillis()
        if (now - lastStart < 1000) return
        lastStart = now

        try {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            )
        } catch (e: Exception) {
            // нічого: залишиться стандартний лаунчер
        }
    }

    override fun onInterrupt() {}

    private companion object {
        val STOCK_LAUNCHERS = setOf(
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
        )
        val HOME_ACTIVITIES = setOf(
            "com.google.android.tvlauncher.MainActivity",
            "com.google.android.apps.tv.launcherx.home.HomeActivity",
        )
    }
}
