package com.example.project.network


import okhttp3.ResponseBody

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST


interface UserApiService {


    // =====================================================
    // 使用者註冊
    //
    // 註冊目前先維持 ResponseBody，
    // 因為我們還沒有正式固定它所有 Response 欄位。
    // =====================================================

    @POST("api/v1/users/register")
    fun register(

        @Body request: RegisterRequest

    ): Call<ResponseBody>


    // =====================================================
    // 使用者登入
    //
    // 現在已經知道 JWT Response 格式，
    // 所以改成 LoginResponse。
    // =====================================================

    @POST("api/v1/users/login")
    fun login(

        @Body request: LoginRequest

    ): Call<LoginResponse>


    // =====================================================
    // 刷新 Access Token
    // =====================================================

    @POST("api/v1/users/refresh")
    fun refresh(

        @Body request: RefreshRequest

    ): Call<RefreshResponse>


    // =====================================================
    // 登出
    // =====================================================

    @POST("api/v1/users/logout")
    fun logout(

        @Body request: LogoutRequest

    ): Call<SimpleStatusResponse>
}