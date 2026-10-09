package com.example.project.data


import android.content.Context

import com.example.project.network.WeatherData
import com.example.project.network.WeatherResponse


object WeatherCache {


    // =====================================================
    // SharedPreferences
    // =====================================================

    private const val PREF_NAME =
        "weather_cache"


    // =====================================================
    // Key
    // =====================================================

    private const val KEY_HAS_DATA =
        "has_data"


    private const val KEY_LOCATION_NAME =
        "location_name"


    private const val KEY_LATITUDE =
        "latitude"


    private const val KEY_LONGITUDE =
        "longitude"


    private const val KEY_FETCH_TIME =
        "fetch_time"


    private const val KEY_WEATHER_DESC =
        "weather_desc"


    private const val KEY_RAIN_PROBABILITY =
        "rain_probability"


    private const val KEY_TEMPERATURE =
        "temperature"


    private const val KEY_COMFORT =
        "comfort"


    private const val KEY_WIND_DIRECTION =
        "wind_direction"


    private const val KEY_WIND_SPEED =
        "wind_speed"


    private const val KEY_HUMIDITY =
        "humidity"


    private const val KEY_SAVED_AT =
        "saved_at"


    // =====================================================
    // 取得 SharedPreferences
    // =====================================================

    private fun preferences(
        context: Context
    ) =
        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )


    // =====================================================
    // 儲存最新一筆天氣
    //
    // API 成功後呼叫
    // =====================================================

    fun save(

        context: Context,

        response: WeatherResponse,

        fallbackLatitude: Double,

        fallbackLongitude: Double

    ) {


        val weather =
            response.weatherData
                ?: return


        val latitude =
            response.latitude
                ?: fallbackLatitude


        val longitude =
            response.longitude
                ?: fallbackLongitude


        preferences(
            context
        )
            .edit()
            .putBoolean(
                KEY_HAS_DATA,
                true
            )
            .putString(
                KEY_LOCATION_NAME,
                response.locationName
            )
            .putLong(
                KEY_LATITUDE,
                latitude.toBits()
            )
            .putLong(
                KEY_LONGITUDE,
                longitude.toBits()
            )
            .putString(
                KEY_FETCH_TIME,
                response.fetchTime
            )
            .putString(
                KEY_WEATHER_DESC,
                weather.weatherDesc
            )
            .putString(
                KEY_RAIN_PROBABILITY,
                weather.rainProbability
            )
            .putString(
                KEY_TEMPERATURE,
                weather.temperature
            )
            .putString(
                KEY_COMFORT,
                weather.comfort
            )
            .putString(
                KEY_WIND_DIRECTION,
                weather.windDirection
            )
            .putString(
                KEY_WIND_SPEED,
                weather.windSpeed
            )
            .putString(
                KEY_HUMIDITY,
                weather.humidity
            )
            .putLong(
                KEY_SAVED_AT,
                System.currentTimeMillis()
            )
            .apply()
    }


    // =====================================================
    // 手機裡是否已有天氣資料
    // =====================================================

    fun hasCache(
        context: Context
    ): Boolean {


        return preferences(
            context
        )
            .getBoolean(
                KEY_HAS_DATA,
                false
            )
    }


    // =====================================================
    // 讀取最近一筆天氣
    // =====================================================

    fun load(
        context: Context
    ): CachedWeather? {


        val prefs =
            preferences(
                context
            )


        if (
            !prefs.getBoolean(
                KEY_HAS_DATA,
                false
            )
        ) {


            return null
        }


        val latitude =
            Double.fromBits(
                prefs.getLong(
                    KEY_LATITUDE,
                    0L
                )
            )


        val longitude =
            Double.fromBits(
                prefs.getLong(
                    KEY_LONGITUDE,
                    0L
                )
            )


        return CachedWeather(

            locationName =
                prefs.getString(
                    KEY_LOCATION_NAME,
                    null
                ),

            latitude =
                latitude,

            longitude =
                longitude,

            fetchTime =
                prefs.getString(
                    KEY_FETCH_TIME,
                    null
                ),

            weatherDesc =
                prefs.getString(
                    KEY_WEATHER_DESC,
                    null
                ),

            rainProbability =
                prefs.getString(
                    KEY_RAIN_PROBABILITY,
                    null
                ),

            temperature =
                prefs.getString(
                    KEY_TEMPERATURE,
                    null
                ),

            comfort =
                prefs.getString(
                    KEY_COMFORT,
                    null
                ),

            windDirection =
                prefs.getString(
                    KEY_WIND_DIRECTION,
                    null
                ),

            windSpeed =
                prefs.getString(
                    KEY_WIND_SPEED,
                    null
                ),

            humidity =
                prefs.getString(
                    KEY_HUMIDITY,
                    null
                ),

            savedAt =
                prefs.getLong(
                    KEY_SAVED_AT,
                    0L
                )
        )
    }


    // =====================================================
    // 把快取資料轉回 WeatherResponse
    //
    // 這樣 WorkoutFragment 原本顯示天氣的程式
    // 幾乎不用重寫
    // =====================================================

    fun loadAsWeatherResponse(
        context: Context
    ): WeatherResponse? {


        val cache =
            load(
                context
            ) ?: return null


        return cache.toWeatherResponse()
    }


    // =====================================================
    // 最近快取距離現在多久
    // =====================================================

    fun getCacheAgeMillis(
        context: Context
    ): Long {


        val savedAt =
            preferences(
                context
            )
                .getLong(
                    KEY_SAVED_AT,
                    0L
                )


        if (
            savedAt <= 0L
        ) {


            return Long.MAX_VALUE
        }


        return System.currentTimeMillis() -
                savedAt
    }


    // =====================================================
    // 是否超過一小時
    // =====================================================

    fun isOlderThanOneHour(
        context: Context
    ): Boolean {


        return getCacheAgeMillis(
            context
        ) >=
                60L * 60L * 1000L
    }


    // =====================================================
    // 測試時才會用到
    // =====================================================

    fun clear(
        context: Context
    ) {


        preferences(
            context
        )
            .edit()
            .clear()
            .apply()
    }
}


// =========================================================
// 手機裡保存的「最近一筆天氣」
// =========================================================

data class CachedWeather(

    val locationName: String?,

    val latitude: Double,

    val longitude: Double,

    val fetchTime: String?,

    val weatherDesc: String?,

    val rainProbability: String?,

    val temperature: String?,

    val comfort: String?,

    val windDirection: String?,

    val windSpeed: String?,

    val humidity: String?,

    val savedAt: Long

) {


    // =====================================================
    // 轉成你目前 App 已經在使用的 WeatherResponse
    // =====================================================

    fun toWeatherResponse():
            WeatherResponse {


        return WeatherResponse(

            status =
                "cached",

            locationName =
                locationName,

            latitude =
                latitude,

            longitude =
                longitude,

            fetchTime =
                fetchTime,

            forecastPeriod =
                null,

            weatherData =
                WeatherData(

                    weatherDesc =
                        weatherDesc,

                    rainProbability =
                        rainProbability,

                    temperature =
                        temperature,

                    comfort =
                        comfort,

                    windDirection =
                        windDirection,

                    windSpeed =
                        windSpeed,

                    humidity =
                        humidity
                )
        )
    }
}