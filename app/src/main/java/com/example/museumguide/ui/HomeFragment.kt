package com.example.museumguide.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.museumguide.R
import com.example.museumguide.ai.AiConfiguration
import com.example.museumguide.databinding.FragmentHomeBinding
import com.example.museumguide.model.ChinaMuseums
import com.example.museumguide.model.MuseumInfo
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Home / Settings screen for the Museum Guide app.
 *
 * Features:
 * - Museum location setting (manual + GPS auto-detect)
 * - Quick access to China Top 10 Museums
 * - Link to AI Settings page
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var currentConfig: AiConfiguration = AiConfiguration()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            detectLocationByGps()
        } else {
            Toast.makeText(requireContext(), "需要位置权限才能自动定位", Toast.LENGTH_SHORT).show()
            binding.btnGpsLocate.isEnabled = true
            binding.btnGpsLocate.text = "📡 自动定位"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentConfig = AiConfiguration.load(requireContext())
        applyConfigToUi()

        binding.btnSaveLocation.setOnClickListener { saveLocation() }
        binding.btnGpsLocate.setOnClickListener { onGpsClicked() }

        // Navigate to AI settings page
        binding.btnGoAiSettings.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_ai_settings)
        }

        setupMuseumList()
        binding.rvMuseums.post { updateMuseumRanks() }
    }

    private fun applyConfigToUi() {
        // Museum city
        binding.etMuseumCity.setText(currentConfig.museumCity)
        updateCurrentMuseumText(currentConfig.museumCity)
    }

    private fun onGpsClicked() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        detectLocationByGps()
    }

    private fun saveLocation() {
        val city = binding.etMuseumCity.text.toString().trim()
        if (city.isBlank()) {
            Toast.makeText(requireContext(), "请输入城市名称", Toast.LENGTH_SHORT).show()
            return
        }
        currentConfig = currentConfig.copy(museumCity = city)
        AiConfiguration.save(requireContext(), currentConfig)
        updateCurrentMuseumText(city)
        Toast.makeText(requireContext(), "博物馆位置已保存: $city", Toast.LENGTH_SHORT).show()
    }

    private fun updateCurrentMuseumText(city: String) {
        val museum = ChinaMuseums.MUSEUMS.find {
            it.city.contains(city) || city.contains(it.city)
        }
        val museumName = museum?.let { " · ${it.name}" } ?: ""
        binding.tvCurrentMuseum.text = "当前定位：$city$museumName"
    }

    private fun detectLocationByGps() {
        binding.btnGpsLocate.isEnabled = false
        binding.btnGpsLocate.text = "定位中…"

        lifecycleScope.launch {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(requireContext())
                val cancellationTokenSource = CancellationTokenSource()
                val location = withContext(Dispatchers.IO) {
                    fusedClient.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationTokenSource.token
                    ).result
                }

                if (location != null) {
                    val geocoder = Geocoder(requireContext(), java.util.Locale.CHINESE)
                    val addresses = withContext(Dispatchers.IO) {
                        geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    }

                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val city = extractCity(address)
                        if (city.isNotBlank()) {
                            binding.etMuseumCity.setText(city)
                            currentConfig = currentConfig.copy(
                                museumCity = city,
                                museumLatitude = location.latitude,
                                museumLongitude = location.longitude
                            )
                            AiConfiguration.save(requireContext(), currentConfig)
                            updateCurrentMuseumText(city)
                            Toast.makeText(requireContext(), "已自动定位到: $city", Toast.LENGTH_SHORT).show()
                            // Find nearest museums within 1km
                            findNearbyMuseums(location.latitude, location.longitude, city)
                        } else {
                            Toast.makeText(requireContext(), "无法识别城市，请手动输入", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "无法获取位置信息", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "定位失败，请确保已开启GPS", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Toast.makeText(requireContext(), "定位权限被拒绝", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "定位出错: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnGpsLocate.isEnabled = true
                binding.btnGpsLocate.text = "📡 自动定位"
            }
        }
    }

    /**
     * Find museums within 1km of the current location.
     * If exactly one, auto-select and show info.
     * If multiple, show a dialog for user to pick.
     */
    private fun findNearbyMuseums(lat: Double, lng: Double, city: String) {
        val nearby = ChinaMuseums.MUSEUMS.filter { museum ->
            distanceKm(lat, lng, museum.latitude, museum.longitude) <= 1.0
        }

        if (nearby.isEmpty()) {
            // No museum within 1km, but we still have the city
            return
        }

        if (nearby.size == 1) {
            // Auto-select single museum
            showMuseumMatched(nearby.first())
            return
        }

        // Multiple museums — let user choose
        val names = nearby.map { "${it.name}（${it.city}）" }.toTypedArray()
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("发现多个博物馆")
            .setMessage("附近1公里内有多个博物馆，请选择：")
            .setItems(names) { _, which ->
                showMuseumMatched(nearby[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showMuseumMatched(museum: MuseumInfo) {
        binding.tvCurrentMuseum.text = "📍 ${museum.name}（${museum.city}）— 距您最近"
        Toast.makeText(requireContext(), "已定位到: ${museum.name}", Toast.LENGTH_LONG).show()
    }

    /** Haversine distance in km between two lat/lng points. */
    private fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val sinDLat = sin(dLat / 2.0)
        val sinDLng = sin(dLng / 2.0)
        val a = sinDLat * sinDLat + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sinDLng * sinDLng
        return r * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun extractCity(address: Address): String {
        val adminArea = address.adminArea ?: ""
        val locality = address.locality ?: ""
        val subAdmin = address.subAdminArea ?: ""
        return when {
            locality.isNotBlank() -> locality
            adminArea.isNotBlank() -> adminArea
            subAdmin.isNotBlank() -> subAdmin
            else -> ""
        }
    }

    private fun setupMuseumList() {
        binding.rvMuseums.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMuseums.adapter = MuseumAdapter(
            museums = ChinaMuseums.MUSEUMS,
            onClick = { museum ->
                val bundle = Bundle().apply { putInt("museum_id", museum.id) }
                findNavController().navigate(R.id.action_home_to_museum_detail, bundle)
            }
        )
    }

    private fun updateMuseumRanks() {
        val adapter = binding.rvMuseums.adapter ?: return
        for (i in 0 until adapter.itemCount) {
            val holder = binding.rvMuseums.findViewHolderForAdapterPosition(i)
            val rankView = holder?.itemView?.findViewById<android.widget.TextView>(R.id.museum_rank)
            rankView?.text = "${i + 1}"
        }
    }

    override fun onResume() {
        super.onResume()
        currentConfig = AiConfiguration.load(requireContext())
        applyConfigToUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
