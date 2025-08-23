package com.andreseptian.realtimegpsdata

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(onLocationUpdated: (latitude: Double, longitude: Double, speed: Float) -> Unit) {
        // Define os parâmetros da requisição de localização
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, // Prioridade alta para melhor precisão
            5000 // Intervalo de atualização em milissegundos (5 segundos)
        ).apply {
            setMinUpdateIntervalMillis(2000) // Intervalo de atualização mais rápido (2 segundos)
            setWaitForAccurateLocation(false) // Não espera por uma localização precisa para começar
        }.build()

        // Define o callback para receber as atualizações de localização
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                // Percorre todas as localizações recebidas
                for (location in locationResult.locations) {
                    if (location != null) {
                        val latitude = location.latitude
                        val longitude = location.longitude
                        val speed = location.speed
                        onLocationUpdated(latitude, longitude, speed)
                    }
                }
            }
        }

        // Solicita atualizações de localização
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            context.mainLooper
        )
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }
}
