package edu.wpi.cs528finalproject.ui.home

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.beust.klaxon.Klaxon
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.maps.android.SphericalUtil
import edu.wpi.cs528finalproject.*
import edu.wpi.cs528finalproject.location.CityData
import edu.wpi.cs528finalproject.location.CityDataWrapper
import java.util.*
import kotlin.collections.HashMap

class HomeFragment :
    Fragment(),
    OnMapReadyCallback,
    GoogleMap.OnPoiClickListener,
    GoogleMap.OnMapClickListener {

    private lateinit var homeViewModel: HomeViewModel
    private var mMap: GoogleMap? = null
    private var mapView: MapView? = null
    private val defaultZoom: Float = 16.0F
    private val biasRadius = 1000.0
    private lateinit var alertTextNumCasesView: TextView
    private lateinit var alertTextZoneView: TextView
    private lateinit var alertCircle: View
    private var searchMarker: Marker? = null
    private var selectedPoi: PointOfInterest? = null
    private var previousCity = ""
    private var currentLocation: Location? = null
    private var databaseReportLocations: HashMap<*, *>? = null
    private var markers: HashMap<LatLng, Marker> = hashMapOf()

    private lateinit var database: DatabaseReference

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        val root = inflater.inflate(R.layout.fragment_home, container, false)

        alertTextNumCasesView = root.findViewById(R.id.alertTextNumCases)
        alertTextZoneView = root.findViewById(R.id.alertTextZone)
        alertCircle = root.findViewById(R.id.alertCircle)

        var locationAutocompleteFragment =
            childFragmentManager.findFragmentById(R.id.search_bar_home)
                    as AutocompleteSupportFragment

        locationAutocompleteFragment =
            locationAutocompleteFragment.setHint(getString(R.string.LocationHint))
        locationAutocompleteFragment = locationAutocompleteFragment.setPlaceFields(
            listOf(
                Place.Field.LAT_LNG, Place.Field.ADDRESS,
                Place.Field.NAME
            )
        )
        locationAutocompleteFragment =
            locationAutocompleteFragment.setTypeFilter(TypeFilter.ESTABLISHMENT)

        val tempLoc = currentLocation
        if (tempLoc != null) {
            val currentLocationLatLng = LatLng(tempLoc.latitude, tempLoc.longitude)
            val bounds = LatLngBounds.Builder()
                .include(SphericalUtil.computeOffset(currentLocationLatLng, biasRadius, 0.0))
                .include(SphericalUtil.computeOffset(currentLocationLatLng, biasRadius, 90.0))
                .include(SphericalUtil.computeOffset(currentLocationLatLng, biasRadius, 180.0))
                .include(SphericalUtil.computeOffset(currentLocationLatLng, biasRadius, 270.0))
                .build()
            locationAutocompleteFragment = locationAutocompleteFragment.setLocationBias(
                RectangularBounds.newInstance(bounds)
            )
        }

        locationAutocompleteFragment =
            locationAutocompleteFragment.setOnPlaceSelectedListener(object :
                PlaceSelectionListener {
                override fun onPlaceSelected(place: Place) {
                    place.latLng?.let { showPlace(it) }
                    locationAutocompleteFragment =
                        locationAutocompleteFragment.setText(place.address)
                    Log.i(ContentValues.TAG, "Place: ${place.latLng}")
                }

                override fun onError(status: Status) {
                    Log.i(ContentValues.TAG, "An error occurred: $status")
                }
            })
        database = Firebase.database.reference
        mapView = root.findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)
        (requireActivity() as NavigationActivity).addOnLocationChangedListener(object :
            LocationChangedListener {
            override fun onLocationChanged(location: Location?) {
                if (location != null) {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    val address =
                        geocoder.getFromLocation(location.latitude, location.longitude, 1)[0]
                    val city = address.locality
                    if (city != previousCity) {
                        previousCity = city
                        Fuel.post("https://hat1omnl1j.execute-api.us-east-2.amazonaws.com")
                            .jsonBody("{ \"town\": \"$city\" }")
                            .response { _, response, result ->
                                handleCityDataResponse(response, result)
                            }
                    }
                    if (currentLocation == null) {
                        mMap?.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(
                                    location.latitude,
                                    location.longitude
                                ), defaultZoom
                            )
                        )
                    }
                    currentLocation = location
                }
            }
        })
        getFirebaseReportData()
        return root
    }

    private fun getFirebaseReportData() {
        val reportEventListener = object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError) {
                // handle error
            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.value != null) {
                    databaseReportLocations = dataSnapshot.value as HashMap<*, *>
                }
                if (databaseReportLocations != null) {
                    for ((key, value) in databaseReportLocations!!) {
                        val latlong = FirebaseEncoder.decodeFromFirebaseKey(key.toString())
                        val lat = latlong.split('(')[1].split(',')[0].toDouble()
                        val long = latlong.split(',')[1].replace(")", "").toDouble()
                        val point = LatLng(lat, long)
                        val tempValue = value as HashMap<*,*>
                        val test = System.currentTimeMillis() - 86400000
                        if (tempValue["timestamp"] as Long > test) {
                            val marker = mMap?.addMarker(
                                    MarkerOptions()
                                            .position(point)
                                            .title(tempValue["noofpeople"].toString() + " people not wearing masks reported")
                            )
                            if (marker != null) {
                                markers[point] = marker
                            }
                        }
                    }
                }
            }
        }
        val ref = database.child("report")
        ref.addListenerForSingleValueEvent(reportEventListener)
    }

    private fun handleCityDataResponse(response: Response, result: Result<ByteArray, FuelError>) {
        val (bytes, error) = result
        if (bytes == null || error != null) {
            displayDataError("An error occurred while fetching COVID data from the network.")
            Log.e("CityAPI", error.toString())
            return
        }
        if (response.statusCode == 404 || bytes.isEmpty()) {
            displayDataError("No COVID data is available for your current location.")
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
            displayDataError("Received an invalid response from the server.")
            return
        }
        displayCityData(cityData)
    }

    private fun displayCityData(cityData: CityData) {
        activity?.runOnUiThread {
            alertTextNumCasesView.text =
                resources.getString(R.string.safetyTextNumCases, cityData.twoWeekCaseCounts)
            alertTextZoneView.text =
                resources.getString(R.string.safetyTextThreatLevel, cityData.covidLevel)
            alertCircle.background.setTint(
                mapOf(
                    "Red" to 0xFFFF0000,
                    "Yellow" to 0xFFFFFF00,
                    "Green" to 0xFF008000,
                    "Grey" to 0xFF808080
                )[cityData.covidLevel]?.toInt() ?: 0
            )
        }
    }

    private fun displayDataError(message: String) {
        Log.e("CityAPI", "ERR: $message")
        activity?.runOnUiThread {
            alertTextNumCasesView.text = message
            alertTextZoneView.text = ""
            alertCircle.background.setTint(0)
        }
    }

    fun requestLocationPermissions() {
        if (mMap == null) return
        if (ActivityCompat.checkSelfPermission(
                this.requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this.requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= 23) {
                PermissionUtils.requestPermission(
                    this.requireActivity() as AppCompatActivity,
                    PermissionRequestCodes.enableMapView,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    false,
                    R.string.location_permission_required,
                    R.string.location_permission_rationale
                )
            }
        } else {
            mMap?.isMyLocationEnabled = true
        }
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        mMap = googleMap
        mMap?.setOnPoiClickListener(this)
        mMap?.setOnMapClickListener(this)
        requestLocationPermissions()

//        if (markers != null) {
//            for ((key, value) in markers!!) {
//                googleMap?.addMarker(
//                        MarkerOptions()
//                                .position(key)
//                )
//            }
//        }
    }


    override fun onMapClick(latLng: LatLng) {
        searchMarker?.remove()
        searchMarker = null
        selectedPoi = null
    }

    override fun onPoiClick(poi: PointOfInterest) {
        selectedPoi = poi
        showPlace(poi.latLng)
    }

    private fun showPlace(latLng: LatLng) {
        if (markers.containsKey(latLng)) {
            markers[latLng]?.showInfoWindow()
        } else if (searchMarker != null){
            if (searchMarker?.position?.equals(latLng) == false) {
                searchMarker?.remove()
                searchMarker = mMap?.addMarker(
                        MarkerOptions()
                                .position(latLng)
                                .title("No reports made for this location")
                )
                searchMarker?.showInfoWindow()
            } else {
                searchMarker?.showInfoWindow()
            }
        } else {
            searchMarker = mMap?.addMarker(
                    MarkerOptions()
                            .position(latLng)
                            .title("No reports made for this location")
            )
            searchMarker?.showInfoWindow()
        }
        mMap?.moveCamera(
            CameraUpdateFactory.newLatLngZoom(latLng, defaultZoom)
        )
    }

    override fun onResume() {
        mapView?.onResume()
        super.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mapView?.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        mapView?.onLowMemory()
        super.onLowMemory()
    }
}