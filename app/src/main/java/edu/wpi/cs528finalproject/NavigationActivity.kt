package edu.wpi.cs528finalproject

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.libraries.places.api.Places
import com.google.android.material.bottomnavigation.BottomNavigationView
import edu.wpi.cs528finalproject.PreferenceKeys.KEY_SHOW_CHECKIN_ALERT
import edu.wpi.cs528finalproject.PreferenceKeys.KEY_START_UPLOAD_FRAGMENT
import edu.wpi.cs528finalproject.PreferenceKeys.KEY_UPLOAD_CLICK
import edu.wpi.cs528finalproject.location.LocationUpdatesService
import edu.wpi.cs528finalproject.ui.home.HomeFragment
import edu.wpi.cs528finalproject.ui.upload.UploadFragment

interface LocationChangedListener {
    fun onLocationChanged(location: Location?)
}

class NavigationActivity : AppCompatActivity() {

    // The BroadcastReceiver used to listen from broadcasts from the service.
    private var myReceiver: MyReceiver? = null

    // A reference to the service used to get location updates.
    private var mService: LocationUpdatesService? = null

    // Tracks the bound state of the service.
    private var mBound = false

    /**
     * Receiver for broadcasts sent by [LocationUpdatesService].
     */
    private class MyReceiver : BroadcastReceiver() {
        var listeners: MutableList<LocationChangedListener> = mutableListOf()
        override fun onReceive(context: Context, intent: Intent) {
            val location: Location? =
                intent.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION)

            for (listener in listeners) {
                listener.onLocationChanged(location)
            }
        }

