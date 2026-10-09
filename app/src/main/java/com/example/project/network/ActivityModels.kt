package com.example.project.network


import com.google.gson.annotations.SerializedName


// =====================================================
// GET /api/v1/activity/qry_activity
// 運動列表 Response
// =====================================================

data class ActivityListResponse(

    val status: String? = null,

    val total: Int = 0,

    val page: Int = 1,

    @SerializedName("page_size")
    val pageSize: Int = 10,

    val activities: List<ActivitySummary> =
        emptyList(),

    val message: String? = null
)


// =====================================================
// 運動列表中的單筆摘要
// =====================================================

data class ActivitySummary(

    @SerializedName("activity_id")
    val activityId: Long,

    val title: String? = null,

    @SerializedName("mode_id")
    val modeId: Int? = null,

    @SerializedName("start_time")
    val startTime: String? = null,

    @SerializedName("end_time")
    val endTime: String? = null,

    @SerializedName("total_distance_meters")
    val totalDistanceMeters: Double? = null,

    val status: String? = null
)


// =====================================================
// POST /api/v1/activity/add_activity
//
// 使用者開始一筆新的運動紀錄時，
// 後端需要的資料。
// =====================================================

data class AddActivityRequest(

    // 運動模式 ID
    @SerializedName("mode_id")
    val modeId: Int,


    // 運動標題
    val title: String,


    // ISO 8601 時間
    //
    // 例如：
    // 2026-09-24T08:00:00+08:00
    @SerializedName("start_time")
    val startTime: String,


    // 起點緯度
    @SerializedName("start_latitude")
    val startLatitude: Double,


    // 起點經度
    @SerializedName("start_longitude")
    val startLongitude: Double,


    // 起點名稱可以沒有
    @SerializedName("start_location_name")
    val startLocationName: String? = null
)


// =====================================================
// add_activity Response
//
// 成功：
//
// {
//   "status": "success",
//   "activity_id": 4
// }
// =====================================================

data class AddActivityResponse(

    val status: String? = null,

    val message: String? = null,

    @SerializedName("activity_id")
    val activityId: Long? = null
)


// =====================================================
// GPS 單一座標點
//
// fin_activity 裡的 gps_points 使用。
// =====================================================

data class GpsPointUpload(

    // 緯度
    val latitude: Double,


    // 經度
    val longitude: Double,


    // 海拔
    val altitude: Double? = null,


    // 移動速度 m/s
    @SerializedName("speed_ms")
    val speedMs: Double? = null,


    // GPS 精準度
    @SerializedName("accuracy_meters")
    val accuracyMeters: Int? = null,


    // 前進方向
    val heading: Double? = null,


    // GPS 點的時間
    //
    // 例如：
    // 2026-09-24T08:05:00+08:00
    val timestamp: String,


    // 第幾個 GPS 點
    @SerializedName("sequence_order")
    val sequenceOrder: Int
)


// =====================================================
// POST /api/v1/activity/fin_activity
//
// 運動結束後，
// 一次送出摘要 + GPS 軌跡。
// =====================================================

data class FinishActivityRequest(

    // add_activity 回傳的 activity_id
    @SerializedName("activity_id")
    val activityId: Long,


    // 結束時間
    @SerializedName("end_time")
    val endTime: String,


    // 實際運動時間
    @SerializedName("actual_duration")
    val actualDuration: Long,


    // 總距離（公尺）
    @SerializedName("total_distance_meters")
    val totalDistanceMeters: Double,


    // 總距離（公里）
    @SerializedName("distance_km")
    val distanceKm: Double,


    // 卡路里
    @SerializedName("calories_burned")
    val caloriesBurned: Double? = null,


    // 終點緯度
    @SerializedName("end_latitude")
    val endLatitude: Double,


    // 終點經度
    @SerializedName("end_longitude")
    val endLongitude: Double,


    // 終點名稱
    @SerializedName("end_location_name")
    val endLocationName: String? = null,


    // completed
    @SerializedName("completion_status")
    val completionStatus: String = "completed",


    // 使用者備註
    @SerializedName("user_notes")
    val userNotes: String? = null,


    // 全部 GPS 點
    @SerializedName("gps_points")
    val gpsPoints: List<GpsPointUpload>
)


// =====================================================
// fin_activity Response
//
// 成功：
//
// {
//   "status": "success",
//   "activity_id": 4,
//   "gps_points_count": 3
// }
// =====================================================

data class FinishActivityResponse(

    val status: String? = null,

    val message: String? = null,

    @SerializedName("activity_id")
    val activityId: Long? = null,

    @SerializedName("gps_points_count")
    val gpsPointsCount: Int? = null
)

// =====================================================
// GET /api/v1/activity/qry_activity/{activity_id}
//
// 單筆運動詳細資料 Response
// =====================================================

data class ActivityDetailResponse(

    val status: String? = null,

    val activity: ActivityDetail? = null,

    @SerializedName("gps_points")
    val gpsPoints: List<ActivityGpsPoint> =
        emptyList(),

    val message: String? = null
)


// =====================================================
// 後端單筆 Activity 詳細資料
//
// 後端實際已確認至少會回：
//
// activity_id
// title
// mode_id
// start_time
// end_time
// total_distance_meters
// status
// completion_status
// =====================================================

data class ActivityDetail(

    @SerializedName("activity_id")
    val activityId: Long,

    val title: String? = null,

    @SerializedName("mode_id")
    val modeId: Int? = null,

    @SerializedName("start_time")
    val startTime: String? = null,

    @SerializedName("end_time")
    val endTime: String? = null,

    @SerializedName("total_distance_meters")
    val totalDistanceMeters: Double? = null,

    val status: String? = null,

    @SerializedName("completion_status")
    val completionStatus: String? = null
)


// =====================================================
// 後端回傳的 GPS Point
//
// 後端至少會回：
//
// latitude
// longitude
// altitude
// timestamp
// sequence_order
//
// speed / accuracy / heading
// 有的話也可以接。
// =====================================================

data class ActivityGpsPoint(

    val latitude: Double,

    val longitude: Double,

    val altitude: Double? = null,

    @SerializedName("speed_ms")
    val speedMs: Double? = null,

    @SerializedName("accuracy_meters")
    val accuracyMeters: Double? = null,

    val heading: Double? = null,

    val timestamp: String? = null,

    @SerializedName("sequence_order")
    val sequenceOrder: Int = 0
)