package com.example.project.network


import retrofit2.Call

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query


interface ActivityApiService {


    // =====================================================
    // 建立 Activity
    // =====================================================

    @POST(
        "api/v1/activity/add_activity"
    )
    fun addActivity(

        @Header(
            "Authorization"
        )
        authorization: String,

        @Body
        request: AddActivityRequest

    ): Call<AddActivityResponse>


    // =====================================================
    // 結束 Activity + GPS
    // =====================================================

    @POST(
        "api/v1/activity/fin_activity"
    )
    fun finishActivity(

        @Header(
            "Authorization"
        )
        authorization: String,

        @Body
        request: FinishActivityRequest

    ): Call<FinishActivityResponse>


    // =====================================================
    // 歷史紀錄
    // =====================================================

    @GET(
        "api/v1/activity/qry_activity"
    )
    fun queryActivities(

        @Header(
            "Authorization"
        )
        authorization: String,


        @Query(
            "page"
        )
        page: Int =
            1,


        @Query(
            "page_size"
        )
        pageSize: Int =
            10,


        @Query(
            "mode_id"
        )
        modeId: Int? =
            null,


        @Query(
            "status"
        )
        status: String? =
            null,


        @Query(
            "start_date"
        )
        startDate: String? =
            null,


        @Query(
            "end_date"
        )
        endDate: String? =
            null

    ): Call<ActivityListResponse>


    // =====================================================
    // 單筆詳細資料 + GPS
    // =====================================================

    @GET(
        "api/v1/activity/qry_activity/{activity_id}"
    )
    fun queryActivityDetail(

        @Header(
            "Authorization"
        )
        authorization: String,


        @Path(
            "activity_id"
        )
        activityId: Long,


        @Query(
            "with_track"
        )
        withTrack: Boolean =
            true

    ): Call<ActivityDetailResponse>


    // =====================================================
    // 每週運動統計
    //
    // 預計新增的後端 API：
    //
    // GET
    // /api/v1/activity/weekly_summary
    //
    // start_date = 2026-10-05
    // end_date   = 2026-10-11
    // mode_id    = 2
    // =====================================================

    @GET(
        "api/v1/activity/get_week_report"
    )
    fun getWeekReport(

        @Header(
            "Authorization"
        )
        authorization: String,


        @Query(
            "year"
        )
        year: Int,


        @Query(
            "month"
        )
        month: Int,


        @Query(
            "week"
        )
        week: Int

    ): Call<WeeklySummaryResponse>
}