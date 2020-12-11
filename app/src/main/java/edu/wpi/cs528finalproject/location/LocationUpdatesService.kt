package edu.wpi.cs528finalproject.location


import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Geocoder
import android.location.Location
import android.os.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.beust.klaxon.Klaxon
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.maps.android.SphericalUtil
import edu.wpi.cs528finalproject.*
import edu.wpi.cs528finalproject.PreferenceKeys.KEY_CHECKIN_PHOTO_SUBMITTED
import edu.wpi.cs528finalproject.PreferenceKeys.KEY_CHECKIN_TIMESTAMP
import edu.wpi.cs528finalproject.PreferenceKeys.KEY_SHOW_CHECKIN_ALERT
import edu.wpi.cs528finalproject.PreferenceKeys.KEY_START_UPLOAD_FRAGMENT
import edu.wpi.cs528finalproject.R
import java.time.Instant
import java.util.*


/**
 * A bound and started service that is promoted to a foreground service when location updates have
 * been requested and all clients unbind.
 *
 * For apps running in the background on "O" devices, location is computed only once every 10
 * minutes and delivered batched every 30 minutes. This restriction applies even to apps
 * targeting "N" or lower which are run on "O" devices.
 *
 * This sample show how to use a long-running service for location updates. When an activity is
 * bound to this service, frequent location updates are permitted. When the activity is removed
 * from the foreground, the service promotes itself to a foreground service, and location updates
 * continue. When the activity comes back to the foreground, the foreground service stops, and the
 * notification associated with that service is removed.
 */
class LocationUpdatesService : Service() {
    private val mBinder: IBinder = LocalBinder()

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private var mChangingConfiguration = false
    private var foregrounded = false
    private lateinit var mNotificationManager: NotificationManager

    /**
     * Contains parameters used by [com.google.android.gms.location.FusedLocationProviderApi].
     */
    private var mLocationRequest: LocationRequest? = null

    /**
     * Provides access to the Fused Location Provider API.
     */
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    /**
     * Callback for changes in location.
     */
    private var mLocationCallback: LocationCallback? = null
    private lateinit var mServiceHandler: Handler
    private var checkInHandler: Handler? = null

    /**
     * The current location.
     */
    private var mLocation: Location? = null

    private var lastCity: String = ""

    private var curActivity: AppCompatActivity? = null

    // Parameters for check-in
    private var hasPlacePermission = false

    private lateinit var mPlacesClient: PlacesClient

    private var currentPlace: Place? = null
    private var entryTime = 0L
    private var sentCheckInForCurrentPlace = false
    private var checkedInCurrentPlace = false

    private lateinit var database: DatabaseReference

    fun setCurrentActivity(activity: AppCompatActivity) {
        curActivity = activity
    }

