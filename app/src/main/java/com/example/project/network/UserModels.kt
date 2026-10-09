package com.example.project.network


import com.google.gson.annotations.SerializedName


// =====================================================
// 註冊 Request
//
// POST /api/v1/users/register
// =====================================================

data class RegisterRequest(

    val username: String,

    val email: String,

    val password: String
)


// =====================================================
// 登入 Request
//
// POST /api/v1/users/login
//
// account 可以是：
// 1. username
// 2. email
// =====================================================

data class LoginRequest(

    val account: String,

    val password: String
)


// =====================================================
// 登入 Response
//
// 登入成功後 FastAPI 目前會回：
//
// {
//   "status": "success",
//   "access_token": "...",
//   "refresh_token": "...",
//   "token_type": "bearer",
//   "expires_in": 1800,
//   "username": "..."
// }
//
// 如果登入失敗，可能只有：
//
// {
//   "status": "error",
//   "message": "..."
// }
//
// 所以 Token 欄位都設成 nullable。
// =====================================================

data class LoginResponse(

    val status: String? = null,

    val message: String? = null,

    @SerializedName("access_token")
    val accessToken: String? = null,

    @SerializedName("refresh_token")
    val refreshToken: String? = null,

    @SerializedName("token_type")
    val tokenType: String? = null,

    @SerializedName("expires_in")
    val expiresIn: Int? = null,

    val username: String? = null
)


// =====================================================
// Refresh Token Request
//
// POST /api/v1/users/refresh
// =====================================================

data class RefreshRequest(

    @SerializedName("refresh_token")
    val refreshToken: String
)


// =====================================================
// Refresh Token Response
// =====================================================

data class RefreshResponse(

    val status: String? = null,

    val message: String? = null,

    @SerializedName("access_token")
    val accessToken: String? = null,

    @SerializedName("token_type")
    val tokenType: String? = null,

    @SerializedName("expires_in")
    val expiresIn: Int? = null
)


// =====================================================
// 登出 Request
//
// POST /api/v1/users/logout
// =====================================================

data class LogoutRequest(

    @SerializedName("refresh_token")
    val refreshToken: String
)


// =====================================================
// 一般 status / message Response
// =====================================================

data class SimpleStatusResponse(

    val status: String? = null,

    val message: String? = null
)