package ua.pastushak.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import java.text.Collator
import java.util.Locale

/** Один застосунок, який можна запустити з лаунчера. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    val isLeanback: Boolean,
    val banner: Drawable?,
    val icon: Drawable,
    val isSystem: Boolean,
)

object AppRepository {

    /** Збирає всі застосунки: спершу ТВ-версії (з банерами), потім звичайні. */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val result = LinkedHashMap<String, AppEntry>()

        fun collect(category: String, leanback: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val list = safe { pm.queryIntentActivities(intent, 0) } ?: emptyList()
            for (ri in list) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName
                if (pkg == context.packageName || result.containsKey(pkg)) continue

                val banner: Drawable? = if (leanback) {
                    safe { ai.loadBanner(pm) } ?: safe { ai.applicationInfo.loadBanner(pm) }
                } else null
                val icon: Drawable = safe { ri.loadIcon(pm) } ?: pm.defaultActivityIcon
                val label = safe { ri.loadLabel(pm).toString() }
                    ?.takeIf { it.isNotBlank() } ?: pkg
                val isSystem = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                result[pkg] = AppEntry(
                    packageName = pkg,
                    label = label,
                    component = ComponentName(pkg, ai.name),
                    isLeanback = leanback,
                    banner = banner,
                    icon = icon,
                    isSystem = isSystem,
                )
            }
        }

        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, leanback = true)
        collect(Intent.CATEGORY_LAUNCHER, leanback = false)

        val collator = Collator.getInstance(Locale.forLanguageTag("uk"))
        return result.values.sortedWith(compareBy(collator) { it.label })
    }

    fun launchIntent(app: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(
                if (app.isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
            )
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private inline fun <T> safe(block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            null
        }
}
