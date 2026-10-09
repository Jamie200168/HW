package com.example.project.network

import com.google.gson.annotations.SerializedName


data class WeekWeatherResponse(

    val status: String? = null,


    @SerializedName("county_name")
    val countyName: String? = null,


    @SerializedName("location_name")
    val locationName: String? = null,


    @SerializedName("data_updated_at")
    val dataUpdatedAt: String? = null,


    @SerializedName("is_stale")
    val isStale: Boolean? = null,


    @SerializedName("total_periods")
    val totalPeriods: Int? = null,


    @SerializedName("forecast_list")
    val forecastList:
    List<ForecastPeriodResponse>? = null
)


data class ForecastPeriodResponse(

    @SerializedName("start_time")
    val startTime: String? = null,


    @SerializedName("end_time")
    val endTime: String? = null,


    @SerializedName("weather_desc")
    val weatherDesc: String? = null,


    @SerializedName("rain_probability")
    val rainProbability: String? = null,


    val temperature: String? = null,


    val comfort: String? = null,


    @SerializedName("wind_direction")
    val windDirection: String? = null,


    @SerializedName("wind_speed")
    val windSpeed: String? = null,


    val humidity: String? = null
)