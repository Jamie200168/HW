package com.example.project.data

data class ActiveWorkoutSession(

    val activityType: String,

    val ownerUsername: String,

    val startWallClockMillis: Long,

    val startElapsedRealtime: Long,

    val elapsedSeconds: Long = 0L,

    val steps: Int = 0,

    val distanceMeters: Float = 0f,

    val startingSteps: Float? = null,

    val detectorSteps: Int = 0
)