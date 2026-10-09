package com.example.project.data

data class WorkoutState(

    // 本次步數
    val steps: Int = 0,

    // 距離，單位公尺
    val distanceMeters: Float = 0f,

    // 運動時間，單位秒
    val elapsedSeconds: Long = 0L,

    // 是否正在運動
    val isTracking: Boolean = false
)