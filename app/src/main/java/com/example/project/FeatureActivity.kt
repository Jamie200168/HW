package com.example.project

import android.content.Intent
import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment

import com.example.project.auth.TokenManager
import com.example.project.data.ActiveWorkoutSession
import com.example.project.data.TrackingRepository
import com.example.project.data.WorkoutDatabaseHelper
import com.example.project.service.TrackingService


class FeatureActivity :
    AppCompatActivity() {


    companion object {

        const val EXTRA_START_PAGE =
            "start_page"


        // APP 重開時，
        // 用來表示是否要恢復進行中的運動
        const val EXTRA_RESUME_ACTIVE_WORKOUT =
            "resume_active_workout"


        const val PAGE_WORKOUT =
            "workout"


        const val PAGE_HISTORY =
            "history"


        const val PAGE_WEATHER =
            "weather"
    }


    private var isRedirectingToLogin =
        false


    private lateinit var database:
            WorkoutDatabaseHelper


    private var requestedStartPage =
        PAGE_WORKOUT


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {


        super.onCreate(
            savedInstanceState
        )


        database =
            WorkoutDatabaseHelper(
                applicationContext
            )


        requestedStartPage =
            intent
                .getStringExtra(
                    EXTRA_START_PAGE
                )
                ?: PAGE_WORKOUT


        if (
            !checkPageAccess(
                requestedStartPage
            )
        ) {


            return
        }


        setContentView(
            R.layout.activity_feature
        )


        if (
            savedInstanceState != null
        ) {


            return
        }


        val navHostFragment =
            supportFragmentManager
                .findFragmentById(
                    R.id.nav_host_fragment
                )
                    as NavHostFragment


        val navController =
            navHostFragment
                .navController


        when (
            requestedStartPage
        ) {


            PAGE_HISTORY -> {


                navigateAsRoot(

                    navController =
                        navController,

                    destinationId =
                        R.id.historyFragment
                )
            }


            PAGE_WEATHER -> {


                navigateAsRoot(

                    navController =
                        navController,

                    destinationId =
                        R.id.weatherFragment
                )
            }


            PAGE_WORKOUT -> {


                val activeSession =
                    database
                        .getActiveWorkoutSession()


                if (
                    activeSession != null
                ) {


                    // 先恢復 UI StateFlow，
                    // 避免 Fragment 建立時看到 false 而把狀態清掉。
                    hydrateActiveWorkout(
                        activeSession
                    )


                    ensureTrackingServiceRunning()


                    navigateAsRoot(

                        navController =
                            navController,

                        destinationId =
                            R.id.workoutFragment,

                        arguments =
                            bundleOf(

                                "activityType"
                                        to activeSession.activityType
                            )
                    )
                }


                // activeSession == null
                // 就維持 nav_graph 預設 WorkoutChoiceFragment。
            }
        }
    }


    override fun onResume() {


        super.onResume()


        checkPageAccess(
            requestedStartPage
        )
    }


    // =====================================================
    // 在 WorkoutFragment 建立前
    // 先恢復 Repository。
    // =====================================================

    private fun hydrateActiveWorkout(
        session: ActiveWorkoutSession
    ) {


        val wallElapsedSeconds =
            (
                    (
                            System.currentTimeMillis() -
                                    session.startWallClockMillis
                            )
                        .coerceAtLeast(
                            0L
                        )
                    ) /
                    1000L


        val elapsedSeconds =
            maxOf(

                session.elapsedSeconds,

                wallElapsedSeconds
            )


        TrackingRepository.restore(

            activityType =
                session.activityType,

            steps =
                session.steps,

            distanceMeters =
                session.distanceMeters,

            elapsedSeconds =
                elapsedSeconds,

            routePoints =
                database
                    .getActiveRoutePoints()
        )
    }


    private fun checkPageAccess(
        page: String
    ): Boolean {


        if (
            TokenManager
                .isLoggedIn(
                    applicationContext
                )
        ) {


            return true
        }


        // Token 不存在時，
        // 唯一允許繼續的是「正在進行中的運動」。
        val canContinueActiveWorkout =
            page ==
                    PAGE_WORKOUT &&
                    database
                        .hasActiveWorkoutSession()


        if (
            canContinueActiveWorkout
        ) {


            return true
        }


        redirectToLogin()


        return false
    }


    private fun ensureTrackingServiceRunning() {


        val resumeIntent =
            Intent(
                this,
                TrackingService::class.java
            ).apply {


                action =
                    TrackingService.ACTION_RESUME
            }


        ContextCompat
            .startForegroundService(

                this,

                resumeIntent
            )
    }


    private fun navigateAsRoot(

        navController: NavController,

        destinationId: Int,

        arguments: Bundle? = null

    ) {


        val navOptions =
            NavOptions
                .Builder()
                .setPopUpTo(

                    navController
                        .graph
                        .startDestinationId,

                    true
                )
                .build()


        navController.navigate(

            destinationId,

            arguments,

            navOptions
        )
    }


    private fun redirectToLogin() {


        if (
            isRedirectingToLogin
        ) {


            return
        }


        isRedirectingToLogin =
            true


        val loginIntent =
            Intent(
                this,
                LoginActivity::class.java
            )


        loginIntent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK


        startActivity(
            loginIntent
        )


        finish()
    }
}