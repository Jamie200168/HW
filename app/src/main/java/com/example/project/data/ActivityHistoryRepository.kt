package com.example.project.data


import android.content.Context

import com.example.project.auth.TokenManager

import com.example.project.network.ActivityDetailResponse
import com.example.project.network.ActivityListResponse
import com.example.project.network.ApiClient
import com.example.project.network.RefreshRequest
import com.example.project.network.RefreshResponse

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


object ActivityHistoryRepository {


    // =====================================================
    // 查詢歷史運動列表
    //
    // GET
    // /api/v1/activity/qry_activity
    // =====================================================

    fun loadActivities(

        context: Context,

        page: Int = 1,

        pageSize: Int = 20,

        onSuccess: (
            response: ActivityListResponse
        ) -> Unit,

        onError: (
            message: String
        ) -> Unit

    ) {


        val appContext =
            context.applicationContext


        getValidAccessToken(

            context =
                appContext,

            onSuccess = {
                    accessToken ->


                requestActivityList(

                    context =
                        appContext,

                    accessToken =
                        accessToken,

                    page =
                        page,

                    pageSize =
                        pageSize,

                    allowRefreshRetry =
                        true,

                    onSuccess =
                        onSuccess,

                    onError =
                        onError
                )
            },

            onError =
                onError
        )
    }


    // =====================================================
    // 查詢單筆運動詳細資料 + GPS
    //
    // GET
    // /api/v1/activity/qry_activity/{activity_id}
    // =====================================================

    fun loadActivityDetail(

        context: Context,

        activityId: Long,

        onSuccess: (
            response: ActivityDetailResponse
        ) -> Unit,

        onError: (
            message: String
        ) -> Unit

    ) {


        val appContext =
            context.applicationContext


        getValidAccessToken(

            context =
                appContext,

            onSuccess = {
                    accessToken ->


                requestActivityDetail(

                    context =
                        appContext,

                    accessToken =
                        accessToken,

                    activityId =
                        activityId,

                    allowRefreshRetry =
                        true,

                    onSuccess =
                        onSuccess,

                    onError =
                        onError
                )
            },

            onError =
                onError
        )
    }


    // =====================================================
    // 真正呼叫活動列表 API
    // =====================================================

