package com.example.project.network


import com.google.gson.annotations.SerializedName


data class WeeklySummaryResponse(

    val status: String? = null,

    val message: String? = null,

    val year: Int? = null,

    val month: Int? = null,

    val week: Int? = null,


    @SerializedName("week_start_date")
    val weekStartDate: String? = null,


    @SerializedName("week_end_date")
    val weekEndDate: String? = null,


    val statistics: List<WeeklyStatistic> =
        emptyList()
)


data class WeeklyStatistic(

    // 1 = 爬山
    // 2 = 健走
    // 3 = 單車
    @SerializedName("mode_id")
    val modeId: Int? = null,


    // 這一週的統計起始日期
    @SerializedName("stat_date")
    val statDate: String? = null,


    // 這週此運動共有幾次
    @SerializedName("activities_count")
    val activitiesCount: Int = 0,


    // 總距離，單位：公尺
    @SerializedName("total_distance_meters")
    val totalDistanceMeters: Double = 0.0,


    // 總運動時間，單位：秒
    @SerializedName("total_duration_seconds")
    val totalDurationSeconds: Long = 0L,


    // 平均速度，單位：m/s
    @SerializedName("avg_speed_ms")
    val avgSpeedMs: Double? = null,


    // 總爬升，單位：公尺
    @SerializedName("total_elevation_gain_meters")
    val totalElevationGainMeters: Double = 0.0,


    // =================================================
    // 目前後端截圖還沒有這個欄位。
    //
    // 但你的需求：
    // 健走 / 登山要顯示一週總步數。
    //
    // 所以先預留。
    // 後端之後增加後 APP 不必再改 Model。
    // =================================================
    @SerializedName("total_steps")
    val totalSteps: Long? = null
)