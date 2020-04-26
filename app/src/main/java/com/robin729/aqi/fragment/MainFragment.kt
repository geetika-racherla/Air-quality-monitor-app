package com.robin729.aqi.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Color
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.text.Html
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.LatLng
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions
import com.robin729.aqi.R
import com.robin729.aqi.utils.Constants.AUTOCOMPLETE_REQUEST_CODE
import com.robin729.aqi.utils.PermissionUtils
import com.robin729.aqi.utils.Util
import com.robin729.aqi.utils.Util.getColorRes
import com.robin729.aqi.viewmodel.AqiViewModel
import kotlinx.android.synthetic.main.fragment_main.*
import timber.log.Timber
import java.util.*
import kotlin.math.roundToInt


class MainFragment : Fragment() {

    private val ANIMATION_DURATION = 1000.toLong()

    private val aqiViewModel: AqiViewModel by lazy {
        ViewModelProvider(this).get(AqiViewModel::class.java)
    }

    private val geocoder by lazy { Geocoder(context, Locale.getDefault()) }

    private val fusedLocationProviderClient: FusedLocationProviderClient by lazy {
        FusedLocationProviderClient(context!!)
    }

    var lat: Double = 0.00
    var long: Double = 0.00

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult?) {
            super.onLocationResult(p0)
            if (Util.hasNetwork(context)) {
                val newLat = p0?.locations?.get(0)?.latitude!!
                val newLong = p0.locations[0]?.longitude!!
                if (lat != newLat && long != newLong) {
                    lat = newLat
                    long = newLong
                    fetchData(LatLng(lat, long))
                }
            }
        }
    }

    private val locationRequest: LocationRequest by lazy { LocationRequest() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment

        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        (activity as AppCompatActivity).actionBar?.setDisplayShowTitleEnabled(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            txt_no2.text =
                Html.fromHtml("NO<sub><small>2</small></sub>", HtmlCompat.FROM_HTML_MODE_LEGACY)
            txt_so2.text =
                Html.fromHtml("SO<sub><small>2</small></sub>", HtmlCompat.FROM_HTML_MODE_LEGACY)
        } else {
            txt_no2.text = Html.fromHtml("NO<sub><small>2</small></sub>")
            txt_so2.text = Html.fromHtml("SO<sub><small>2</small></sub>")
        }

        handleNetworkChanges()

        aqiViewModel.loading.observe(viewLifecycleOwner, Observer {
            parent_layout.visibility = if (it) View.INVISIBLE else View.VISIBLE
            loading.visibility = if (it) View.VISIBLE else View.INVISIBLE
        })

        aqiViewModel.location.observe(viewLifecycleOwner, Observer {
            location.text = resources.getString(R.string.location, it)
        })

        aqiViewModel.weather.observe(viewLifecycleOwner, Observer {
            Log.e("TAG", "${it.time} time")
            weather_icon.setImageResource(Util.getArtForWeatherCondition(it.weather[0].id))
            temp.text = resources.getString(R.string.temp, it.main.temp.roundToInt().toString())
            date.text = Util.formatDate(it.time)
            weather_description.text = it.weather[0].desp
        })

        aqiViewModel.aqi.observe(viewLifecycleOwner, Observer {
            Log.e("TAG", "observe")
            aqi.text = it.data.index.details.aqi.toString()
            category.text = it.data.index.details.category
            card_view.setCardBackgroundColor(Color.parseColor(it.data.index.details.color))
            co.text = resources.getString(
                R.string.conc,
                it.data.pollutants.co.concentration.value.toString(),
                it.data.pollutants.co.concentration.units
            )
            no2.text = resources.getString(
                R.string.conc,
                it.data.pollutants.no2.concentration.value.toString(),
                it.data.pollutants.no2.concentration.units
            )
            pm10.text = resources.getString(
                R.string.conc,
                it.data.pollutants.pm10.concentration.value.toString(),
                it.data.pollutants.pm10.concentration.units
            )
            pm25.text = resources.getString(
                R.string.conc,
                it.data.pollutants.pm25.concentration.value.toString(),
                it.data.pollutants.pm25.concentration.units
            )
            so2.text = resources.getString(
                R.string.conc,
                it.data.pollutants.so2.concentration.value.toString(),
                it.data.pollutants.so2.concentration.units
            )
            general_recom.text = it.data.recommendations.general
            Log.e("TAG", it.data.index.details.color + "vv")
        })

    }

    override fun onStart() {
        super.onStart()
        if (PermissionUtils.isLocationEnabled(context!!)) {
            getLocationUpdates()
        } else {
            PermissionUtils.showGPSNotEnableDialog(context!!)
        }
    }

    private fun onSearchCalled() {
        // Set the fields to specify which types of place data to return.
        val placeOptions = PlaceOptions.builder()
            .toolbarColor(ContextCompat.getColor(context!!, R.color.textColor))
            .backgroundColor(ContextCompat.getColor(context!!, R.color.textColor))
            .hint("Enter the location...")
            .build()

        val intent = PlaceAutocomplete.IntentBuilder()
            .accessToken(getString(R.string.mapbox_key))
            .placeOptions(placeOptions)
            .build(activity)
        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
    }

    private fun handleNetworkChanges() {
        Util.getNetworkLiveData(context!!).observe(viewLifecycleOwner, Observer { isConnected ->
            if (!isConnected) {
                loading.visibility = View.GONE
                textViewNetworkStatus.text = getString(R.string.text_no_connectivity)
                networkStatusLayout.apply {
                    alpha = 0f
                    visibility = View.VISIBLE
                    setBackgroundColor(getColorRes(R.color.colorStatusNotConnected))
                    animate()
                        .alpha(1f)
                        .setDuration(ANIMATION_DURATION)
                        .setListener(null)
                }
            } else {
                loading.visibility = View.VISIBLE
                if (parent_layout.visibility == View.INVISIBLE) {
                    getLocationUpdates()
                }
                textViewNetworkStatus.text = getString(R.string.text_connectivity)
                networkStatusLayout.apply {
                    setBackgroundColor(getColorRes(R.color.colorStatusConnected))

                    animate()
                        .alpha(0f)
                        .setStartDelay(ANIMATION_DURATION)
                        .setDuration(ANIMATION_DURATION)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                visibility = View.GONE
                            }
                        })
                }

            }
        })
    }

    private fun getLocationUpdates() {
        locationRequest.run {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            fastestInterval = 60000
            interval = 60000
            smallestDisplacement = 800f
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun fetchData(latLng: LatLng) {
        aqiViewModel.fetchRepos(latLng.latitude, latLng.longitude, geocoder)
        aqiViewModel.fetchWeather(latLng.latitude, latLng.longitude)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.main_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> onSearchCalled()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            val feature = PlaceAutocomplete.getPlace(data)
            fetchData(LatLng(feature.center()?.latitude()!!, feature.center()?.longitude()!!))
        }
    }

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
        Timber.e("destroyed")
    }

}