    private fun requestActivityList(

        context: Context,

        accessToken: String,

        page: Int,

        pageSize: Int,

        allowRefreshRetry: Boolean,

        onSuccess: (
            response: ActivityListResponse
        ) -> Unit,

        onError: (
            message: String
        ) -> Unit

    ) {


        ApiClient
            .activityApi
            .queryActivities(

                authorization =
                    "Bearer $accessToken",

                page =
                    page,

                pageSize =
                    pageSize
            )
            .enqueue(

                object :
                    Callback<ActivityListResponse> {


                    override fun onResponse(

                        call:
                        Call<ActivityListResponse>,

                        response:
                        Response<ActivityListResponse>

                    ) {


                        // =====================================
                        // Token 被後端判定失效
                        //
                        // 即使手機自己判定還沒到期，
                        // Server 還是可能回 401。
                        //
                        // 這時 Refresh 一次，
                        // 然後重試原本 API。
                        // =====================================

                        if (
                            response.code() == 401 &&
                            allowRefreshRetry
                        ) {


                            refreshAndRetryActivityList(

                                context =
                                    context,

                                page =
                                    page,

                                pageSize =
                                    pageSize,

                                onSuccess =
                                    onSuccess,

                                onError =
                                    onError
                            )


                            return
                        }


                        if (
                            response.code() == 401
                        ) {


                            onError(
                                "登入已逾期，請重新登入"
                            )


                            return
                        }


                        if (
                            !response.isSuccessful
                        ) {


                            onError(
                                "取得歷史紀錄失敗（HTTP ${response.code()}）"
                            )


                            return
                        }


                        val body =
                            response.body()


                        if (
                            body == null
                        ) {


                            onError(
                                "取得歷史紀錄失敗：伺服器沒有回傳資料"
                            )


                            return
                        }


                        if (
                            !body.status.equals(
                                "success",
                                ignoreCase = true
                            )
                        ) {


                            onError(
                                body.message
                                    ?: "取得歷史紀錄失敗"
                            )


                            return
                        }


                        onSuccess(
                            body
                        )
                    }


                    override fun onFailure(

                        call:
                        Call<ActivityListResponse>,

                        t:
                        Throwable

                    ) {


                        onError(
                            "取得歷史紀錄時連線失敗：${
                                t.message
                                    ?: "未知錯誤"
                            }"
                        )
                    }
                }
            )
    }


    // =====================================================
    // 真正呼叫單筆 Activity API
    // =====================================================

    private fun requestActivityDetail(

        context: Context,

        accessToken: String,

        activityId: Long,

        allowRefreshRetry: Boolean,

        onSuccess: (
            response: ActivityDetailResponse
        ) -> Unit,

        onError: (
            message: String
        ) -> Unit

    ) {


        ApiClient
            .activityApi
            .queryActivityDetail(

                authorization =
                    "Bearer $accessToken",

                activityId =
                    activityId,

                withTrack =
                    true
            )
            .enqueue(

                object :
                    Callback<ActivityDetailResponse> {


                    override fun onResponse(

                        call:
                        Call<ActivityDetailResponse>,

                        response:
                        Response<ActivityDetailResponse>

                    ) {


                        // =====================================
                        // 401
                        // →
                        // Refresh
                        // →
                        // 重試一次
                        // =====================================

                        if (
                            response.code() == 401 &&
                            allowRefreshRetry
                        ) {


                            refreshAndRetryActivityDetail(

                                context =
                                    context,

                                activityId =
                                    activityId,

                                onSuccess =
                                    onSuccess,

                                onError =
                                    onError
                            )


                            return
                        }


                        if (
                            response.code() == 401
                        ) {


                            onError(
                                "登入已逾期，請重新登入"
                            )


                            return
                        }


                        // =====================================
                        // 404
                        //
                        // 後端如果不是自己的 Activity，
                        // 或 Activity 不存在，
                        // 都可能回 404。
                        // =====================================

                        if (
                            response.code() == 404
                        ) {


                            onError(
                                "找不到這筆運動紀錄"
                            )


                            return
                        }


                        if (
                            !response.isSuccessful
                        ) {


                            onError(
                                "取得運動詳細資料失敗（HTTP ${response.code()}）"
                            )


                            return
                        }


                        val body =
                            response.body()


                        if (
                            body == null
                        ) {


                            onError(
                                "取得運動詳細資料失敗：伺服器沒有回傳資料"
                            )


                            return
                        }


                        if (
                            !body.status.equals(
                                "success",
                                ignoreCase = true
                            )
                        ) {


                            onError(
                                body.message
                                    ?: "取得運動詳細資料失敗"
                            )


                            return
                        }


                        onSuccess(
                            body
                        )
                    }


                    override fun onFailure(

                        call:
                        Call<ActivityDetailResponse>,

                        t:
                        Throwable

                    ) {


                        onError(
                            "取得運動詳細資料時連線失敗：${
                                t.message
                                    ?: "未知錯誤"
                            }"
                        )
                    }
                }
            )
    }


    // =====================================================
    // 取得目前可使用的 Access Token
    //
    // 手機自己知道 Token 已過期：
    //
    // → 先 Refresh
    //
    // 還沒過期：
    //
    // → 直接使用
    // =====================================================

    private fun getValidAccessToken(

        context: Context,

        onSuccess: (
            accessToken: String
        ) -> Unit,

        onError: (
            message: String
        ) -> Unit

    ) {


        val accessToken =
            TokenManager
                .getAccessToken(
                    context
                )


        val refreshToken =
            TokenManager
                .getRefreshToken(
                    context
                )


        if (
            accessToken.isNullOrBlank() ||
            refreshToken.isNullOrBlank()
        ) {


            onError(
                "沒有登入資料，請重新登入"
            )


            return
        }


        // =================================================
        // Access Token 還沒過期
        // =================================================

        if (
            !TokenManager
                .isAccessTokenExpired(
                    context
                )
        ) {


            onSuccess(
                accessToken
            )


            return
        }


        // =================================================
        // 手機已知道 Access Token 過期
        // =================================================

        refreshAccessToken(

            context =
                context,

            onSuccess =
                onSuccess,

            onError =
                onError
        )
    }


    // =====================================================
    // List API 收到 401
    //
    // 強制 Refresh 後重試一次。
    // =====================================================

    private fun refreshAndRetryActivityList(

        context: Context,

        page: Int,

        pageSize: Int,

        onSuccess: (
            response: ActivityListResponse
        ) -> Unit,

        onError: (
            message: String
        ) -> Unit

    ) {


        refreshAccessToken(

            context =
                context,

            onSuccess = {
                    newAccessToken ->


                requestActivityList(

                    context =
                        context,

                    accessToken =
                        newAccessToken,

                    page =
                        page,

                    pageSize =
                        pageSize,

                    allowRefreshRetry =
                        false,

                    onSuccess =
                        onSuccess,

                    onError =
                        onError
                )
            },

            onError =
                onError
        )
    }


    // =====================================================
    // Detail API 收到 401
    //
    // 強制 Refresh 後重試一次。
    // =====================================================

    private fun refreshAndRetryActivityDetail(

        context: Context,

        activityId: Long,

        onSuccess: (
            response: ActivityDetailResponse
        ) -> Unit,

        onError: (
            message: String
        ) -> Unit

    ) {


        refreshAccessToken(

            context =
                context,

            onSuccess = {
                    newAccessToken ->


                requestActivityDetail(

                    context =
                        context,

                    accessToken =
                        newAccessToken,

                    activityId =
                        activityId,

                    allowRefreshRetry =
                        false,

                    onSuccess =
                        onSuccess,

                    onError =
                        onError
                )
            },

            onError =
                onError
        )
    }


    // =====================================================
    // Refresh Access Token
    //
    // POST /api/v1/users/refresh
    // =====================================================

    private fun refreshAccessToken(

        context: Context,

        onSuccess: (
            accessToken: String
        ) -> Unit,

        onError: (
            message: String
        ) -> Unit

    ) {


        val refreshToken =
            TokenManager
                .getRefreshToken(
                    context
                )


        if (
            refreshToken.isNullOrBlank()
        ) {


            TokenManager.clear(
                context
            )


            onError(
                "登入已逾期，請重新登入"
            )


            return
        }


        val request =
            RefreshRequest(

                refreshToken =
                    refreshToken
            )


        ApiClient
            .userApi
            .refresh(
                request
            )
            .enqueue(

                object :
                    Callback<RefreshResponse> {


                    override fun onResponse(

                        call:
                        Call<RefreshResponse>,

                        response:
                        Response<RefreshResponse>

                    ) {


                        if (
                            !response.isSuccessful
                        ) {


                            if (
                                response.code() == 401
                            ) {


                                TokenManager.clear(
                                    context
                                )
                            }


                            onError(
                                "登入已逾期，請重新登入"
                            )


                            return
                        }


                        val body =
                            response.body()


                        if (
                            body == null
                        ) {


                            onError(
                                "更新登入狀態失敗：伺服器沒有回傳資料"
                            )


                            return
                        }


                        if (
                            !body.status.equals(
                                "success",
                                ignoreCase = true
                            )
                        ) {


                            TokenManager.clear(
                                context
                            )


                            onError(
                                body.message
                                    ?: "登入已逾期，請重新登入"
                            )


                            return
                        }


                        val newAccessToken =
                            body.accessToken


                        if (
                            newAccessToken.isNullOrBlank()
                        ) {


                            onError(
                                "伺服器沒有回傳新的 Access Token"
                            )


                            return
                        }


                        TokenManager
                            .saveAccessToken(

                                context =
                                    context,

                                accessToken =
                                    newAccessToken,

                                tokenType =
                                    body.tokenType
                                        ?: "bearer",

                                expiresIn =
                                    body.expiresIn
                                        ?: 1800
                            )


                        onSuccess(
                            newAccessToken
                        )
                    }


                    override fun onFailure(

                        call:
                        Call<RefreshResponse>,

                        t:
                        Throwable

                    ) {


                        onError(
                            "更新登入狀態時連線失敗：${
                                t.message
                                    ?: "未知錯誤"
                            }"
                        )
                    }
                }
            )
    }
}