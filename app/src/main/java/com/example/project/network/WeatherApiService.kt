package com.example.project.network

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query


interface WeatherApiService {


    // =====================================================
    // 運動畫面：GPS 查目前天氣
    // =====================================================

    @GET("api/v1/weather/get_current")
    fun getCurrentWeather(

        @Query("lat")
        latitude: Double,

        @Query("lng")
        longitude: Double

    ): Call<WeatherResponse>


    // =====================================================
    // 三天預報
    //
    // countyName：
    // 桃園市
    //
    // locationName：
    // 中壢區
    // =====================================================

    @GET("api/v1/weather/get_3days32")
    fun getThreeDayWeather(

        @Query("countyName")
        countyName: String,

        @Query("locationName")
        locationName: String

    ): Call<WeekWeatherResponse>


    // =====================================================
    // 一週預報
    // =====================================================

    @GET("api/v1/weather/get_week")
    fun getWeekWeather(

        @Query("countyName")
        countyName: String,

        @Query("locationName")
        locationName: String

    ): Call<WeekWeatherResponse>


    // =====================================================
    // FastAPI 健康檢查
    // =====================================================

    @GET("health")
    fun checkHealth():
            Call<String>
}