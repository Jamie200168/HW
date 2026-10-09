package com.example.project.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


object TrackingRepository {


    private val _state =
        MutableStateFlow(
            WorkoutState()
        )


    val state:
            StateFlow<WorkoutState> =
        _state.asStateFlow()


    private val _routePoints =
        MutableStateFlow<List<RoutePoint>>(
            emptyList()
        )


    val routePoints:
            StateFlow<List<RoutePoint>> =
        _routePoints.asStateFlow()


    private val _activeActivityType =
        MutableStateFlow<String?>(
            null
        )


    val activeActivityType:
            StateFlow<String?> =
        _activeActivityType.asStateFlow()


    fun reset(
        activityType: String
    ) {


        _activeActivityType.value =
            activityType


        _state.value =
            WorkoutState(

                steps =
                    0,

                distanceMeters =
                    0f,

                elapsedSeconds =
                    0L,

                isTracking =
                    true
            )


        _routePoints.value =
            emptyList()
    }


    // =====================================================
    // Service / Process 被重建後
    // 從 SQLite 回復同一場運動
    // =====================================================

    fun restore(

        activityType: String,

        steps: Int,

        distanceMeters: Float,

        elapsedSeconds: Long,

        routePoints: List<RoutePoint>

    ) {


        _activeActivityType.value =
            activityType


        _state.value =
            WorkoutState(

                steps =
                    steps.coerceAtLeast(
                        0
                    ),

                distanceMeters =
                    distanceMeters.coerceAtLeast(
                        0f
                    ),

                elapsedSeconds =
                    elapsedSeconds.coerceAtLeast(
                        0L
                    ),

                isTracking =
                    true
            )


        _routePoints.value =
            routePoints.toList()
    }


    fun clear() {


        _activeActivityType.value =
            null


        _state.value =
            WorkoutState(

                steps =
                    0,

                distanceMeters =
                    0f,

                elapsedSeconds =
                    0L,

                isTracking =
                    false
            )


        _routePoints.value =
            emptyList()
    }


    fun updateSteps(
        steps: Int
    ) {


        _state.value =
            _state.value.copy(

                steps =
                    steps.coerceAtLeast(
                        0
                    )
            )
    }


    fun updateDistance(
        distance: Float
    ) {


        _state.value =
            _state.value.copy(

                distanceMeters =
                    distance.coerceAtLeast(
                        0f
                    )
            )
    }


    fun updateTime(
        seconds: Long
    ) {


        _state.value =
            _state.value.copy(

                elapsedSeconds =
                    seconds.coerceAtLeast(
                        0L
                    )
            )
    }


    fun stopTracking() {


        _state.value =
            _state.value.copy(

                isTracking =
                    false
            )


        _activeActivityType.value =
            null
    }


    fun addRoutePoint(

        latitude: Double,

        longitude: Double

    ) {


        addRoutePoint(

            RoutePoint(

                latitude =
                    latitude,

                longitude =
                    longitude
            )
        )
    }


    fun addRoutePoint(
        point: RoutePoint
    ) {


        _routePoints.value =
            _routePoints.value +
                    point
    }
}