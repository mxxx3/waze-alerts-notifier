package com.mg.trafficalerts.source

import android.location.Location
import com.mg.trafficalerts.model.RoadAlert
import com.mg.trafficalerts.settings.AppSettings

interface AlertProvider {
    suspend fun alertsNear(location: Location, settings: AppSettings, radiusMeters: Int = settings.radiusMeters): List<RoadAlert>
}
