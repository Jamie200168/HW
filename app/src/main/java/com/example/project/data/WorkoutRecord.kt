package com.example.project.data


data class WorkoutRecord(

    // 手機 SQLite 本機 ID
    val id: Long = 0,

    // 走路 / 登山 / 騎自行車
    val activityType: String,

    // 開始時間
    val startTimeMillis: Long,

    // 結束時間
    val endTimeMillis: Long,

    // 運動秒數
    val durationSeconds: Long,

    // 步數
    val steps: Int,

    // 公尺
    val distanceMeters: Float,

    // 後端 sport_modes.mode_id
    //
    // 1 = 爬山
    // 2 = 健走
    // 3 = 單車
    val modeId: Int? = null,

    // 這筆本機資料屬於哪個登入帳號
    val ownerUsername: String? = null,

    // FastAPI activity_id
    val serverActivityId: Long? = null,

    // pending / synced / failed / legacy
    val syncStatus: String =
        SYNC_PENDING,

    // 最後同步錯誤
    val syncError: String? = null
) {

    companion object {

        const val SYNC_PENDING =
            "pending"

        const val SYNC_SYNCED =
            "synced"

        const val SYNC_FAILED =
            "failed"

        const val SYNC_LEGACY =
            "legacy"
    }
}