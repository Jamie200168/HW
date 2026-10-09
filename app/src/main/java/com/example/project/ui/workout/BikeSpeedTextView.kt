package com.example.project.ui.workout


import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet

import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.example.project.data.RoutePoint
import com.example.project.data.TrackingRepository

import kotlinx.coroutines.launch

import java.util.Locale


class BikeSpeedTextView
@JvmOverloads
constructor(

    context: Context,

    attrs: AttributeSet? =
        null,

    defStyleAttr: Int =
        android.R.attr.textViewStyle

) :
    AppCompatTextView(
        context,
        attrs,
        defStyleAttr
    ) {


    companion object {

        // 超過 8 秒沒有新的有效 GPS，
        // 就視為目前速度 0。
        private const val SPEED_STALE_MS =
            8000L
    }


    private val handler =
        Handler(
            Looper.getMainLooper()
        )


    private var latestRoute:
            List<RoutePoint> =
        emptyList()


    private var observationStarted =
        false


    private val staleRunnable =
        object :
            Runnable {


            override fun run() {


                if (
                    isCyclingMode()
                ) {


                    renderSpeed(
                        latestRoute
                    )
                }


                handler.postDelayed(
                    this,
                    1000L
                )
            }
        }


    override fun onAttachedToWindow() {

        super.onAttachedToWindow()


        post {


            if (
                isCyclingMode()
            ) {


                text =
                    "時速：0.0 km/h"


                startObservation()


                handler.removeCallbacks(
                    staleRunnable
                )


                handler.post(
                    staleRunnable
                )
            }
        }
    }


    override fun onDetachedFromWindow() {


        handler.removeCallbacks(
            staleRunnable
        )


        super.onDetachedFromWindow()
    }


    private fun startObservation() {


        if (
            observationStarted
        ) {

            return
        }


        val owner =
            findViewTreeLifecycleOwner()
                ?: return


        observationStarted =
            true


        owner
            .lifecycleScope
            .launch {


                owner.repeatOnLifecycle(
                    Lifecycle.State.STARTED
                ) {


                    TrackingRepository
                        .routePoints
                        .collect {
                                route ->


                            latestRoute =
                                route


                            renderSpeed(
                                route
                            )
                        }
                }
            }
    }


    private fun renderSpeed(
        route: List<RoutePoint>
    ) {


        if (
            !isCyclingMode()
        ) {

            return
        }


        val speedKmh =
            calculateCurrentSpeedKmh(
                route
            )


        text =
            String.format(

                Locale.TAIWAN,

                "時速：%.1f km/h",

                speedKmh
            )
    }


    // =====================================================
    // 即時速度
    //
    // 優先：
    // 最近最多 3 個 GPS speed_ms 平均
    //
    // speed_ms 沒有時：
    // 使用最後兩個 GPS 點自行算。
    // =====================================================

    private fun calculateCurrentSpeedKmh(
        route: List<RoutePoint>
    ): Double {


        if (
            route.isEmpty()
        ) {

            return 0.0
        }


        val lastPoint =
            route.last()


        val now =
            System.currentTimeMillis()


        if (
            lastPoint.timestampMillis >
            0L &&
            now -
            lastPoint.timestampMillis >
            SPEED_STALE_MS
        ) {

            return 0.0
        }


        val recentSpeedValues =
            route
                .asReversed()
                .mapNotNull {
                        point ->


                    point.speedMs
                        ?.takeIf {
                                value ->


                            value.isFinite() &&
                                    value >=
                                    0.0
                        }
                }
                .take(
                    3
                )


        if (
            recentSpeedValues.isNotEmpty()
        ) {


            val averageMetersPerSecond =
                recentSpeedValues.average()


            return (
                    averageMetersPerSecond *
                            3.6
                    )
                .coerceAtLeast(
                    0.0
                )
        }


        // =================================================
        // speed_ms 不存在時
        // 用最後兩點估算速度。
        // =================================================

        if (
            route.size <
            2
        ) {

            return 0.0
        }


        val previous =
            route[
                route.lastIndex -
                        1
            ]


        val current =
            route.last()


        val timeDifferenceMillis =
            current.timestampMillis -
                    previous.timestampMillis


        if (
            timeDifferenceMillis <=
            0L
        ) {

            return 0.0
        }


        val result =
            FloatArray(
                1
            )


        Location.distanceBetween(

            previous.latitude,

            previous.longitude,

            current.latitude,

            current.longitude,

            result
        )


        val distanceMeters =
            result[0]
                .toDouble()


        val seconds =
            timeDifferenceMillis /
                    1000.0


        if (
            seconds <=
            0.0
        ) {

            return 0.0
        }


        return (
                distanceMeters /
                        seconds *
                        3.6
                )
            .coerceAtLeast(
                0.0
            )
    }


    private fun isCyclingMode():
            Boolean {


        val activityType =
            try {


                val fragment =
                    FragmentManager
                        .findFragment<Fragment>(
                            this
                        )


                fragment
                    .arguments
                    ?.getString(
                        "activityType"
                    )


            } catch (
                _: Exception
            ) {


                TrackingRepository
                    .activeActivityType
                    .value
            }


        return activityType ==
                "騎自行車" ||
                activityType ==
                "單車"
    }
}