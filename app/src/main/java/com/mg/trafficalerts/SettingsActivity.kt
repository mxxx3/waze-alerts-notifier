package com.mg.trafficalerts

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.mg.trafficalerts.MainActivity
import com.mg.trafficalerts.model.AlertKind
import com.mg.trafficalerts.monitor.AlertMonitorService
import com.mg.trafficalerts.settings.AppSettings
import com.mg.trafficalerts.store.AlertStore
import com.mg.trafficalerts.ui.ThemeMode
import com.mg.trafficalerts.ui.UiPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : Activity() {
    private lateinit var settings: AppSettings
    private lateinit var alertStore: AlertStore
    private lateinit var root: LinearLayout
    private lateinit var palette: UiPalette

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        settings = AppSettings(this)
        alertStore = AlertStore(this)
        palette = UiPalette.from(this, settings.themeMode)
        palette.applyWindow(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    private fun render() {
        palette = UiPalette.from(this, settings.themeMode)
        palette.applyWindow(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = backgroundGradient()
        }
        val scroll = ScrollView(this).apply {
            background = backgroundGradient()
            addView(root)
        }
        setContentView(scroll)
        applySystemBarPadding(scroll)

        header()
        appearancePanel()
        controlsPanel()
        navigationPanel()
        sourcesPanel()
        diagnosticsPanel()
        behaviorPanel()
        cachePanel()
        alertTypesPanel()
        permissionPanel()
    }

    private fun header() {
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 15.dp, 16.dp, 16.dp)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(palette.accent, if (palette.dark) 0xFF234A84.toInt() else 0xFF4CA6D8.toInt())
            ).apply { cornerRadius = 8.dp.toFloat() }
            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text("Settings", 24f, 0xFFFFFFFF.toInt(), bold = true))
                addView(text("Monitoring and alert preferences", 13f, 0xDDEFFFFF.toInt()), blockParams(top = 3.dp))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(this@SettingsActivity).apply {
                text = "Done"
                palette.styleButton(this, compact = true)
                setTextColor(0xFFFFFFFF.toInt())
                background = rounded(0x24FFFFFF, 0x40FFFFFF)
                setOnClickListener { finish() }
            })
        }, blockParams(bottom = 12.dp))
    }

    private fun appearancePanel() {
        root.addView(panel {
            addView(sectionHeader("Appearance", "Follow the phone theme or choose a fixed mode."))
            val row = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            ThemeMode.entries.forEach { mode ->
                row.addView(Button(this@SettingsActivity).apply {
                    text = mode.label
                    palette.styleButton(this, selected = settings.themeMode == mode, compact = true)
                    setOnClickListener {
                        settings.themeMode = mode
                        render()
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, 8.dp, 0)
                })
            }
            addView(row, blockParams(top = 8.dp))
        })
    }

    private fun behaviorPanel() {
        root.addView(panel {
            addView(sectionHeader("Behavior", "Control background work and notification delivery."))
            addSwitch("Background monitoring", settings.monitoringEnabled) { enabled ->
                settings.monitoringEnabled = enabled
                if (enabled) startMonitoring() else stopMonitoring()
            }
            addSwitch("Notifications", settings.notificationsEnabled) {
                settings.notificationsEnabled = it
            }
        })
    }

    private fun cachePanel() {
        root.addView(panel {
            addView(sectionHeader("Movement Cache", "Fetch wider during drives, show only current alerts."))
            addView(sliderRow(
                label = "Cache time",
                value = "${settings.alertCacheTtlMinutes} min",
                max = CACHE_TTL_STEPS_MINUTES.size - 1,
                progress = stepIndex(CACHE_TTL_STEPS_MINUTES, settings.alertCacheTtlMinutes),
                onProgress = { settings.alertCacheTtlMinutes = CACHE_TTL_STEPS_MINUTES[it] },
                valueText = { "${settings.alertCacheTtlMinutes} min" }
            ))
            addView(sliderRow(
                label = "Minimum cache radius",
                value = formatRadius(settings.cacheMinRadiusMeters),
                max = CACHE_MIN_RADIUS_STEPS.size - 1,
                progress = stepIndex(CACHE_MIN_RADIUS_STEPS, settings.cacheMinRadiusMeters),
                onProgress = {
                    settings.cacheMinRadiusMeters = CACHE_MIN_RADIUS_STEPS[it]
                    if (settings.cacheMaxRadiusMeters < settings.cacheMinRadiusMeters) {
                        settings.cacheMaxRadiusMeters = settings.cacheMinRadiusMeters
                    }
                },
                valueText = { formatRadius(settings.cacheMinRadiusMeters) }
            ))
            addView(sliderRow(
                label = "Maximum cache radius",
                value = formatRadius(settings.cacheMaxRadiusMeters),
                max = CACHE_MAX_RADIUS_STEPS.size - 1,
                progress = stepIndex(CACHE_MAX_RADIUS_STEPS, settings.cacheMaxRadiusMeters),
                onProgress = {
                    settings.cacheMaxRadiusMeters = CACHE_MAX_RADIUS_STEPS[it].coerceAtLeast(settings.cacheMinRadiusMeters)
                },
                valueText = { formatRadius(settings.cacheMaxRadiusMeters) }
            ))
            addView(sliderRow(
                label = "Radius expansion",
                value = "${settings.cacheRadiusMultiplier}x",
                max = CACHE_MULTIPLIER_STEPS.size - 1,
                progress = stepIndex(CACHE_MULTIPLIER_STEPS, settings.cacheRadiusMultiplier),
                onProgress = { settings.cacheRadiusMultiplier = CACHE_MULTIPLIER_STEPS[it] },
                valueText = { "${settings.cacheRadiusMultiplier}x" }
            ))
            addView(sliderRow(
                label = "Visible alert limit",
                value = settings.maxVisibleAlerts.toString(),
                max = VISIBLE_ALERT_STEPS.size - 1,
                progress = stepIndex(VISIBLE_ALERT_STEPS, settings.maxVisibleAlerts),
                onProgress = { settings.maxVisibleAlerts = VISIBLE_ALERT_STEPS[it] },
                valueText = { settings.maxVisibleAlerts.toString() }
            ))
        })
    }

    private fun navigationPanel() {
        root.addView(panel {
            addView(sectionHeader("Navigation", "App to open when navigating to an alert."))
            val row = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            listOf("google_maps" to "Google Maps", "waze" to "Waze").forEachIndexed { i, (key, label) ->
                row.addView(Button(this@SettingsActivity).apply {
                    text = label
                    palette.styleButton(this, selected = settings.navApp == key, compact = true)
                    setOnClickListener {
                        settings.navApp = key
                        render()
                    }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i == 0) setMargins(0, 0, 8.dp, 0)
                })
            }
            addView(row, blockParams(top = 8.dp))
        })
    }

    private fun sourcesPanel() {
        root.addView(panel {
            addView(sectionHeader("Sources", "Choose live feeds and fallbacks for active alerts."))
            addSwitch("Waze Live Map", settings.wazeLiveMapEnabled) {
                settings.wazeLiveMapEnabled = it
            }
            addSwitch("OpenStreetMap cameras", settings.osmCamerasEnabled) {
                settings.osmCamerasEnabled = it
            }

            addView(text("TomTom API key", 14f, palette.body), blockParams(top = 10.dp))
            val tomTomKeyField = EditText(this@SettingsActivity).apply {
                setText(settings.tomTomApiKey)
                hint = "Optional global traffic source"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                setSingleLine(true)
                setTextColor(palette.title)
                setHintTextColor(palette.secondary)
                background = rounded(palette.surface, palette.border)
                setPadding(12.dp, 8.dp, 12.dp, 8.dp)
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) settings.tomTomApiKey = text.toString()
                }
            }
            addView(tomTomKeyField, blockParams(top = 6.dp))
            addView(Button(this@SettingsActivity).apply {
                text = "Save traffic key"
                palette.styleButton(this)
                setOnClickListener {
                    settings.tomTomApiKey = tomTomKeyField.text.toString()
                    currentFocus?.clearFocus()
                }
            }, blockParams(top = 8.dp))

        })
    }

    private fun diagnosticsPanel() {
        root.addView(panel {
            addView(sectionHeader("Diagnostics", "Provider health, runtime state, and notification test tools."))

            val health = alertStore.providerHealth()
            val providers = listOf("waze" to "Waze", "osm-camera" to "OpenStreetMap", "tomtom" to "TomTom", "demo" to "Demo")
            providers.forEach { (key, label) ->
                val item = health[key]
                addView(diagnosticRow(
                    title = label,
                    value = when {
                        item == null -> "No refresh yet"
                        !item.enabled -> "Disabled"
                        item.success -> "OK · ${item.alertCount} alert(s) · ${formatAge(item.updatedAtMillis)}"
                        else -> "Failed · ${item.message.ifBlank { "unknown" }} · ${formatAge(item.updatedAtMillis)}"
                    },
                    ok = item?.success == true || item?.enabled == false
                ), blockParams(top = 8.dp))
            }

            addView(diagnosticRow("Runtime", alertStore.diagnosticsSummary(), ok = true), blockParams(top = 10.dp))
            addView(Button(this@SettingsActivity).apply {
                text = "Post test alert notification"
                palette.styleButton(this)
                setOnClickListener { postTestNotification() }
            }, blockParams(top = 12.dp))
        })
    }

    private fun diagnosticRow(title: String, value: String, ok: Boolean): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp, 9.dp, 12.dp, 9.dp)
            background = rounded(if (ok) palette.surface else 0x22CC3D3D, palette.border)
            addView(text(title, 13f, palette.title, bold = true))
            addView(text(value, 12f, if (ok) palette.secondary else palette.danger), blockParams(top = 3.dp))
        }

    private fun postTestNotification() {
        if (Build.VERSION.SDK_INT >= 33 && !isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, "Road alerts", NotificationManager.IMPORTANCE_HIGH))
        }

        val alertId = "test:${System.currentTimeMillis()}"
        val notificationId = alertId.hashCode()
        val senderName = "Police: ↑ ahead 400 m"
        val sender = Person.Builder().setName(senderName).setBot(true).build()
        val style = NotificationCompat.MessagingStyle(sender)
            .setConversationTitle(senderName)
            .setGroupConversation(false)
            .addMessage("Test alert from diagnostics", System.currentTimeMillis(), sender)
        val replyFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val replyIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            Intent(this, com.mg.trafficalerts.monitor.NotificationActionReceiver::class.java)
                .setAction(ACTION_REPLY)
                .putExtra(EXTRA_ALERT_ID, alertId),
            replyFlags
        )
        val replyAction = NotificationCompat.Action.Builder(R.mipmap.ic_launcher, "Reply", replyIntent)
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build())
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        val markReadIntent = PendingIntent.getBroadcast(
            this,
            notificationId + ACTION_REQUEST_OFFSET,
            Intent(this, com.mg.trafficalerts.monitor.NotificationActionReceiver::class.java)
                .setAction(ACTION_MARK_READ)
                .putExtra(EXTRA_ALERT_ID, alertId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markReadAction = NotificationCompat.Action.Builder(R.mipmap.ic_launcher, "Mark as read", markReadIntent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName)
            .setContentText("Test alert from diagnostics")
            .setStyle(style)
            .setContentIntent(contentIntent)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(10 * 60_000L)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        nm.notify(notificationId, notification)
        alertStore.saveActiveNotificationIds(alertStore.activeNotificationIds() + notificationId)
        Toast.makeText(this, "Test alert posted", Toast.LENGTH_SHORT).show()
    }

    private fun alertTypesPanel() {
        root.addView(panel {
            addView(sectionHeader("Alert Types", "Choose which categories appear in notifications."))
            AlertKind.entries.forEach { kind ->
                addSwitch(kind.label, settings.isKindEnabled(kind)) {
                    settings.setKindEnabled(kind, it)
                }
            }
        })
    }

    private fun permissionPanel() {
        root.addView(panel {
            addView(sectionHeader("Permissions", "Tap any row to grant or open system settings."))

            addView(permissionRow(
                label = "Fine location",
                description = "Required to detect alerts near your position.",
                granted = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION),
                action = {
                    ActivityCompat.requestPermissions(
                        this@SettingsActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        REQ_LOCATION
                    )
                }
            ), blockParams(top = 10.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addView(permissionRow(
                    label = "Background location (Always)",
                    description = "Choose \"Allow all the time\" so monitoring works with the screen off.",
                    granted = isPermissionGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    action = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            openAppDetails()
                        } else {
                            ActivityCompat.requestPermissions(
                                this@SettingsActivity,
                                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                REQ_BG_LOCATION
                            )
                        }
                    }
                ), blockParams(top = 10.dp))
            }

            if (Build.VERSION.SDK_INT >= 33) {
                addView(permissionRow(
                    label = "Notifications",
                    description = "Required to deliver road-alert notifications.",
                    granted = isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS),
                    action = {
                        ActivityCompat.requestPermissions(
                            this@SettingsActivity,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            REQ_NOTIFICATIONS
                        )
                    }
                ), blockParams(top = 10.dp))
            }

            addView(permissionRow(
                label = "Notification access (Maps)",
                description = "Lets the app detect Google Maps navigation for ahead-only filtering.",
                granted = isNotificationListenerEnabled(),
                action = {
                    runCatching {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }.onFailure { openAppDetails() }
                }
            ), blockParams(top = 10.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                addView(permissionRow(
                    label = "Exact alarms",
                    description = "Used by the watchdog heartbeat to revive the service quickly if Android kills it.",
                    granted = canScheduleExactAlarms(),
                    action = {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.fromParts("package", packageName, null)
                            })
                        }.onFailure { openAppDetails() }
                    }
                ), blockParams(top = 10.dp))
            }

            addView(permissionRow(
                label = "Unrestricted battery",
                description = "Required for Android Auto reliability — prevents Doze from killing the service.",
                granted = isIgnoringBatteryOptimizations(),
                action = {
                    runCatching {
                        @Suppress("BatteryLife")
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    }.onFailure {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }.onFailure { openAppDetails() }
                    }
                }
            ), blockParams(top = 10.dp))

            addView(Button(this@SettingsActivity).apply {
                text = "Open all app settings"
                palette.styleButton(this)
                setOnClickListener { openAppDetails() }
            }, blockParams(top = 14.dp))

            addView(Button(this@SettingsActivity).apply {
                text = "Re-check status"
                palette.styleButton(this, compact = true)
                setOnClickListener { render() }
            }, blockParams(top = 8.dp))
        })
    }

    private fun permissionRow(
        label: String,
        description: String,
        granted: Boolean,
        action: () -> Unit
    ): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp, 10.dp, 12.dp, 10.dp)
            background = rounded(palette.surface, palette.border)
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            text(label, 14f, palette.title, bold = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        titleRow.addView(statusBadge(granted))
        container.addView(titleRow)
        container.addView(text(description, 12f, palette.secondary), blockParams(top = 4.dp))
        container.addView(Button(this).apply {
            text = if (granted) "Open settings" else "Grant"
            palette.styleButton(this, compact = true)
            setOnClickListener {
                action()
                postDelayed({ if (!isFinishing) render() }, 400)
            }
        }, blockParams(top = 8.dp))
        return container
    }

    private fun statusBadge(granted: Boolean): TextView {
        val (label, fg, bg) = if (granted) {
            Triple("Granted", 0xFF1F8B4C.toInt(), 0x331F8B4C)
        } else {
            Triple("Not granted", 0xFFCC3D3D.toInt(), 0x33CC3D3D)
        }
        return TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(fg)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(10.dp, 3.dp, 10.dp, 3.dp)
            background = rounded(bg, fg)
        }
    }

    private fun isPermissionGranted(name: String): Boolean =
        ContextCompat.checkSelfPermission(this, name) == PackageManager.PERMISSION_GRANTED

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { it.startsWith("$packageName/") }
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openAppDetails() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }

    private fun LinearLayout.addSwitch(text: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
        addView(LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 9.dp, 0, 9.dp)
            addView(text(text, 14f, palette.body), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(this@SettingsActivity).apply {
                isChecked = checked
                setOnCheckedChangeListener { _: CompoundButton, enabled: Boolean -> onChanged(enabled) }
            })
        })
    }

    private fun controlsPanel() {
        root.addView(panel {
            addView(sectionHeader("Controls", "Tune the scan area and update cadence."))
            val metrics = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            metrics.addView(metricTile("Scan", formatRadius(settings.radiusMeters), "radius"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8.dp, 0)
            })
            metrics.addView(metricTile("Refresh", formatRefresh(settings.pollIntervalMillis), "cadence"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(metrics, blockParams(top = 4.dp, bottom = 8.dp))
            addView(sliderRow(
                label = "Radius",
                value = formatRadius(settings.radiusMeters),
                max = RADIUS_STEPS.size - 1,
                progress = stepIndex(RADIUS_STEPS, settings.radiusMeters),
                onProgress = { settings.radiusMeters = RADIUS_STEPS[it] },
                valueText = { formatRadius(settings.radiusMeters) }
            ))
            addView(sliderRow(
                label = "Refresh time",
                value = formatRefresh(settings.pollIntervalMillis),
                max = REFRESH_STEPS_MILLIS.size - 1,
                progress = stepIndexLong(REFRESH_STEPS_MILLIS, settings.pollIntervalMillis),
                onProgress = { settings.pollIntervalMillis = REFRESH_STEPS_MILLIS[it] },
                valueText = { formatRefresh(settings.pollIntervalMillis) }
            ))
        })
    }

    private fun metricTile(label: String, value: String, caption: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp, 10.dp, 12.dp, 11.dp)
            background = rounded(if (palette.dark) 0xFF182824.toInt() else 0xFFF6FAF8.toInt(), palette.border)
            addView(text(label, 11f, palette.secondary, bold = true))
            addView(text(value, 18f, palette.title, bold = true), blockParams(top = 4.dp))
            addView(text(caption, 11f, palette.secondary), blockParams(top = 3.dp))
        }

    private fun sliderRow(
        label: String,
        value: String,
        max: Int,
        progress: Int,
        onProgress: (Int) -> Unit,
        valueText: () -> String
    ): LinearLayout {
        val valueView = text(value, 14f, palette.title, bold = true)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10.dp, 0, 0)
            val row = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(text(label, 14f, palette.body), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(valueView)
            addView(row)
            addView(SeekBar(this@SettingsActivity).apply {
                this.max = max
                this.progress = progress
                setPadding(0, 2.dp, 0, 0)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        onProgress(progress)
                        valueView.text = valueText()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                })
            })
        }
    }

    private fun sectionHeader(title: String, subtitle: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 17f, palette.title, bold = true))
            addView(text(subtitle, 12.5f, palette.secondary), blockParams(top = 3.dp))
        }

    private fun panel(content: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(15.dp, 13.dp, 15.dp, 14.dp)
            background = rounded(palette.panel, palette.border)
            content()
        }.also {
            it.layoutParams = blockParams(top = 12.dp)
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            palette.styleText(this, color, size, bold)
        }

    private fun rounded(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = 8.dp.toFloat()
            if (stroke != 0) setStroke(1.dp, stroke)
        }

    private fun backgroundGradient(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(palette.backgroundAlt, palette.background)
        )

    private fun blockParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, top, 0, bottom)
        }

    private fun applySystemBarPadding(scroll: ScrollView) {
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(16.dp, bars.top + 16.dp, 16.dp, bars.bottom + 24.dp)
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
    }

    private fun requestNeededPermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }

    private fun startMonitoring() {
        requestNeededPermissions()
        com.mg.trafficalerts.monitor.ServiceWatchdog.startMonitoring(this)
        com.mg.trafficalerts.monitor.ServiceWatchdog.scheduleWatchdog(this)
    }

    private fun stopMonitoring() {
        com.mg.trafficalerts.monitor.ServiceWatchdog.cancelWatchdog(this)
        com.mg.trafficalerts.monitor.ServiceWatchdog.cancelHeartbeat(this)
        stopService(Intent(this, AlertMonitorService::class.java))
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun formatRefresh(millis: Long): String {
        val seconds = millis / 1000
        return if (seconds < 60) "${seconds}s" else "${seconds / 60} min"
    }

    private fun formatAge(timeMillis: Long): String {
        if (timeMillis <= 0L) return "never"
        val ageSeconds = ((System.currentTimeMillis() - timeMillis) / 1000).coerceAtLeast(0)
        return when {
            ageSeconds < 60 -> "${ageSeconds}s ago"
            ageSeconds < 3600 -> "${ageSeconds / 60}m ago"
            else -> SimpleDateFormat("HH:mm", Locale.US).format(Date(timeMillis))
        }
    }

    private fun stepIndexLong(steps: LongArray, value: Long): Int =
        steps.indexOf(steps.minByOrNull { kotlin.math.abs(it - value) } ?: steps.first()).coerceAtLeast(0)

    private fun stepIndex(steps: IntArray, value: Int): Int =
        steps.indexOf(steps.minByOrNull { kotlin.math.abs(it - value) } ?: steps.first()).coerceAtLeast(0)

    private fun formatRadius(meters: Int): String =
        if (meters >= 1000) "${meters / 1000} km" else "$meters m"

    companion object {
        private const val REQ_LOCATION = 100
        private const val REQ_BG_LOCATION = 101
        private const val REQ_NOTIFICATIONS = 102
        private const val CHANNEL_ALERTS = "road_alerts"
        private const val ACTION_REPLY = "com.mg.trafficalerts.ACTION_REPLY"
        private const val ACTION_MARK_READ = "com.mg.trafficalerts.ACTION_MARK_READ"
        private const val EXTRA_ALERT_ID = "extra_alert_id"
        private const val KEY_REPLY = "key_reply"
        private const val ACTION_REQUEST_OFFSET = 17_000
        private val RADIUS_STEPS = intArrayOf(100, 200, 300, 500, 1000, 2000, 3000)
        private val REFRESH_STEPS_MILLIS = longArrayOf(30_000L, 60_000L, 120_000L, 180_000L, 300_000L)
        private val CACHE_TTL_STEPS_MINUTES = intArrayOf(5, 10, 20, 30, 60, 120)
        private val CACHE_MIN_RADIUS_STEPS = intArrayOf(3_000, 5_000, 10_000, 15_000, 25_000, 50_000)
        private val CACHE_MAX_RADIUS_STEPS = intArrayOf(5_000, 10_000, 15_000, 25_000, 50_000, 75_000, 100_000)
        private val CACHE_MULTIPLIER_STEPS = intArrayOf(1, 2, 3, 5, 8, 10)
        private val VISIBLE_ALERT_STEPS = intArrayOf(6, 12, 24, 36, 48, 60)
    }
}
