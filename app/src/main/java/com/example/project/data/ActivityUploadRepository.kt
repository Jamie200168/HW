package com.example.project.data


import android.content.Context

import com.example.project.auth.TokenManager

import com.example.project.network.AddActivityRequest
import com.example.project.network.AddActivityResponse
import com.example.project.network.ApiClient
import com.example.project.network.FinishActivityRequest
import com.example.project.network.FinishActivityResponse
import com.example.project.network.GpsPointUpload
import com.example.project.network.RefreshRequest
import com.example.project.network.RefreshResponse

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import kotlin.math.roundToInt


object ActivityUploadRepository {


    // =====================================================
    // 對外主要入口
    //
    // existingServerActivityId == null
    //
    // → 尚未建立後端 Activity
    // → 先 add_activity
    //
    //
    // existingServerActivityId != null
    //
    // → 之前 add_activity 已經成功
    // → 不重複建立
    // → 直接 fin_activity
    // =====================================================

    fun uploadWorkout(

        context: Context,

        modeId: Int,

        title: String,

        startTimeMillis: Long,

        endTimeMillis: Long,

        durationSeconds: Long,

        distanceMeters: Double,

        routePoints: List<RoutePoint>,


        // =================================================
        // 如果 SQLite 已經有 server_activity_id，
        // 直接傳進來。
        //
        // 第一次上傳時通常是 null。
        // =================================================

        existingServerActivityId: Long? = null,


        // =================================================
        // add_activity 一成功，
        // 立刻把 activity_id 通知外面。
        //
        // TrackingService / Retry Repository
        // 可以立刻保存到 SQLite。
        //
        // 這一步非常重要，
        // 可以避免 fin_activity 失敗後
        // 重傳又建立一筆新的 Activity。
        // =================================================

        onServerActivityCreated: (
            activityId: Long
        ) -> Unit = {},


        onSuccess: (
            activityId: Long,
            gpsPointsCount: Int
        ) -> Unit,


        onError: (
            message: String
        ) -> Unit

    ) {


        // =================================================
        // 後端 fin_activity
        // 至少需要 1 個 GPS 點。
        // =================================================

        if (
            routePoints.isEmpty()
        ) {


            onError(
                "沒有 GPS 軌跡，暫時無法上傳此筆運動紀錄"
            )


            return
        }


        // =================================================
        // 取得有效 Access Token
        // =================================================

        getValidAccessToken(

            context =
                context,

            onSuccess = {
                    accessToken ->


                // =========================================
                // 已經有 server_activity_id
                //
                // 代表以前 add_activity 已成功。
                //
                // 不可以再 add_activity。
                // =========================================

                if (
                    existingServerActivityId != null
                ) {


                    finishServerActivity(

                        context =
                            context,

                        accessToken =
                            accessToken,

                        activityId =
                            existingServerActivityId,

                        endTimeMillis =
                            endTimeMillis,

                        durationSeconds =
                            durationSeconds,

                        distanceMeters =
                            distanceMeters,

                        routePoints =
                            routePoints,

                        hasRetriedAfterUnauthorized =
                            false,

                        onSuccess =
                            onSuccess,

                        onError =
                            onError
                    )


                    return@getValidAccessToken
                }


                // =========================================
                // 沒有 server_activity_id
                //
                // 第一次上傳：
                //
                // add_activity
                // →
                // activity_id
                // →
                // fin_activity
                // =========================================

                createServerActivity(

                    context =
                        context,

                    accessToken =
                        accessToken,

                    modeId =
                        modeId,

                    title =
                        title,

                    startTimeMillis =
                        startTimeMillis,

                    endTimeMillis =
                        endTimeMillis,

                    durationSeconds =
                        durationSeconds,

                    distanceMeters =
                        distanceMeters,

                    routePoints =
                        routePoints,

                    hasRetriedAfterUnauthorized =
                        false,

                    onServerActivityCreated =
                        onServerActivityCreated,

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
    // 取得可以使用的 Access Token
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


        // =================================================
        // 沒有登入資訊
        // =================================================

        if (
            accessToken.isNullOrBlank() ||
            refreshToken.isNullOrBlank()
        ) {


            onError(
                "登入資料不存在，請重新登入"
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
        // Access Token 已過期
        //
        // 用 refresh_token 取得新 token。
        // =================================================

        refreshAccessToken(

            context =
                context,

            refreshToken =
                refreshToken,

            onSuccess =
                onSuccess,

            onError =
                onError
        )
    }


    // =====================================================
    // API 真正回 401 時重新 Refresh
    //
    // 有些情況：
    //
    // 手機本機計算認為 Access Token 尚未過期，
    // 但 Server 已經判定 Token 無效。
    //
    // 此時不能直接要求重新登入。
    //
    // 流程：
    //
    // 401
    // →
    // Refresh Token
    // →
    // 新 Access Token
    // →
    // 原 API 重試一次
    // =====================================================

    private fun refreshAfterUnauthorized(

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


        // =================================================
        // 已經沒有 Refresh Token
        //
        // 無法再恢復登入狀態。
        // =================================================

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


        refreshAccessToken(

            context =
                context,

            refreshToken =
                refreshToken,

            onSuccess =
                onSuccess,

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

        refreshToken: String,

        onSuccess: (
            accessToken: String
        ) -> Unit,

        onError: (
            message: String
        ) -> Unit

    ) {


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


                        // =====================================
                        // HTTP Error
                        // =====================================

                        if (
                            !response.isSuccessful
                        ) {


                            if (
                                response.code() == 401
                            ) {


                                TokenManager.clear(
                                    context
                                )


                                onError(
                                    "登入已逾期，請重新登入"
                                )


                                return
                            }


                            onError(
                                "更新登入狀態失敗（HTTP ${response.code()}）"
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


                        // =====================================
                        // 後端自己的 status
                        // =====================================

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


                        // =====================================
                        // 只更新 Access Token。
                        //
                        // 原本 username / refresh token 保留。
                        // =====================================

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


    // =====================================================
    // 建立後端 Activity
    //
    // POST /api/v1/activity/add_activity
    //
    // hasRetriedAfterUnauthorized：
    //
    // false
    // → 這次還沒有因 401 重試過
    //
    // true
    // → 已經 Refresh + Retry 過一次
    //
    // 避免 401 無限循環。
    // =====================================================

    private fun createServerActivity(

        context: Context,

        accessToken: String,

        modeId: Int,

        title: String,

        startTimeMillis: Long,

        endTimeMillis: Long,

        durationSeconds: Long,

        distanceMeters: Double,

        routePoints: List<RoutePoint>,

        hasRetriedAfterUnauthorized: Boolean,

        onServerActivityCreated: (
            activityId: Long
        ) -> Unit,

        onSuccess: (
            activityId: Long,
            gpsPointsCount: Int
        ) -> Unit,

        onError: (
            message: String
        ) -> Unit

    ) {


        val firstPoint =
            routePoints.first()


        val request =
            AddActivityRequest(

                modeId =
                    modeId,

                title =
                    title,

                startTime =
                    formatTime(
                        startTimeMillis
                    ),

                startLatitude =
                    firstPoint.latitude,

                startLongitude =
                    firstPoint.longitude,

                startLocationName =
                    null
            )


        ApiClient
            .activityApi
            .addActivity(

                authorization =
                    "Bearer $accessToken",

                request =
                    request
            )
            .enqueue(

                object :
                    Callback<AddActivityResponse> {


                    override fun onResponse(

                        call:
                        Call<AddActivityResponse>,

                        response:
                        Response<AddActivityResponse>

                    ) {


                        // =====================================
                        // Server 真正回 401
                        //
                        // 第一次：
                        //
                        // Refresh
                        // →
                        // 原 add_activity 重試一次
                        //
                        // 第二次仍 401：
                        //
                        // 不再 Refresh
                        // →
                        // 清掉登入資料
                        // →
                        // 要求重新登入
                        // =====================================

                        if (
                            response.code() == 401
                        ) {


                            if (
                                !hasRetriedAfterUnauthorized
                            ) {


                                refreshAfterUnauthorized(

                                    context =
                                        context,

                                    onSuccess = {
                                            newAccessToken ->


                                        createServerActivity(

                                            context =
                                                context,

                                            accessToken =
                                                newAccessToken,

                                            modeId =
                                                modeId,

                                            title =
                                                title,

                                            startTimeMillis =
                                                startTimeMillis,

                                            endTimeMillis =
                                                endTimeMillis,

                                            durationSeconds =
                                                durationSeconds,

                                            distanceMeters =
                                                distanceMeters,

                                            routePoints =
                                                routePoints,

                                            hasRetriedAfterUnauthorized =
                                                true,

                                            onServerActivityCreated =
                                                onServerActivityCreated,

                                            onSuccess =
                                                onSuccess,

                                            onError =
                                                onError
                                        )
                                    },

                                    onError =
                                        onError
                                )


                                return
                            }


                            // =================================
                            // Refresh 後重試仍然 401
                            //
                            // Token 已無法使用。
                            // =================================

                            TokenManager.clear(
                                context
                            )


                            onError(
                                "登入狀態已失效，請重新登入後再同步"
                            )


                            return
                        }


                        // =====================================
                        // HTTP Error
                        // =====================================

                        if (
                            !response.isSuccessful
                        ) {


                            onError(
                                "建立運動紀錄失敗（HTTP ${response.code()}）"
                            )


                            return
                        }


                        val body =
                            response.body()


                        if (
                            body == null
                        ) {


                            onError(
                                "建立運動紀錄失敗：伺服器沒有回傳資料"
                            )


                            return
                        }


                        // =====================================
                        // API status
                        // =====================================

                        if (
                            !body.status.equals(
                                "success",
                                ignoreCase = true
                            )
                        ) {


                            onError(
                                body.message
                                    ?: "建立運動紀錄失敗"
                            )


                            return
                        }


                        val activityId =
                            body.activityId


                        if (
                            activityId == null
                        ) {


                            onError(
                                "建立成功，但伺服器沒有回傳 activity_id"
                            )


                            return
                        }


                        // =====================================
                        // 非常重要
                        //
                        // add_activity 一成功，
                        //
                        // 還沒呼叫 fin_activity 之前，
                        //
                        // 就通知外面：
                        //
                        // 「Server activity_id 已經存在」
                        //
                        // 讓 SQLite 馬上保存。
                        // =====================================

                        onServerActivityCreated(
                            activityId
                        )


                        // =====================================
                        // 接著結束活動 + 上傳 GPS
                        //
                        // fin_activity 自己有獨立的
                        // 401 Retry 一次機制。
                        // =====================================

                        finishServerActivity(

                            context =
                                context,

                            accessToken =
                                accessToken,

                            activityId =
                                activityId,

                            endTimeMillis =
                                endTimeMillis,

                            durationSeconds =
                                durationSeconds,

                            distanceMeters =
                                distanceMeters,

                            routePoints =
                                routePoints,

                            hasRetriedAfterUnauthorized =
                                false,

                            onSuccess =
                                onSuccess,

                            onError =
                                onError
                        )
                    }


                    override fun onFailure(

                        call:
                        Call<AddActivityResponse>,

                        t:
                        Throwable

                    ) {


                        // =====================================
                        // 注意：
                        //
                        // 一般網路斷線不能自動重新
                        // add_activity。
                        //
                        // 因為有可能 Server 已經建立成功，
                        // 只是 Response 沒有回到手機。
                        //
                        // 這個問題之後必須由後端
                        // idempotency 解決。
                        // =====================================

                        onError(
                            "建立運動紀錄時連線失敗：${
                                t.message
                                    ?: "未知錯誤"
                            }"
                        )
                    }
                }
            )
    }


    // =====================================================
    // 結束 Activity + GPS
    //
    // POST /api/v1/activity/fin_activity
    //
    // 401 時：
    //
    // Refresh
    // →
    // 原 fin_activity 最多重試一次
    // =====================================================

    private fun finishServerActivity(

        context: Context,

        accessToken: String,

        activityId: Long,

        endTimeMillis: Long,

        durationSeconds: Long,

        distanceMeters: Double,

        routePoints: List<RoutePoint>,

        hasRetriedAfterUnauthorized: Boolean,

        onSuccess: (
            activityId: Long,
            gpsPointsCount: Int
        ) -> Unit,

        onError: (
            message: String
        ) -> Unit

    ) {


        val lastPoint =
            routePoints.last()


        // =================================================
        // APP RoutePoint
        // →
        // FastAPI GpsPointUpload
        // =================================================

        val gpsPoints =
            routePoints
                .mapIndexed { index,
                              point ->


                    GpsPointUpload(

                        latitude =
                            point.latitude,

                        longitude =
                            point.longitude,

                        altitude =
                            point.altitude,

                        speedMs =
                            point.speedMs,

                        // =============================================
                        // Android GPS accuracy 原本可能是小數
                        //
                        // 例如：
                        // 4.7
                        //
                        // 後端 route_points.accuracy_meters
                        // 是 INT，
                        //
                        // 所以上傳前四捨五入成整數：
                        // 4.7 → 5
                        // =============================================

                        accuracyMeters =
                            point.accuracyMeters
                                ?.roundToInt(),

                        heading =
                            point.heading,

                        timestamp =
                            formatTime(
                                point.timestampMillis
                            ),

                        sequenceOrder =
                            index
                    )
                }


        val request =
            FinishActivityRequest(

                activityId =
                    activityId,

                endTime =
                    formatTime(
                        endTimeMillis
                    ),

                actualDuration =
                    durationSeconds,

                totalDistanceMeters =
                    distanceMeters,

                distanceKm =
                    distanceMeters /
                            1000.0,

                caloriesBurned =
                    null,

                endLatitude =
                    lastPoint.latitude,

                endLongitude =
                    lastPoint.longitude,

                endLocationName =
                    null,

                completionStatus =
                    "completed",

                userNotes =
                    null,

                gpsPoints =
                    gpsPoints
            )


        ApiClient
            .activityApi
            .finishActivity(

                authorization =
                    "Bearer $accessToken",

                request =
                    request
            )
            .enqueue(

                object :
                    Callback<FinishActivityResponse> {


                    override fun onResponse(

                        call:
                        Call<FinishActivityResponse>,

                        response:
                        Response<FinishActivityResponse>

                    ) {


                        // =====================================
                        // Server 真正回 401
                        //
                        // 第一次：
                        //
                        // Refresh
                        // →
                        // 原 fin_activity 重試一次
                        //
                        // 第二次仍然 401：
                        //
                        // 清除登入資料。
                        // =====================================

                        if (
                            response.code() == 401
                        ) {


                            if (
                                !hasRetriedAfterUnauthorized
                            ) {


                                refreshAfterUnauthorized(

                                    context =
                                        context,

                                    onSuccess = {
                                            newAccessToken ->


                                        finishServerActivity(

                                            context =
                                                context,

                                            accessToken =
                                                newAccessToken,

                                            activityId =
                                                activityId,

                                            endTimeMillis =
                                                endTimeMillis,

                                            durationSeconds =
                                                durationSeconds,

                                            distanceMeters =
                                                distanceMeters,

                                            routePoints =
                                                routePoints,

                                            hasRetriedAfterUnauthorized =
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


                                return
                            }


                            // =================================
                            // Refresh 後仍然 401
                            // =================================

                            TokenManager.clear(
                                context
                            )


                            onError(
                                "登入狀態已失效，請重新登入後再同步"
                            )


                            return
                        }


                        // =====================================
                        // 其他 HTTP Error
                        // =====================================

                        if (
                            !response.isSuccessful
                        ) {


                            onError(
                                "上傳運動資料失敗（HTTP ${response.code()}）"
                            )


                            return
                        }


                        val body =
                            response.body()


                        if (
                            body == null
                        ) {


                            onError(
                                "上傳失敗：伺服器沒有回傳資料"
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
                                    ?: "上傳運動資料失敗"
                            )


                            return
                        }


                        // =====================================
                        // 全部完成
                        // =====================================

                        onSuccess(

                            body.activityId
                                ?: activityId,

                            body.gpsPointsCount
                                ?: gpsPoints.size
                        )
                    }


                    override fun onFailure(

                        call:
                        Call<FinishActivityResponse>,

                        t:
                        Throwable

                    ) {


                        // =====================================
                        // 一般網路失敗不在這裡自動亂重送。
                        //
                        // SQLite 會保留：
                        //
                        // server_activity_id
                        // +
                        // failed 狀態
                        //
                        // 之後由 PendingWorkoutSyncRepository
                        // 再續傳。
                        // =====================================

                        onError(
                            "上傳運動資料時連線失敗：${
                                t.message
                                    ?: "未知錯誤"
                            }"
                        )
                    }
                }
            )
    }


    // =====================================================
    // milliseconds
    // →
    // ISO 8601
    //
    // minSdk 24 可使用。
    //
    // 例如：
    //
    // 2026-09-28T23:30:00+08:00
    // =====================================================

    private fun formatTime(
        timeMillis: Long
    ): String {


        val formatter =
            SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.US
            )


        formatter.timeZone =
            TimeZone.getDefault()


        return formatter.format(
            Date(
                timeMillis
            )
        )
    }
}