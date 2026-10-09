package com.example.project.data


import android.content.Context

import com.example.project.auth.TokenManager

import com.example.project.network.ApiClient
import com.example.project.network.RefreshRequest
import com.example.project.network.RefreshResponse
import com.example.project.network.WeeklySummaryResponse

import org.json.JSONArray
import org.json.JSONObject

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


object WeeklySummaryRepository {


    // =====================================================
    // 對外入口
    // =====================================================

    fun loadWeekReport(

        context: Context,

        year: Int,

        month: Int,

        week: Int,

        onSuccess:
            (
            WeeklySummaryResponse
        ) -> Unit,

        onError:
            (
            String
        ) -> Unit

    ) {


        val appContext =
            context.applicationContext


        getValidAccessToken(

            context =
                appContext,


            onSuccess = {
                    accessToken ->


                requestWeekReport(

                    context =
                        appContext,

                    accessToken =
                        accessToken,

                    year =
                        year,

                    month =
                        month,

                    week =
                        week,

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
    // 真正呼叫：
    //
    // GET /api/v1/activity/get_week_report
    // =====================================================

    private fun requestWeekReport(

        context: Context,

        accessToken: String,

        year: Int,

        month: Int,

        week: Int,

        allowRefreshRetry: Boolean,

        onSuccess:
            (
            WeeklySummaryResponse
        ) -> Unit,

        onError:
            (
            String
        ) -> Unit

    ) {


        ApiClient
            .activityApi
            .getWeekReport(

                authorization =
                    "Bearer $accessToken",

                year =
                    year,

                month =
                    month,

                week =
                    week
            )
            .enqueue(

                object :
                    Callback<WeeklySummaryResponse> {


                    override fun onResponse(

                        call:
                        Call<WeeklySummaryResponse>,

                        response:
                        Response<WeeklySummaryResponse>

                    ) {


                        // =====================================
                        // Token 失效
                        //
                        // 只 Refresh 一次。
                        // =====================================

                        if (
                            response.code() ==
                            401 &&
                            allowRefreshRetry
                        ) {


                            refreshAccessToken(

                                context =
                                    context,


                                onSuccess = {
                                        newAccessToken ->


                                    requestWeekReport(

                                        context =
                                            context,

                                        accessToken =
                                            newAccessToken,

                                        year =
                                            year,

                                        month =
                                            month,

                                        week =
                                            week,

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


                            return
                        }


                        // =====================================
                        // Refresh 過仍 401
                        // =====================================

                        if (
                            response.code() ==
                            401
                        ) {


                            TokenManager.clear(
                                context
                            )


                            onError(
                                "登入已逾期，請重新登入"
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

                                parseHttpError(
                                    response
                                )
                            )


                            return
                        }


                        val body =
                            response.body()


                        if (
                            body ==
                            null
                        ) {


                            onError(
                                "後端沒有回傳每週統計資料"
                            )


                            return
                        }


                        // =====================================
                        // HTTP 200
                        // 仍要判斷 status
                        // =====================================

                        if (
                            !body.status.equals(

                                "success",

                                ignoreCase =
                                    true
                            )
                        ) {


                            onError(

                                body.message
                                    ?: "取得每週統計失敗"
                            )


                            return
                        }


                        onSuccess(
                            body
                        )
                    }


                    override fun onFailure(

                        call:
                        Call<WeeklySummaryResponse>,

                        t:
                        Throwable

                    ) {


                        onError(

                            "取得每週統計時連線失敗：${
                                t.message
                                    ?: "未知錯誤"
                            }"
                        )
                    }
                }
            )
    }


    // =====================================================
    // 取得可用 Access Token
    // =====================================================

    private fun getValidAccessToken(

        context: Context,

        onSuccess:
            (
            String
        ) -> Unit,

        onError:
            (
            String
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
    // Refresh Access Token
    // =====================================================

    private fun refreshAccessToken(

        context: Context,

        onSuccess:
            (
            String
        ) -> Unit,

        onError:
            (
            String
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


        ApiClient
            .userApi
            .refresh(

                RefreshRequest(

                    refreshToken =
                        refreshToken
                )
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
                                response.code() ==
                                401
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
                            body ==
                            null
                        ) {


                            onError(
                                "更新登入狀態失敗"
                            )


                            return
                        }


                        if (
                            !body.status.equals(

                                "success",

                                ignoreCase =
                                    true
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
                                "後端沒有回傳新的 Access Token"
                            )


                            return
                        }


                        TokenManager.saveAccessToken(

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
    // HTTP Error
    // =====================================================

    private fun parseHttpError(
        response: Response<WeeklySummaryResponse>
    ): String {


        val rawBody =
            response
                .errorBody()
                ?.string()
                ?.trim()


        if (
            !rawBody.isNullOrBlank()
        ) {


            try {


                val json =
                    JSONObject(
                        rawBody
                    )


                val message =
                    json
                        .optString(
                            "message"
                        )
                        .trim()


                if (
                    message.isNotBlank()
                ) {


                    return message
                }


                val detail =
                    json.opt(
                        "detail"
                    )


                if (
                    detail is String &&
                    detail.isNotBlank()
                ) {


                    return detail
                }


                if (
                    detail is JSONArray
                ) {


                    val messages =
                        mutableListOf<String>()


                    for (
                    index in
                    0 until detail.length()
                    ) {


                        val item =
                            detail
                                .optJSONObject(
                                    index
                                )


                        val msg =
                            item
                                ?.optString(
                                    "msg"
                                )
                                ?.trim()


                        if (
                            !msg.isNullOrBlank()
                        ) {


                            messages.add(
                                msg
                            )
                        }
                    }


                    if (
                        messages.isNotEmpty()
                    ) {


                        return messages
                            .joinToString(
                                "\n"
                            )
                    }
                }


            } catch (
                _: Exception
            ) {


                // 使用下面的 fallback。
            }
        }


        return when (
            response.code()
        ) {


            401 ->
                "登入已逾期，請重新登入"


            404 ->
                "找不到每週統計接口"


            422 ->
                "週別參數不符合後端規格"


            500 ->
                "伺服器發生錯誤"


            else ->
                "取得每週統計失敗（HTTP ${response.code()}）"
        }
    }
}