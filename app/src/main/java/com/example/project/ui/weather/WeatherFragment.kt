package com.example.project.ui.weather


import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

import com.example.project.R
import com.example.project.data.ForecastItem
import com.example.project.data.TaiwanLocations
import com.example.project.network.ApiClient
import com.example.project.network.WeekWeatherResponse

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class WeatherFragment :
    Fragment(
        R.layout.fragment_weather
    ) {


    // =====================================================
    // 三天 / 一週模式
    // =====================================================

    private enum class WeatherMode {

        THREE_DAY,

        WEEK
    }


    private var currentMode =
        WeatherMode.THREE_DAY


    // =====================================================
    // 畫面元件
    // =====================================================

    private lateinit var btnBack:
            Button


    private lateinit var spinnerCity:
            Spinner


    private lateinit var spinnerTown:
            Spinner


    private lateinit var btnSearchWeather:
            Button


    private lateinit var weatherModeGroup:
            RadioGroup


    private lateinit var weatherLoading:
            ProgressBar


    private lateinit var tvSelectedLocation:
            TextView


    private lateinit var tvForecastType:
            TextView


    private lateinit var tvLastUpdate:
            TextView


    private lateinit var forecastContainer:
            LinearLayout


    // =====================================================
    // 使用者目前查詢的地區
    // =====================================================

    private var selectedCity =
        ""


    private var selectedTown =
        ""


    // =====================================================
    // GPS 自動定位
    // =====================================================

    private lateinit var fusedLocationClient:
            FusedLocationProviderClient


    private var locationTokenSource:
            CancellationTokenSource? =
        null


    // =====================================================
    // GPS 正在指定的縣市 / 行政區
    //
    // 例如：
    //
    // 桃園市
    // 中壢區
    //
    // 目的：
    //
    // City Spinner 改變時會重新建立 Town Spinner。
    //
    // 如果沒有保存這兩個值，
    // Town Spinner 很容易又跳回第一個地區。
    // =====================================================

    private var pendingGpsCity:
            String? =
        null


    private var pendingGpsTown:
            String? =
        null


    // =====================================================
    // 現在畫面顯示的地區
    // 是否為 GPS 自動取得的位置
    // =====================================================

    private var isUsingCurrentGpsLocation =
        false


    // =====================================================
    // 自動更新 Handler
    // =====================================================

    private val weatherHandler =
        Handler(
            Looper.getMainLooper()
        )


    companion object {


        // =================================================
        // 三天預報
        //
        // 每 3 小時自動更新
        // =================================================

        private const val THREE_DAY_REFRESH =

            3L *
                    60L *
                    60L *
                    1000L


        // =================================================
        // 一週預報
        //
        // 每 12 小時自動更新
        // =================================================

        private const val WEEK_REFRESH =

            12L *
                    60L *
                    60L *
                    1000L
    }


    // =====================================================
    // 自動更新
    // =====================================================

    private val weatherRefreshRunnable =

        object :
            Runnable {


            override fun run() {


                if (
                    selectedCity.isNotBlank() &&
                    selectedTown.isNotBlank()
                ) {


                    loadWeather()
                }


                scheduleNextRefresh()
            }
        }


    // =====================================================
    // Fragment 建立完成
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
        // UI
        // =================================================

        btnBack =
            view.findViewById(
                R.id.btnBack
            )


        spinnerCity =
            view.findViewById(
                R.id.spinnerCity
            )


        spinnerTown =
            view.findViewById(
                R.id.spinnerTown
            )


        btnSearchWeather =
            view.findViewById(
                R.id.btnSearchWeather
            )


        weatherModeGroup =
            view.findViewById(
                R.id.weatherModeGroup
            )


        weatherLoading =
            view.findViewById(
                R.id.weatherLoading
            )


        tvSelectedLocation =
            view.findViewById(
                R.id.tvSelectedLocation
            )


        tvForecastType =
            view.findViewById(
                R.id.tvForecastType
            )


        tvLastUpdate =
            view.findViewById(
                R.id.tvLastUpdate
            )


        forecastContainer =
            view.findViewById(
                R.id.forecastContainer
            )


        // =================================================
        // GPS Client
        // =================================================

        fusedLocationClient =
            LocationServices
                .getFusedLocationProviderClient(
                    requireActivity()
                )


        // =================================================
        // 返回
        // =================================================

        btnBack
            .setOnClickListener {


                requireActivity()
                    .finish()
            }


        // =================================================
        // 先建立下拉選單
        // =================================================

        setupCitySpinner()


        // =================================================
        // 三天 / 一週
        // =================================================

        setupWeatherMode()


        // =================================================
        // 手動查詢
        // =================================================

        btnSearchWeather
            .setOnClickListener {


                selectedCity =

                    spinnerCity
                        .selectedItem
                        ?.toString()
                        ?.trim()
                        .orEmpty()


                selectedTown =

                    spinnerTown
                        .selectedItem
                        ?.toString()
                        ?.trim()
                        .orEmpty()


                if (
                    selectedCity.isBlank() ||
                    selectedTown.isBlank()
                ) {


                    Toast.makeText(

                        requireContext(),

                        "請先選擇縣市與鄉鎮市區",

                        Toast.LENGTH_SHORT

                    ).show()


                    return@setOnClickListener
                }


                // =========================================
                // 使用者手動選擇後，
                // 不再標示為「目前位置」。
                // =========================================

                isUsingCurrentGpsLocation =
                    false


                updateSelectedLocationText()


                loadWeather()


                scheduleNextRefresh()
            }


        // =================================================
        // 進入天氣頁
        //
        // MainActivity 已經在登入成功後詢問定位權限。
        //
        // 這裡只負責：
        //
        // GPS
        // ↓
        // 縣市 / 行政區
        // ↓
        // Spinner
        // ↓
        // 自動查天氣
        // =================================================

        startDefaultLocationFromGps()
    }


    // =====================================================
    // 建立縣市 Spinner
    // =====================================================

    private fun setupCitySpinner() {


        val cities =
            TaiwanLocations
                .cityToTowns
                .keys
                .toList()


        val adapter =
            ArrayAdapter(

                requireContext(),

                android.R.layout
                    .simple_spinner_item,

                cities
            )


        adapter.setDropDownViewResource(

            android.R.layout
                .simple_spinner_dropdown_item
        )


        spinnerCity.adapter =
            adapter


        spinnerCity
            .onItemSelectedListener =

            object :
                AdapterView.OnItemSelectedListener {


                override fun onItemSelected(

                    parent:
                    AdapterView<*>?,

                    view:
                    View?,

                    position:
                    Int,

                    id:
                    Long

                ) {


                    if (
                        position !in
                        cities.indices
                    ) {


                        return
                    }


                    val city =
                        cities[
                            position
                        ]


                    // =========================================
                    // 如果 GPS 正在設定：
                    //
                    // 桃園市 / 中壢區
                    //
                    // City Spinner 被設定成桃園市後，
                    // Town Spinner 重建時
                    // 必須仍然選中中壢區。
                    // =========================================

                    val preferredTown =

                        if (
                            pendingGpsCity !=
                            null &&
                            normalizeTaiwanLocationName(
                                city
                            ) ==
                            normalizeTaiwanLocationName(
                                pendingGpsCity
                                    .orEmpty()
                            )
                        ) {


                            pendingGpsTown


                        } else {


                            null
                        }


                    setupTownSpinner(

                        city =
                            city,

                        preferredTown =
                            preferredTown
                    )
                }


                override fun onNothingSelected(
                    parent:
                    AdapterView<*>?
                ) {


                    // 不需要處理
                }
            }
    }


    // =====================================================
    // 建立行政區 Spinner
    // =====================================================

    private fun setupTownSpinner(

        city: String,

        preferredTown: String? =
            null

    ) {


        val towns =
            TaiwanLocations
                .cityToTowns[
                city
            ]
                .orEmpty()


        val adapter =
            ArrayAdapter(

                requireContext(),

                android.R.layout
                    .simple_spinner_item,

                towns
            )


        adapter.setDropDownViewResource(

            android.R.layout
                .simple_spinner_dropdown_item
        )


        spinnerTown.adapter =
            adapter


        // =================================================
        // 如果是 GPS 自動指定位置，
        // Adapter 建立後立刻切到正確行政區。
        // =================================================

        if (
            !preferredTown.isNullOrBlank()
        ) {


            val townIndex =
                towns
                    .indexOfFirst {


                        normalizeTaiwanLocationName(
                            it
                        ) ==
                                normalizeTaiwanLocationName(
                                    preferredTown
                                )
                    }


            if (
                townIndex >=
                0
            ) {


                spinnerTown
                    .setSelection(

                        townIndex,

                        false
                    )
            }
        }
    }


    // =====================================================
    // 三天 / 一週
    // =====================================================

    private fun setupWeatherMode() {


        weatherModeGroup
            .setOnCheckedChangeListener {
                    _,
                    checkedId ->


                when (
                    checkedId
                ) {


                    R.id.radioThreeDay -> {


                        currentMode =
                            WeatherMode.THREE_DAY


                        tvForecastType.text =
                            "三天預報"


                        if (
                            selectedCity.isNotBlank() &&
                            selectedTown.isNotBlank()
                        ) {


                            loadWeather()


                            scheduleNextRefresh()
                        }
                    }


                    R.id.radioWeek -> {


                        currentMode =
                            WeatherMode.WEEK


                        tvForecastType.text =
                            "一週預報"


                        if (
                            selectedCity.isNotBlank() &&
                            selectedTown.isNotBlank()
                        ) {


                            loadWeather()


                            scheduleNextRefresh()
                        }
                    }
                }
            }
    }


    // =====================================================
    // 進天氣頁後取得目前位置
    // =====================================================

    private fun startDefaultLocationFromGps() {


        tvSelectedLocation.text =
            "正在取得目前位置..."


        // =================================================
        // MainActivity 應該已經詢問過權限。
        //
        // 如果使用者沒有允許，
        // 這裡不再跳第二次權限視窗。
        // =================================================

        if (
            hasLocationPermission()
        ) {


            requestCurrentWeatherLocation()


        } else {


            tvSelectedLocation.text =
                "未允許定位，請手動選擇地區"


            Toast.makeText(

                requireContext(),

                "未允許定位權限，可手動選擇縣市與鄉鎮市區",

                Toast.LENGTH_LONG

            ).show()
        }
    }


    // =====================================================
    // 定位權限
    // =====================================================

    private fun hasLocationPermission():
            Boolean {


        val fineGranted =
            ContextCompat
                .checkSelfPermission(

                    requireContext(),

                    Manifest.permission
                        .ACCESS_FINE_LOCATION

                ) ==
                    PackageManager
                        .PERMISSION_GRANTED


        val coarseGranted =
            ContextCompat
                .checkSelfPermission(

                    requireContext(),

                    Manifest.permission
                        .ACCESS_COARSE_LOCATION

                ) ==
                    PackageManager
                        .PERMISSION_GRANTED


        return fineGranted ||
                coarseGranted
    }


    // =====================================================
    // 取得目前 GPS
    // =====================================================

    private fun requestCurrentWeatherLocation() {


        if (
            !hasLocationPermission()
        ) {


            showGpsLocationFailure()


            return
        }


        locationTokenSource
            ?.cancel()


        locationTokenSource =
            CancellationTokenSource()


        val fineGranted =
            ContextCompat
                .checkSelfPermission(

                    requireContext(),

                    Manifest.permission
                        .ACCESS_FINE_LOCATION

                ) ==
                    PackageManager
                        .PERMISSION_GRANTED


        val priority =

            if (
                fineGranted
            ) {


                Priority
                    .PRIORITY_HIGH_ACCURACY


            } else {


                Priority
                    .PRIORITY_BALANCED_POWER_ACCURACY
            }


        try {


            fusedLocationClient
                .getCurrentLocation(

                    priority,

                    locationTokenSource!!
                        .token
                )
                .addOnSuccessListener {
                        location ->


                    if (
                        !isAdded ||
                        view ==
                        null
                    ) {


                        return@addOnSuccessListener
                    }


                    if (
                        location !=
                        null
                    ) {


                        reverseGeocodeCurrentLocation(

                            latitude =
                                location.latitude,

                            longitude =
                                location.longitude
                        )


                    } else {


                        // =====================================
                        // 即時定位失敗
                        //
                        // 再試最後一次已知位置。
                        // =====================================

                        requestLastKnownWeatherLocation()
                    }
                }
                .addOnFailureListener {


                    if (
                        !isAdded ||
                        view ==
                        null
                    ) {


                        return@addOnFailureListener
                    }


                    requestLastKnownWeatherLocation()
                }


        } catch (
            _: SecurityException
        ) {


            showGpsLocationFailure()
        }
    }


    // =====================================================
    // 使用最後已知 GPS
    // =====================================================

    private fun requestLastKnownWeatherLocation() {


        if (
            !hasLocationPermission()
        ) {


            showGpsLocationFailure()


            return
        }


        try {


            fusedLocationClient
                .lastLocation
                .addOnSuccessListener {
                        location ->


                    if (
                        !isAdded ||
                        view ==
                        null
                    ) {


                        return@addOnSuccessListener
                    }


                    if (
                        location !=
                        null
                    ) {


                        reverseGeocodeCurrentLocation(

                            latitude =
                                location.latitude,

                            longitude =
                                location.longitude
                        )


                    } else {


                        showGpsLocationFailure()
                    }
                }
                .addOnFailureListener {


                    if (
                        !isAdded ||
                        view ==
                        null
                    ) {


                        return@addOnFailureListener
                    }


                    showGpsLocationFailure()
                }


        } catch (
            _: SecurityException
        ) {


            showGpsLocationFailure()
        }
    }


    // =====================================================
    // GPS 經緯度
    // →
    // 縣市 / 鄉鎮市區
    //
    // 例如：
    //
    // 24.xxxxx
    // 121.xxxxx
    //
    // ↓
    //
    // 桃園市
    // 中壢區
    // =====================================================

    private fun reverseGeocodeCurrentLocation(

        latitude: Double,

        longitude: Double

    ) {


        if (
            !Geocoder.isPresent()
        ) {


            showGpsLocationFailure()


            return
        }


        val appContext =
            requireContext()
                .applicationContext


        val geocoder =
            Geocoder(

                appContext,

                Locale.TAIWAN
            )


        // =================================================
        // Geocoder 不在 UI Thread 執行
        // =================================================

        Thread {


            try {


                @Suppress(
                    "DEPRECATION"
                )

                val addresses =
                    geocoder
                        .getFromLocation(

                            latitude,

                            longitude,

                            1
                        )
                        .orEmpty()


                val address =
                    addresses
                        .firstOrNull()


                if (
                    address ==
                    null
                ) {


                    activity
                        ?.runOnUiThread {


                            if (
                                isAdded &&
                                view !=
                                null
                            ) {


                                showGpsLocationFailure()
                            }
                        }


                    return@Thread
                }


                val localArea =
                    findTaiwanLocalArea(
                        address
                    )


                activity
                    ?.runOnUiThread {


                        if (
                            !isAdded ||
                            view ==
                            null
                        ) {


                            return@runOnUiThread
                        }


                        if (
                            localArea ==
                            null
                        ) {


                            showGpsLocationFailure()


                        } else {


                            applyGpsLocationToWeather(

                                city =
                                    localArea.first,

                                town =
                                    localArea.second
                            )
                        }
                    }


            } catch (
                _: Exception
            ) {


                activity
                    ?.runOnUiThread {


                        if (
                            isAdded &&
                            view !=
                            null
                        ) {


                            showGpsLocationFailure()
                        }
                    }
            }


        }.start()
    }


    // =====================================================
    // Android Address
    // →
    // TaiwanLocations 中的縣市 / 地區
    // =====================================================

    private fun findTaiwanLocalArea(
        address: Address
    ): Pair<String, String>? {


        val parts =
            mutableListOf<String>()


        address.adminArea
            ?.let {
                parts.add(
                    it
                )
            }


        address.subAdminArea
            ?.let {
                parts.add(
                    it
                )
            }


        address.locality
            ?.let {
                parts.add(
                    it
                )
            }


        address.subLocality
            ?.let {
                parts.add(
                    it
                )
            }


        address.featureName
            ?.let {
                parts.add(
                    it
                )
            }


        try {


            address
                .getAddressLine(
                    0
                )
                ?.let {


                    parts.add(
                        it
                    )
                }


        } catch (
            _: Exception
        ) {


            // 沒有完整地址時忽略
        }


        val fullAddress =
            normalizeTaiwanLocationName(

                parts.joinToString(
                    " "
                )
            )


        // =================================================
        // 找縣市
        // =================================================

        val city =
            TaiwanLocations
                .cityToTowns
                .keys
                .firstOrNull {
                        cityName ->


                    fullAddress.contains(

                        normalizeTaiwanLocationName(
                            cityName
                        )
                    )
                }
                ?: return null


        // =================================================
        // 找行政區
        // =================================================

        val towns =
            TaiwanLocations
                .cityToTowns[
                city
            ]
                .orEmpty()


        val town =
            towns
                .firstOrNull {
                        townName ->


                    fullAddress.contains(

                        normalizeTaiwanLocationName(
                            townName
                        )
                    )
                }
                ?: return null


        return Pair(
            city,
            town
        )
    }


    // =====================================================
    // GPS 定位結果
    // →
    // 同步兩個 Spinner
    // →
    // 同步 selectedCity / selectedTown
    // →
    // 自動查天氣
    // =====================================================

    private fun applyGpsLocationToWeather(

        city: String,

        town: String

    ) {


        val cities =
            TaiwanLocations
                .cityToTowns
                .keys
                .toList()


        val cityIndex =
            cities
                .indexOfFirst {


                    normalizeTaiwanLocationName(
                        it
                    ) ==
                            normalizeTaiwanLocationName(
                                city
                            )
                }


        if (
            cityIndex <
            0
        ) {


            showGpsLocationFailure()


            return
        }


        val actualCity =
            cities[
                cityIndex
            ]


        val towns =
            TaiwanLocations
                .cityToTowns[
                actualCity
            ]
                .orEmpty()


        val townIndex =
            towns
                .indexOfFirst {


                    normalizeTaiwanLocationName(
                        it
                    ) ==
                            normalizeTaiwanLocationName(
                                town
                            )
                }


        if (
            townIndex <
            0
        ) {


            showGpsLocationFailure()


            return
        }


        val actualTown =
            towns[
                townIndex
            ]


        // =================================================
        // 先保存 GPS 的目標。
        //
        // City Spinner 的 callback
        // 如果重新建立 Town Spinner，
        // 仍然知道要選 actualTown。
        // =================================================

        pendingGpsCity =
            actualCity


        pendingGpsTown =
            actualTown


        // =================================================
        // 設定 City
        // =================================================

        spinnerCity
            .setSelection(

                cityIndex,

                false
            )


        // =================================================
        // 不依賴 Spinner callback，
        // 直接再建立一次正確 Town。
        // =================================================

        setupTownSpinner(

            city =
                actualCity,

            preferredTown =
                actualTown
        )


        // =================================================
        // 下一輪 UI Queue 再做最終確認。
        //
        // 這就是修正：
        //
        // 「下面顯示中壢區，
        //  但上面 Spinner 還停在桃園區」
        //
        // 的重點。
        // =================================================

        spinnerTown.post {


            if (
                !isAdded ||
                view ==
                null
            ) {


                return@post
            }


            // =============================================
            // 再確認 City
            // =============================================

            if (
                normalizeTaiwanLocationName(

                    spinnerCity
                        .selectedItem
                        ?.toString()
                        .orEmpty()

                ) !=
                normalizeTaiwanLocationName(
                    actualCity
                )
            ) {


                spinnerCity
                    .setSelection(

                        cityIndex,

                        false
                    )
            }


            // =============================================
            // 再確認 Town Adapter + selection
            // =============================================

            setupTownSpinner(

                city =
                    actualCity,

                preferredTown =
                    actualTown
            )


            val finalTownIndex =
                TaiwanLocations
                    .cityToTowns[
                    actualCity
                ]
                    .orEmpty()
                    .indexOfFirst {


                        normalizeTaiwanLocationName(
                            it
                        ) ==
                                normalizeTaiwanLocationName(
                                    actualTown
                                )
                    }


            if (
                finalTownIndex >=
                0
            ) {


                spinnerTown
                    .setSelection(

                        finalTownIndex,

                        false
                    )
            }


            // =============================================
            // selectedCity / Town
            // 與 Spinner 使用同一份資料
            // =============================================

            selectedCity =
                actualCity


            selectedTown =
                actualTown


            isUsingCurrentGpsLocation =
                true


            updateSelectedLocationText()


            // =============================================
            // GPS 套用完成
            // =============================================

            pendingGpsCity =
                null


            pendingGpsTown =
                null


            // =============================================
            // 自動查目前所在地天氣
            // =============================================

            loadWeather()


            scheduleNextRefresh()
        }
    }


    // =====================================================
    // 台 / 臺 正規化
    // =====================================================

    private fun normalizeTaiwanLocationName(
        value: String
    ): String {


        return value

            .replace(
                "臺",
                "台"
            )

            .replace(
                " ",
                ""
            )

            .trim()
    }


    // =====================================================
    // GPS 失敗
    // =====================================================

    private fun showGpsLocationFailure() {


        if (
            !isAdded ||
            view ==
            null
        ) {


            return
        }


        tvSelectedLocation.text =
            "無法自動判斷所在地，請手動選擇地區"


        Toast.makeText(

            requireContext(),

            "目前無法取得所在地區，請手動選擇縣市與鄉鎮市區",

            Toast.LENGTH_LONG

        ).show()
    }


    // =====================================================
    // 目前地區文字
    // =====================================================

    private fun updateSelectedLocationText() {


        if (
            selectedCity.isBlank() ||
            selectedTown.isBlank()
        ) {


            return
        }


        tvSelectedLocation.text =

            if (
                isUsingCurrentGpsLocation
            ) {


                "$selectedCity $selectedTown（目前位置）"


            } else {


                "$selectedCity $selectedTown"
            }
    }


    // =====================================================
    // 查詢天氣
    // =====================================================

    private fun loadWeather() {


        if (
            selectedCity.isBlank() ||
            selectedTown.isBlank()
        ) {


            return
        }


        forecastContainer
            .removeAllViews()


        weatherLoading.visibility =
            View.VISIBLE


        when (
            currentMode
        ) {


            WeatherMode.THREE_DAY -> {


                loadThreeDayWeather(

                    city =
                        selectedCity,

                    town =
                        selectedTown
                )
            }


            WeatherMode.WEEK -> {


                loadWeekWeather(

                    city =
                        selectedCity,

                    town =
                        selectedTown
                )
            }
        }
    }


    // =====================================================
    // 三天天氣
    //
    // GET /api/v1/weather/get_3days32
    // =====================================================

    private fun loadThreeDayWeather(

        city: String,

        town: String

    ) {


        weatherLoading.visibility =
            View.VISIBLE


        ApiClient
            .weatherApi
            .getThreeDayWeather(

                countyName =
                    city,

                locationName =
                    town
            )
            .enqueue(

                object :
                    Callback<WeekWeatherResponse> {


                    override fun onResponse(

                        call:
                        Call<WeekWeatherResponse>,

                        response:
                        Response<WeekWeatherResponse>

                    ) {


                        if (
                            !isAdded ||
                            view ==
                            null
                        ) {


                            return
                        }


                        weatherLoading.visibility =
                            View.GONE


                        val body =
                            response.body()


                        if (
                            response.isSuccessful &&
                            body !=
                            null &&
                            body.status ==
                            "success"
                        ) {


                            val allForecasts =
                                body
                                    .forecastList
                                    .orEmpty()


                            // =====================================
                            // 只保留前三個不同日期
                            // =====================================

                            val firstThreeDates =
                                allForecasts
                                    .mapNotNull {
                                            forecast ->


                                        forecast
                                            .startTime
                                            ?.take(
                                                10
                                            )
                                    }
                                    .distinct()
                                    .take(
                                        3
                                    )
                                    .toSet()


                            val threeDayForecasts =
                                allForecasts
                                    .filter {
                                            forecast ->


                                        val date =
                                            forecast
                                                .startTime
                                                ?.take(
                                                    10
                                                )


                                        date in
                                                firstThreeDates
                                    }


                            val items =
                                threeDayForecasts
                                    .map {
                                            forecast ->


                                        ForecastItem(

                                            period =
                                                formatForecastPeriod(

                                                    forecast.startTime,

                                                    forecast.endTime
                                                ),


                                            weather =
                                                forecast
                                                    .weatherDesc
                                                    ?: "--",


                                            rainProbability =
                                                forecast
                                                    .rainProbability
                                                    ?: "--",


                                            temperature =
                                                forecast
                                                    .temperature
                                                    ?: "--",


                                            comfort =
                                                forecast
                                                    .comfort
                                                    ?: "--",


                                            windDirection =
                                                forecast
                                                    .windDirection
                                                    ?: "--",


                                            windSpeed =
                                                forecast
                                                    .windSpeed
                                                    ?: "--",


                                            humidity =
                                                forecast
                                                    .humidity
                                                    ?: "--"
                                        )
                                    }


                            // =====================================
                            // API 回來的縣市 / 地區
                            //
                            // selectedCity / selectedTown
                            // 仍然維持與 Spinner 同步。
                            // =====================================

                            updateSelectedLocationText()


                            tvForecastType.text =
                                "三天預報（每3小時）"


                            showForecastItems(
                                items
                            )


                            showServerUpdateTime(
                                body
                            )


                        } else {


                            showApiError(

                                "三天天氣取得失敗",

                                response.code()
                            )
                        }
                    }


                    override fun onFailure(

                        call:
                        Call<WeekWeatherResponse>,

                        t:
                        Throwable

                    ) {


                        if (
                            !isAdded ||
                            view ==
                            null
                        ) {


                            return
                        }


                        weatherLoading.visibility =
                            View.GONE


                        Toast.makeText(

                            requireContext(),

                            "三天天氣連線失敗：${
                                t.message
                                    ?: "未知錯誤"
                            }",

                            Toast.LENGTH_LONG

                        ).show()
                    }
                }
            )
    }


    // =====================================================
    // 一週天氣
    //
    // GET /api/v1/weather/get_week
    // =====================================================

    private fun loadWeekWeather(

        city: String,

        town: String

    ) {


        weatherLoading.visibility =
            View.VISIBLE


        ApiClient
            .weatherApi
            .getWeekWeather(

                countyName =
                    city,

                locationName =
                    town
            )
            .enqueue(

                object :
                    Callback<WeekWeatherResponse> {


                    override fun onResponse(

                        call:
                        Call<WeekWeatherResponse>,

                        response:
                        Response<WeekWeatherResponse>

                    ) {


                        if (
                            !isAdded ||
                            view ==
                            null
                        ) {


                            return
                        }


                        weatherLoading.visibility =
                            View.GONE


                        val body =
                            response.body()


                        if (
                            response.isSuccessful &&
                            body !=
                            null &&
                            body.status ==
                            "success"
                        ) {


                            val forecasts =
                                body
                                    .forecastList
                                    .orEmpty()


                            val items =
                                forecasts
                                    .map {
                                            forecast ->


                                        ForecastItem(

                                            period =
                                                formatWeekPeriod(

                                                    forecast.startTime,

                                                    forecast.endTime
                                                ),


                                            weather =
                                                forecast
                                                    .weatherDesc
                                                    ?: "--",


                                            rainProbability =
                                                forecast
                                                    .rainProbability
                                                    ?: "--",


                                            temperature =
                                                forecast
                                                    .temperature
                                                    ?: "--",


                                            comfort =
                                                forecast
                                                    .comfort
                                                    ?: "--",


                                            windDirection =
                                                forecast
                                                    .windDirection
                                                    ?: "--",


                                            windSpeed =
                                                forecast
                                                    .windSpeed
                                                    ?: "--",


                                            humidity =
                                                forecast
                                                    .humidity
                                                    ?: "--"
                                        )
                                    }


                            updateSelectedLocationText()


                            tvForecastType.text =
                                "一週預報（每12小時，共 ${
                                    body.totalPeriods
                                        ?: forecasts.size
                                } 筆）"


                            showForecastItems(
                                items
                            )


                            showServerUpdateTime(
                                body
                            )


                        } else {


                            showApiError(

                                "一週天氣取得失敗",

                                response.code()
                            )
                        }
                    }


                    override fun onFailure(

                        call:
                        Call<WeekWeatherResponse>,

                        t:
                        Throwable

                    ) {


                        if (
                            !isAdded ||
                            view ==
                            null
                        ) {


                            return
                        }


                        weatherLoading.visibility =
                            View.GONE


                        Toast.makeText(

                            requireContext(),

                            "一週天氣連線失敗：${
                                t.message
                                    ?: "未知錯誤"
                            }",

                            Toast.LENGTH_LONG

                        ).show()
                    }
                }
            )
    }


    // =====================================================
    // 三天天氣時間格式
    //
    // 2026-09-15T06:00:00+08:00
    //
    // →
    //
    // 09/15 06:00
    // =====================================================

    private fun formatForecastTime(
        time: String?
    ): String {


        if (
            time.isNullOrBlank()
        ) {


            return "--"
        }


        return try {


            val month =
                time.substring(
                    5,
                    7
                )


            val day =
                time.substring(
                    8,
                    10
                )


            val hourMinute =
                time.substring(
                    11,
                    16
                )


            "$month/$day $hourMinute"


        } catch (
            _: Exception
        ) {


            time
        }
    }


    // =====================================================
    // 時間區間
    // =====================================================

    private fun formatForecastPeriod(

        startTime: String?,

        endTime: String?

    ): String {


        val start =
            formatForecastTime(
                startTime
            )


        val end =
            formatForecastTime(
                endTime
            )


        return "$start ～ $end"
    }


    // =====================================================
    // 一週：
    //
    // 白天 / 晚上
    // =====================================================

    private fun formatWeekPeriod(

        startTime: String?,

        endTime: String?

    ): String {


        if (
            startTime.isNullOrBlank()
        ) {


            return "--"
        }


        return try {


            val month =
                startTime.substring(
                    5,
                    7
                )


            val day =
                startTime.substring(
                    8,
                    10
                )


            val hour =
                startTime
                    .substring(
                        11,
                        13
                    )
                    .toInt()


            val periodName =

                if (
                    hour >=
                    6 &&
                    hour <
                    18
                ) {


                    "白天"


                } else {


                    "晚上"
                }


            "$month/$day $periodName"


        } catch (
            _: Exception
        ) {


            formatForecastPeriod(

                startTime,

                endTime
            )
        }
    }


    // =====================================================
    // 顯示天氣卡片
    // =====================================================

    private fun showForecastItems(
        items: List<ForecastItem>
    ) {


        weatherLoading.visibility =
            View.GONE


        forecastContainer
            .removeAllViews()


        if (
            items.isEmpty()
        ) {


            val emptyView =
                TextView(
                    requireContext()
                )


            emptyView.text =
                "目前沒有天氣預報資料"


            emptyView.textSize =
                18f


            emptyView.setPadding(

                10,

                24,

                10,

                24
            )


            forecastContainer
                .addView(
                    emptyView
                )


            return
        }


        items.forEach {
                item ->


            val itemView =
                layoutInflater
                    .inflate(

                        R.layout
                            .item_weather_forecast,

                        forecastContainer,

                        false
                    )


            val tvPeriod =
                itemView
                    .findViewById<TextView>(
                        R.id.tvItemPeriod
                    )


            val tvWeather =
                itemView
                    .findViewById<TextView>(
                        R.id.tvItemWeather
                    )


            val tvRain =
                itemView
                    .findViewById<TextView>(
                        R.id.tvItemRain
                    )


            val tvTemperature =
                itemView
                    .findViewById<TextView>(
                        R.id.tvItemTemperature
                    )


            val tvComfort =
                itemView
                    .findViewById<TextView>(
                        R.id.tvItemComfort
                    )


            val tvWindDirection =
                itemView
                    .findViewById<TextView>(
                        R.id.tvItemWindDirection
                    )


            val tvWindSpeed =
                itemView
                    .findViewById<TextView>(
                        R.id.tvItemWindSpeed
                    )


            val tvHumidity =
                itemView
                    .findViewById<TextView>(
                        R.id.tvItemHumidity
                    )


            tvPeriod.text =
                item.period


            tvWeather.text =
                "天氣狀態：${item.weather}"


            tvRain.text =
                "降雨機率：${item.rainProbability}"


            tvTemperature.text =
                "溫度：${item.temperature}"


            tvComfort.text =
                "舒適度：${item.comfort}"


            tvWindDirection.text =
                "風向：${item.windDirection}"


            tvWindSpeed.text =
                "風速：${item.windSpeed}"


            tvHumidity.text =
                "相對濕度：${item.humidity}"


            forecastContainer
                .addView(
                    itemView
                )
        }
    }


    // =====================================================
    // 後端資料更新時間
    // =====================================================

    private fun showServerUpdateTime(
        body: WeekWeatherResponse
    ) {


        if (
            !body.dataUpdatedAt
                .isNullOrBlank()
        ) {


            tvLastUpdate.text =

                if (
                    body.isStale ==
                    true
                ) {


                    "資料更新時間：${body.dataUpdatedAt}（舊資料）"


                } else {


                    "資料更新時間：${body.dataUpdatedAt}"
                }


        } else {


            updateLocalTime()
        }
    }


    // =====================================================
    // 本機時間
    // =====================================================

    private fun updateLocalTime() {


        val formatter =
            SimpleDateFormat(

                "yyyy/MM/dd HH:mm:ss",

                Locale.TAIWAN
            )


        val now =
            formatter.format(
                Date()
            )


        tvLastUpdate.text =
            "資料更新時間：$now"
    }


    // =====================================================
    // API Error
    // =====================================================

    private fun showApiError(

        message: String,

        code: Int

    ) {


        weatherLoading.visibility =
            View.GONE


        Toast.makeText(

            requireContext(),

            "$message：HTTP $code",

            Toast.LENGTH_LONG

        ).show()
    }


    // =====================================================
    // 自動更新排程
    // =====================================================

    private fun scheduleNextRefresh() {


        weatherHandler
            .removeCallbacks(
                weatherRefreshRunnable
            )


        if (
            selectedCity.isBlank() ||
            selectedTown.isBlank()
        ) {


            return
        }


        val interval =

            when (
                currentMode
            ) {


                WeatherMode.THREE_DAY ->

                    THREE_DAY_REFRESH


                WeatherMode.WEEK ->

                    WEEK_REFRESH
            }


        weatherHandler
            .postDelayed(

                weatherRefreshRunnable,

                interval
            )
    }


    // =====================================================
    // 回到頁面
    // =====================================================

    override fun onStart() {


        super.onStart()


        if (
            selectedCity.isNotBlank() &&
            selectedTown.isNotBlank()
        ) {


            scheduleNextRefresh()
        }
    }


    // =====================================================
    // 離開頁面
    // =====================================================

    override fun onStop() {


        weatherHandler
            .removeCallbacks(
                weatherRefreshRunnable
            )


        super.onStop()
    }


    // =====================================================
    // View 銷毀
    // =====================================================

    override fun onDestroyView() {


        weatherHandler
            .removeCallbacks(
                weatherRefreshRunnable
            )


        locationTokenSource
            ?.cancel()


        locationTokenSource =
            null


        super.onDestroyView()
    }
}