    override fun onCreate() {
        database = Firebase.database.reference
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                onNewLocation(locationResult.lastLocation)
            }
        }
        mPlacesClient = Places.createClient(this)
        createLocationRequest()
        lastLocation
        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        mServiceHandler = Handler(handlerThread.looper)
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.app_name)
            // Create the channel for the notification
            val mServiceChannel =
                NotificationChannel(SERVICE_CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
            val mAlertChannel =
                NotificationChannel(ALERT_CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH)

            // Set the Notification Channel for the Notification Manager.
            mNotificationManager.createNotificationChannel(mServiceChannel)
            mNotificationManager.createNotificationChannel(mAlertChannel)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")
        val startedFromNotification = intent.getBooleanExtra(
            EXTRA_STARTED_FROM_NOTIFICATION,
            false
        )

        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification && curActivity != null) {
            removeLocationUpdates(curActivity!!)
            stopSelf()
        }
        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mChangingConfiguration = true
    }

    override fun onBind(intent: Intent): IBinder {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        Log.i(TAG, "in onBind()")
        stopForeground(true)
        foregrounded = false
        mChangingConfiguration = false
        return mBinder
    }

    override fun onRebind(intent: Intent) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(TAG, "in onRebind()")
        stopForeground(true)
        foregrounded = false
        mChangingConfiguration = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "Last client unbound from service")

        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration && Utils.requestingLocationUpdates(this)) {
            Log.i(TAG, "Starting foreground service")
            foregrounded = true
            startForeground(FOREGROUND_SERVICE_NOTIFICATION_ID, notification)
        }
        return true // Ensures onRebind() is called when a client re-binds.
    }

    override fun onDestroy() {
        mServiceHandler.removeCallbacksAndMessages(null)
        checkInHandler?.removeCallbacksAndMessages(null)
    }

    /**
     * Makes a request for location updates.
     */
    fun requestLocationUpdates(activity: AppCompatActivity) {
        Log.i(TAG, "Requesting location updates")
        Utils.setRequestingLocationUpdates(this, true)
        startService(Intent(applicationContext, LocationUpdatesService::class.java))
        if (ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= 23) {
                PermissionUtils.requestPermission(
                    activity, PermissionRequestCodes.enableLocationUpdatesService,
                    Manifest.permission.ACCESS_FINE_LOCATION, false,
                    R.string.location_permission_required,
                    R.string.location_permission_rationale
                )
            }
            Utils.setRequestingLocationUpdates(this, false)
        } else {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback, Looper.myLooper()
            )
        }
    }

    /**
     * Removes location updates.
     */
    private fun removeLocationUpdates(activity: AppCompatActivity) {
        Log.i(TAG, "Removing location updates")
        if (ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= 23) {
                PermissionUtils.requestPermission(
                    activity, PermissionRequestCodes.enableLocationUpdatesService,
                    Manifest.permission.ACCESS_FINE_LOCATION, false,
                    R.string.location_permission_required,
                    R.string.location_permission_rationale
                )
            }
            Utils.setRequestingLocationUpdates(this, true)
        } else {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback)
            Utils.setRequestingLocationUpdates(this, false)
            stopSelf()
        }
    }

    fun requestPlaceUpdates(activity: AppCompatActivity) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= 23) {
                PermissionUtils.requestPermission(
                    activity, PermissionRequestCodes.requestCurrentPlace,
                    Manifest.permission.ACCESS_FINE_LOCATION, false,
                    R.string.location_permission_required,
                    R.string.location_permission_rationale
                )
            }
        } else {
            hasPlacePermission = true
        }
    }

    private fun getCurrentPlace() {
        if (hasPlacePermission) {
            try {
                // Get the likely places - that is, the businesses and other points of interest that
                // are the best match for the device's current location.
                // Use fields to define the data types to return.
                val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS,
                    Place.Field.LAT_LNG)

                // Use the builder to create a FindCurrentPlaceRequest.
                val request = FindCurrentPlaceRequest.newInstance(placeFields)

                val placeResult = mPlacesClient.findCurrentPlace(request)
                var returnPlace: Place? = null
                placeResult.addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val likelyPlaces = task.result
                        if (likelyPlaces != null) {
                            var maxLikelihood = 0.0
                            for (placeLikelihood in likelyPlaces.placeLikelihoods) {
                                if (placeLikelihood.likelihood >= maxLikelihood) {
                                    maxLikelihood = placeLikelihood.likelihood
                                    returnPlace = placeLikelihood.place
                                }
                            }
                            if (shouldCheckIn(returnPlace)) checkIn()
                        }
                    } else {
                        Log.e(TAG, "Exception: %s", task.exception)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Exception: %s", e)
            }
        }
    }

    private fun shouldCheckIn(newPlace: Place?): Boolean {
        val tempLocation = mLocation ?: return false
        if (SphericalUtil.computeDistanceBetween(
                LatLng(tempLocation.latitude, tempLocation.longitude), newPlace?.latLng
            ) <= MAX_DISTANCE) {
            if (currentPlace == null) {
                currentPlace = newPlace
                entryTime = Instant.now().toEpochMilli()
            } else if (newPlace == null) {
                return false
            } else if (currentPlace == newPlace) {
                val now = Instant.now().toEpochMilli()
                if (!sentCheckInForCurrentPlace && now - entryTime >= DWELL_TIME) {
                    sentCheckInForCurrentPlace = true
                    if (checkedInCurrentPlace) {
                        return false
                    }
                    checkedInCurrentPlace = true
                    return true
                }
            } else if (currentPlace != newPlace) {
                currentPlace = newPlace
                entryTime = Instant.now().toEpochMilli()
                sentCheckInForCurrentPlace = false
            }
        }
        return false
    }

    private fun checkIn() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putLong(KEY_CHECKIN_TIMESTAMP, Instant.now().toEpochMilli())
            .putBoolean(KEY_CHECKIN_PHOTO_SUBMITTED, false)
            .apply()

        if (foregrounded) {
            // Send notification
            val uploadIntent = Intent(this, NavigationActivity::class.java)
            uploadIntent.putExtra(KEY_START_UPLOAD_FRAGMENT, true)
                .flags = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            val activityPendingIntent = PendingIntent.getActivity(
                this, 0, uploadIntent, PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .setContentIntent(activityPendingIntent)
                .setContentText(getString(R.string.check_in_content))
                .setContentTitle(getString(R.string.check_in_title))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(getString(R.string.check_in_content))
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
            // Set the Channel ID for Android O.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(ALERT_CHANNEL_ID) // Channel ID
            }
            mNotificationManager.notify(CHECK_IN_NOTIFICATION_ID, builder.build())
        } else {
            // Create dialog
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(KEY_SHOW_CHECKIN_ALERT, true)
                .apply()
        }
        setCheckInTimer()
    }

    private fun setCheckInTimer() {
        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        checkInHandler = Handler(handlerThread.looper)
        val timerRunnable = Runnable {
            val photoSubmitted = PreferenceManager.getDefaultSharedPreferences(this@LocationUpdatesService)
                .getBoolean(KEY_CHECKIN_PHOTO_SUBMITTED, false)

            if (!photoSubmitted) {
                val currentFirebaseUser = FirebaseAuth.getInstance().currentUser?.email?.split('@')?.get(0)
                    ?: "No User"

                val valueEventListener = object : ValueEventListener {
                    override fun onCancelled(databaseError: DatabaseError) {
                        // handle error
                    }
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        val numPrompts = (dataSnapshot.child("numberOfTimesPromptedToWearMask")
                            .value
                            ?: 0L) as Long
                        database.child("maskWearing").child(currentFirebaseUser)
                            .child("numberOfTimesPromptedToWearMask")
                            .setValue(numPrompts + 1)
                    }
                }
                checkedInCurrentPlace = true

                val ref = database.child("maskWearing").child(currentFirebaseUser)
                ref.addListenerForSingleValueEvent(valueEventListener)

            }
        }
        checkInHandler?.postDelayed(timerRunnable, CHECK_IN_TIME_LIMIT)
    }

    private fun getNewCityNotification(city: String, level: String): Notification {
        val text = getString(R.string.new_city_level_notification, city, level)
        // The PendingIntent to launch activity.
        val activityPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, LoginActivity::class.java), 0
        )
        val builder = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentIntent(activityPendingIntent)
            .setContentText(text)
            .setContentTitle(getString(R.string.new_city_level_title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker(text)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(ALERT_CHANNEL_ID) // Channel ID
        }
        return builder.build()
    }


    /**
     * Returns the [NotificationCompat] used as part of the foreground service.
     */
    private val notification: Notification
        get() {
            val intent = Intent(this, LocationUpdatesService::class.java)
            val text: CharSequence = Utils.getLocationText(mLocation)

            // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
            intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)

            // The PendingIntent that leads to a call to onStartCommand() in this service.
            val servicePendingIntent = PendingIntent.getService(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )

            // The PendingIntent to launch activity.
            val activityPendingIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, LoginActivity::class.java), 0
            )
            val builder = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .addAction(
                    0, getString(R.string.launch_app),
                    activityPendingIntent
                )
                .addAction(
                    0, getString(R.string.remove_location_updates),
                    servicePendingIntent
                )
                .setContentText(text)
                .setContentTitle(Utils.getLocationTitle(this))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis())

            // Set the Channel ID for Android O.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(SERVICE_CHANNEL_ID) // Channel ID
            }
            return builder.build()
        }

    private val lastLocation: Unit
        get() {
            if (curActivity != null) {
                if (ActivityCompat.checkSelfPermission(
                        curActivity!!,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        curActivity!!,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        PermissionUtils.requestPermission(
                            curActivity!!, PermissionRequestCodes.enableLocationUpdatesService,
                            Manifest.permission.ACCESS_FINE_LOCATION, false,
                            R.string.location_permission_required,
                            R.string.location_permission_rationale
                        )
                    }
                } else {
                    mFusedLocationClient.lastLocation
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful && task.result != null) {
                                mLocation = task.result
                            } else {
                                Log.w(
                                    TAG,
                                    "Failed to get location."
                                )
                            }
                        }
                }
            }
        }

    private fun onNewLocation(location: Location) {
        Log.i(TAG, "New location: $location")
        mLocation = location

        // Notify anyone listening for broadcasts about the new location.
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        val geocoder = Geocoder(this, Locale.getDefault())
        val city = geocoder.getFromLocation(location.latitude, location.longitude, 1)[0].locality
        if (city != lastCity) {
            lastCity = city
            Fuel.post("https://hat1omnl1j.execute-api.us-east-2.amazonaws.com")
                .jsonBody("{ \"town\": \"$city\" }")
                .response { _, response, result ->
                    handleCityDataResponse(response, result)
                }
        }

        getCurrentPlace()
        // Update notification content if running as a foreground service.
        if (serviceIsRunningInForeground(this)) {
            mNotificationManager.notify(
                FOREGROUND_SERVICE_NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun handleCityDataResponse(response: Response, result: Result<ByteArray, FuelError>) {
        val (bytes, error) = result
        if (bytes == null || error != null) {
            Log.e("CityAPI", error.toString())
            return
        }
        if (response.statusCode == 404 || bytes.isEmpty()) {
            Log.e("CityAPI", "Got a 404 or empty response")
            return
        }
        val json = String(response.data)
        Log.d("CityAPI", json)
        val cityData: CityData?
        try {
            val cityDataWrapper = Klaxon()
                .parse<CityDataWrapper>(json) ?: throw Error("cityDataWrapper is null")
            cityData = Klaxon()
                .parseArray<CityData>(cityDataWrapper.body)?.get(0)
                ?: throw Error("cityData is null")

        } catch (error: Error) {
            Log.e("CityAPI", error.toString())
            return
        }
        mNotificationManager.notify(
            CITY_CHANGE_NOTIFICATION_ID,
            getNewCityNotification(cityData.cityTown, cityData.covidLevel)
        )
    }

    /**
     * Sets the location request parameters.
     */
    private fun createLocationRequest() {
        mLocationRequest = LocationRequest()
        mLocationRequest!!.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest!!.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        val service: LocationUpdatesService
            get() = this@LocationUpdatesService
    }

    /**
     * Returns true if this is a foreground service.
     *
     * @param context The [Context].
     */
    private fun serviceIsRunningInForeground(context: Context): Boolean {
        val manager = context.getSystemService(
            ACTIVITY_SERVICE
        ) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (javaClass.name == service.service.className) {
                if (service.foreground) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private const val PACKAGE_NAME =
            "edu.wpi.cs528finalproject.location"
        private val TAG = LocationUpdatesService::class.java.simpleName

        /**
         * The name of the channel for notifications.
         */
        private const val SERVICE_CHANNEL_ID = "service_channel"
        private const val ALERT_CHANNEL_ID = "alert_channel"
        const val ACTION_BROADCAST = "$PACKAGE_NAME.broadcast"
        const val EXTRA_LOCATION = "$PACKAGE_NAME.location"
        private const val EXTRA_STARTED_FROM_NOTIFICATION = PACKAGE_NAME +
                ".started_from_notification"

        /**
         * The desired interval for location updates. Inexact. Updates may be more or less frequent.
         */
        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 30000

        /**
         * The fastest rate for active location updates. Updates will never be more frequent
         * than this value.
         */
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2

        /**
         * The identifier for the notification displayed for the foreground service.
         */
        private const val FOREGROUND_SERVICE_NOTIFICATION_ID = 34
        private const val CITY_CHANGE_NOTIFICATION_ID = 42
        private const val CHECK_IN_NOTIFICATION_ID = 14

        // Parameters to determine whether user is at a place
        private const val DWELL_TIME = 5 * 60 * 1000L  // 5 minutes
//        private const val DWELL_TIME = 1 * 20 * 1000L  // 5 minutes
        private const val MAX_DISTANCE = 500
        const val CHECK_IN_TIME_LIMIT = 5 * 60 * 1000L  // 5 minutes
//        const val CHECK_IN_TIME_LIMIT = 1 * 20 * 1000L  // 5 minutes
    }
}
