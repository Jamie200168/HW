package com.example.project.network


import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object ApiClient {


    // =====================================================
    // Raspberry Pi FastAPI
    // =====================================================

    private const val BASE_URL =
        "http://192.168.0.15:8088/"


    // =====================================================
    // Retrofit
    // =====================================================

    private val retrofit: Retrofit by lazy {


        Retrofit.Builder()

            .baseUrl(
                BASE_URL
            )

            .addConverterFactory(
                GsonConverterFactory.create()
            )

            .build()
    }


    // =====================================================
    // 天氣 API
    // =====================================================

    val weatherApi:
            WeatherApiService by lazy {


        retrofit.create(
            WeatherApiService::class.java
        )
    }


    // =====================================================
    // 使用者 API
    // =====================================================

    val userApi:
            UserApiService by lazy {


        retrofit.create(
            UserApiService::class.java
        )
    }


    // =====================================================
    // 運動紀錄 API
    // =====================================================

    val activityApi:
            ActivityApiService by lazy {


        retrofit.create(
            ActivityApiService::class.java
        )
    }
}