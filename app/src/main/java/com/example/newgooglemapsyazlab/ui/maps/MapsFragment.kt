package com.example.newgooglemapsyazlab.ui.maps

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognizerIntent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.newgooglemapsyazlab.R
import com.example.newgooglemapsyazlab.Util.Constants.ACTION_SERVICE_START
import com.example.newgooglemapsyazlab.Util.Constants.ACTION_SERVICE_STOP
import com.example.newgooglemapsyazlab.Util.ExtensionFunction.disable
import com.example.newgooglemapsyazlab.Util.ExtensionFunction.enable
import com.example.newgooglemapsyazlab.Util.ExtensionFunction.hide
import com.example.newgooglemapsyazlab.Util.ExtensionFunction.show
import com.example.newgooglemapsyazlab.Util.Permissions.hasBackgroundLocationPermission
import com.example.newgooglemapsyazlab.Util.Permissions.requestBackgroundLocationPermission
import com.example.newgooglemapsyazlab.databinding.FragmentMapsBinding
import com.example.newgooglemapsyazlab.model.Result
import com.example.newgooglemapsyazlab.service.TrackerService
import com.example.newgooglemapsyazlab.ui.maps.MapUtil.calculateElapsedTime
import com.example.newgooglemapsyazlab.ui.maps.MapUtil.calculateTheDistance
import com.example.newgooglemapsyazlab.ui.maps.MapUtil.setCameraPosition
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.ButtCap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.vmadalin.easypermissions.EasyPermissions
import com.vmadalin.easypermissions.dialogs.SettingsDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Suppress("DEPRECATION")
class MapsFragment : Fragment(),OnMapReadyCallback,GoogleMap.OnMyLocationButtonClickListener,GoogleMap.OnMarkerClickListener,EasyPermissions.PermissionCallbacks {
    private var _binding:FragmentMapsBinding?=null
    private val binding get() = _binding!!
    private lateinit var map:GoogleMap

    val started=MutableLiveData(false)

    private var startTime=0L
    private var stopTime=0L
    private var locationList= mutableListOf<LatLng>()
    private var polylineList= mutableListOf<Polyline>()
    private var markerList= mutableListOf<Marker>()

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private val SPEECH_REQUEST_CODE = 0

    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        startActivityForResult(intent, SPEECH_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0) ?: ""

            // Şimdi, kullanıcının konuştuğu metni işleyebiliriz.
            // Örneğin, Google Haritalar API'sini kullanarak yönlendirme yapabiliriz.
            // spokenText'i Google Haritalar API'sine geçirebilir ve kullanıcıyı belirtilen yere yönlendirebiliriz.

            // Kullanıcının konuştuğu metni bir adres olarak kabul ediyoruz.
            val address = spokenText // Kullanıcının söylediği adres metni

            // Google Haritalar'ı başlatmak için bir intent oluşturuyoruz.
            val intentUri = Uri.parse("google.navigation:q=$address&mode=w") // mode=w ile yürüyüş modunu belirtiyoruz
            val intent = Intent(Intent.ACTION_VIEW, intentUri)
            intent.setPackage("com.google.android.apps.maps") // Sadece Google Haritalar uygulaması açılacak şekilde sınırlama

