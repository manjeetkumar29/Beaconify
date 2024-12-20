// utils/location/LocationManager.kt
package com.iiitl.locateme.utils.location

import android.content.Context
import android.util.Log
import com.iiitl.locateme.utils.beacon.BeaconScanner
import com.iiitl.locateme.utils.positioning.PositionCalculator
import com.iiitl.locateme.utils.positioning.PositionCalculatorFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LocationManager(
    context: Context,
    calculatorType: PositionCalculatorFactory.CalculatorType = PositionCalculatorFactory.CalculatorType.INDOOR_POSITIONING


) {
    private val TAG = "LocationManager"
    private var beaconScanner: BeaconScanner? = null
    private val positionCalculator: PositionCalculator = PositionCalculatorFactory.getCalculator(calculatorType)
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _locationUpdates = MutableStateFlow(LocationUpdate(null, emptyList()))
    val locationUpdates: StateFlow<LocationUpdate> = _locationUpdates.asStateFlow()

    init {

        try {
            beaconScanner = BeaconScanner(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize beacon scanner: ${e.message}")
            cleanup()
        }

        // Observe beacon scanner updates
        coroutineScope.launch {
            beaconScanner?.scannedBeacons
                ?.onEach { beacons ->
                    try {
                        val position = positionCalculator.calculatePosition(beacons)
                        _locationUpdates.emit(LocationUpdate(
                            position = position,
                            nearbyBeacons = beacons
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error calculating position: ${e.message}")
                        _locationUpdates.emit(LocationUpdate(
                            position = null,
                            nearbyBeacons = beacons,
                            error = "Error calculating position: ${e.message}"
                        ))
                    }
                }
                ?.catch { e ->
                    Log.e(TAG, "Error in location updates: ${e.message}")
                    _locationUpdates.emit(LocationUpdate(
                        position = null,
                        nearbyBeacons = emptyList(),
                        error = "Error processing beacons: ${e.message}"
                    ))
                }
                ?.collect()
        }
    }

    fun startLocationUpdates() {
        beaconScanner?.startScanning()
    }

    fun stopLocationUpdates() {
        beaconScanner?.stopScanning()
    }

    fun cleanup() {
        try {
            beaconScanner?.cleanup()
            beaconScanner = null
            coroutineScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }

    }
}