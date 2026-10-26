package ua.pastushak.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var root: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var settingsButton: View

    private var apps: List<AppEntry> = emptyList()
    private var loadGeneration = 0
    private var dialog: Dialog? = null
    private var timeReceiverRegistered = false
    private val ukrainian: Locale = Locale.forLanguageTag("uk")

    /** Куди повернути фокус після перемальовування екрана. */
    private data class FocusTarget(val pkg: String, val inFavorites: Boolean)

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateClock()
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reloadApps()
        }
    }

    // ---------------------------------------------------------------- життєвий цикл

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        buildLayout()

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        register(packageReceiver, packageFilter)
        reloadApps()
    }

    override fun onResume() {
        super.onResume()
        val timeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        register(timeReceiver, timeFilter)
        timeReceiverRegistered = true
        updateClock()
    }

    override fun onPause() {
        super.onPause()
        if (timeReceiverRegistered) {
            safeUnregister(timeReceiver)
            timeReceiverRegistered = false
        }
    }

    override fun onDestroy() {
        safeUnregister(packageReceiver)
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    /** Кнопка Home, коли лаунчер уже відкритий: повертаємось на початок. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        goToStart()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Головний екран нікуди не закриваємо — лише повертаємось на початок.
        goToStart()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val tile = currentFocus as? AppTile
            if (tile != null) showAppMenu(tile) else showSettings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------------------------------------------------------------- розмітка

    private fun buildLayout() {
        root = FrameLayout(this)
        root.background = Backgrounds.drawable(prefs.backgroundIndex)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(SIDE), dp(24), dp(SIDE), 0)
            clipChildren = false
            clipToPadding = false
        }

        // Шапка: годинник ліворуч, кнопка налаштувань праворуч
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(INNER), 0, dp(INNER), dp(4))
        }
        val clockBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        timeView = TextView(this).apply {
            textSize = 44f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            includeFontPadding = false
        }
        dateView = TextView(this).apply {
            textSize = 16f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(0, dp(4), 0, 0)
        }
        clockBox.addView(timeView)
        clockBox.addView(dateView)
        header.addView(clockBox, LinearLayout.LayoutParams(0, WRAP, 1f))

        settingsButton = headerButton(R.drawable.ic_settings, "Налаштування") { showSettings() }
        header.addView(settingsButton, LinearLayout.LayoutParams(WRAP, WRAP))
        column.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(40))
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            isFocusable = false
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(INNER), dp(4), dp(INNER), dp(48))
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        column.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        content.addView(hint("Завантаження застосунків…"))
    }

    private fun render(target: FocusTarget? = currentTarget()) {
        content.removeAllViews()

        val byPkg = apps.associateBy { it.packageName }
        val hidden = prefs.hidden
        val favorites = prefs.favorites.filter { it !in hidden }.mapNotNull { byPkg[it] }
        val visible = apps.filter { it.packageName !in hidden }

        val cols = prefs.columns
        val gap = dp(GAP)
        val available = resources.displayMetrics.widthPixels - 2 * dp(SIDE) - 2 * dp(INNER)
        val tileWidth = (available - gap * (cols - 1)) / cols

        var first: View? = null
        var favMatch: View? = null
        var gridMatch: View? = null

        // --- Улюблене
        content.addView(sectionTitle("Улюблене"))
        if (favorites.isEmpty()) {
            content.addView(hint("Затисніть OK на застосунку й оберіть «Додати в улюблене»."))
        } else {
            val pad = dp(12)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            for ((i, app) in favorites.withIndex()) {
                val tile = createTile(app, inFavorites = true, width = tileWidth)
                val lp = LinearLayout.LayoutParams(WRAP, WRAP)
                if (i > 0) lp.leftMargin = gap
                row.addView(tile, lp)
                if (first == null) first = tile
                if (target != null && target.pkg == app.packageName) favMatch = tile
            }
            val rowScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                clipToPadding = false
                isFocusable = false
                setPadding(pad, pad, pad, pad)
            }
            rowScroll.addView(row)
            val lp = LinearLayout.LayoutParams(MATCH, WRAP)
            lp.leftMargin = -pad
            lp.rightMargin = -pad
            lp.topMargin = -pad / 2
            content.addView(rowScroll, lp)
        }

        // --- Усі застосунки
        content.addView(sectionTitle("Усі застосунки · ${visible.size}"))
        if (visible.isEmpty()) {
            content.addView(hint("Застосунків не знайдено."))
        }
        for (chunk in visible.chunked(cols)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            for ((i, app) in chunk.withIndex()) {
                val tile = createTile(app, inFavorites = false, width = tileWidth)
                val lp = LinearLayout.LayoutParams(WRAP, WRAP)
                if (i > 0) lp.leftMargin = gap
                row.addView(tile, lp)
                if (first == null) first = tile
                if (target != null && target.pkg == app.packageName) gridMatch = tile
            }
            val lp = LinearLayout.LayoutParams(MATCH, WRAP)
            lp.bottomMargin = dp(20)
            content.addView(row, lp)
        }

        val preferred = if (target?.inFavorites == true) favMatch ?: gridMatch else gridMatch ?: favMatch
        (preferred ?: first ?: settingsButton).requestFocus()
    }

    private fun createTile(app: AppEntry, inFavorites: Boolean, width: Int): AppTile =
        AppTile(this, app, inFavorites, width).apply {
            setOnClickListener { launch(app) }
            setOnLongClickListener { v ->
                showAppMenu(v as AppTile)
                true
            }
        }

    private fun headerButton(iconRes: Int, text: String, onClick: () -> Unit): View {
        val idle = pill(0x33FFFFFF)
        val focused = pill(Color.WHITE)
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
        }
        val label = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(8), 0, 0, 0)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(18), dp(8))
            background = idle
            isFocusable = true
            isClickable = true
            addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(label, LinearLayout.LayoutParams(WRAP, WRAP))
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, hasFocus ->
                background = if (hasFocus) focused else idle
                val color = if (hasFocus) Color.BLACK else Color.WHITE
                icon.setColorFilter(color)
                label.setTextColor(color)
            }
        }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(0xE6FFFFFF.toInt())
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dp(18), 0, dp(10))
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(0x99FFFFFF.toInt())
        setPadding(0, dp(6), 0, dp(12))
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat()
        setColor(color)
    }

    // ---------------------------------------------------------------- дані

    private fun reloadApps() {
        val generation = ++loadGeneration
        thread(name = "load-apps") {
            val list = AppRepository.load(applicationContext)
            runOnUiThread {
                if (generation == loadGeneration && !isDestroyed) {
                    apps = list
                    seedFavoritesOnFirstRun()
                    render()
                }
            }
        }
    }

    /** При першому запуску додаємо в улюблене популярні застосунки, якщо вони встановлені. */
    private fun seedFavoritesOnFirstRun() {
        if (prefs.initialized) return
        val installed = apps.map { it.packageName }.toSet()
        prefs.favorites = DEFAULT_FAVORITES.filter { it in installed }
        prefs.initialized = true
    }

    private fun updateClock() {
        val now = Date()
        timeView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val date = SimpleDateFormat("EEEE, d MMMM", ukrainian).format(now)
        dateView.text = date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(ukrainian) else it.toString() }
    }

    // ---------------------------------------------------------------- дії

    private fun launch(app: AppEntry) {
        try {
            startActivity(AppRepository.launchIntent(app))
            return
        } catch (e: Exception) {
            // нижче спробуємо запасний варіант
        }
        val pm = packageManager
        val fallback = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
        if (fallback == null) {
            toast("Не вдалося відкрити «${app.label}»")
        } else {
            openSafely(fallback)
        }
    }

    private fun openSafely(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Не вдалося відкрити")
        }
    }

    private fun goToStart() {
        dialog?.dismiss()
        dialog = null
        scroll.smoothScrollTo(0, 0)
        (findFirstTile(content) ?: settingsButton).requestFocus()
    }

    private fun showAppMenu(tile: AppTile) {
        val app = tile.app
        val pkg = app.packageName
        val favs = prefs.favorites.toMutableList()
        val actions = ArrayList<Pair<String, () -> Unit>>()

        actions.add(action("Відкрити") { launch(app) })
        if (pkg in favs) {
            if (tile.inFavorites) {
                val i = favs.indexOf(pkg)
                if (i > 0) {
                    actions.add(action("Перемістити ліворуч") {
                        Collections.swap(favs, i, i - 1)
                        prefs.favorites = favs
                        render(FocusTarget(pkg, inFavorites = true))
                    })
                }
                if (i < favs.lastIndex) {
                    actions.add(action("Перемістити праворуч") {
                        Collections.swap(favs, i, i + 1)
                        prefs.favorites = favs
                        render(FocusTarget(pkg, inFavorites = true))
                    })
                }
            }
            actions.add(action("Прибрати з улюбленого") {
                favs.remove(pkg)
                prefs.favorites = favs
                render(FocusTarget(pkg, inFavorites = false))
            })
        } else {
            actions.add(action("Додати в улюблене") {
                favs.add(pkg)
                prefs.favorites = favs
                render(FocusTarget(pkg, inFavorites = false))
            })
        }
        actions.add(action("Сховати з головного екрана") {
            favs.remove(pkg)
            prefs.favorites = favs
            prefs.hidden = prefs.hidden + pkg
            render(null)
        })
        actions.add(action("Про застосунок") {
            openSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        })
        if (!app.isSystem) {
            actions.add(action("Видалити") {
                openSafely(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
            })
        }
        showList(app.label, actions)
    }

    private fun showSettings() {
        val presets = Backgrounds.presets
        val bgName = presets[prefs.backgroundIndex.coerceIn(0, presets.lastIndex)].name
        val homeState = if (isHomeServiceEnabled()) "увімкнено" else "вимкнено"
        val actions = listOf(
            action("Фон: $bgName") { chooseBackground() },
            action("Колонок у сітці: ${prefs.columns}") { chooseColumns() },
            action("Приховані застосунки (${prefs.hidden.size})") { manageHidden() },
            action("Кнопка Home → цей лаунчер ($homeState)") { showHomeHelp() },
            action("Системні налаштування Android") { openSafely(Intent(Settings.ACTION_SETTINGS)) },
            action("Про лаунчер") { showAbout() },
        )
        showList("Налаштування лаунчера", actions)
    }

    private fun chooseBackground() {
        val names = Backgrounds.presets.map { it.name }.toTypedArray()
        val current = prefs.backgroundIndex.coerceIn(0, names.lastIndex)
        dialog = AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle("Фон")
            .setSingleChoiceItems(names, current) { d, which ->
                prefs.backgroundIndex = which
                root.background = Backgrounds.drawable(which)
                d.dismiss()
            }
            .show()
    }

    private fun chooseColumns() {
        val options = intArrayOf(4, 5, 6, 7)
        val names = options.map { "$it" }.toTypedArray()
        val current = options.indexOf(prefs.columns).coerceAtLeast(0)
        dialog = AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle("Колонок у сітці")
            .setSingleChoiceItems(names, current) { d, which ->
                prefs.columns = options[which]
                d.dismiss()
                render(null)
            }
            .show()
    }

    private fun manageHidden() {
        val hidden = prefs.hidden.toList()
        if (hidden.isEmpty()) {
            toast("Прихованих застосунків немає")
            return
        }
        val byPkg = apps.associateBy { it.packageName }
        val labels = hidden.map { byPkg[it]?.label ?: it }.toTypedArray()
        val checked = BooleanArray(hidden.size) { true }
        dialog = AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle("Зніміть позначку, щоб показати")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Готово") { _, _ ->
                prefs.hidden = hidden.filterIndexed { i, _ -> checked[i] }.toSet()
                render(null)
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun showHomeHelp() {
        val state = if (isHomeServiceEnabled()) "Зараз служба увімкнена ✓" else "Зараз служба вимкнена."
        val message = "Стандартний лаунчер Android TV може перехоплювати кнопку Home. " +
            "Щоб вона завжди відкривала цей лаунчер, увімкніть службу:\n\n" +
            "Налаштування → Параметри пристрою → Спеціальні можливості → " +
            "«${getString(R.string.home_service_label)}» → Увімкнути.\n\n" +
            "Служба реагує лише на появу стандартного лаунчера і нічого не читає з екрана.\n\n" +
            state
        dialog = AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle("Кнопка Home")
            .setMessage(message)
            .setPositiveButton("Відкрити спеціальні можливості") { _, _ ->
                openSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Закрити", null)
            .show()
    }

    private fun showAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
        val message = "Версія $version\n\n" +
            "OK — відкрити застосунок\n" +
            "Затиснути OK або кнопка «Меню» — дії із застосунком\n" +
            "Назад або Home — повернутися на початок"
        dialog = AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(getString(R.string.app_name))
            .setMessage(message)
            .setPositiveButton("Закрити", null)
            .show()
    }

    private fun showList(title: String, actions: List<Pair<String, () -> Unit>>) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    private fun isHomeServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, HomeButtonService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    // ---------------------------------------------------------------- допоміжне

    private fun action(title: String, block: () -> Unit): Pair<String, () -> Unit> = Pair(title, block)

    private fun currentTarget(): FocusTarget? =
        (currentFocus as? AppTile)?.let { FocusTarget(it.app.packageName, it.inFavorites) }

    private fun findFirstTile(group: ViewGroup): View? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is AppTile) return child
            if (child is ViewGroup) findFirstTile(child)?.let { return it }
        }
        return null
    }

    private fun register(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun safeUnregister(receiver: BroadcastReceiver) {
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // не був зареєстрований
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SIDE = 48   // безпечна зона краю екрана телевізора
        const val INNER = 12  // запас, щоб збільшена плитка не обрізалась
        const val GAP = 16

        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        val DIALOG_THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert

        val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.google.android.youtube.tvmusic",
            "com.android.vending",
        )
    }
}