            // Eğer cihazda Google Haritalar uygulaması yüklüyse, intent'i başlatıyoruz.
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            } else {
                // Eğer cihazda Google Haritalar uygulaması yoksa, kullanıcıya bilgi verilebilir.
                Toast.makeText(requireContext(), "Google Haritalar uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding= FragmentMapsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner=this
        binding.tracking=this
        binding.startVoiceButton.setOnClickListener {
            startVoiceInput()

        }

        binding.startButton.setOnClickListener {
            onStartButtonClicked()
        }
        binding.stopButton.setOnClickListener {
            onStopButtonClicked()
        }
        binding.resetButton.setOnClickListener {
            onResetButtonClicked()
        }

        fusedLocationProviderClient=LocationServices.getFusedLocationProviderClient(requireActivity())

        return binding.root
    }




    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    @SuppressLint("MissingPermission", "PotentialBehaviorOverride")
    override fun onMapReady(googleMap: GoogleMap) {
        map=googleMap
        map.isMyLocationEnabled=true
        map.setOnMyLocationButtonClickListener(this)
        map.setOnMarkerClickListener (this)
        map.uiSettings.apply {
            isZoomControlsEnabled=false
            isZoomGesturesEnabled=false
            isRotateGesturesEnabled=false
            isTiltGesturesEnabled=false
            isCompassEnabled=false
            isScrollGesturesEnabled=false
        }
        observeTrackerService()

    }

    private fun observeTrackerService(){
        TrackerService.locationList.observe(viewLifecycleOwner) {
            if (it != null) {
                locationList = it
                if (locationList.size>1){
                    binding.stopButton.enable()
                }
                drawPolyline()
                followPolyline()
            }
        }
        TrackerService.started.observe(viewLifecycleOwner) {
            started.value = it
        }
        TrackerService.startTime.observe(viewLifecycleOwner) {
            startTime = it
        }
        TrackerService.stopTime.observe(viewLifecycleOwner) {
            stopTime = it
            if (stopTime!=0L){
                showBiggerPicture()
                displayResults()
            }
        }
    }
    private fun drawPolyline(){
        val polyline=map.addPolyline(
            PolylineOptions().apply {
                width(10f)
                color(android.graphics.Color.RED)
                jointType(JointType.ROUND)
                startCap(ButtCap())
                endCap(ButtCap())
                addAll(locationList)
            }
        )
        polylineList.add(polyline)
    }
    private fun followPolyline(){
        if (locationList.isNotEmpty()){
            map.animateCamera((CameraUpdateFactory.newCameraPosition(setCameraPosition(locationList.last()))),100,null)
        }
    }
    private fun onStartButtonClicked() {
        if (hasBackgroundLocationPermission(requireContext())){
            startCountDown()
            binding.startButton.disable()
            binding.startButton.hide()
            binding.stopButton.show()

        }
        else{
            requestBackgroundLocationPermission(this)

        }

    }
    private fun onStopButtonClicked() {

        stopForegroundService()
        binding.stopButton.hide()
        binding.startButton.show()
    }

    private fun onResetButtonClicked() {
        mapReset()
    }




    private fun startCountDown() {
        binding.timerTextView.show()
        binding.stopButton.disable()
        val timer: CountDownTimer = object : CountDownTimer(4000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val currentSecond = millisUntilFinished / 1000
                if (currentSecond.toString() == "0") {
                    binding.timerTextView.text = "GO"
                    binding.timerTextView.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.black
                        )
                    )
                } else {
                    binding.timerTextView.text = currentSecond.toString()
                    binding.timerTextView.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.red
                        )
                    )
                }
            }

            override fun onFinish() {
                sendActionCommandToService(ACTION_SERVICE_START)

                binding.timerTextView.hide()
            }
        }
        timer.start()
    }
    private fun stopForegroundService() {
        binding.startButton.disable()

        sendActionCommandToService(ACTION_SERVICE_STOP)
    }

    private fun sendActionCommandToService(action: String) {
        Intent(
            requireContext(),
            TrackerService::class.java
        ).apply {
            this.action = action
            requireContext().startService(this)
        }
    }

    private fun showBiggerPicture() {

        val bounds=LatLngBounds.Builder()
        for (location in locationList){
            bounds.include(location)
        }
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),100
            ),2000,null
        )
        addMarker(locationList.first())
        addMarker(locationList.last())

    }

    private fun addMarker(position:LatLng){
        val marker=map.addMarker(MarkerOptions().position(position))
        if (marker != null) {
            markerList.add(marker)
        }

    }

    private fun displayResults(){
        val result= Result(
            calculateTheDistance(locationList),
            calculateElapsedTime(startTime,stopTime)


        )
        lifecycleScope.launch {
            delay(2500)
            val direction=MapsFragmentDirections.actionMapsFragmentToResultFragment(result)
            findNavController().navigate(direction)
            binding.startButton.apply {
                hide()
                enable()
            }
            binding.stopButton.hide()
            binding.resetButton.show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun mapReset() {
        fusedLocationProviderClient.lastLocation.addOnCompleteListener {
            val lastKnownLocation = LatLng(
                it.result.latitude,
                it.result.longitude
            )
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    setCameraPosition(lastKnownLocation)
                )
            )

            for (polyLine in polylineList) {
                polyLine.remove()
            }



            for (marker in markerList){
                marker.remove()
            }
            locationList.clear()
            markerList.clear()
            binding.resetButton.hide()
            binding.startButton.show()
        }
    }




    override fun onMyLocationButtonClick(): Boolean {
        binding.hintTextView.animate().alpha(0f).duration=1500
        lifecycleScope.launch {
            delay(2500)
            binding.hintTextView.hide()
            binding.startButton.show()
        }
        return false
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            SettingsDialog.Builder(requireActivity()).build().show()
        } else {
            requestBackgroundLocationPermission(this)
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        onStartButtonClicked()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding=null

    }

    override fun onMarkerClick(p0: Marker): Boolean {
        return true
          }




}