        fun addOnLocationChangedListener(listener: LocationChangedListener) {
            listeners.add(listener)
        }
    }

    fun addOnLocationChangedListener(listener: LocationChangedListener) {
        myReceiver!!.addOnLocationChangedListener(listener)
    }

    // Monitors the state of the connection to the service.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder: LocationUpdatesService.LocalBinder = service as LocationUpdatesService.LocalBinder
            mService = binder.service
            mService?.setCurrentActivity(this@NavigationActivity)
            mBound = true
            enableForegroundLocationFeatures(PermissionRequestCodes.enableLocationUpdatesService)
            enableForegroundLocationFeatures(PermissionRequestCodes.requestCurrentPlace)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mService = null
            mBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Places.initialize(applicationContext, getString(R.string.google_places_key))
        myReceiver = MyReceiver()
        setContentView(R.layout.activity_navigation)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        navView.setupWithNavController(navController)
        navView.setOnNavigationItemReselectedListener {  }
        addOnLocationChangedListener(object : LocationChangedListener {
            override fun onLocationChanged(location: Location?) {
                if (PreferenceManager.getDefaultSharedPreferences(this@NavigationActivity)
                        .getBoolean(KEY_SHOW_CHECKIN_ALERT, false)) {
                    val dialog = AlertDialog.Builder(this@NavigationActivity)
                        .setTitle(R.string.check_in_title)
                        .setMessage(R.string.check_in_content)
                        .setPositiveButton(android.R.string.ok
                        ) { _, _ ->
                            val uploadIntent =
                                Intent(this@NavigationActivity, NavigationActivity::class.java)
                            uploadIntent.putExtra(KEY_START_UPLOAD_FRAGMENT, true)
                            startActivity(uploadIntent)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .create()
                    dialog.show()
                    PreferenceManager.getDefaultSharedPreferences(this@NavigationActivity)
                        .edit()
                        .putBoolean(KEY_SHOW_CHECKIN_ALERT, false)
                        .apply()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        // Bind to the service. If the service is in foreground mode, this signals to the service
        // that since this activity is in the foreground, the service can exit foreground mode.
        bindService(
            Intent(this, LocationUpdatesService::class.java), mServiceConnection,
            BIND_AUTO_CREATE
        )
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null && intent.getBooleanExtra(KEY_START_UPLOAD_FRAGMENT, false)) {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val uploadFragment = UploadFragment()
            val bundle = Bundle()
            bundle.putBoolean(KEY_UPLOAD_CLICK, true)
            uploadFragment.arguments = bundle
            val navController = navHostFragment.navController
            val action = MobileNavigationDirections.actionGlobalNavigationUpload(true)
            navController.navigate(action)
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(KEY_START_UPLOAD_FRAGMENT, false)
                .apply()
        }
        intent?.removeExtra(KEY_START_UPLOAD_FRAGMENT)
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            myReceiver!!,
            IntentFilter(LocationUpdatesService.ACTION_BROADCAST)
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver!!)
        super.onPause()
    }

    override fun onStop() {
        if (mBound) {
            unbindService(mServiceConnection)
            mBound = false
        }
        super.onStop()
    }

    override fun onBackPressed() {
        val webView = findViewById<WebView>(R.id.webview)
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun enableForegroundLocationFeatures(requestCode: Int) {
        if (requestCode == PermissionRequestCodes.enableLocationUpdatesService) {
            mService?.requestLocationUpdates(this)
        } else if (requestCode == PermissionRequestCodes.requestCurrentPlace) {
            mService?.requestPlaceUpdates(this)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (permissions.isEmpty()) {
            DeferredPermissions.deferredMap[requestCode] = true
        } else {
            if (requestCode == PermissionRequestCodes.enableLocationUpdatesService) {
                if (PermissionUtils.isPermissionGranted(
                        permissions, grantResults,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )) {
                    mService?.requestLocationUpdates(this)
                    if (DeferredPermissions.deferredMap[PermissionRequestCodes.enableMapView] == true) {
                        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                        val homeFragment = navHostFragment.childFragmentManager.primaryNavigationFragment as HomeFragment
                        homeFragment.requestLocationPermissions()
                        DeferredPermissions.deferredMap[PermissionRequestCodes.enableMapView] = false
                    }
                    if (DeferredPermissions.deferredMap[PermissionRequestCodes.requestCurrentPlace] == true) {
                        enableForegroundLocationFeatures(PermissionRequestCodes.requestCurrentPlace)
                        DeferredPermissions.deferredMap[PermissionRequestCodes.requestCurrentPlace] = false
                    }
                } else {
                    DeferredPermissions.deferredMap[PermissionRequestCodes.enableMapView] = false
                    DeferredPermissions.deferredMap[PermissionRequestCodes.requestCurrentPlace] = false
                }
            } else if (requestCode == PermissionRequestCodes.enableMapView) {
                if (PermissionUtils.isPermissionGranted(
                        permissions, grantResults,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )) {
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                    val homeFragment = navHostFragment.childFragmentManager.primaryNavigationFragment as HomeFragment
                    homeFragment.requestLocationPermissions()
                    if (DeferredPermissions.deferredMap[PermissionRequestCodes.enableLocationUpdatesService] == true) {
                        mService?.requestLocationUpdates(this)
                        DeferredPermissions.deferredMap[PermissionRequestCodes.enableLocationUpdatesService] = false
                    }
                    if (DeferredPermissions.deferredMap[PermissionRequestCodes.requestCurrentPlace] == true) {
                        enableForegroundLocationFeatures(PermissionRequestCodes.requestCurrentPlace)
                        DeferredPermissions.deferredMap[PermissionRequestCodes.requestCurrentPlace] = false
                    }
                } else {
                    DeferredPermissions.deferredMap[PermissionRequestCodes.enableLocationUpdatesService] = false
                    DeferredPermissions.deferredMap[PermissionRequestCodes.requestCurrentPlace] = false
                }
            } else if (requestCode == PermissionRequestCodes.requestCurrentPlace) {
                if (PermissionUtils.isPermissionGranted(
                        permissions, grantResults,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )) {
                    enableForegroundLocationFeatures(PermissionRequestCodes.requestCurrentPlace)
                    if (DeferredPermissions.deferredMap[PermissionRequestCodes.enableLocationUpdatesService] == true) {
                        mService?.requestLocationUpdates(this)
                        DeferredPermissions.deferredMap[PermissionRequestCodes.enableLocationUpdatesService] = false
                    }
                    if (DeferredPermissions.deferredMap[PermissionRequestCodes.enableMapView] == true) {
                        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                        val homeFragment = navHostFragment.childFragmentManager.primaryNavigationFragment as HomeFragment
                        homeFragment.requestLocationPermissions()
                        DeferredPermissions.deferredMap[PermissionRequestCodes.enableMapView] = false
                    }
                } else {
                    DeferredPermissions.deferredMap[PermissionRequestCodes.enableLocationUpdatesService] = false
                    DeferredPermissions.deferredMap[PermissionRequestCodes.enableMapView] = false
                }
            } else if (requestCode == PermissionRequestCodes.enableCamera) {
                val navHostFragment =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                val uploadFragment =
                    navHostFragment.childFragmentManager.primaryNavigationFragment as UploadFragment
                if (PermissionUtils.isPermissionGranted(
                        permissions, grantResults, Manifest.permission.CAMERA)) {
                    uploadFragment.requestCameraPermissions(false)
                } else {
                    uploadFragment.requestCameraPermissions(true)
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}