package com.yiyi.xposed

import android.content.Context
import android.location.Geocoder
import android.os.Bundle
import android.os.StrictMode
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.yiyi.xposed.model.FakeLocationConfig
import com.yiyi.xposed.util.PrefsHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var editSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var searchResults: ListView
    private lateinit var textLat: TextView
    private lateinit var textLon: TextView
    private lateinit var textAddress: TextView
    private lateinit var editAccuracy: EditText
    private lateinit var editAltitude: EditText
    private lateinit var editRandom: EditText
    private lateinit var switchWifi: SwitchMaterial
    private lateinit var switchCell: SwitchMaterial
    private lateinit var switchGnss: SwitchMaterial
    private lateinit var btnToggle: Button

    private var marker: Marker? = null
    private var currentLat: Double = 28.0004
    private var currentLon: Double = 119.1382
    private var currentAddress: String = ""
    private var isActive = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val AMAP_KEY = "77307996c1d945194fdafea3c683ce5d"
    private val USER_AGENT = "YiYiXposed/1.0"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid配置
        Configuration.getInstance().userAgentValue = USER_AGENT
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        initViews()
        initMap()
        loadSavedConfig()

        // 默认定位到龙泉市
        updateLocation(GeoPoint(currentLat, currentLon))
    }

    private fun initViews() {
        editSearch = findViewById(R.id.editSearch)
        btnSearch = findViewById(R.id.btnSearch)
        searchResults = findViewById(R.id.searchResults)
        textLat = findViewById(R.id.textLat)
        textLon = findViewById(R.id.textLon)
        textAddress = findViewById(R.id.textAddress)
        editAccuracy = findViewById(R.id.editAccuracy)
        editAltitude = findViewById(R.id.editAltitude)
        editRandom = findViewById(R.id.editRandom)
        switchWifi = findViewById(R.id.switchWifi)
        switchCell = findViewById(R.id.switchCell)
        switchGnss = findViewById(R.id.switchGnss)
        btnToggle = findViewById(R.id.btnToggle)

        btnSearch.setOnClickListener {
            val query = editSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                searchLocation(query)
            }
        }

        btnToggle.setOnClickListener {
            toggleMock()
        }
    }

    private fun initMap() {
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(currentLat, currentLon))

        // 点击地图选择位置
        val eventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                updateLocation(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                updateLocation(p)
                return true
            }
        }
        map.overlays.add(MapEventsOverlay(eventsReceiver))
    }

    private fun updateLocation(point: GeoPoint) {
        currentLat = point.latitude
        currentLon = point.longitude

        // 更新标记
        marker?.let { map.overlays.remove(it) }
        marker = Marker(map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "目标位置"
        }
        map.overlays.add(marker)
        map.invalidate()

        // 更新文本显示
        textLat.text = "纬度: ${String.format("%.6f", currentLat)}"
        textLon.text = "经度: ${String.format("%.6f", currentLon)}"

        // 反向地理编码
        reverseGeocode(currentLat, currentLon)
    }

    // ========================================
    // 搜索（高德REST API）
    // ========================================
    private fun searchLocation(query: String) {
        Thread {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://restapi.amap.com/v3/place/text" +
                        "?keywords=$encodedQuery" +
                        "&key=$AMAP_KEY" +
                        "&offset=10" +
                        "&page=1" +
                        "&extensions=base"

                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@Thread
                val json = JSONObject(body)

                if (json.getInt("status") != 1) {
                    runOnUiThread { Toast.makeText(this, "搜索失败", Toast.LENGTH_SHORT).show() }
                    return@Thread
                }

                val pois = json.getJSONArray("pois")
                val results = mutableListOf<Pair<String, GeoPoint>>()

                for (i in 0 until pois.length()) {
                    val poi = pois.getJSONObject(i)
                    val name = poi.getString("name")
                    val address = poi.optString("address", "")
                    val location = poi.getString("location")
                    val parts = location.split(",")
                    if (parts.size == 2) {
                        val lon = parts[0].toDouble()
                        val lat = parts[1].toDouble()
                        // GCJ-02 → WGS-84
                        val (wgsLat, wgsLon) = gcj02ToWgs84(lat, lon)
                        results.add(Pair("$name ($address)", GeoPoint(wgsLat, wgsLon)))
                    }
                }

                runOnUiThread {
                    if (results.isEmpty()) {
                        Toast.makeText(this, "未找到结果", Toast.LENGTH_SHORT).show()
                    } else {
                        showSearchResults(results)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "搜索错误: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun showSearchResults(results: List<Pair<String, GeoPoint>>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, results.map { it.first })
        searchResults.adapter = adapter
        searchResults.visibility = View.VISIBLE

        searchResults.setOnItemClickListener { _, _, position, _ ->
            val (_, point) = results[position]
            updateLocation(point)
            map.controller.animateTo(point)
            searchResults.visibility = View.GONE
        }
    }

    // ========================================
    // 反向地理编码
    // ========================================
    private fun reverseGeocode(lat: Double, lon: Double) {
        Thread {
            try {
                // WGS-84 → GCJ-02 for AMap API
                val (gcjLat, gcjLon) = wgs84ToGcj02(lat, lon)
                val location = "$gcjLon,$gcjLat"
                val url = "https://restapi.amap.com/v3/geocode/regeo" +
                        "?location=$location" +
                        "&key=$AMAP_KEY" +
                        "&radius=100" +
                        "&extensions=base"

                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@Thread
                val json = JSONObject(body)

                if (json.getInt("status") == 1) {
                    val regeocode = json.getJSONObject("regeocode")
                    val formattedAddress = regeocode.getString("formatted_address")
                    currentAddress = formattedAddress
                    runOnUiThread { textAddress.text = "地址: $formattedAddress" }
                }
            } catch (e: Exception) {
                // 静默失败
            }
        }.start()
    }

    // ========================================
    // 启动/停止模拟
    // ========================================
    private fun toggleMock() {
        isActive = !isActive

        if (isActive) {
            // 保存配置（启用状态）
            val config = FakeLocationConfig(
                enabled = true,
                latitude = currentLat,
                longitude = currentLon,
                altitude = editAltitude.text.toString().toDoubleOrNull() ?: 50.0,
                accuracy = editAccuracy.text.toString().toFloatOrNull() ?: 15.0f,
                randomRadius = editRandom.text.toString().toIntOrNull() ?: 0,
                wifiSpoof = switchWifi.isChecked,
                cellSpoof = switchCell.isChecked,
                gnssSpoof = switchGnss.isChecked
            )
            PrefsHelper.save(this, config)

            btnToggle.text = "停止模拟"
            btnToggle.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            Toast.makeText(this, "模拟已启动\n在VirtualApp中打开钉钉即可生效", Toast.LENGTH_LONG).show()
        } else {
            // 保存配置（禁用状态）
            val config = PrefsHelper.loadFromUI(this).copy(enabled = false)
            PrefsHelper.save(this, config)

            btnToggle.text = getString(R.string.btn_start)
            btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500))
            Toast.makeText(this, "模拟已停止", Toast.LENGTH_SHORT).show()
        }
    }

    // ========================================
    // 加载已保存的配置
    // ========================================
    private fun loadSavedConfig() {
        val config = PrefsHelper.loadFromUI(this)
        if (config.latitude != 0.0 && config.longitude != 0.0) {
            currentLat = config.latitude
            currentLon = config.longitude
        }
        editAccuracy.setText(config.accuracy.toInt().toString())
        editAltitude.setText(config.altitude.toInt().toString())
        editRandom.setText(config.randomRadius.toString())
        switchWifi.isChecked = config.wifiSpoof
        switchCell.isChecked = config.cellSpoof
        switchGnss.isChecked = config.gnssSpoof

        if (config.enabled) {
            isActive = true
            btnToggle.text = "停止模拟"
            btnToggle.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        // 保存当前配置
        val config = FakeLocationConfig(
            enabled = isActive,
            latitude = currentLat,
            longitude = currentLon,
            altitude = editAltitude.text.toString().toDoubleOrNull() ?: 50.0,
            accuracy = editAccuracy.text.toString().toFloatOrNull() ?: 15.0f,
            randomRadius = editRandom.text.toString().toIntOrNull() ?: 0,
            wifiSpoof = switchWifi.isChecked,
            cellSpoof = switchCell.isChecked,
            gnssSpoof = switchGnss.isChecked
        )
        PrefsHelper.save(this, config)
    }

    // ========================================
    // 坐标转换（简化版，完整版在CoordTransform中）
    // ========================================
    private fun wgs84ToGcj84(wgsLat: Double, wgsLon: Double): Pair<Double, Double> {
        return com.yiyi.xposed.util.CoordTransform.wgs84ToGcj02(wgsLat, wgsLon)
    }

    private fun gcj02ToWgs84(gcjLat: Double, gcjLon: Double): Pair<Double, Double> {
        return com.yiyi.xposed.util.CoordTransform.gcj02ToWgs84(gcjLat, gcjLon)
    }

    private fun wgs84ToGcj02(wgsLat: Double, wgsLon: Double): Pair<Double, Double> {
        return com.yiyi.xposed.util.CoordTransform.wgs84ToGcj02(wgsLat, wgsLon)
    }
}
