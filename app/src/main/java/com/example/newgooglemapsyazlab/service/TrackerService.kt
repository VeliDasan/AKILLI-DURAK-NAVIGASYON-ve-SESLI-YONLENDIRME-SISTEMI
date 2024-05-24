    package com.example.newgooglemapsyazlab.service


    import android.annotation.SuppressLint
    import android.app.NotificationChannel
    import android.app.NotificationManager
    import android.app.NotificationManager.IMPORTANCE_LOW
    import android.content.Context
    import android.content.Intent
    import android.content.pm.ServiceInfo
    import android.location.Location
    import android.os.Build
    import android.os.Looper
    import android.util.Log
    import androidx.core.app.NotificationCompat
    import androidx.lifecycle.LifecycleService
    import androidx.lifecycle.MutableLiveData
    import com.example.newgooglemapsyazlab.R
    import com.example.newgooglemapsyazlab.Util.Constants.ACTION_SERVICE_START
    import com.example.newgooglemapsyazlab.Util.Constants.ACTION_SERVICE_STOP
    import com.example.newgooglemapsyazlab.Util.Constants.LOCATION_FASTEST_UPDATE_INTERVAL
    import com.example.newgooglemapsyazlab.Util.Constants.LOCATION_UPDATE_INTERVAL
    import com.example.newgooglemapsyazlab.Util.Constants.NOTIFICATION_CHANNEL_ID
    import com.example.newgooglemapsyazlab.Util.Constants.NOTIFICATION_CHANNEL_NAME
    import com.example.newgooglemapsyazlab.Util.Constants.NOTIFICATION_ID
    import com.example.newgooglemapsyazlab.ui.maps.MapUtil
    import com.example.newgooglemapsyazlab.ui.maps.MapUtil.calculateTheDistance
    import com.google.android.gms.location.FusedLocationProviderClient
    import com.google.android.gms.location.LocationCallback
    import com.google.android.gms.location.LocationRequest
    import com.google.android.gms.location.LocationResult
    import com.google.android.gms.location.LocationServices
    import com.google.android.gms.maps.model.LatLng
    import dagger.hilt.android.AndroidEntryPoint
    import javax.inject.Inject

    @Suppress("DEPRECATION")
    @AndroidEntryPoint
    class TrackerService:LifecycleService() {

        @Inject
        lateinit var notification: NotificationCompat.Builder
        @Inject
        lateinit var notificationManager: NotificationManager

        private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
        companion object {
            val started = MutableLiveData<Boolean>()
            val startTime=MutableLiveData<Long>()
            val stopTime=MutableLiveData<Long>()
            val locationList=MutableLiveData<MutableList<LatLng>>()

        }
        private fun setInitialValues() {
            started.postValue(false)
            startTime.postValue(0L)
            stopTime.postValue(0L)
            locationList.postValue(mutableListOf())

        }

        private val locationCallback=object :LocationCallback(){
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                result.locations.let { locations ->
                    for (location in locations){
                        updateLocationList(location)
                        updateNotificationPeriodically()


                    }
                }
            }
        }



        private fun updateLocationList(location: Location){
            val newLatLng=LatLng(location.latitude,location.longitude)
            locationList.value?.apply {
                add(newLatLng)
                locationList.postValue(this)
            }
        }

        override fun onCreate() {
            super.onCreate()
            notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
            createNotificationChannel()
            setInitialValues()
            notification
                .setSmallIcon(R.drawable.ic_run)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
        }



        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            intent?.let {
                when (it.action) {
                    ACTION_SERVICE_START -> {
                        started.postValue(true)
                        startForegroundService()
                        startLocationUpdates()
                    }
                    ACTION_SERVICE_STOP -> {
                        started.postValue(false)
                        stopForegroundService()
                    }
                    else -> {
                    }
                }
            }
            return super.onStartCommand(intent, flags, startId)
        }




        @SuppressLint("MissingForegroundServiceType", "ForegroundServiceType")
        private fun startForegroundService(){
            createNotificationChannel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 ve sonrası için
                startForeground(NOTIFICATION_ID, notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                // Android 12 öncesi için
                startForeground(NOTIFICATION_ID, notification.build())
            }
        }


        @SuppressLint("MissingPermission")
    private fun startLocationUpdates(){
          val locationRequest=LocationRequest().apply {
              interval= LOCATION_UPDATE_INTERVAL
              fastestInterval= LOCATION_FASTEST_UPDATE_INTERVAL
              priority=LocationRequest.PRIORITY_LOW_POWER
          }
          fusedLocationProviderClient.requestLocationUpdates(
              locationRequest,
              locationCallback,
              Looper.getMainLooper()
          )
          startTime.postValue(System.currentTimeMillis())
      }
        private fun stopForegroundService() {

            removeLocationUpdates()
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(
                NOTIFICATION_ID
            )
            stopForeground(true)
            stopSelf()
            stopTime.postValue(System.currentTimeMillis())
        }

        private fun removeLocationUpdates() {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
        private fun updateNotificationPeriodically() {
            notification.apply {
                setContentTitle("Distance Travelled")
                setContentText(locationList.value?.let { calculateTheDistance(it) }+"km")
            }
            notificationManager.notify(NOTIFICATION_ID,notification.build())

        }

        private fun createNotificationChannel() {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              val channel = NotificationChannel(
                  NOTIFICATION_CHANNEL_ID,
                  NOTIFICATION_CHANNEL_NAME,
                  IMPORTANCE_LOW
              )
              notificationManager.createNotificationChannel(channel)
          }
      }
  }