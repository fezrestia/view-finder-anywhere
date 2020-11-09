@file:Suppress("PrivatePropertyName")

package com.fezrestia.android.lib.location

import android.content.Context
import android.location.Location
import android.widget.Toast
import com.fezrestia.android.lib.util.log.IS_DEBUG
import com.fezrestia.android.lib.util.log.logD
import com.fezrestia.android.lib.util.log.logE
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import java.lang.Exception

class LatestLocation(val context: Context) {
    private val TAG = "LatestLocation"

    private val locationRequest: LocationRequest
    private val locationSettingsRequest: LocationSettingsRequest
    private val fusedLocationProviderClient: FusedLocationProviderClient
    private val settingsClient: SettingsClient

    private val locationCallback: LocationCallback = LocationCallbackImpl()

    var latestLocation: Location? = null
            private set

    private val UPDATE_INTERVAL_MILLIS = 5000L
    private val FASTEST_UPDATE_INTERVAL_MILLIS = 1000L

    /**
     * CONSTRUCTOR.
     */
    init {
        if (IS_DEBUG) logD(TAG, "CONSTRUCTOR()")

        locationRequest = LocationRequest().apply {
            interval = UPDATE_INTERVAL_MILLIS
            fastestInterval = FASTEST_UPDATE_INTERVAL_MILLIS
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationSettingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .build()

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        settingsClient = LocationServices.getSettingsClient(context)

    }

    private inner class LocationCallbackImpl : LocationCallback() {
        override fun onLocationAvailability(availability: LocationAvailability) {
            if (IS_DEBUG) logD(TAG, "onLocationAvailability() : ${availability.isLocationAvailable}")
            super.onLocationAvailability(availability)
            // NOP.
        }

        override fun onLocationResult(result: LocationResult) {
            if (IS_DEBUG) logD(TAG, "onLocationResult() : result=$result")
            super.onLocationResult(result)

            latestLocation = result.lastLocation

            if (IS_DEBUG) {
                latestLocation?.let {
                    logD(TAG, "latitude                     = ${it.latitude}")
                    logD(TAG, "longitude                    = ${it.longitude}")
                    logD(TAG, "altitude                     = ${it.altitude}")
                    logD(TAG, "provider                     = ${it.provider}")
                    logD(TAG, "accuracy                     = ${it.accuracy}")
                    logD(TAG, "bearing                      = ${it.bearing}")
                    logD(TAG, "bearingAccuracyDegrees       = ${it.bearingAccuracyDegrees}")
                    logD(TAG, "speed                        = ${it.speed}")
                    logD(TAG, "speedAccuracyMetersPerSecond = ${it.speedAccuracyMetersPerSecond}")
                    logD(TAG, "verticalAccuracyMeters       = ${it.verticalAccuracyMeters}")
                    logD(TAG, "time                         = ${it.time}")
                    logD(TAG, "elapsedRealtimeNanos         = ${it.elapsedRealtimeNanos}")
                } ?: run {
                    logE(TAG, "## Location Result is NULL")
                }
            }
        }
    }

    /**
     * Release all references.
     */
    fun release() {
        if (IS_DEBUG) logD(TAG, "release()")
        // NOP.
    }

    /**
     * Start updating current location.
     */
    fun start() {
        if (IS_DEBUG) logD(TAG, "start() : E")

        val checkTask: Task<LocationSettingsResponse>
                = settingsClient.checkLocationSettings(locationSettingsRequest)
        checkTask.addOnSuccessListener(OnLocationSuccessListenerImpl())
        checkTask.addOnFailureListener(OnLocationFailureListenerImpl())

        if (IS_DEBUG) logD(TAG, "start() : X")
    }

    private inner class OnLocationSuccessListenerImpl : OnSuccessListener<LocationSettingsResponse> {
        override fun onSuccess(response: LocationSettingsResponse) {
            if (IS_DEBUG) logD(TAG, "OnLocationSuccessListenerImpl.onSuccess()")

            try {
                fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (IS_DEBUG) logD(TAG, "First last location = $location")
                    latestLocation = location
                }

                fusedLocationProviderClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        null) // Callback thread.
            } catch (e: SecurityException) {
                val errMsg = "## SecurityException : LOCATION permission is NOT granted ?"
                logE(TAG, errMsg)
                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private inner class OnLocationFailureListenerImpl : OnFailureListener {
        override fun onFailure(e: Exception) {
            if (IS_DEBUG) logD(TAG, "OnLocationSuccessListenerImpl.onFailure()")

            val statusCode = (e as ApiException).statusCode
            val errMsg = "## Location Settings Error : code=$statusCode"

            logE(TAG, errMsg)
            Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Stop updating current location.
     */
    fun stop() {
        if (IS_DEBUG) logD(TAG, "stop() : E")

        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
                .addOnCompleteListener {
                    if (IS_DEBUG) logD(TAG, "fusedLocationProviderClient.removeLocationUpdates() : DONE")
                }

        if (IS_DEBUG) logD(TAG, "stop() : X")
    }

}
