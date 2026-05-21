package com.mg.trafficalerts.store

import android.content.Context
import com.mg.trafficalerts.model.AlertKind
import com.mg.trafficalerts.model.RoadAlert
import org.json.JSONArray
import org.json.JSONObject

class AlertStore(context: Context) {
    private val prefs = context.getSharedPreferences("alert_store", Context.MODE_PRIVATE)

    data class ProviderHealth(
        val name: String,
        val enabled: Boolean,
        val success: Boolean,
        val alertCount: Int,
        val updatedAtMillis: Long,
        val message: String
    )

    fun activeAlerts(): List<RoadAlert> {
        val raw = prefs.getString(KEY_ACTIVE_ALERTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toRoadAlert())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun cachedAlerts(maxAgeMillis: Long): List<RoadAlert> {
        val fetchedAt = prefs.getLong(KEY_CACHED_ALERTS_FETCHED_AT, 0L)
        if (fetchedAt == 0L || System.currentTimeMillis() - fetchedAt > maxAgeMillis) return emptyList()

        val raw = prefs.getString(KEY_CACHED_ALERTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toRoadAlert())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveActiveAlerts(alerts: List<RoadAlert>) {
        val array = JSONArray()
        alerts.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ACTIVE_ALERTS, array.toString()).commit()
    }

    fun saveCachedAlerts(alerts: List<RoadAlert>, updateFetchedAt: Boolean = true) {
        val array = JSONArray()
        alerts.forEach { array.put(it.toJson()) }
        val editor = prefs.edit().putString(KEY_CACHED_ALERTS, array.toString())
        if (updateFetchedAt) {
            editor.putLong(KEY_CACHED_ALERTS_FETCHED_AT, System.currentTimeMillis())
        }
        editor.commit()
    }

    fun clearCachedAlerts() {
        prefs.edit()
            .remove(KEY_CACHED_ALERTS)
            .remove(KEY_CACHED_ALERTS_FETCHED_AT)
            .apply()
    }

    fun markAlertsSeen(alertIds: Collection<String>, seenAtMillis: Long = System.currentTimeMillis()) {
        if (alertIds.isEmpty()) return
        val seen = alertSeenAt().toMutableMap()
        val missing = alertMissingCounts().toMutableMap()
        alertIds.forEach { id ->
            seen[id] = seenAtMillis
            missing.remove(id)
        }
        prefs.edit()
            .putString(KEY_ALERT_LAST_SEEN_AT, seen.toLongJsonObject().toString())
            .putString(KEY_ALERT_MISSING_COUNTS, missing.toIntJsonObject().toString())
            .apply()
    }

    fun alertLastSeenAt(alertId: String): Long? = alertSeenAt()[alertId]

    fun recordAlertMissing(alertId: String): Int {
        val missing = alertMissingCounts().toMutableMap()
        val count = (missing[alertId] ?: 0) + 1
        missing[alertId] = count
        prefs.edit().putString(KEY_ALERT_MISSING_COUNTS, missing.toIntJsonObject().toString()).apply()
        return count
    }

    fun pruneAlertLifecycle(retainedAlertIds: Set<String>) {
        val seen = alertSeenAt().filterKeys { it in retainedAlertIds }
        val missing = alertMissingCounts().filterKeys { it in retainedAlertIds }
        prefs.edit()
            .putString(KEY_ALERT_LAST_SEEN_AT, seen.toLongJsonObject().toString())
            .putString(KEY_ALERT_MISSING_COUNTS, missing.toIntJsonObject().toString())
            .apply()
    }

    fun activeNotificationIds(): Set<Int> =
        prefs.getStringSet(KEY_ACTIVE_NOTIFICATION_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()

    fun saveActiveNotificationIds(ids: Set<Int>) {
        prefs.edit()
            .putStringSet(KEY_ACTIVE_NOTIFICATION_IDS, ids.map { it.toString() }.toSet())
            .apply()
    }

    fun clearActiveNotificationIds() {
        prefs.edit().remove(KEY_ACTIVE_NOTIFICATION_IDS).apply()
    }

    fun saveProviderHealth(
        name: String,
        enabled: Boolean,
        success: Boolean,
        alertCount: Int,
        message: String = "",
        updatedAtMillis: Long = System.currentTimeMillis()
    ) {
        val health = providerHealth().toMutableMap()
        health[name] = ProviderHealth(name, enabled, success, alertCount, updatedAtMillis, message)
        prefs.edit().putString(KEY_PROVIDER_HEALTH, health.values.toJsonArray().toString()).apply()
    }

    fun providerHealth(): Map<String, ProviderHealth> {
        val raw = prefs.getString(KEY_PROVIDER_HEALTH, null) ?: return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            buildMap {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index).toProviderHealth()
                    put(item.name, item)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun recordHeadsUp(alertId: String, shownAtMillis: Long = System.currentTimeMillis()) {
        val shown = headsUpShownAt().toMutableMap()
        shown[alertId] = shownAtMillis
        prefs.edit().putString(KEY_HEADS_UP_SHOWN_AT, shown.toLongJsonObject().toString()).apply()
    }

    fun lastHeadsUpAt(alertId: String): Long? = headsUpShownAt()[alertId]

    fun isMuted(alertId: String): Boolean = mutedIds().contains(alertId)

    fun setMuted(alertId: String, muted: Boolean) {
        val ids = mutedIds().toMutableSet()
        if (muted) ids += alertId else ids -= alertId
        prefs.edit().putStringSet(KEY_MUTED_ALERTS, ids).apply()
    }

    fun mutedIds(): Set<String> = prefs.getStringSet(KEY_MUTED_ALERTS, emptySet()).orEmpty()

    fun passedAlertIds(): Set<String> = prefs.getStringSet(KEY_PASSED_ALERTS, emptySet()).orEmpty()

    fun markPassed(alertId: String) {
        val ids = passedAlertIds().toMutableSet()
        ids += alertId
        val trimmed = if (ids.size > 200) ids.toList().takeLast(200).toSet() else ids
        prefs.edit().putStringSet(KEY_PASSED_ALERTS, trimmed).apply()
    }

    fun clearPassed() {
        prefs.edit().remove(KEY_PASSED_ALERTS).apply()
    }

    fun diagnosticsSummary(): String =
        buildString {
            append("active=${activeAlerts().size}")
            append(", cached=${cachedAlerts(Long.MAX_VALUE).size}")
            append(", muted=${mutedIds().size}")
            append(", passed=${passedAlertIds().size}")
            append(", notifications=${activeNotificationIds().size}")
        }

    private fun RoadAlert.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("kind", kind.name)
            .put("title", title)
            .put("description", description)
            .put("address", address)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("distanceMeters", distanceMeters.toDouble())
            .put("reportedAtMillis", reportedAtMillis)

    private fun JSONObject.toRoadAlert(): RoadAlert =
        RoadAlert(
            id = getString("id"),
            kind = AlertKind.valueOf(getString("kind")),
            title = getString("title"),
            description = getString("description"),
            address = optString("address").takeIf { it.isNotBlank() && it != "null" },
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
            distanceMeters = getDouble("distanceMeters").toFloat(),
            reportedAtMillis = getLong("reportedAtMillis")
        )

    private fun ProviderHealth.toJson(): JSONObject =
        JSONObject()
            .put("name", name)
            .put("enabled", enabled)
            .put("success", success)
            .put("alertCount", alertCount)
            .put("updatedAtMillis", updatedAtMillis)
            .put("message", message)

    private fun JSONObject.toProviderHealth(): ProviderHealth =
        ProviderHealth(
            name = getString("name"),
            enabled = optBoolean("enabled", true),
            success = optBoolean("success", false),
            alertCount = optInt("alertCount", 0),
            updatedAtMillis = optLong("updatedAtMillis", 0L),
            message = optString("message", "")
        )

    private fun Collection<ProviderHealth>.toJsonArray(): JSONArray =
        JSONArray().also { array -> forEach { array.put(it.toJson()) } }

    private fun alertSeenAt(): Map<String, Long> =
        prefs.getString(KEY_ALERT_LAST_SEEN_AT, null)
            ?.toLongMap()
            ?: emptyMap()

    private fun alertMissingCounts(): Map<String, Int> =
        prefs.getString(KEY_ALERT_MISSING_COUNTS, null)
            ?.toIntMap()
            ?: emptyMap()

    private fun headsUpShownAt(): Map<String, Long> =
        prefs.getString(KEY_HEADS_UP_SHOWN_AT, null)
            ?.toLongMap()
            ?: emptyMap()

    private fun Map<String, Long>.toLongJsonObject(): JSONObject =
        JSONObject().also { json -> forEach { (key, value) -> json.put(key, value) } }

    private fun Map<String, Int>.toIntJsonObject(): JSONObject =
        JSONObject().also { json -> forEach { (key, value) -> json.put(key, value) } }

    private fun String.toLongMap(): Map<String, Long> =
        runCatching {
            val json = JSONObject(this)
            buildMap {
                json.keys().forEach { key -> put(key, json.optLong(key, 0L)) }
            }.filterValues { it > 0L }
        }.getOrDefault(emptyMap())

    private fun String.toIntMap(): Map<String, Int> =
        runCatching {
            val json = JSONObject(this)
            buildMap {
                json.keys().forEach { key -> put(key, json.optInt(key, 0)) }
            }.filterValues { it > 0 }
        }.getOrDefault(emptyMap())

    companion object {
        private const val KEY_ACTIVE_ALERTS = "active_alerts"
        private const val KEY_CACHED_ALERTS = "cached_alerts"
        private const val KEY_CACHED_ALERTS_FETCHED_AT = "cached_alerts_fetched_at"
        private const val KEY_MUTED_ALERTS = "muted_alerts"
        private const val KEY_PASSED_ALERTS = "passed_alerts"
        private const val KEY_ALERT_LAST_SEEN_AT = "alert_last_seen_at"
        private const val KEY_ALERT_MISSING_COUNTS = "alert_missing_counts"
        private const val KEY_ACTIVE_NOTIFICATION_IDS = "active_notification_ids"
        private const val KEY_PROVIDER_HEALTH = "provider_health"
        private const val KEY_HEADS_UP_SHOWN_AT = "heads_up_shown_at"
    }
}
