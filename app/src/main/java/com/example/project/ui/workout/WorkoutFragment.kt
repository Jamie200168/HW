package com.example.project.ui.workout


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.Chronometer
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController

import com.example.project.R
import com.example.project.data.OfflineMapStyle
import com.example.project.data.RoutePoint
import com.example.project.data.TaiwanOfflineMap
import com.example.project.data.TrackingRepository
import com.example.project.data.WeatherRepository
import com.example.project.data.WorkoutDatabaseHelper
import com.example.project.service.TrackingService

import com.google.android.material.button.MaterialButton

import kotlinx.coroutines.launch

import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.location.Location

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource



class WorkoutFragment :
    Fragment(
        R.layout.fragment_workout
    ) {


    // =====================================================
    // ViewModel
    // =====================================================

    private val viewModel:
            WorkoutViewModel by viewModels()


    // =====================================================
    // 運動資料
    // =====================================================

    private var activityType =
        "走路"


    private var startLatitude =
        0.0


    private var startLongitude =
        0.0


    // =====================================================
    // 基本 UI
    // =====================================================

    private lateinit var tvActivityType:
            TextView


    private lateinit var chronometer:
            Chronometer


    private lateinit var tvDistance:
            TextView


    private lateinit var tvSteps:
            TextView


    private lateinit var btnStart:
            Button


    private lateinit var btnFinish:
            Button


    private lateinit var tvCoordinate:
            TextView


    // =====================================================
    // 上次運動紀錄
    // =====================================================

    private lateinit var tvLastWorkoutRecord:
            TextView


    private lateinit var database:
            WorkoutDatabaseHelper


    // =====================================================
    // 天氣 UI
    // =====================================================

    private lateinit var tvWeatherLocation:
            TextView


    private lateinit var tvWeatherStatus:
            TextView


    private lateinit var tvRain:
            TextView


    private lateinit var tvTemperature:
            TextView


    private lateinit var tvComfort:
            TextView


    private lateinit var tvWindDirection:
            TextView


    private lateinit var tvWindSpeed:
            TextView


    private lateinit var tvHumidity:
            TextView


    private lateinit var tvWeatherUpdatedAt:
            TextView


    private lateinit var weatherProgress:
            ProgressBar


    // =====================================================
    // MapLibre
    // =====================================================

    private lateinit var mapView:
            MapView


    private lateinit var tvMapStatus:
            TextView


    private lateinit var mapDownloadProgress:
            ProgressBar


    private lateinit var btnFollowLocation:
            MaterialButton


    private var mapLibreMap:
            MapLibreMap? =
        null


    private var mapStyleReady =
        false


    // =====================================================
    // 最新 GPS 路線
    // =====================================================

    private var latestRoute:
            List<RoutePoint> =
        emptyList()


    // =====================================================
    // 是否自動跟著 GPS
    // =====================================================

    private var followGps =
        true

    // =====================================================
    // 進入 Workout 時取得目前 GPS
    // =====================================================

    private lateinit var fusedLocationClient:
            FusedLocationProviderClient


    private var currentLocationTokenSource:
            CancellationTokenSource? =
        null


    private var initialLocationRequestRunning =
        false
    // =====================================================
    // 計時狀態
    // =====================================================

    private var chronometerRunning =
        false


    // =====================================================
    // 天氣更新
    // =====================================================

    private val weatherHandler =
        Handler(
            Looper.getMainLooper()
        )


    companion object {


        private const val WEATHER_INTERVAL =
            3L * 60L * 60L * 1000L


        private const val ROUTE_SOURCE =
            "workout-route-source"


        private const val ROUTE_LAYER =
            "workout-route-layer"


        private const val START_SOURCE =
            "workout-start-source"


        private const val START_LAYER =
            "workout-start-layer"


        private const val CURRENT_SOURCE =
            "workout-current-source"


        private const val CURRENT_LAYER =
            "workout-current-layer"
    }


    // =====================================================
    // 天氣 Runnable
    // =====================================================

    private val weatherRunnable =
        object : Runnable {


            override fun run() {


                refreshWeather()


                weatherHandler.postDelayed(
                    this,
                    WEATHER_INTERVAL
                )
            }
        }


    // =====================================================
    // 權限 Launcher
    // =====================================================
    // =====================================================
// 只負責進入頁面時取得目前位置
// 不會啟動 TrackingService
// =====================================================

    private val initialLocationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestMultiplePermissions()
        ) {


            if (
                hasLocationPermission()
            ) {


                requestInitialLocation()


            } else {


                if (
                    ::tvCoordinate.isInitialized
                ) {


                    tvCoordinate.text =
                        "座標：尚未允許定位權限"
                }


                Toast.makeText(
                    requireContext(),
                    "請允許定位權限，才能顯示目前位置",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestMultiplePermissions()
        ) {


            if (
                hasRequiredPermissions()
            ) {


                startTrackingService()


            } else {


                Toast.makeText(
                    requireContext(),
                    "請允許定位與活動辨識權限",
                    Toast.LENGTH_LONG
                ).show()
            }
        }


    // =====================================================
    // onCreate
    // =====================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {


        super.onCreate(
            savedInstanceState
        )


        MapLibre.getInstance(
            requireContext()
        )
    }


    // =====================================================
    // onViewCreated
    // =====================================================

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {


        super.onViewCreated(
            view,
            savedInstanceState
        )


        // =================================================
        // 接收上一頁資料
        // =================================================

        activityType =
            arguments?.getString(
                "activityType"
            ) ?: "走路"


        // =================================================
        // 運動進行中時，不允許切換成其他運動模式
        // =================================================

        val activeType =
            TrackingRepository
                .activeActivityType
                .value


        if (
            TrackingRepository.state.value.isTracking &&
            activeType != null &&
            activeType != activityType
        ) {

            Toast.makeText(
                requireContext(),
                "目前正在進行「$activeType」，請先結束運動後再切換模式",
                Toast.LENGTH_LONG
            ).show()

            findNavController().popBackStack()

            return
        }


        // 沒有運動進行中時，清除上一個畫面的暫存路線與即時數值。
        // 歷史紀錄已存進 SQLite，不會被這裡刪除。
        if (
            !TrackingRepository
                .state
                .value
                .isTracking
        ) {

            TrackingRepository.clear()
        }


        startLatitude =
            arguments?.getDouble(
                "startLatitude"
            ) ?: 0.0


        startLongitude =
            arguments?.getDouble(
                "startLongitude"
            ) ?: 0.0


        // =================================================
        // 基本 UI
        // =================================================

        tvActivityType =
            view.findViewById(
                R.id.tvActivityType
            )


        chronometer =
            view.findViewById(
                R.id.chronometer
            )


        tvDistance =
            view.findViewById(
                R.id.tvDistance
            )


        tvSteps =
            view.findViewById(
                R.id.tvSteps
            )


        btnStart =
            view.findViewById(
                R.id.btnStart
            )


        btnFinish =
            view.findViewById(
                R.id.btnFinish
            )


        tvCoordinate =
            view.findViewById(
                R.id.tvCoordinate
            )


        tvLastWorkoutRecord =
            view.findViewById(
                R.id.tvLastWorkoutRecord
            )


        database =
            WorkoutDatabaseHelper(
                requireContext()
            )


        // =================================================
        // 天氣 UI
        // =================================================

        weatherProgress =
            view.findViewById(
                R.id.weatherProgress
            )


        tvWeatherLocation =
            view.findViewById(
                R.id.tvWeatherLocation
            )


        tvWeatherStatus =
            view.findViewById(
                R.id.tvWeatherStatus
            )


        tvRain =
            view.findViewById(
                R.id.tvRain
            )


        tvTemperature =
            view.findViewById(
                R.id.tvTemperature
            )


        tvComfort =
            view.findViewById(
                R.id.tvComfort
            )


        tvWindDirection =
            view.findViewById(
                R.id.tvWindDirection
            )


        tvWindSpeed =
            view.findViewById(
                R.id.tvWindSpeed
            )


        tvHumidity =
            view.findViewById(
                R.id.tvHumidity
            )


        tvWeatherUpdatedAt =
            view.findViewById(
                R.id.tvWeatherUpdatedAt
            )


        // =================================================
        // MapLibre UI
        // =================================================

        mapView =
            view.findViewById(
                R.id.mapView
            )


        tvMapStatus =
            view.findViewById(
                R.id.tvMapStatus
            )


        mapDownloadProgress =
            view.findViewById(
                R.id.mapDownloadProgress
            )


        btnFollowLocation =
            view.findViewById(
                R.id.btnFollowLocation
            )
        // =================================================
        // Google Fused Location
        // =================================================

        fusedLocationClient =
            LocationServices
                .getFusedLocationProviderClient(
                    requireActivity()
                )

        // =================================================
        // MapView 建立
        // =================================================

        mapView.onCreate(
            savedInstanceState
        )


        setupMapTouch()


        // =================================================
        // 顯示運動種類
        // =================================================

        tvActivityType.text =
            when (
                activityType
            ) {


                "走路" ->
                    "🚶 走路"


                "登山" ->
                    "🥾 登山"


                "騎自行車" ->
                    "🚴 騎自行車"


                else ->
                    activityType
            }


        // =================================================
        // 顯示相同運動模式的上一筆紀錄
        // =================================================

        showLastWorkoutRecord()


        // =================================================
        // 運動進行中鎖定返回鍵
        // =================================================

        installBackLock()


        // =================================================
        // 初始座標
        // =================================================

        tvCoordinate.text =
            String.format(
                Locale.TAIWAN,
                "座標：%.6f, %.6f",
                startLatitude,
                startLongitude
            )


        // =================================================
        // 自行車不記錄步數
        // =================================================

        if (
            activityType ==
            "騎自行車"
        ) {


            tvSteps.text =
                "步數：不記錄"
        }


        setupChronometer()


        // =================================================
        // 準備離線地圖
        // =================================================

        prepareOfflineMap()


        // =================================================
        // 一進 Workout 就主動取得目前 GPS
        // =================================================

        requestInitialLocation()


        // =================================================
        // 開始運動
        // =================================================

        btnStart.setOnClickListener {


            checkPermissionsAndStart()
        }


        // =================================================
        // 結束運動
        // =================================================

        btnFinish.setOnClickListener {


            finishWorkout()
        }


        // =================================================
        // 回到定位
        // =================================================

        btnFollowLocation.setOnClickListener {


            followGps =
                true


            val lastPoint =
                TrackingRepository
                    .routePoints
                    .value
                    .lastOrNull()


            if (
                lastPoint != null
            ) {


                moveMapCameraToGps(
                    lastPoint.latitude,
                    lastPoint.longitude
                )


            } else if (
                startLatitude != 0.0 ||
                startLongitude != 0.0
            ) {


                moveMapCameraToGps(
                    startLatitude,
                    startLongitude
                )


            } else {


                // 尚未取得位置時
                // 按「回到定位」就重新要求 GPS

                requestInitialLocation()


                Toast.makeText(
                    requireContext(),
                    "正在重新取得 GPS 位置...",
                    Toast.LENGTH_SHORT
                ).show()


                return@setOnClickListener
            }


            Toast.makeText(
                requireContext(),
                "已回到目前位置",
                Toast.LENGTH_SHORT
            ).show()
        }


        observeWorkout()

        observeRoute()

        observeWeather()
    }


    // =====================================================
    // 運動進行中禁止返回其他運動模式
    // =====================================================

    private fun installBackLock() {


        requireActivity()
            .onBackPressedDispatcher
            .addCallback(
                viewLifecycleOwner,
                object :
                    OnBackPressedCallback(
                        true
                    ) {


                    override fun handleOnBackPressed() {


                        if (
                            TrackingRepository
                                .state
                                .value
                                .isTracking
                        ) {


                            val activeType =
                                TrackingRepository
                                    .activeActivityType
                                    .value
                                    ?: activityType


                            Toast.makeText(
                                requireContext(),
                                "「$activeType」運動正在進行中，請先按「結束運動」",
                                Toast.LENGTH_SHORT
                            ).show()


                            return
                        }


                        isEnabled =
                            false


                        findNavController()
                            .popBackStack()
                    }
                }
            )
    }


    // =====================================================
    // 上次運動紀錄
    //
    // 只顯示目前 activityType 的上一筆
    // 走路不會顯示登山 / 騎自行車
    // =====================================================

    private fun showLastWorkoutRecord() {


        if (
            !::database.isInitialized ||
            !::tvLastWorkoutRecord.isInitialized
        ) {

            return
        }


        val record =
            database
                .getLatestRecords()
                .firstOrNull {
                        item ->

                    item.activityType ==
                            activityType
                }


        if (
            record == null
        ) {


            tvLastWorkoutRecord.text =
                "目前還沒有「$activityType」的運動紀錄"


            return
        }


        val dateFormat =
            SimpleDateFormat(
                "yyyy/MM/dd HH:mm",
                Locale.TAIWAN
            )


        val dateText =
            dateFormat.format(
                Date(
                    record.startTimeMillis
                )
            )


        val hours =
            record.durationSeconds /
                    3600L


        val minutes =
            (
                    record.durationSeconds %
                            3600L
                    ) / 60L


        val seconds =
            record.durationSeconds %
                    60L


        val durationText =
            String.format(
                Locale.TAIWAN,
                "%02d:%02d:%02d",
                hours,
                minutes,
                seconds
            )


        val distanceKm =
            record.distanceMeters /
                    1000f


        val stepText =

            if (
                activityType ==
                "騎自行車"
            ) {

                "步數：不記錄"

            } else {

                "步數：${record.steps}"
            }


        tvLastWorkoutRecord.text =
            String.format(
                Locale.TAIWAN,
                "%s\n時間：%s\n距離：%.2f km\n%s",
                dateText,
                durationText,
                distanceKm,
                stepText
            )
    }


    // =====================================================
    // NestedScrollView / MapView 手勢衝突處理
    // =====================================================

    @Suppress("ClickableViewAccessibility")
    private fun setupMapTouch() {


        mapView.setOnTouchListener { view,
                                     event ->


            when (
                event.actionMasked
            ) {


                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_POINTER_DOWN -> {


                    view.parent
                        ?.requestDisallowInterceptTouchEvent(
                            true
                        )


                    // 使用者開始自己操作地圖
                    followGps =
                        false
                }


                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {


                    view.parent
                        ?.requestDisallowInterceptTouchEvent(
                            false
                        )
                }
            }


            false
        }
    }


    // =====================================================
    // 準備離線地圖
    // =====================================================

    private fun prepareOfflineMap() {


        if (
            TaiwanOfflineMap.isMapReady(
                requireContext()
            )
        ) {


            tvMapStatus.text =
                "台灣離線地圖已就緒"


            mapDownloadProgress.visibility =
                View.GONE


            mapView.visibility =
                View.VISIBLE


            btnFollowLocation.visibility =
                View.VISIBLE


            loadOfflineMap()


            return
        }


        tvMapStatus.text =
            "正在準備台灣離線地圖..."


        mapDownloadProgress.visibility =
            View.VISIBLE


        mapDownloadProgress.progress =
            0


        mapView.visibility =
            View.GONE


        btnFollowLocation.visibility =
            View.GONE


        viewLifecycleOwner
            .lifecycleScope
            .launch {


                val result =
                    TaiwanOfflineMap.prepareMap(
                        requireContext()
                    ) { progress ->


                        mapDownloadProgress.progress =
                            progress


                        tvMapStatus.text =
                            "正在準備台灣離線地圖：$progress%"
                    }


                result
                    .onSuccess {


                        mapDownloadProgress.visibility =
                            View.GONE


                        mapView.visibility =
                            View.VISIBLE


                        btnFollowLocation.visibility =
                            View.VISIBLE


                        tvMapStatus.text =
                            "台灣離線地圖已就緒"


                        loadOfflineMap()
                    }
                    .onFailure { error ->


                        mapDownloadProgress.visibility =
                            View.GONE


                        mapView.visibility =
                            View.GONE


                        btnFollowLocation.visibility =
                            View.GONE


                        tvMapStatus.text =
                            "地圖載入失敗：${error.message}"


                        Toast.makeText(
                            requireContext(),
                            "地圖載入失敗：${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
    }


    // =====================================================
    // 載入 MapLibre + PMTiles
    // =====================================================

    private fun loadOfflineMap() {


        val mapFile =
            TaiwanOfflineMap.getMapFile(
                requireContext()
            )


        if (
            !mapFile.exists()
        ) {


            tvMapStatus.text =
                "找不到 taiwan.pmtiles"


            return
        }


        mapView.getMapAsync { map ->


            mapLibreMap =
                map


            mapStyleReady =
                false


            setupMapGestures(
                map
            )


            val styleJson =
                OfflineMapStyle
                    .createStyleJson(
                        mapFile
                    )


            map.setStyle(

                Style.Builder()
                    .fromJson(
                        styleJson
                    )

            ) {


                mapStyleReady =
                    true


                tvMapStatus.text =
                    "台灣離線地圖已就緒"


                // 已經有運動路線就先畫出來
                updateMapRoute(
                    latestRoute
                )


                // 還沒有運動路線時
                // 使用剛取得的 GPS 位置
                if (
                    latestRoute.isEmpty()
                ) {


                    if (
                        startLatitude != 0.0 ||
                        startLongitude != 0.0
                    ) {


                        followGps =
                            true


                        showCurrentGpsMarker(
                            startLatitude,
                            startLongitude
                        )


                        moveMapCameraToGps(
                            startLatitude,
                            startLongitude
                        )


                    } else {


                        // 還沒有 GPS 時
                        // 先顯示整個台灣
                        map.cameraPosition =
                            CameraPosition
                                .Builder()
                                .target(
                                    LatLng(
                                        23.7,
                                        121.0
                                    )
                                )
                                .zoom(
                                    7.0
                                )
                                .bearing(
                                    0.0
                                )
                                .tilt(
                                    0.0
                                )
                                .build()
                    }
                }
            }
        }
    }
    // =====================================================
    // Google Maps 類似操作手勢
    // =====================================================

    private fun setupMapGestures(
        map: MapLibreMap
    ) {


        map.uiSettings.apply {


            isScrollGesturesEnabled =
                true


            isZoomGesturesEnabled =
                true


            isRotateGesturesEnabled =
                true


            isTiltGesturesEnabled =
                true


            isDoubleTapGesturesEnabled =
                true


            isQuickZoomGesturesEnabled =
                true


            isCompassEnabled =
                true
        }
    }


    // =====================================================
    // 地圖移到目前 GPS
    // =====================================================

    private fun moveMapCameraToGps(
        latitude: Double,
        longitude: Double
    ) {


        val map =
            mapLibreMap
                ?: return


        val oldCamera =
            map.cameraPosition


        var zoom =
            oldCamera.zoom


        if (
            zoom < 15.0
        ) {


            zoom =
                17.0
        }


        val newCamera =
            CameraPosition
                .Builder()
                .target(
                    LatLng(
                        latitude,
                        longitude
                    )
                )
                .zoom(
                    zoom
                )
                .bearing(
                    oldCamera.bearing
                )
                .tilt(
                    oldCamera.tilt
                )
                .build()


        map.animateCamera(

            CameraUpdateFactory
                .newCameraPosition(
                    newCamera
                ),

            500
        )
    }


    // =====================================================
    // Chronometer
    // =====================================================

    private fun setupChronometer() {


        chronometer.base =
            SystemClock.elapsedRealtime()


        chronometer.text =
            "00:00:00"


        chronometer.setOnChronometerTickListener { clock ->


            val elapsedMillis =
                SystemClock.elapsedRealtime() -
                        clock.base


            val seconds =
                if (
                    elapsedMillis > 0L
                ) {


                    elapsedMillis / 1000L


                } else {


                    0L
                }


            clock.text =
                formatTime(
                    seconds
                )
        }
    }


    // =====================================================
    // 定位權限
    // =====================================================
    // =====================================================
// 進入 Workout 後主動取得目前 GPS
// =====================================================

    private fun requestInitialLocation() {


        if (
            initialLocationRequestRunning
        ) {


            return
        }


        // =================================================
        // 還沒有定位權限
        // =================================================

        if (
            !hasLocationPermission()
        ) {


            initialLocationPermissionLauncher.launch(

                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )


            return
        }


        if (
            !::fusedLocationClient.isInitialized
        ) {


            return
        }


        initialLocationRequestRunning =
            true


        if (
            ::tvCoordinate.isInitialized
        ) {


            tvCoordinate.text =
                "座標：正在取得 GPS 定位..."
        }


        // =================================================
        // 如果之前有尚未完成的 GPS request
        // 先取消
        // =================================================

        currentLocationTokenSource
            ?.cancel()


        val tokenSource =
            CancellationTokenSource()


        currentLocationTokenSource =
            tokenSource


        try {


            // =================================================
            // 主動要求高精確度目前位置
            // =================================================

            fusedLocationClient
                .getCurrentLocation(

                    Priority.PRIORITY_HIGH_ACCURACY,

                    tokenSource.token
                )
                .addOnSuccessListener { location ->


                    if (
                        !isAdded ||
                        view == null
                    ) {


                        initialLocationRequestRunning =
                            false


                        return@addOnSuccessListener
                    }


                    if (
                        location != null
                    ) {


                        initialLocationRequestRunning =
                            false


                        applyInitialLocation(
                            location
                        )


                    } else {


                        // =====================================
                        // 如果即時定位暫時拿不到
                        // 再嘗試手機最近一次位置
                        // =====================================

                        getLastKnownLocation()
                    }
                }
                .addOnFailureListener { error ->


                    initialLocationRequestRunning =
                        false


                    if (
                        !isAdded ||
                        view == null
                    ) {


                        return@addOnFailureListener
                    }


                    tvCoordinate.text =
                        "座標：GPS 定位失敗"


                    Toast.makeText(
                        requireContext(),
                        "GPS 定位失敗：${error.message ?: "請確認手機定位已開啟"}",
                        Toast.LENGTH_LONG
                    ).show()
                }


        } catch (
            e: SecurityException
        ) {


            initialLocationRequestRunning =
                false


            tvCoordinate.text =
                "座標：沒有定位權限"


            Toast.makeText(
                requireContext(),
                "目前沒有定位權限",
                Toast.LENGTH_LONG
            ).show()
        }
    }


// =====================================================
// 即時定位拿不到時
// 嘗試取得手機最近一次已知位置
// =====================================================

    private fun getLastKnownLocation() {


        if (
            !hasLocationPermission()
        ) {


            initialLocationRequestRunning =
                false


            return
        }


        try {


            fusedLocationClient
                .lastLocation
                .addOnSuccessListener { location ->


                    initialLocationRequestRunning =
                        false


                    if (
                        !isAdded ||
                        view == null
                    ) {


                        return@addOnSuccessListener
                    }


                    if (
                        location != null
                    ) {


                        applyInitialLocation(
                            location
                        )


                    } else {


                        tvCoordinate.text =
                            "座標：目前無法取得 GPS"


                        Toast.makeText(
                            requireContext(),
                            "目前無法取得 GPS，請確認手機「位置」功能已開啟，並到戶外或窗邊再試一次",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .addOnFailureListener {


                    initialLocationRequestRunning =
                        false


                    if (
                        !isAdded ||
                        view == null
                    ) {


                        return@addOnFailureListener
                    }


                    tvCoordinate.text =
                        "座標：目前無法取得 GPS"
                }


        } catch (
            e: SecurityException
        ) {


            initialLocationRequestRunning =
                false
        }
    }


// =====================================================
// 成功取得目前位置
// =====================================================

    private fun applyInitialLocation(
        location: Location
    ) {


        val latitude =
            location.latitude


        val longitude =
            location.longitude


        // =================================================
        // 存起來
        // 之後天氣與地圖都可以使用
        // =================================================

        startLatitude =
            latitude


        startLongitude =
            longitude


        // =================================================
        // 更新畫面座標
        // =================================================

        tvCoordinate.text =
            String.format(
                Locale.TAIWAN,
                "座標：%.6f, %.6f",
                latitude,
                longitude
            )


        // =================================================
        // 地圖跟到現在的位置
        // =================================================

        followGps =
            true


        if (
            mapStyleReady
        ) {


            showCurrentGpsMarker(
                latitude,
                longitude
            )


            moveMapCameraToGps(
                latitude,
                longitude
            )
        }


        // =================================================
        // 有位置後立即更新所在地天氣
        // =================================================

        WeatherRepository.refreshWeather(
            requireContext(),
            latitude,
            longitude
        )
    }


// =====================================================
// 地圖顯示目前 GPS 紅點
// =====================================================

    private fun showCurrentGpsMarker(
        latitude: Double,
        longitude: Double
    ) {


        if (
            !mapStyleReady
        ) {


            return
        }


        val style =
            mapLibreMap
                ?.style
                ?: return


        updateMapPoint(

            style =
                style,

            sourceId =
                CURRENT_SOURCE,

            layerId =
                CURRENT_LAYER,

            latitude =
                latitude,

            longitude =
                longitude,

            color =
                Color.rgb(
                    244,
                    67,
                    54
                )
        )
    }

    private fun hasLocationPermission():
            Boolean {


        val fine =
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED


        val coarse =
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED


        return fine || coarse
    }


    // =====================================================
    // 活動辨識權限
    // =====================================================

    private fun hasActivityPermission():
            Boolean {


        if (
            activityType ==
            "騎自行車"
        ) {


            return true
        }


        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.Q
        ) {


            return true
        }


        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACTIVITY_RECOGNITION
        ) ==
                PackageManager.PERMISSION_GRANTED
    }


    // =====================================================
    // 所有必要權限
    // =====================================================

    private fun hasRequiredPermissions():
            Boolean {


        return hasLocationPermission() &&
                hasActivityPermission()
    }


    // =====================================================
    // 檢查權限
    // =====================================================

    private fun checkPermissionsAndStart() {


        if (
            hasRequiredPermissions()
        ) {


            startTrackingService()


            return
        }


        val permissions =
            mutableListOf<String>()


        if (
            !hasLocationPermission()
        ) {


            permissions.add(
                Manifest.permission.ACCESS_FINE_LOCATION
            )


            permissions.add(
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }


        if (
            !hasActivityPermission() &&
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {


            permissions.add(
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        }


        permissionLauncher.launch(
            permissions.toTypedArray()
        )
    }


    // =====================================================
    // TrackingService
    // =====================================================

    private fun startTrackingService() {


        val activeState =
            TrackingRepository
                .state
                .value


        val activeType =
            TrackingRepository
                .activeActivityType
                .value


        if (
            activeState.isTracking
        ) {


            if (
                activeType ==
                activityType
            ) {


                Toast.makeText(
                    requireContext(),
                    "「$activityType」已經在記錄中",
                    Toast.LENGTH_SHORT
                ).show()


            } else {


                Toast.makeText(
                    requireContext(),
                    "目前正在進行「${activeType ?: "其他運動"}」，請先結束後再切換",
                    Toast.LENGTH_LONG
                ).show()
            }


            return
        }


        val intent =
            Intent(
                requireContext(),
                TrackingService::class.java
            )


        intent.action =
            TrackingService.ACTION_START


        intent.putExtra(
            TrackingService.EXTRA_ACTIVITY_TYPE,
            activityType
        )


        ContextCompat.startForegroundService(
            requireContext(),
            intent
        )
    }


    // =====================================================
    // 監聽運動資料
    // =====================================================

    private fun observeWorkout() {


        viewLifecycleOwner
            .lifecycleScope
            .launch {


                viewLifecycleOwner
                    .repeatOnLifecycle(
                        Lifecycle.State.STARTED
                    ) {


                        viewModel
                            .workoutState
                            .collect { state ->


                                tvDistance.text =
                                    String.format(
                                        Locale.TAIWAN,
                                        "距離：%.1f m",
                                        state.distanceMeters
                                    )


                                if (
                                    activityType !=
                                    "騎自行車"
                                ) {


                                    tvSteps.text =
                                        "步數：${state.steps}"
                                }


                                if (
                                    state.isTracking
                                ) {


                                    if (
                                        !chronometerRunning
                                    ) {


                                        chronometer.base =
                                            SystemClock.elapsedRealtime() -
                                                    (
                                                            state.elapsedSeconds *
                                                                    1000L
                                                            )


                                        chronometer.start()


                                        chronometerRunning =
                                            true
                                    }


                                } else {


                                    if (
                                        chronometerRunning
                                    ) {


                                        chronometer.stop()


                                        chronometerRunning =
                                            false
                                    }


                                    if (
                                        state.elapsedSeconds ==
                                        0L
                                    ) {


                                        chronometer.base =
                                            SystemClock.elapsedRealtime()


                                        chronometer.text =
                                            "00:00:00"
                                    }
                                }


                                btnStart.isEnabled =
                                    !state.isTracking


                                btnFinish.isEnabled =
                                    state.isTracking
                            }
                    }
            }
    }


    // =====================================================
    // GPS 路線 Observer
    // =====================================================

    private fun observeRoute() {


        viewLifecycleOwner
            .lifecycleScope
            .launch {


                viewLifecycleOwner
                    .repeatOnLifecycle(
                        Lifecycle.State.STARTED
                    ) {


                        TrackingRepository
                            .routePoints
                            .collect { route:
                                       List<RoutePoint> ->


                                latestRoute =
                                    route


                                updateMapRoute(
                                    route
                                )
                            }
                    }
            }
    }


    // =====================================================
    // GPS 路線畫到 MapLibre
    // =====================================================

    private fun updateMapRoute(
        route: List<RoutePoint>
    ) {


        if (
            !mapStyleReady
        ) {


            return
        }


        val map =
            mapLibreMap
                ?: return


        val style =
            map.style
                ?: return


        if (
            route.isEmpty()
        ) {


            return
        }


        // =================================================
        // 起點
        // =================================================

        val firstPoint =
            route.first()


        updateMapPoint(

            style =
                style,

            sourceId =
                START_SOURCE,

            layerId =
                START_LAYER,

            latitude =
                firstPoint.latitude,

            longitude =
                firstPoint.longitude,

            color =
                Color.rgb(
                    76,
                    175,
                    80
                )
        )


        // =================================================
        // 目前位置
        // =================================================

        val lastPoint =
            route.last()


        updateMapPoint(

            style =
                style,

            sourceId =
                CURRENT_SOURCE,

            layerId =
                CURRENT_LAYER,

            latitude =
                lastPoint.latitude,

            longitude =
                lastPoint.longitude,

            color =
                Color.rgb(
                    244,
                    67,
                    54
                )
        )


        // =================================================
        // 至少兩點才能畫路線
        // =================================================

        if (
            route.size >= 2
        ) {


            val points =
                route.map { routePoint ->


                    Point.fromLngLat(
                        routePoint.longitude,
                        routePoint.latitude
                    )
                }


            val routeLine =
                LineString.fromLngLats(
                    points
                )


            val oldSource =
                style.getSourceAs<GeoJsonSource>(
                    ROUTE_SOURCE
                )


            if (
                oldSource == null
            ) {


                val routeSource =
                    GeoJsonSource(
                        ROUTE_SOURCE,
                        routeLine
                    )


                style.addSource(
                    routeSource
                )


                val routeLayer =
                    LineLayer(
                        ROUTE_LAYER,
                        ROUTE_SOURCE
                    )
                        .withProperties(

                            PropertyFactory.lineColor(
                                Color.rgb(
                                    33,
                                    150,
                                    243
                                )
                            ),

                            PropertyFactory.lineWidth(
                                7f
                            ),

                            PropertyFactory.lineOpacity(
                                0.95f
                            )
                        )


                style.addLayer(
                    routeLayer
                )


            } else {


                oldSource.setGeoJson(
                    routeLine
                )
            }
        }


        // =================================================
        // 更新座標
        // =================================================

        tvCoordinate.text =
            String.format(
                Locale.TAIWAN,
                "座標：%.6f, %.6f",
                lastPoint.latitude,
                lastPoint.longitude
            )


        // =================================================
        // 自動跟著 GPS
        // =================================================

        if (
            followGps
        ) {


            moveMapCameraToGps(
                lastPoint.latitude,
                lastPoint.longitude
            )
        }
    }


    // =====================================================
    // 起點 / 目前位置圓點
    // =====================================================

    private fun updateMapPoint(

        style: Style,

        sourceId: String,

        layerId: String,

        latitude: Double,

        longitude: Double,

        color: Int

    ) {


        val point =
            Point.fromLngLat(
                longitude,
                latitude
            )


        val source =
            style.getSourceAs<GeoJsonSource>(
                sourceId
            )


        if (
            source == null
        ) {


            val newSource =
                GeoJsonSource(
                    sourceId,
                    point
                )


            style.addSource(
                newSource
            )


            val layer =
                CircleLayer(
                    layerId,
                    sourceId
                )
                    .withProperties(

                        PropertyFactory.circleRadius(
                            7f
                        ),

                        PropertyFactory.circleColor(
                            color
                        ),

                        PropertyFactory.circleStrokeColor(
                            Color.WHITE
                        ),

                        PropertyFactory.circleStrokeWidth(
                            2.5f
                        )
                    )


            style.addLayer(
                layer
            )


        } else {


            source.setGeoJson(
                point
            )
        }
    }


    // =====================================================
    // 天氣更新
    // =====================================================

    private fun refreshWeather() {


        val latestPoint =
            TrackingRepository
                .routePoints
                .value
                .lastOrNull()


        val latitude =
            latestPoint?.latitude
                ?: startLatitude


        val longitude =
            latestPoint?.longitude
                ?: startLongitude


        if (
            latitude == 0.0 &&
            longitude == 0.0
        ) {


            return
        }


        WeatherRepository.refreshWeather(
            requireContext(),
            latitude,
            longitude
        )
    }


    // =====================================================
    // 天氣 Observer
    // =====================================================

    private fun observeWeather() {


        viewLifecycleOwner
            .lifecycleScope
            .launch {


                viewLifecycleOwner
                    .repeatOnLifecycle(
                        Lifecycle.State.STARTED
                    ) {


                        WeatherRepository
                            .state
                            .collect { state ->


                                weatherProgress.visibility =
                                    if (
                                        state.loading
                                    ) {


                                        View.VISIBLE


                                    } else {


                                        View.GONE
                                    }


                                val response =
                                    state.data


                                if (
                                    response != null
                                ) {


                                    val weatherData =
                                        response.weatherData


                                    tvWeatherLocation.text =
                                        "地區：${
                                            response.locationName
                                                ?: "--"
                                        }"


                                    tvWeatherStatus.text =
                                        "天氣狀態：${
                                            weatherData
                                                ?.weatherDesc
                                                ?: "--"
                                        }"


                                    tvRain.text =
                                        "降雨機率：${
                                            weatherData
                                                ?.rainProbability
                                                ?: "--"
                                        }"


                                    tvTemperature.text =
                                        "溫度：${
                                            weatherData
                                                ?.temperature
                                                ?: "--"
                                        }"


                                    tvComfort.text =
                                        "舒適度：${
                                            weatherData
                                                ?.comfort
                                                ?: "--"
                                        }"


                                    tvWindDirection.text =
                                        "風向：${
                                            weatherData
                                                ?.windDirection
                                                ?: "--"
                                        }"


                                    tvWindSpeed.text =
                                        "風速：${
                                            weatherData
                                                ?.windSpeed
                                                ?: "--"
                                        }"


                                    tvHumidity.text =
                                        "相對濕度：${
                                            weatherData
                                                ?.humidity
                                                ?: "--"
                                        }"


                                    tvWeatherUpdatedAt.text =
                                        "更新時間：${
                                            response.fetchTime
                                                ?: "--"
                                        }"
                                }


                                if (
                                    state.error != null &&
                                    response == null
                                ) {


                                    tvWeatherStatus.text =
                                        "天氣狀態：取得失敗"


                                    tvWeatherUpdatedAt.text =
                                        "錯誤：${state.error}"
                                }
                            }
                    }
            }
    }


    // =====================================================
    // 結束運動
    // =====================================================

    private fun finishWorkout() {


        val state =
            viewModel.workoutState.value


        val finalTime =
            if (
                chronometerRunning
            ) {


                (
                        SystemClock.elapsedRealtime() -
                                chronometer.base
                        ) / 1000L


            } else {


                state.elapsedSeconds
            }


        chronometer.stop()


        chronometerRunning =
            false


        val stopIntent =
            Intent(
                requireContext(),
                TrackingService::class.java
            )


        stopIntent.action =
            TrackingService.ACTION_STOP


        requireContext()
            .startService(
                stopIntent
            )


        findNavController()
            .navigate(

                R.id.summaryFragment,

                bundleOf(

                    "activityType" to
                            activityType,

                    "steps" to
                            state.steps,

                    "distance" to
                            state.distanceMeters,

                    "time" to
                            finalTime
                )
            )
    }


    // =====================================================
    // 時間格式
    // =====================================================

    private fun formatTime(
        totalSeconds: Long
    ): String {


        val hours =
            totalSeconds / 3600L


        val minutes =
            (
                    totalSeconds %
                            3600L
                    ) / 60L


        val seconds =
            totalSeconds % 60L


        return String.format(
            Locale.TAIWAN,
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }


    // =====================================================
    // MapView Lifecycle
    // =====================================================

    override fun onStart() {


        super.onStart()


        if (
            ::mapView.isInitialized
        ) {


            mapView.onStart()
        }


        weatherHandler.removeCallbacks(
            weatherRunnable
        )


        weatherHandler.post(
            weatherRunnable
        )
    }


    override fun onResume() {


        super.onResume()


        if (
            ::mapView.isInitialized
        ) {


            mapView.onResume()
        }


        if (
            ::database.isInitialized &&
            ::tvLastWorkoutRecord.isInitialized
        ) {


            showLastWorkoutRecord()
        }
    }


    override fun onPause() {


        if (
            ::mapView.isInitialized
        ) {


            mapView.onPause()
        }


        super.onPause()
    }


    override fun onStop() {


        weatherHandler.removeCallbacks(
            weatherRunnable
        )


        if (
            ::mapView.isInitialized
        ) {


            mapView.onStop()
        }


        super.onStop()
    }


    override fun onLowMemory() {


        super.onLowMemory()


        if (
            ::mapView.isInitialized
        ) {


            mapView.onLowMemory()
        }
    }


    override fun onSaveInstanceState(
        outState: Bundle
    ) {


        super.onSaveInstanceState(
            outState
        )


        if (
            ::mapView.isInitialized
        ) {


            mapView.onSaveInstanceState(
                outState
            )
        }
    }


    override fun onDestroyView() {


        weatherHandler.removeCallbacks(
            weatherRunnable
        )


        // 取消尚未完成的單次 GPS 定位
        currentLocationTokenSource
            ?.cancel()


        currentLocationTokenSource =
            null


        initialLocationRequestRunning =
            false


        if (
            ::chronometer.isInitialized
        ) {


            chronometer.stop()
        }


        if (
            ::mapView.isInitialized
        ) {


            mapView.onDestroy()
        }


        mapLibreMap =
            null


        mapStyleReady =
            false


        latestRoute =
            emptyList()


        super.onDestroyView()
    }
}
