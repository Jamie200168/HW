package com.example.project.data

import android.content.Context

import com.example.project.network.ApiClient
import com.example.project.network.WeatherResponse

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


data class WeatherUiState(

    val loading: Boolean = false,

    val data: WeatherResponse? = null,

    val error: String? = null,

    // true = 目前畫面顯示的是手機裡的舊資料
    // false = 剛從 API 成功取得
    val isFromCache: Boolean = false
)


object WeatherRepository {


    private val _state =
        MutableStateFlow(
            WeatherUiState()
        )


    val state:
            StateFlow<WeatherUiState> =

        _state.asStateFlow()


    // =====================================================
    // 只讀取手機最近一筆天氣
    //
    // 不需要網路
    // 進 Workout 畫面時可以先呼叫
    // =====================================================

    fun loadCachedWeather(
        context: Context
    ) {


        val cached =
            WeatherCache.loadAsWeatherResponse(
                context.applicationContext
            )


        if (
            cached != null
        ) {


            _state.value =
                WeatherUiState(

                    loading = false,

                    data = cached,

                    error = null,

                    isFromCache = true
                )
        }
    }


    // =====================================================
    // 依 GPS 座標取得目前天氣
    //
    // 流程：
    //
    // 1. 先把手機快取顯示出來
    // 2. 再嘗試連 FastAPI
    // 3. 成功 -> 儲存最新資料
    // 4. 失敗 -> 繼續顯示舊資料
    // =====================================================

    fun refreshWeather(

        context: Context,

        latitude: Double,

        longitude: Double

    ) {


        val appContext =
            context.applicationContext


        // =================================================
        // 先讀手機裡最近一筆資料
        // =================================================

        val cached =
            WeatherCache.loadAsWeatherResponse(
                appContext
            )


        // =================================================
        // 如果有快取
        //
        // API 還在讀取期間
        // 畫面也不會變成空白
        // =================================================

        _state.value =
            WeatherUiState(

                loading = true,

                data = cached,

                error = null,

                isFromCache =
                    cached != null
            )


        // =================================================
        // 呼叫 Raspberry Pi FastAPI
        // =================================================

        ApiClient
            .weatherApi
            .getCurrentWeather(

                latitude =
                    latitude,

                longitude =
                    longitude
            )

            .enqueue(

                object :
                    Callback<WeatherResponse> {


                    // =========================================
                    // FastAPI 有回應
                    // =========================================

                    override fun onResponse(

                        call:
                        Call<WeatherResponse>,

                        response:
                        Response<WeatherResponse>

                    ) {


                        val body =
                            response.body()


                        // =====================================
                        // API 成功
                        // =====================================

                        if (
                            response.isSuccessful &&
                            body != null
                        ) {


                            // =================================
                            // 最新天氣存進手機
                            // =================================

                            WeatherCache.save(

                                context =
                                    appContext,

                                response =
                                    body,

                                fallbackLatitude =
                                    latitude,

                                fallbackLongitude =
                                    longitude
                            )


                            // =================================
                            // 畫面顯示最新 API 資料
                            // =================================

                            _state.value =
                                WeatherUiState(

                                    loading = false,

                                    data = body,

                                    error = null,

                                    isFromCache = false
                                )


                        } else {


                            // =================================
                            // API 回傳錯誤
                            //
                            // 有舊資料：
                            // 不清掉，繼續顯示
                            //
                            // 沒舊資料：
                            // 才顯示錯誤
                            // =================================

                            _state.value =
                                WeatherUiState(

                                    loading = false,

                                    data = cached,

                                    error =
                                        if (
                                            cached == null
                                        ) {

                                            "API 回傳錯誤：${response.code()}"

                                        } else {

                                            "無法取得最新天氣，目前顯示最近一次資料"
                                        },

                                    isFromCache =
                                        cached != null
                                )
                        }
                    }


                    // =========================================
                    // Raspberry Pi / 網路無法連線
                    // =========================================

                    override fun onFailure(

                        call:
                        Call<WeatherResponse>,

                        t:
                        Throwable

                    ) {


                        // =====================================
                        // 重點：
                        //
                        // API 失敗時絕對不要把 cached 清空
                        // =====================================

                        _state.value =
                            WeatherUiState(

                                loading = false,

                                data = cached,

                                error =
                                    if (
                                        cached == null
                                    ) {

                                        t.message
                                            ?: "目前無法取得天氣資料"

                                    } else {

                                        "目前無法連線，顯示最近一次天氣資料"
                                    },

                                isFromCache =
                                    cached != null
                            )
                    }
                }
            )
    }
}