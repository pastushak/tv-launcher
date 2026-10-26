package ua.pastushak.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
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
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var root: FrameLayout
    private lateinit var dimView: View
    private lateinit var scroll: ScrollView
    private lateinit var greetingView: TextView
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var settingsButton: View
    private lateinit var dockBody: FrameLayout
    private lateinit var allTitle: TextView
    private lateinit var grid: LinearLayout

    private var apps: List<AppEntry> = emptyList()
    private var loadGeneration = 0
    private var backgroundGeneration = 0
    private var dialog: Dialog? = null
    private var timeReceiverRegistered = false
    private val ukrainian: Locale = Locale.forLanguageTag("uk")

    /** Куди повернути фокус після перемальовування. */
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
        applyBackground(prefs.backgroundIndex)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        goToStart()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
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
        val screenH = resources.displayMetrics.heightPixels

        root = FrameLayout(this)
        root.setBackgroundColor(Theme.INK)

        // Затемнення зверху і знизу, щоб текст читався на світлому небі
        val scrim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66000000, 0x1A000000, 0x00000000, 0x33000000, 0x99000000.toInt())
            )
        }
        root.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))

        // Додаткове затемнення, коли прокручуємо до всіх застосунків
        dimView = View(this).apply {
            setBackgroundColor(0xB3141413.toInt())
            alpha = 0f
        }
        root.addView(dimView, FrameLayout.LayoutParams(MATCH, MATCH))

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(36))
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            isFocusable = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(SIDE), 0, dp(SIDE), dp(56))
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        // --- Верхня частина: привітання, годинник, дата
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(34), 0, 0)
            clipChildren = false
            clipToPadding = false
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
        }
        val spark = ImageView(this).apply { setImageResource(R.drawable.ic_spark) }
        topRow.addView(spark, LinearLayout.LayoutParams(dp(18), dp(18)))
        greetingView = TextView(this).apply {
            textSize = 19f
            setTextColor(Theme.CREAM_80)
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            setPadding(dp(10), 0, 0, 0)
            setShadowLayer(dp(8).toFloat(), 0f, dp(1).toFloat(), 0x66000000)
        }
        topRow.addView(greetingView, LinearLayout.LayoutParams(0, WRAP, 1f))
        settingsButton = glassIconButton(R.drawable.ic_settings) { showSettings() }
        topRow.addView(settingsButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        hero.addView(topRow, LinearLayout.LayoutParams(MATCH, WRAP))

        timeView = TextView(this).apply {
            textSize = 84f
            setTextColor(Theme.CREAM)
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            includeFontPadding = false
            letterSpacing = -0.02f
            setShadowLayer(dp(16).toFloat(), 0f, dp(2).toFloat(), 0x59000000)
        }
        val timeLp = LinearLayout.LayoutParams(WRAP, WRAP)
        timeLp.topMargin = dp(14)
        hero.addView(timeView, timeLp)

        dateView = TextView(this).apply {
            textSize = 15f
            setTextColor(Theme.CREAM_80)
            letterSpacing = 0.03f
            setPadding(dp(2), dp(6), 0, 0)
            setShadowLayer(dp(8).toFloat(), 0f, dp(1).toFloat(), 0x66000000)
        }
        hero.addView(dateView)

        content.addView(hero, LinearLayout.LayoutParams(MATCH, (screenH * 0.47f).toInt()))

        // --- Скляний «док» з улюбленим
        val dock = GlassLayout(this).apply {
            cornerRadius = dp(26).toFloat()
            tintColor = 0x1AFFFFFF
            borderColor = 0x2EFFFFFF
            setPadding(dp(DOCK_PAD), dp(14), dp(DOCK_PAD), dp(8))
            clipChildren = false
            clipToPadding = false
        }
        val dockColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        dockColumn.addView(caption("Улюблене"))
        dockBody = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        dockColumn.addView(dockBody, LinearLayout.LayoutParams(MATCH, WRAP))
        dock.addView(dockColumn, FrameLayout.LayoutParams(MATCH, WRAP))
        content.addView(dock, LinearLayout.LayoutParams(MATCH, WRAP))

        // --- Усі застосунки
        allTitle = caption("Усі застосунки").apply { setPadding(dp(4), dp(34), 0, dp(14)) }
        content.addView(allTitle)
        grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        content.addView(grid, LinearLayout.LayoutParams(MATCH, WRAP))

        scroll.viewTreeObserver.addOnScrollChangedListener {
            val dim = (scroll.scrollY / dp(240).toFloat()).coerceIn(0f, 1f)
            dimView.alpha = dim
            GlassBackground.dim = dim
        }

        setContentView(root)
        dockBody.addView(hint("Завантаження…"))
    }

    private fun applyBackground(index: Int) {
        val generation = ++backgroundGeneration
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        thread(name = "background") {
            val bitmap = try {
                Backgrounds.render(applicationContext, index, w, h)
            } catch (e: Throwable) {
                null
            }
            val blurred = bitmap?.let { Backgrounds.blur(it) }
            runOnUiThread {
                if (generation == backgroundGeneration && !isDestroyed && bitmap != null && blurred != null) {
                    root.background = BitmapDrawable(resources, bitmap)
                    GlassBackground.set(blurred, w, h)
                }
            }
        }
    }

    private fun render(target: FocusTarget? = currentTarget()) {
        dockBody.removeAllViews()
        grid.removeAllViews()

        val byPkg = apps.associateBy { it.packageName }
        val hidden = prefs.hidden
        val favorites = prefs.favorites.filter { it !in hidden }.mapNotNull { byPkg[it] }
        val visible = apps.filter { it.packageName !in hidden }

        val cols = prefs.columns
        val gap = dp(GAP)
        val screenW = resources.displayMetrics.widthPixels
        val gridWidth = screenW - 2 * dp(SIDE)
        val tileWidth = (gridWidth - gap * (cols - 1)) / cols
        val dockInner = gridWidth - 2 * dp(DOCK_PAD)
        val dockTileWidth = (dockInner - gap * (cols - 1)) / cols

        var first: View? = null
        var favMatch: View? = null
        var gridMatch: View? = null

        // --- Улюблене
        if (favorites.isEmpty()) {
            dockBody.addView(hint("Затисніть OK на застосунку нижче й оберіть «Додати в улюблене»."))
        } else {
            val pad = dp(10)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            for ((i, app) in favorites.withIndex()) {
                val tile = createTile(app, inFavorites = true, width = dockTileWidth)
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
            val lp = FrameLayout.LayoutParams(MATCH, WRAP)
            lp.leftMargin = -pad
            lp.rightMargin = -pad
            dockBody.addView(rowScroll, lp)
        }

        // --- Усі застосунки
        allTitle.text = "Усі застосунки · ${visible.size}"
        if (visible.isEmpty()) grid.addView(hint("Застосунків не знайдено."))
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
            lp.bottomMargin = dp(22)
            grid.addView(row, lp)
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

    /** Кругла скляна кнопка з іконкою. */
    private fun glassIconButton(iconRes: Int, onClick: () -> Unit): View {
        val size = dp(44)
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Theme.CREAM)
        }
        return GlassLayout(this).apply {
            cornerRadius = size / 2f
            tintColor = 0x1FFFFFFF
            borderColor = 0x33FFFFFF
            isFocusable = true
            isClickable = true
            addView(icon, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, hasFocus ->
                tintColor = if (hasFocus) Theme.CREAM else 0x1FFFFFFF
                icon.setColorFilter(if (hasFocus) Theme.INK else Theme.CREAM)
                val s = if (hasFocus) 1.1f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(160).start()
            }
        }
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text.uppercase(ukrainian)
        textSize = 11f
        letterSpacing = 0.18f
        setTextColor(Theme.CREAM_60)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(2), 0, 0, dp(2))
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Theme.CREAM_60)
        setPadding(dp(2), dp(14), 0, dp(18))
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
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greetingView.text = when (hour) {
            in 5..11 -> "Доброго ранку"
            in 12..17 -> "Добрий день"
            in 18..22 -> "Добрий вечір"
            else -> "Доброї ночі"
        }
    }

    // ---------------------------------------------------------------- дії

    private fun launch(app: AppEntry) {
        try {
            startActivity(AppRepository.launchIntent(app))
            return
        } catch (e: Exception) {
            // запасний варіант нижче
        }
        val pm = packageManager
        val fallback = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
        if (fallback == null) toast("Не вдалося відкрити «${app.label}»") else openSafely(fallback)
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
        (findFirstTile(dockBody) ?: findFirstTile(grid) ?: settingsButton).requestFocus()
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
        val bgName = Backgrounds.presets[Backgrounds.clampIndex(prefs.backgroundIndex)].name
        val homeState = if (isHomeServiceEnabled()) "увімкнено" else "вимкнено"
        val actions = listOf(
            action("Фон: $bgName") { chooseBackground() },
            action("Колонок у сітці: ${prefs.columns}") { chooseColumns() },
            action("Приховані застосунки (${prefs.hidden.size})") { manageHidden() },
            action("Кнопка Home → цей лаунчер ($homeState)") { showHomeHelp() },
            action("Системні налаштування Android") { openSafely(Intent(Settings.ACTION_SETTINGS)) },
            action("Про лаунчер") { showAbout() },
        )
        showList("Налаштування", actions)
    }

    private fun chooseBackground() {
        val names = Backgrounds.presets.map { it.name }.toTypedArray()
        val current = Backgrounds.clampIndex(prefs.backgroundIndex)
        dialog = AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle("Фон")
            .setSingleChoiceItems(names, current) { d, which ->
                prefs.backgroundIndex = which
                applyBackground(which)
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
            .setPositiveButton("Відкрити") { _, _ ->
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
            "Затиснути OK або «Меню» — дії із застосунком\n" +
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
        const val SIDE = 56      // безпечна зона краю екрана
        const val GAP = 16
        const val DOCK_PAD = 18

        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        val DIALOG_THEME = R.style.LauncherDialog

        val DEFAULT_FAVORITES = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.google.android.youtube.tvmusic",
            "com.android.vending",
        )
    }
}
