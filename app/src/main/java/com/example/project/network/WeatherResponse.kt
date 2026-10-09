package com.example.project.network

import com.google.gson.annotations.SerializedName


data class WeatherResponse(

    val status: String? = null,

    @SerializedName("location_name")
    val locationName: String? = null,

    val latitude: Double? = null,

    val longitude: Double? = null,

    @SerializedName("fetch_time")
    val fetchTime: String? = null,

    @SerializedName("forecast_period")
    val forecastPeriod: ForecastPeriod? = null,

    @SerializedName("weather_data")
    val weatherData: WeatherData? = null
)


data class ForecastPeriod(

    @SerializedName("start_time")
    val startTime: String? = null,

    @SerializedName("end_time")
    val endTime: String? = null
)


data class WeatherData(

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