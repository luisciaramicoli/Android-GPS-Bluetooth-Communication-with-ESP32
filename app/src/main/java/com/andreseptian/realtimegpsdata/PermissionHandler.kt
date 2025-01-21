package com.andreseptian.realtimegpsdata

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class PermissionHandler(private val activity: AppCompatActivity) {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 101
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 102
    }

    // Periksa izin lokasi
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Periksa izin Bluetooth (termasuk BLUETOOTH_CONNECT untuk Android 12+)
    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Untuk API < 31 hanya periksa BLUETOOTH dan BLUETOOTH_ADMIN
            ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.BLUETOOTH_ADMIN
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Meminta izin lokasi
    fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    // Meminta izin Bluetooth
    fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        ActivityCompat.requestPermissions(
            activity,
            permissions,
            BLUETOOTH_PERMISSION_REQUEST_CODE
        )
    }

    // Menangani hasil permintaan izin
    fun handlePermissionResult(
        requestCode: Int,
        grantResults: IntArray,
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit
    ) {
        if (grantResults.isNotEmpty()) {
            when (requestCode) {
                LOCATION_PERMISSION_REQUEST_CODE -> {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        onPermissionGranted()
                    } else {
                        onPermissionDenied()
                    }
                }
                BLUETOOTH_PERMISSION_REQUEST_CODE -> {
                    if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                        onPermissionGranted()
                    } else {
                        onPermissionDenied()
                    }
                }
            }
        }
    }
}