package com.example.project.ui.history


import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController

import com.example.project.R
import com.example.project.data.ActivityHistoryRepository
import com.example.project.data.OfflineMapStyle
import com.example.project.data.TaiwanOfflineMap
import com.example.project.data.WorkoutDatabaseHelper
import com.example.project.data.WorkoutRecord
import com.example.project.network.ActivityDetail

import kotlinx.coroutines.launch

import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
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
import java.util.TimeZone


class HistoryDetailFragment :
    Fragment(
        R.layout.fragment_history_detail
    ) {


    // =====================================================
    // SQLite
    // =====================================================

    private lateinit var database:
            WorkoutDatabaseHelper


    // =====================================================
    // Detail UI
    // =====================================================

    private lateinit var tvActivity:
            TextView


    private lateinit var tvDate:
            TextView


    private lateinit var tvStart:
            TextView


    private lateinit var tvEnd:
            TextView


    private lateinit var tvDuration:
            TextView


    private lateinit var tvDistance:
            TextView


    private lateinit var tvSteps:
            TextView


    // =====================================================
    // Map UI
    // =====================================================

    private lateinit var mapView:
            MapView


    private lateinit var tvMapStatus:
            TextView


    private lateinit var mapProgress:
            ProgressBar


    private var mapLibreMap:
            MapLibreMap? =
        null


    private var mapStyleReady =
        false


    // =====================================================
    // 上一頁傳入
    // =====================================================

    private var historySource =
        "local"


    private var recordId =
        -1L


    private var activityId =
        -1L


    // =====================================================
    // 地圖真正使用的簡化 GPS Point
    // =====================================================

    private data class HistoryMapPoint(

        val latitude: Double,

        val longitude: Double
    )


    private var latestMapPoints:
            List<HistoryMapPoint> =
        emptyList()


    // =====================================================
    // Map Source / Layer ID
    // =====================================================

    companion object {

        private const val ROUTE_SOURCE =
            "history-route-source"


        private const val ROUTE_LAYER =
            "history-route-layer"


        private const val START_SOURCE =
            "history-start-source"


        private const val START_LAYER =
            "history-start-layer"


        private const val END_SOURCE =
            "history-end-source"


        private const val END_LAYER =
            "history-end-layer"
    }


    // =====================================================
    // MapLibre 初始化
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
        // SQLite
        // =================================================

        database =
            WorkoutDatabaseHelper(
                requireContext()
            )


        // =================================================
        // Detail UI
        // =================================================

        val btnBack =
            view.findViewById<Button>(
                R.id.btnBackHistory
            )


        tvActivity =
            view.findViewById(
                R.id.tvDetailActivity
            )


        tvDate =
            view.findViewById(
                R.id.tvDetailDate
            )


        tvStart =
            view.findViewById(
                R.id.tvDetailStart
            )


        tvEnd =
            view.findViewById(
                R.id.tvDetailEnd
            )


        tvDuration =
            view.findViewById(
                R.id.tvDetailDuration
            )


        tvDistance =
            view.findViewById(
                R.id.tvDetailDistance
            )


        tvSteps =
            view.findViewById(
                R.id.tvDetailSteps
            )


        // =================================================
        // Map UI
        // =================================================

        mapView =
            view.findViewById(
                R.id.historyMapView
            )


        tvMapStatus =
            view.findViewById(
                R.id.tvHistoryMapStatus
            )


        mapProgress =
            view.findViewById(
                R.id.historyMapProgress
            )


        // =================================================
        // MapView lifecycle
        // =================================================

        mapView.onCreate(
            savedInstanceState
        )


        setupMapTouch()


        // =================================================
        // 返回 History
        // =================================================

        btnBack
            .setOnClickListener {


                findNavController()
                    .popBackStack()
            }


        // =================================================
        // 上頁傳入參數
        // =================================================

        historySource =
            arguments
                ?.getString(
                    "historySource"
                )
                ?: "local"


        recordId =
            arguments
                ?.getLong(
                    "recordId",
                    -1L
                )
                ?: -1L


        activityId =
            arguments
                ?.getLong(
                    "activityId",
                    -1L
                )
                ?: -1L


        // =================================================
        // Loading
        // =================================================

        showLoading()


        // =================================================
        // 先準備離線地圖
        // =================================================

        prepareOfflineMap()


        // =================================================
        // 再載入運動資料
        //
        // 地圖和 API 哪一個先完成都沒關係。
        //
        // latestMapPoints 會暫存 GPS。
        // =================================================

        when (
            historySource
        ) {


            "server" ->
                loadServerDetail()


            else ->
                loadLocalDetail()
        }
    }


    // =====================================================
    // Server 詳細資料
    // =====================================================

    private fun loadServerDetail() {


        if (
            activityId <= 0L
        ) {


            if (
                recordId > 0L
            ) {

                loadLocalDetail()

            } else {

                showNotFound()

                showNoRoute(
                    "沒有可使用的運動紀錄 ID"
                )
            }


            return
        }


        ActivityHistoryRepository
            .loadActivityDetail(

                context =
                    requireContext()
                        .applicationContext,

                activityId =
                    activityId,

                onSuccess = {
                        response ->


                    if (
                        !isAdded
                    ) {

                        return@loadActivityDetail
                    }


                    val activity =
                        response.activity


                    if (
                        activity == null
                    ) {


                        fallbackToLocal(
                            "伺服器沒有回傳此筆運動詳細資料"
                        )


                        return@loadActivityDetail
                    }


                    val localRecord =

                        if (
                            recordId > 0L
                        ) {

                            database
                                .getRecordById(
                                    recordId
                                )

                        } else {

                            null
                        }


                    // =====================================
                    // 顯示文字資料
                    // =====================================

                    showServerDetail(

                        activity =
                            activity,

                        localRecord =
                            localRecord
                    )


                    // =====================================
                    // Server GPS
                    //
                    // 依 sequence_order 排序。
                    // =====================================

                    val serverMapPoints =
                        response
                            .gpsPoints
                            .sortedBy {
                                it.sequenceOrder
                            }
                            .map {
                                    gps ->


                                HistoryMapPoint(

                                    latitude =
                                        gps.latitude,

                                    longitude =
                                        gps.longitude
                                )
                            }


                    if (
                        serverMapPoints.isNotEmpty()
                    ) {


                        setHistoryRoute(
                            serverMapPoints
                        )


                    } else if (
                        recordId > 0L
                    ) {


                        // =================================
                        // Server 沒 GPS，
                        // 但手機可能還留著 SQLite GPS。
                        // =================================

                        loadLocalRoute(
                            recordId
                        )


                    } else {


                        showNoRoute(
                            "這筆雲端紀錄沒有 GPS 軌跡"
                        )
                    }
                },

                onError = {
                        message ->


                    if (
                        !isAdded
                    ) {

                        return@loadActivityDetail
                    }


                    fallbackToLocal(
                        message
                    )
                }
            )
    }


    // =====================================================
    // Server 失敗
    // →
    // Local fallback
    // =====================================================

    private fun fallbackToLocal(
        serverError: String
    ) {


        if (
            recordId <= 0L
        ) {


            showError(
                serverError
            )


            showNoRoute(
                "無法取得雲端 GPS 軌跡"
            )


            return
        }


        val localRecord =
            database
                .getRecordById(
                    recordId
                )


        if (
            localRecord == null
        ) {


            showError(
                serverError
            )


            showNoRoute(
                "手機也沒有此筆 GPS 紀錄"
            )


            return
        }


        showLocalDetail(
            localRecord
        )


        loadLocalRoute(
            recordId
        )


        Toast.makeText(

            requireContext(),

            "目前無法取得雲端詳細資料，已改用手機本機紀錄\n$serverError",

            Toast.LENGTH_LONG

        ).show()
    }


    // =====================================================
    // Local 詳細資料
    // =====================================================

    private fun loadLocalDetail() {


        if (
            recordId <= 0L
        ) {


            showNotFound()


            showNoRoute(
                "沒有本機運動紀錄 ID"
            )


            return
        }


        val record =
            database
                .getRecordById(
                    recordId
                )


        if (
            record == null
        ) {


            showNotFound()


            showNoRoute(
                "找不到本機 GPS 紀錄"
            )


            return
        }


        showLocalDetail(
            record
        )


        loadLocalRoute(
            recordId
        )
    }


    // =====================================================
    // 讀 SQLite GPS
    // =====================================================

    private fun loadLocalRoute(
        localRecordId: Long
    ) {


        val route =
            database
                .getRoutePoints(
                    localRecordId
                )


        val points =
            route
                .map {
                        point ->


                    HistoryMapPoint(

                        latitude =
                            point.latitude,

                        longitude =
                            point.longitude
                    )
                }


        if (
            points.isEmpty()
        ) {


            showNoRoute(
                "這筆本機紀錄沒有 GPS 軌跡"
            )


            return
        }


        setHistoryRoute(
            points
        )
    }


    // =====================================================
    // 保存 GPS 給 Map
    // =====================================================

    private fun setHistoryRoute(
        points: List<HistoryMapPoint>
    ) {


        latestMapPoints =
            points


        tvMapStatus.text =
            "GPS 軌跡：${points.size} 個座標點"


        drawHistoryRoute()
    }


    // =====================================================
    // 無 GPS
    // =====================================================

    private fun showNoRoute(
        message: String
    ) {


        latestMapPoints =
            emptyList()


        tvMapStatus.text =
            message
    }


    // =====================================================
    // ScrollView / MapView 手勢衝突
    // =====================================================

    @Suppress("ClickableViewAccessibility")
    private fun setupMapTouch() {


        mapView
            .setOnTouchListener {
                    view,
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
    // 準備台灣離線地圖
    // =====================================================

    private fun prepareOfflineMap() {


        if (
            TaiwanOfflineMap.isMapReady(
                requireContext()
            )
        ) {


            mapProgress.visibility =
                View.GONE


            mapView.visibility =
                View.VISIBLE


            tvMapStatus.text =
                "台灣離線地圖已就緒"


            loadOfflineMap()


            return
        }


        tvMapStatus.text =
            "正在準備台灣離線地圖..."


        mapProgress.visibility =
            View.VISIBLE


        mapProgress.progress =
            0


        mapView.visibility =
            View.GONE


        viewLifecycleOwner
            .lifecycleScope
            .launch {


                val result =
                    TaiwanOfflineMap
                        .prepareMap(
                            requireContext()
                        ) {
                                progress ->


                            mapProgress.progress =
                                progress


                            tvMapStatus.text =
                                "正在準備台灣離線地圖：$progress%"
                        }


                result
                    .onSuccess {


                        if (
                            !isAdded
                        ) {

                            return@onSuccess
                        }


                        mapProgress.visibility =
                            View.GONE


                        mapView.visibility =
                            View.VISIBLE


                        tvMapStatus.text =
                            "台灣離線地圖已就緒"


                        loadOfflineMap()
                    }
                    .onFailure {
                            error ->


                        if (
                            !isAdded
                        ) {

                            return@onFailure
                        }


                        mapProgress.visibility =
                            View.GONE


                        mapView.visibility =
                            View.GONE


                        tvMapStatus.text =
                            "地圖載入失敗：${
                                error.message
                                    ?: "未知錯誤"
                            }"
                    }
            }
    }


    // =====================================================
    // MapLibre + PMTiles
    // =====================================================

    private fun loadOfflineMap() {


        val mapFile =
            TaiwanOfflineMap
                .getMapFile(
                    requireContext()
                )


        if (
            !mapFile.exists()
        ) {


            tvMapStatus.text =
                "找不到 taiwan.pmtiles"


            return
        }


        mapView
            .getMapAsync {
                    map ->


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


                    if (
                        latestMapPoints.isEmpty()
                    ) {


                        tvMapStatus.text =
                            "地圖已就緒，等待 GPS 軌跡"


                        showTaiwanOverview()


                    } else {


                        tvMapStatus.text =
                            "GPS 軌跡：${latestMapPoints.size} 個座標點"


                        drawHistoryRoute()
                    }
                }
            }
    }


    // =====================================================
    // 地圖操作
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
    // 沒 Route 時顯示台灣
    // =====================================================

    private fun showTaiwanOverview() {


        val map =
            mapLibreMap
                ?: return


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


    // =====================================================
    // 畫歷史 Route
    // =====================================================

    private fun drawHistoryRoute() {


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


        val route =
            latestMapPoints


        if (
            route.isEmpty()
        ) {

            return
        }


        val first =
            route.first()


        val last =
            route.last()


        // =================================================
        // 起點綠色
        // =================================================

        updateMapPoint(

            style =
                style,

            sourceId =
                START_SOURCE,

            layerId =
                START_LAYER,

            latitude =
                first.latitude,

            longitude =
                first.longitude,

            color =
                Color.rgb(
                    76,
                    175,
                    80
                )
        )


        // =================================================
        // 終點紅色
        // =================================================

        updateMapPoint(

            style =
                style,

            sourceId =
                END_SOURCE,

            layerId =
                END_LAYER,

            latitude =
                last.latitude,

            longitude =
                last.longitude,

            color =
                Color.rgb(
                    244,
                    67,
                    54
                )
        )


        // =================================================
        // 至少 2 點才畫 LineString
        // =================================================

        if (
            route.size >= 2
        ) {


            val geoPoints =
                route
                    .map {
                            point ->


                        Point.fromLngLat(

                            point.longitude,

                            point.latitude
                        )
                    }


            val line =
                LineString
                    .fromLngLats(
                        geoPoints
                    )


            val oldSource =
                style
                    .getSourceAs<GeoJsonSource>(
                        ROUTE_SOURCE
                    )


            if (
                oldSource == null
            ) {


                val source =
                    GeoJsonSource(

                        ROUTE_SOURCE,

                        line
                    )


                style.addSource(
                    source
                )


                val layer =
                    LineLayer(

                        ROUTE_LAYER,

                        ROUTE_SOURCE
                    )
                        .withProperties(

                            PropertyFactory
                                .lineColor(
                                    Color.rgb(
                                        33,
                                        150,
                                        243
                                    )
                                ),

                            PropertyFactory
                                .lineWidth(
                                    7f
                                ),

                            PropertyFactory
                                .lineOpacity(
                                    0.95f
                                )
                        )


                style.addLayer(
                    layer
                )


            } else {


                oldSource
                    .setGeoJson(
                        line
                    )
            }
        }


        // =================================================
        // Camera 顯示整段 Route
        // =================================================

        moveCameraToWholeRoute(
            route
        )
    }


    // =====================================================
    // 起點 / 終點 Marker
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


        val oldSource =
            style
                .getSourceAs<GeoJsonSource>(
                    sourceId
                )


        if (
            oldSource == null
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

                        PropertyFactory
                            .circleRadius(
                                7f
                            ),

                        PropertyFactory
                            .circleColor(
                                color
                            ),

                        PropertyFactory
                            .circleStrokeColor(
                                Color.WHITE
                            ),

                        PropertyFactory
                            .circleStrokeWidth(
                                2.5f
                            )
                    )


            style.addLayer(
                layer
            )


        } else {


            oldSource
                .setGeoJson(
                    point
                )
        }
    }


    // =====================================================
    // Camera 自動顯示完整路線
    //
    // 這裡不用額外 LatLngBounds，
    // 直接依 GPS 範圍計算中心及 Zoom。
    // =====================================================

    private fun moveCameraToWholeRoute(
        route: List<HistoryMapPoint>
    ) {


        val map =
            mapLibreMap
                ?: return


        if (
            route.isEmpty()
        ) {

            return
        }


        val minLatitude =
            route.minOf {
                it.latitude
            }


        val maxLatitude =
            route.maxOf {
                it.latitude
            }


        val minLongitude =
            route.minOf {
                it.longitude
            }


        val maxLongitude =
            route.maxOf {
                it.longitude
            }


        val centerLatitude =
            (
                    minLatitude +
                            maxLatitude
                    ) /
                    2.0


        val centerLongitude =
            (
                    minLongitude +
                            maxLongitude
                    ) /
                    2.0


        val latitudeSpan =
            maxLatitude -
                    minLatitude


        val longitudeSpan =
            maxLongitude -
                    minLongitude


        val maxSpan =
            maxOf(
                latitudeSpan,
                longitudeSpan
            )


        // =================================================
        // 大致依路線大小選 Zoom
        // =================================================

        val zoom =

            when {


                maxSpan <
                        0.001 ->

                    17.0


                maxSpan <
                        0.003 ->

                    16.0


                maxSpan <
                        0.008 ->

                    15.0


                maxSpan <
                        0.02 ->

                    14.0


                maxSpan <
                        0.05 ->

                    13.0


                maxSpan <
                        0.10 ->

                    12.0


                maxSpan <
                        0.30 ->

                    10.0


                else ->

                    8.0
            }


        map.cameraPosition =
            CameraPosition
                .Builder()
                .target(
                    LatLng(

                        centerLatitude,

                        centerLongitude
                    )
                )
                .zoom(
                    zoom
                )
                .bearing(
                    0.0
                )
                .tilt(
                    0.0
                )
                .build()
    }


    // =====================================================
    // Local 詳情
    // =====================================================

    private fun showLocalDetail(
        record: WorkoutRecord
    ) {


        val date =
            formatDate(
                record.startTimeMillis
            )


        val startTime =
            formatClockTime(
                record.startTimeMillis
            )


        val endTime =
            formatClockTime(
                record.endTimeMillis
            )


        val distanceKm =
            record.distanceMeters /
                    1000.0


        tvActivity.text =
            "運動方式：${record.activityType}"


        tvDate.text =
            "日期：$date"


        tvStart.text =
            "開始時間：$startTime"


        tvEnd.text =
            "結束時間：$endTime"


        tvDuration.text =
            "運動時間：${
                formatDuration(
                    record.durationSeconds
                )
            }"


        tvDistance.text =
            String.format(

                Locale.TAIWAN,

                "距離：%.2f km",

                distanceKm
            )


        tvSteps.text =

            if (
                record.modeId == 3 ||
                record.activityType ==
                "騎自行車" ||
                record.activityType ==
                "單車"
            ) {

                "步數：不記錄"

            } else {

                "步數：${record.steps}"
            }
    }


    // =====================================================
    // Server 詳情
    // =====================================================

    private fun showServerDetail(

        activity: ActivityDetail,

        localRecord: WorkoutRecord?

    ) {


        val startMillis =
            parseBackendTime(
                activity.startTime
            )


        val endMillis =
            parseBackendTime(
                activity.endTime
            )


        val dateText =

            if (
                startMillis != null
            ) {

                formatDate(
                    startMillis
                )

            } else {

                "—"
            }


        val startText =

            if (
                startMillis != null
            ) {

                formatClockTime(
                    startMillis
                )

            } else {

                "—"
            }


        val endText =

            if (
                endMillis != null
            ) {

                formatClockTime(
                    endMillis
                )

            } else {

                "—"
            }


        val durationSeconds =

            localRecord
                ?.durationSeconds
                ?: calculateDurationSeconds(

                    startMillis =
                        startMillis,

                    endMillis =
                        endMillis
                )


        val activityName =

            activity
                .title
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: modeIdToName(
                    activity.modeId
                )


        val distanceKm =
            (
                    activity
                        .totalDistanceMeters
                        ?: localRecord
                            ?.distanceMeters
                            ?.toDouble()
                        ?: 0.0
                    ) /
                    1000.0


        tvActivity.text =
            "運動方式：$activityName"


        tvDate.text =
            "日期：$dateText"


        tvStart.text =
            "開始時間：$startText"


        tvEnd.text =
            "結束時間：$endText"


        tvDuration.text =

            if (
                durationSeconds != null
            ) {

                "運動時間：${
                    formatDuration(
                        durationSeconds
                    )
                }"

            } else {

                "運動時間：—"
            }


        tvDistance.text =
            String.format(

                Locale.TAIWAN,

                "距離：%.2f km",

                distanceKm
            )


        tvSteps.text =

            if (
                activity.modeId == 3 ||
                activityName ==
                "騎自行車" ||
                activityName ==
                "單車"
            ) {

                "步數：不記錄"

            } else if (
                localRecord != null
            ) {

                "步數：${localRecord.steps}"

            } else {

                "步數：—"
            }
    }


    // =====================================================
    // mode_id
    // =====================================================

    private fun modeIdToName(
        modeId: Int?
    ): String {


        return when (
            modeId
        ) {

            1 ->
                "登山"

            2 ->
                "走路"

            3 ->
                "騎自行車"

            else ->
                "運動"
        }
    }


    // =====================================================
    // Backend ISO Time
    // =====================================================

    private fun parseBackendTime(
        value: String?
    ): Long? {


        if (
            value.isNullOrBlank()
        ) {

            return null
        }


        val patterns =
            listOf(

                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",

                "yyyy-MM-dd'T'HH:mm:ssXXX",

                "yyyy-MM-dd'T'HH:mm:ss.SSS",

                "yyyy-MM-dd'T'HH:mm:ss"
            )


        patterns
            .forEach {
                    pattern ->


                try {


                    val formatter =
                        SimpleDateFormat(

                            pattern,

                            Locale.US
                        )


                    formatter.isLenient =
                        false


                    formatter.timeZone =
                        TimeZone.getDefault()


                    val date =
                        formatter.parse(
                            value
                        )


                    if (
                        date != null
                    ) {

                        return date.time
                    }


                } catch (
                    ignored: Exception
                ) {

                }
            }


        return null
    }


    // =====================================================
    // Duration
    // =====================================================

    private fun calculateDurationSeconds(

        startMillis: Long?,

        endMillis: Long?

    ): Long? {


        if (
            startMillis == null ||
            endMillis == null
        ) {

            return null
        }


        return (
                endMillis -
                        startMillis
                )
            .coerceAtLeast(
                0L
            ) /
                1000L
    }


    // =====================================================
    // 日期
    // =====================================================

    private fun formatDate(
        timeMillis: Long
    ): String {


        val formatter =
            SimpleDateFormat(

                "yyyy/MM/dd",

                Locale.TAIWAN
            )


        return formatter.format(

            Date(
                timeMillis
            )
        )
    }


    // =====================================================
    // 時間
    // =====================================================

    private fun formatClockTime(
        timeMillis: Long
    ): String {


        val formatter =
            SimpleDateFormat(

                "HH:mm:ss",

                Locale.TAIWAN
            )


        return formatter.format(

            Date(
                timeMillis
            )
        )
    }


    // =====================================================
    // HH:mm:ss
    // =====================================================

    private fun formatDuration(
        totalSeconds: Long
    ): String {


        val hours =
            totalSeconds /
                    3600L


        val minutes =
            (
                    totalSeconds %
                            3600L
                    ) /
                    60L


        val seconds =
            totalSeconds %
                    60L


        return String.format(

            Locale.TAIWAN,

            "%02d:%02d:%02d",

            hours,

            minutes,

            seconds
        )
    }


    // =====================================================
    // Loading
    // =====================================================

    private fun showLoading() {


        tvActivity.text =
            "正在載入運動紀錄…"


        tvDate.text =
            "日期：—"


        tvStart.text =
            "開始時間：—"


        tvEnd.text =
            "結束時間：—"


        tvDuration.text =
            "運動時間：—"


        tvDistance.text =
            "距離：—"


        tvSteps.text =
            "步數：—"
    }


    // =====================================================
    // 找不到
    // =====================================================

    private fun showNotFound() {


        tvActivity.text =
            "找不到此筆運動紀錄"


        tvDate.text =
            "日期：—"


        tvStart.text =
            "開始時間：—"


        tvEnd.text =
            "結束時間：—"


        tvDuration.text =
            "運動時間：—"


        tvDistance.text =
            "距離：—"


        tvSteps.text =
            "步數：—"
    }


    // =====================================================
    // Error
    // =====================================================

    private fun showError(
        message: String
    ) {


        tvActivity.text =
            "無法取得此筆運動紀錄"


        tvDate.text =
            "日期：—"


        tvStart.text =
            "開始時間：—"


        tvEnd.text =
            "結束時間：—"


        tvDuration.text =
            "運動時間：—"


        tvDistance.text =
            "距離：—"


        tvSteps.text =
            "步數：—"


        if (
            isAdded
        ) {


            Toast.makeText(

                requireContext(),

                message,

                Toast.LENGTH_LONG

            ).show()
        }
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
    }


    override fun onResume() {


        super.onResume()


        if (
            ::mapView.isInitialized
        ) {


            mapView.onResume()
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


            mapView
                .onSaveInstanceState(
                    outState
                )
        }
    }


    override fun onDestroyView() {


        if (
            ::mapView.isInitialized
        ) {


            mapView.onDestroy()
        }


        mapLibreMap =
            null


        mapStyleReady =
            false


        latestMapPoints =
            emptyList()


        super.onDestroyView()
    }
}