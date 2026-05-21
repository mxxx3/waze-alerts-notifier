package com.mg.wazealerts.source

import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.mg.wazealerts.model.RoadAlert
import com.mg.wazealerts.settings.AppSettings
import com.mg.wazealerts.AppLogger
import java.io.IOException
import java.util.Locale

private const val DEDUP_RADIUS_METERS = 200f

data class AlertFetchResult(
    val alerts: List<RoadAlert>,
    val successfulProviders: Set<String>,
    val failedProviders: Set<String>
)

class AlertRepository(context: Context) {
    private val appContext = context.applicationContext
    private val demoProvider = DemoAlertProvider()
    private val wazeProvider = WazeLiveMapAlertProvider(context.applicationContext)
    private val tomTomProvider = TomTomTrafficAlertProvider()
    private val osmCameraProvider = OpenStreetMapCameraProvider(context.applicationContext)

    suspend fun nearby(location: Location, radiusMeters: Int? = null): List<RoadAlert> =
        nearbyResult(location, radiusMeters).alerts

    suspend fun nearbyResult(location: Location, radiusMeters: Int? = null): AlertFetchResult {
        val settings = AppSettings(appContext)
        val requestedRadius = radiusMeters ?: settings.radiusMeters

        val specs = listOf(
            ProviderSpec("waze", settings.wazeLiveMapEnabled) {
                wazeProvider.alertsNear(location, settings, requestedRadius)
            },
            ProviderSpec("osm-camera", settings.osmCamerasEnabled) {
                osmCameraProvider.alertsNear(location, settings, requestedRadius)
            },
            ProviderSpec("tomtom", settings.tomTomApiKey.isNotBlank()) {
                tomTomProvider.alertsNear(location, settings, requestedRadius)
            },
            ProviderSpec("demo", settings.demoAlertsEnabled) {
                demoProvider.alertsNear(location, settings, requestedRadius)
            }
        )

        val alerts = mutableListOf<RoadAlert>()
        val successful = mutableSetOf<String>()
        val failed = mutableSetOf<String>()

        for (spec in specs) {
            if (!spec.enabled) {
                successful += spec.name
                continue
            }
            runCatching { spec.fetch() }
                .onSuccess {
                    successful += spec.name
                    alerts += it
                }
                .onFailure {
                    failed += spec.name
                    AppLogger.w(TAG, "${spec.name} provider failed: ${it.javaClass.simpleName}: ${it.message}")
                }
        }

        val filtered = alerts
            .filter { it.kind in settings.enabledKinds() }
            .sortedBy { it.distanceMeters }
            .deduplicateNearby()
            .map { it.withResolvedAddress() }
        return AlertFetchResult(filtered, successful, failed)
    }

    private fun RoadAlert.withResolvedAddress(): RoadAlert {
        if (!address.isNullOrBlank()) return this

        val resolved = reverseGeocode(latitude, longitude)
        return copy(address = resolved ?: coordinateLabel(latitude, longitude))
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null

        return try {
            val geocoder = Geocoder(appContext, Locale.getDefault())
            val address = geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            when {
                address == null -> null
                !address.getAddressLine(0).isNullOrBlank() -> address.getAddressLine(0)
                !address.thoroughfare.isNullOrBlank() -> address.thoroughfare
                !address.locality.isNullOrBlank() -> address.locality
                else -> null
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun List<RoadAlert>.deduplicateNearby(): List<RoadAlert> {
        val kept = mutableListOf<RoadAlert>()
        for (alert in this) {
            val isDupe = kept.any { other ->
                other.kind == alert.kind && distanceBetween(alert, other) < DEDUP_RADIUS_METERS
            }
            if (!isDupe) kept.add(alert)
        }
        return kept
    }

    private fun distanceBetween(a: RoadAlert, b: RoadAlert): Float {
        val result = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        return result[0]
    }

    fun destroy() = wazeProvider.destroy()

    private fun coordinateLabel(latitude: Double, longitude: Double): String =
        "%.5f, %.5f".format(Locale.US, latitude, longitude)

    private data class ProviderSpec(
        val name: String,
        val enabled: Boolean,
        val fetch: suspend () -> List<RoadAlert>
    )

    companion object {
        private const val TAG = "AlertRepository"
    }
}
