package com.example.museumguide.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.museumguide.R
import com.example.museumguide.databinding.FragmentMapBinding
import com.example.museumguide.model.Exhibit
import com.example.museumguide.model.ExhibitDatabase
import com.example.museumguide.exhibit.ExhibitRepository
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Displays the museum floor plan as an interactive Google Map overlay.
 *
 * Each exhibit is represented by a map marker. Tapping a marker opens
 * the exhibit detail screen.
 *
 * In production, the museum floor plan would be rendered as a custom
 * ground overlay tile overlay, with markers positioned in real-world
 * coordinates. For this demo, we use an abstract coordinate space with
 * markers placed at normalised positions.
 */
class MuseumMapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var googleMap: GoogleMap? = null
    private lateinit var exhibitRepository: ExhibitRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = ExhibitDatabase.getInstance(requireContext())
        exhibitRepository = ExhibitRepository(db.exhibitDao())

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map_container) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.setMinZoomPreference(15f)
        googleMap?.setMaxZoomPreference(22f)
        // Default map type - in production replace with museum floor plan tiles
        googleMap?.mapType = GoogleMap.MAP_TYPE_NORMAL

        // Load and place exhibit markers
        lifecycleScope.launch(Dispatchers.IO) {
            loadExhibitMarkers()
        }
    }

    private suspend fun loadExhibitMarkers() {
        val exhibits = exhibitRepository.getAllExhibits()
        // Switch to Main thread for Google Maps API calls
        withContext(Dispatchers.Main) {
            for (exhibit in exhibits) {
                // Convert normalised positions to approximate LatLng coordinates.
                // In production: replace with actual museum mapping coordinates.
                val position = normalizedToLatLng(exhibit.mapPositionX, exhibit.mapPositionY)
                googleMap?.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(exhibit.name)
                        .snippet(exhibit.era)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                )?.tag = exhibit
            }

            // Centre map on the museum area
            if (exhibits.isNotEmpty()) {
                val first = exhibits.first()
                val pos = normalizedToLatLng(first.mapPositionX, first.mapPositionY)
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
            }

            // Set up marker click listener
            googleMap?.setOnMarkerClickListener { marker ->
                val exhibit = marker.tag as? Exhibit
                exhibit?.let { showExhibitDetail(it) }
                true
            }
        }
    }

    /**
     * Convert normalised (0..1) positions to approximate LatLng.
     * This is a placeholder — replace with actual museum mapping.
     */
    private fun normalizedToLatLng(x: Float, y: Float): LatLng {
        val baseLat = 39.9042   // Beijing area as placeholder
        val baseLng = 116.4074
        return LatLng(
            baseLat + (y - 0.5) * 0.002,
            baseLng + (x - 0.5) * 0.002
        )
    }

    private fun showExhibitDetail(exhibit: Exhibit) {
        val bundle = Bundle().apply {
            putLong("exhibit_id", exhibit.id)
        }
        findNavController().navigate(
            R.id.action_map_to_exhibit_detail,
            bundle
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
