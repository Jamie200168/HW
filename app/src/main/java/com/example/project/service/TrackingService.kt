package com.example.project.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast

import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

import com.example.project.auth.TokenManager
import com.example.project.data.ActiveWorkoutSession
import com.example.project.data.ActivityUploadRepository
import com.example.project.data.RoutePoint
import com.example.project.data.TrackingRepository
import com.example.project.data.WorkoutDatabaseHelper
import com.example.project.data.WorkoutRecord

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

import java.util.Locale

import kotlin.math.max


class TrackingService :
    Service(),
    SensorEventListener {


    companion object {


        const val ACTION_START =
            "ACTION_START_TRACKING"


        const val ACTION_RESUME =
            "ACTION_RESUME_TRACKING"


        const val ACTION_STOP =
            "ACTION_STOP_TRACKING"


        const val EXTRA_ACTIVITY_TYPE =
            "ACTIVITY_TYPE"


        const val CHANNEL_ID =
            "tracking_channel"


        const val NOTIFICATION_ID =
            1001


        // =================================================
        // GPS 設定
        // =================================================

        // 約每 3 秒要求一次 GPS。
        //
        // Android / Fused Location 不保證一定精準每 3 秒回傳。
        private const val GPS_INTERVAL_MS =
            3000L


        // 希望至少移動約 3 公尺才收到新 Location。
        private const val GPS_MIN_UPDATE_DISTANCE_METERS =
            3f


        // accuracy 超過 15 公尺：
        // 不拿來畫路線，也不拿來算距離。
        private const val GPS_MAX_ACCURACY_METERS =
            15f


        // GPS 誤差和「是否真的移動」的判斷比例。
        //
        // 例如：
        //
        // accuracy = 10m
        //
        // 10 × 0.6 = 6m
        //
        // 只移動 3~4m 時，
        // 很可能只是 GPS 飄動，不保存。
        private const val GPS_ACCURACY_MOVEMENT_FACTOR =
            0.6f


        // 走路 / 登山最大合理速度：
        //
        // 4.5 m/s
        // = 16.2 km/h
        private const val WALKING_MAX_SPEED_MPS =
            4.5f


        // 自行車最大合理速度：
        //
        // 15 m/s
        // = 54 km/h
        private const val CYCLING_MAX_SPEED_MPS =
            15f
    }


    // =====================================================
    // Database
    // =====================================================

    private lateinit var database:
            WorkoutDatabaseHelper


    // =====================================================
    // Sensor
    // =====================================================

    private lateinit var sensorManager:
            SensorManager


    private var stepCounterSensor:
            Sensor? =
        null


    private var stepDetectorSensor:
            Sensor? =
        null


    // =====================================================
    // GPS
    // =====================================================

    private lateinit var fusedLocationClient:
            FusedLocationProviderClient


    // =====================================================
    // 運動狀態
    // =====================================================

    private var isTracking =
        false


    private var activityType =
        "走路"


    private var ownerUsername:
            String? =
        null


    private var startWallClockMillis =
        0L


    private var startElapsedRealtime =
        0L


    // =====================================================
    // 步數
    // =====================================================

    private var startingSteps:
            Float? =
        null


    private var detectorSteps =
        0


    // =====================================================
    // GPS 最後一個「已接受」的有效點
    //
    // 注意：
    //
    // 不是 Android 最新回傳的 Location，
    // 而是已經通過 accuracy / 距離 / 速度
    // 等篩選後的點。
    //
    // 所以被判定為 GPS 漂移的點
    // 不會更新這個值。
    // =====================================================

    private var lastAcceptedLocation:
            Location? =
        null


    // =====================================================
    // 總距離
    // =====================================================

    private var totalDistance =
        0f


    // =====================================================
    // Timer
    // =====================================================

    private val handler =
        Handler(
            Looper.getMainLooper()
        )


    // =====================================================
    // onCreate
    // =====================================================

    override fun onCreate() {


        super.onCreate()


        database =
            WorkoutDatabaseHelper(
                applicationContext
            )


        createNotificationChannel()


        sensorManager =
            getSystemService(
                SENSOR_SERVICE
            ) as SensorManager


        stepCounterSensor =
            sensorManager.getDefaultSensor(
                Sensor.TYPE_STEP_COUNTER
            )


        stepDetectorSensor =
            sensorManager.getDefaultSensor(
                Sensor.TYPE_STEP_DETECTOR
            )


        fusedLocationClient =
            LocationServices
                .getFusedLocationProviderClient(
                    this
                )
    }


    // =====================================================
    // onStartCommand
    // =====================================================

    override fun onStartCommand(

        intent: Intent?,

        flags: Int,

        startId: Int

    ): Int {


        when (
            intent?.action
        ) {


            // =================================================
            // 開始新的運動
            // =================================================

            ACTION_START -> {


                if (
                    isTracking
                ) {


                    return START_STICKY
                }


                // SQLite 已經有一場未結束運動，
                // 不可以再建立第二場。
                if (
                    database
                        .hasActiveWorkoutSession()
                ) {


                    restorePersistedWorkout(
                        startRuntime =
                            true
                    )


                    return START_STICKY
                }


                val requestedActivityType =
                    intent.getStringExtra(
                        EXTRA_ACTIVITY_TYPE
                    ) ?: "走路"


                startNewWorkout(
                    requestedActivityType
                )
            }


            // =================================================
            // APP 重開後恢復
            // =================================================

            ACTION_RESUME -> {


                if (
                    !isTracking
                ) {


                    restorePersistedWorkout(
                        startRuntime =
                            true
                    )
                }
            }


            // =================================================
            // 結束運動
            // =================================================

            ACTION_STOP -> {


                if (
                    !isTracking
                ) {


                    val restored =
                        restorePersistedWorkout(
                            startRuntime =
                                false
                        )


                    if (
                        !restored
                    ) {


                        stopSelf()


                        return START_NOT_STICKY
                    }
                }


                finishWorkout()
            }


            // =================================================
            // START_STICKY 自動重建
            //
            // intent 有可能是 null。
            // =================================================

            else -> {


                if (
                    !isTracking
                ) {


                    val restored =
                        restorePersistedWorkout(
                            startRuntime =
                                true
                        )


                    if (
                        !restored
                    ) {


                        stopSelf()


                        return START_NOT_STICKY
                    }
                }
            }
        }


        return if (
            isTracking ||
            database.hasActiveWorkoutSession()
        ) {


            START_STICKY


        } else {


            START_NOT_STICKY
        }
    }


    // =====================================================
    // 開始新的運動
    // =====================================================

    private fun startNewWorkout(
        requestedActivityType: String
    ) {


        val username =
            TokenManager
                .getUsername(
                    applicationContext
                )
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }


        if (
            username == null
        ) {


            Toast.makeText(

                applicationContext,

                "找不到目前登入使用者，請重新登入後再開始運動",

                Toast.LENGTH_LONG

            ).show()


            stopSelf()


            return
        }


        cleanupTracking()


        activityType =
            requestedActivityType


        ownerUsername =
            username


        startWallClockMillis =
            System.currentTimeMillis()


        startElapsedRealtime =
            SystemClock.elapsedRealtime()


        startingSteps =
            null


        detectorSteps =
            0


        lastAcceptedLocation =
            null


        totalDistance =
            0f


        TrackingRepository.reset(
            activityType
        )


        // =================================================
        // 先建立 SQLite Active Session
        // =================================================

        val sessionSaved =
            database.beginActiveWorkout(

                ActiveWorkoutSession(

                    activityType =
                        activityType,

                    ownerUsername =
                        username,

                    startWallClockMillis =
                        startWallClockMillis,

                    startElapsedRealtime =
                        startElapsedRealtime
                )
            )


        if (
            !sessionSaved
        ) {


            TrackingRepository.clear()


            Toast.makeText(

                applicationContext,

                "無法建立本機運動暫存，請稍後再試",

                Toast.LENGTH_LONG

            ).show()


            stopSelf()


            return
        }


        isTracking =
            true


        startForegroundTracking(
            0L
        )


        startRuntimeCollectors()
    }


    // =====================================================
    // 從 SQLite 恢復運動
    // =====================================================

    private fun restorePersistedWorkout(
        startRuntime: Boolean
    ): Boolean {


        val session =
            database
                .getActiveWorkoutSession()
                ?: return false


        activityType =
            session.activityType


        ownerUsername =
            session.ownerUsername


        startWallClockMillis =
            session.startWallClockMillis


        startingSteps =
            session.startingSteps


        detectorSteps =
            session.detectorSteps


        totalDistance =
            session.distanceMeters


        val currentElapsedRealtime =
            SystemClock.elapsedRealtime()


        startElapsedRealtime =

            if (
                session.startElapsedRealtime >
                0L &&
                session.startElapsedRealtime <=
                currentElapsedRealtime
            ) {


                session.startElapsedRealtime


            } else {


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


                val recoveredSeconds =
                    max(

                        session.elapsedSeconds,

                        wallElapsedSeconds
                    )


                currentElapsedRealtime -
                        (
                                recoveredSeconds *
                                        1000L
                                )
            }


        val currentSeconds =
            (
                    (
                            currentElapsedRealtime -
                                    startElapsedRealtime
                            )
                        .coerceAtLeast(
                            0L
                        )
                    ) /
                    1000L


        val routePoints =
            database
                .getActiveRoutePoints()


        TrackingRepository.restore(

            activityType =
                activityType,

            steps =
                session.steps,

            distanceMeters =
                session.distanceMeters,

            elapsedSeconds =
                max(

                    session.elapsedSeconds,

                    currentSeconds
                ),

            routePoints =
                routePoints
        )


        // =================================================
        // 將最後一個 SQLite GPS 點
        // 當作新的 GPS Filter 基準。
        // =================================================

        val lastPoint =
            routePoints
                .lastOrNull()


        lastAcceptedLocation =
            lastPoint
                ?.let {

                    routePointToLocation(
                        it
                    )
                }


        isTracking =
            true


        persistProgress()


        if (
            startRuntime
        ) {


            startForegroundTracking(

                TrackingRepository
                    .state
                    .value
                    .elapsedSeconds
            )


            startRuntimeCollectors()
        }


        return true
    }


    // =====================================================
    // 啟動 Sensor / GPS / Timer
    // =====================================================

    private fun startRuntimeCollectors() {


        if (
            activityType !=
            "騎自行車"
        ) {


            startStepTracking()
        }


        startLocationTracking()


        handler.removeCallbacks(
            timerRunnable
        )


        handler.post(
            timerRunnable
        )
    }


    // =====================================================
    // Timer
    // =====================================================

    private val timerRunnable =
        object :
            Runnable {


            override fun run() {


                if (
                    !isTracking ||
                    startElapsedRealtime <=
                    0L
                ) {


                    return
                }


                val elapsedSeconds =
                    (
                            SystemClock.elapsedRealtime() -
                                    startElapsedRealtime
                            )
                        .coerceAtLeast(
                            0L
                        ) /
                            1000L


                TrackingRepository.updateTime(
                    elapsedSeconds
                )


                // 每 5 秒保存目前進度。
                if (
                    elapsedSeconds %
                    5L ==
                    0L
                ) {


                    persistProgress()


                    updateNotification(
                        elapsedSeconds
                    )
                }


                handler.postDelayed(
                    this,
                    1000L
                )
            }
        }


    // =====================================================
    // Step Sensor
    // =====================================================

    private fun startStepTracking() {


        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(

                this,

                Manifest.permission.ACTIVITY_RECOGNITION

            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {


            return
        }


        if (
            stepCounterSensor !=
            null
        ) {


            sensorManager.registerListener(

                this,

                stepCounterSensor,

                SensorManager.SENSOR_DELAY_NORMAL
            )


        } else if (
            stepDetectorSensor !=
            null
        ) {


            sensorManager.registerListener(

                this,

                stepDetectorSensor,

                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }


    override fun onSensorChanged(
        event: SensorEvent?
    ) {


        if (
            event == null ||
            !isTracking
        ) {


            return
        }


        when (
            event.sensor.type
        ) {


            // =================================================
            // Step Counter
            // =================================================

            Sensor.TYPE_STEP_COUNTER -> {


                val currentTotalSteps =
                    event.values[0]


                val alreadyRecordedSteps =
                    TrackingRepository
                        .state
                        .value
                        .steps


                if (
                    startingSteps ==
                    null
                ) {


                    startingSteps =
                        currentTotalSteps -
                                alreadyRecordedSteps
                                    .toFloat()


                    persistProgress()


                } else if (
                    currentTotalSteps <
                    (
                            startingSteps
                                ?: 0f
                            )
                ) {


                    // 手機重開機後
                    // Step Counter 可能重新計算。
                    startingSteps =
                        currentTotalSteps -
                                alreadyRecordedSteps
                                    .toFloat()


                    persistProgress()
                }


                val workoutSteps =
                    (
                            currentTotalSteps -
                                    (
                                            startingSteps
                                                ?: currentTotalSteps
                                            )
                            )
                        .toInt()
                        .coerceAtLeast(
                            0
                        )


                TrackingRepository.updateSteps(
                    workoutSteps
                )
            }


            // =================================================
            // Step Detector fallback
            // =================================================

            Sensor.TYPE_STEP_DETECTOR -> {


                detectorSteps++


                TrackingRepository.updateSteps(
                    detectorSteps
                )


                if (
                    detectorSteps %
                    5 ==
                    0
                ) {


                    persistProgress()
                }
            }
        }
    }


    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {


        // 不需要處理
    }


    // =====================================================
    // GPS Callback
    // =====================================================

    private val locationCallback =
        object :
            LocationCallback() {


            override fun onLocationResult(
                result: LocationResult
            ) {


                if (
                    !isTracking
                ) {


                    return
                }


                result.locations
                    .forEach {
                            location ->


                        processLocation(
                            location
                        )
                    }
            }
        }


    // =====================================================
    // 啟動 GPS
    // =====================================================

    private fun startLocationTracking() {


        val finePermission =
            ContextCompat.checkSelfPermission(

                this,

                Manifest.permission.ACCESS_FINE_LOCATION
            )


        val coarsePermission =
            ContextCompat.checkSelfPermission(

                this,

                Manifest.permission.ACCESS_COARSE_LOCATION
            )


        if (
            finePermission !=
            PackageManager.PERMISSION_GRANTED &&
            coarsePermission !=
            PackageManager.PERMISSION_GRANTED
        ) {


            return
        }


        val request =
            LocationRequest.Builder(

                Priority.PRIORITY_HIGH_ACCURACY,

                GPS_INTERVAL_MS
            )
                .setMinUpdateDistanceMeters(
                    GPS_MIN_UPDATE_DISTANCE_METERS
                )
                .build()


        try {


            fusedLocationClient
                .requestLocationUpdates(

                    request,

                    locationCallback,

                    Looper.getMainLooper()
                )


        } catch (
            e: SecurityException
        ) {


            e.printStackTrace()
        }
    }


    // =====================================================
    // GPS 統一處理
    //
    // 所有：
    //
    // 路線
    // SQLite
    // 距離
    //
    // 都必須先通過這一個 Filter。
    // =====================================================

    private fun processLocation(
        newLocation: Location
    ) {


        if (
            !isTracking
        ) {


            return
        }


        // =================================================
        // 1. 必須有 accuracy
        // =================================================

        if (
            !newLocation.hasAccuracy()
        ) {


            return
        }


        // =================================================
        // 2. Accuracy 太差
        //
        // > 15m 完全不要。
        // =================================================

        if (
            newLocation.accuracy >
            GPS_MAX_ACCURACY_METERS
        ) {


            return
        }


        val oldLocation =
            lastAcceptedLocation


        // =================================================
        // 第一個有效 GPS
        // =================================================

        if (
            oldLocation ==
            null
        ) {


            if (
                saveDetailedRoutePoint(
                    newLocation
                )
            ) {


                lastAcceptedLocation =
                    Location(
                        newLocation
                    )
            }


            return
        }


        // =================================================
        // 3. GPS 時間差
        // =================================================

        val timeDifferenceSeconds =
            getLocationTimeDifferenceSeconds(

                oldLocation =
                    oldLocation,

                newLocation =
                    newLocation
            )


        if (
            timeDifferenceSeconds <=
            0f
        ) {


            return
        }


        // =================================================
        // 4. 兩個 GPS 點相距多少公尺
        // =================================================

        val distance =
            oldLocation.distanceTo(
                newLocation
            )


        // =================================================
        // 5. GPS 漂移判斷
        //
        // 不能只設定固定 2m / 3m。
        //
        // 例如：
        //
        // accuracy = 12m
        // 人實際只移動 4m
        //
        // 4m 很可能只是 GPS 自己飄。
        //
        // 所以最低可靠位移：
        //
        // max(
        //     3m,
        //     accuracy × 0.6
        // )
        // =================================================

        val oldAccuracy =

            if (
                oldLocation.hasAccuracy()
            ) {


                oldLocation.accuracy


            } else {


                newLocation.accuracy
            }


        val worseAccuracy =
            maxOf(

                oldAccuracy,

                newLocation.accuracy
            )


        val minimumReliableDistance =
            maxOf(

                GPS_MIN_UPDATE_DISTANCE_METERS,

                worseAccuracy *
                        GPS_ACCURACY_MOVEMENT_FACTOR
            )


        if (
            distance <
            minimumReliableDistance
        ) {


            // 位移不足以證明真的有移動。
            //
            // 不存。
            // 不畫。
            // 不算距離。
            return
        }


        // =================================================
        // 6. 用「距離 ÷ 時間」算實際速度
        // =================================================

        val calculatedSpeed =
            distance /
                    timeDifferenceSeconds


        val maximumReasonableSpeed =
            getMaximumReasonableSpeed()


        // =================================================
        // 7. 異常瞬移
        //
        // 例如走路時：
        //
        // 3 秒跳 60m
        //
        // 60 ÷ 3
        // = 20m/s
        //
        // 明顯是 GPS 漂移。
        // =================================================

        if (
            calculatedSpeed >
            maximumReasonableSpeed
        ) {


            return
        }


        // =================================================
        // 通過所有 Filter
        //
        // 才是一個真正有效 GPS 點。
        // =================================================

        val saved =
            saveDetailedRoutePoint(
                newLocation
            )


        if (
            !saved
        ) {


            return
        }


        // =================================================
        // 使用完全相同的有效 GPS
        // 計算距離。
        // =================================================

        totalDistance +=
            distance


        TrackingRepository
            .updateDistance(
                totalDistance
            )


        // =================================================
        // 更新最後有效點
        // =================================================

        lastAcceptedLocation =
            Location(
                newLocation
            )


        // =================================================
        // GPS / 距離一有有效更新
        // 就同步保存 Active Session。
        // =================================================

        persistProgress()
    }


    // =====================================================
    // GPS 時間差
    //
    // 優先使用 elapsedRealtimeNanos。
    //
    // 比 Location.time 更不容易受到
    // 手機系統時間改動影響。
    // =====================================================

    private fun getLocationTimeDifferenceSeconds(

        oldLocation: Location,

        newLocation: Location

    ): Float {


        val oldElapsed =
            oldLocation
                .elapsedRealtimeNanos


        val newElapsed =
            newLocation
                .elapsedRealtimeNanos


        if (
            oldElapsed >
            0L &&
            newElapsed >
            oldElapsed
        ) {


            return (
                    newElapsed -
                            oldElapsed
                    ) /
                    1_000_000_000f
        }


        // =================================================
        // SQLite 恢復出的 Location
        // 沒有 elapsedRealtimeNanos。
        //
        // 改用 timestamp。
        // =================================================

        val timeDifferenceMillis =
            newLocation.time -
                    oldLocation.time


        if (
            timeDifferenceMillis <=
            0L
        ) {


            return 0f
        }


        return timeDifferenceMillis /
                1000f
    }


    // =====================================================
    // 不同運動模式最大合理速度
    // =====================================================

    private fun getMaximumReasonableSpeed():
            Float {


        return when (
            activityType
        ) {


            "騎自行車",
            "單車" ->


                CYCLING_MAX_SPEED_MPS


            else ->


                WALKING_MAX_SPEED_MPS
        }
    }


    // =====================================================
    // 真正保存有效 GPS
    // =====================================================

    private fun saveDetailedRoutePoint(
        location: Location
    ): Boolean {


        val routePoint =
            RoutePoint(


                latitude =
                    location.latitude,


                longitude =
                    location.longitude,


                altitude =

                    if (
                        location.hasAltitude()
                    ) {


                        location.altitude


                    } else {


                        null
                    },


                speedMs =

                    if (
                        location.hasSpeed()
                    ) {


                        location.speed
                            .toDouble()


                    } else {


                        null
                    },


                accuracyMeters =
                    location.accuracy
                        .toDouble(),


                heading =

                    if (
                        location.hasBearing()
                    ) {


                        location.bearing
                            .toDouble()


                    } else {


                        null
                    },


                timestampMillis =

                    if (
                        location.time >
                        0L
                    ) {


                        location.time


                    } else {


                        System.currentTimeMillis()
                    }
            )


        // =================================================
        // 第一優先：
        // SQLite
        // =================================================

        val insertedId =
            database
                .appendActiveRoutePoint(
                    routePoint
                )


        if (
            insertedId ==
            -1L
        ) {


            return false
        }


        // =================================================
        // 第二：
        // Repository / Map
        // =================================================

        TrackingRepository
            .addRoutePoint(
                routePoint
            )


        return true
    }


    // =====================================================
    // Sport Mode → Backend mode_id
    // =====================================================

    private fun getModeId(
        activityType: String
    ): Int? {


        return when (
            activityType
        ) {


            "登山",
            "爬山" ->

                1


            "走路",
            "健走" ->

                2


            "騎自行車",
            "單車" ->

                3


            else ->

                null
        }
    }


    // =====================================================
    // 結束運動
    // =====================================================

    private fun finishWorkout() {


        if (
            !isTracking
        ) {


            return
        }


        val persistedSession =
            database
                .getActiveWorkoutSession()


        val finalOwnerUsername =
            persistedSession
                ?.ownerUsername
                ?: ownerUsername


        val finalActivityType =
            persistedSession
                ?.activityType
                ?: activityType


        val endTimeMillis =
            System.currentTimeMillis()


        val durationSeconds =
            (
                    SystemClock.elapsedRealtime() -
                            startElapsedRealtime
                    )
                .coerceAtLeast(
                    0L
                ) /
                    1000L


        TrackingRepository.updateTime(
            durationSeconds
        )


        persistProgress()


        val state =
            TrackingRepository
                .state
                .value


        val routePointsFromDisk =
            database
                .getActiveRoutePoints()


        val routePoints =

            if (
                routePointsFromDisk.isNotEmpty()
            ) {


                routePointsFromDisk


            } else {


                TrackingRepository
                    .routePoints
                    .value
                    .toList()
            }


        val modeId =
            getModeId(
                finalActivityType
            )


        val canSyncLater =
            modeId !=
                    null &&
                    !finalOwnerUsername
                        .isNullOrBlank()


        // =================================================
        // 第一優先：
        // 正式保存 SQLite。
        // =================================================

        val localRecordId =
            database
                .finalizeActiveWorkout(


                    record =
                        WorkoutRecord(


                            activityType =
                                finalActivityType,


                            startTimeMillis =
                                startWallClockMillis,


                            endTimeMillis =
                                endTimeMillis,


                            durationSeconds =
                                durationSeconds,


                            steps =
                                state.steps,


                            distanceMeters =
                                state.distanceMeters,


                            modeId =
                                modeId,


                            ownerUsername =
                                finalOwnerUsername,


                            syncStatus =

                                if (
                                    canSyncLater
                                ) {


                                    WorkoutRecord.SYNC_PENDING


                                } else {


                                    WorkoutRecord.SYNC_FAILED
                                },


                            syncError =

                                when {


                                    modeId ==
                                            null ->


                                        "找不到「$finalActivityType」對應的後端 mode_id"


                                    finalOwnerUsername
                                        .isNullOrBlank() ->


                                        "找不到這場運動所屬的使用者"


                                    else ->


                                        null
                                }
                        ),


                    routePoints =
                        routePoints
                )


        if (
            localRecordId ==
            -1L
        ) {


            Toast.makeText(

                applicationContext,

                "本機運動紀錄儲存失敗，運動仍保持進行中，請再按一次結束",

                Toast.LENGTH_LONG

            ).show()


            return
        }


        TrackingRepository
            .stopTracking()


        isTracking =
            false


        cleanupTracking()


        stopForeground(
            STOP_FOREGROUND_REMOVE
        )


        if (
            modeId ==
            null ||
            finalOwnerUsername
                .isNullOrBlank()
        ) {


            Toast.makeText(

                applicationContext,

                "運動已保存到手機，但目前無法同步到後端",

                Toast.LENGTH_LONG

            ).show()


            stopSelf()


            return
        }


        val currentUsername =
            TokenManager
                .getUsername(
                    applicationContext
                )
                ?.trim()


        val hasUsableLogin =
            TokenManager
                .isLoggedIn(
                    applicationContext
                ) &&
                    currentUsername ==
                    finalOwnerUsername


        if (
            !hasUsableLogin
        ) {


            Toast.makeText(

                applicationContext,

                "運動已保存到手機，重新登入後可再上傳到後端",

                Toast.LENGTH_LONG

            ).show()


            stopSelf()


            return
        }


        // =================================================
        // 第二優先：
        // 後端同步。
        // =================================================

        ActivityUploadRepository
            .uploadWorkout(


                context =
                    applicationContext,


                modeId =
                    modeId,


                title =
                    finalActivityType,


                startTimeMillis =
                    startWallClockMillis,


                endTimeMillis =
                    endTimeMillis,


                durationSeconds =
                    durationSeconds,


                distanceMeters =
                    state
                        .distanceMeters
                        .toDouble(),


                routePoints =
                    routePoints,


                existingServerActivityId =
                    null,


                onServerActivityCreated = {
                        activityId ->


                    database
                        .saveServerActivityId(

                            recordId =
                                localRecordId,

                            serverActivityId =
                                activityId
                        )
                },


                onSuccess = {
                        activityId,
                        gpsPointsCount ->


                    database
                        .markSyncSuccess(

                            recordId =
                                localRecordId,

                            serverActivityId =
                                activityId
                        )


                    Toast.makeText(

                        applicationContext,

                        "運動紀錄同步成功\n" +
                                "activity_id：$activityId\n" +
                                "GPS：$gpsPointsCount 點",

                        Toast.LENGTH_LONG

                    ).show()
                },


                onError = {
                        message ->


                    database
                        .markSyncFailed(

                            recordId =
                                localRecordId,

                            errorMessage =
                                message
                        )


                    Toast.makeText(

                        applicationContext,

                        "本機紀錄已保存\n" +
                                "雲端同步失敗：$message",

                        Toast.LENGTH_LONG

                    ).show()
                }
            )


        stopSelf()
    }


    // =====================================================
    // 保存進行中狀態
    // =====================================================

    private fun persistProgress() {


        if (
            !isTracking
        ) {


            return
        }


        val state =
            TrackingRepository
                .state
                .value


        database
            .updateActiveWorkoutProgress(


                elapsedSeconds =
                    state.elapsedSeconds,


                steps =
                    state.steps,


                distanceMeters =
                    state.distanceMeters,


                startingSteps =
                    startingSteps,


                detectorSteps =
                    detectorSteps
            )
    }


    // =====================================================
    // 停止 Sensor / GPS / Timer
    // =====================================================

    private fun cleanupTracking() {


        if (
            ::sensorManager.isInitialized
        ) {


            sensorManager.unregisterListener(
                this
            )
        }


        if (
            ::fusedLocationClient.isInitialized
        ) {


            fusedLocationClient
                .removeLocationUpdates(
                    locationCallback
                )
        }


        handler.removeCallbacks(
            timerRunnable
        )
    }


    // =====================================================
    // Foreground Service
    // =====================================================

    private fun startForegroundTracking(
        elapsedSeconds: Long
    ) {


        val notification =
            createNotification(
                elapsedSeconds
            )


        if (
            Build.VERSION.SDK_INT >=
            34
        ) {


            val type =

                if (
                    activityType ==
                    "騎自行車"
                ) {


                    ServiceInfo
                        .FOREGROUND_SERVICE_TYPE_LOCATION


                } else {


                    ServiceInfo
                        .FOREGROUND_SERVICE_TYPE_LOCATION or
                            ServiceInfo
                                .FOREGROUND_SERVICE_TYPE_HEALTH
                }


            startForeground(

                NOTIFICATION_ID,

                notification,

                type
            )


        } else if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {


            startForeground(

                NOTIFICATION_ID,

                notification,

                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_LOCATION
            )


        } else {


            startForeground(

                NOTIFICATION_ID,

                notification
            )
        }
    }


    // =====================================================
    // Notification Channel
    // =====================================================

    private fun createNotificationChannel() {


        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {


            val channel =
                NotificationChannel(

                    CHANNEL_ID,

                    "運動紀錄",

                    NotificationManager.IMPORTANCE_LOW
                )


            val manager =
                getSystemService(
                    NotificationManager::class.java
                )


            manager.createNotificationChannel(
                channel
            )
        }
    }


    // =====================================================
    // Notification
    // =====================================================

    private fun createNotification(
        elapsedSeconds: Long
    ): Notification {


        val hours =
            elapsedSeconds /
                    3600L


        val minutes =
            (
                    elapsedSeconds %
                            3600L
                    ) /
                    60L


        val seconds =
            elapsedSeconds %
                    60L


        val timeText =
            String.format(

                Locale.TAIWAN,

                "%02d:%02d:%02d",

                hours,

                minutes,

                seconds
            )


        val distanceKm =
            TrackingRepository
                .state
                .value
                .distanceMeters /
                    1000f


        return NotificationCompat.Builder(

            this,

            CHANNEL_ID

        )
            .setContentTitle(
                "${activityType}紀錄進行中"
            )
            .setContentText(

                String.format(

                    Locale.TAIWAN,

                    "時間 %s　距離 %.2f km",

                    timeText,

                    distanceKm
                )
            )
            .setSmallIcon(
                android.R.drawable.ic_menu_mylocation
            )
            .setOnlyAlertOnce(
                true
            )
            .setOngoing(
                true
            )
            .build()
    }


    private fun updateNotification(
        elapsedSeconds: Long
    ) {


        val manager =
            getSystemService(
                NotificationManager::class.java
            )


        manager.notify(

            NOTIFICATION_ID,

            createNotification(
                elapsedSeconds
            )
        )
    }


    // =====================================================
    // RoutePoint → Location
    //
    // APP / Service 恢復時使用。
    // =====================================================

    private fun routePointToLocation(
        point: RoutePoint
    ): Location {


        return Location(
            "restored"
        ).apply {


            latitude =
                point.latitude


            longitude =
                point.longitude


            time =
                point.timestampMillis


            point.altitude
                ?.let {


                    altitude =
                        it
                }


            point.speedMs
                ?.let {


                    speed =
                        it.toFloat()
                }


            point.accuracyMeters
                ?.let {


                    accuracy =
                        it.toFloat()
                }


            point.heading
                ?.let {


                    bearing =
                        it.toFloat()
                }
        }
    }


    // =====================================================
    // 使用者滑掉 APP
    //
    // 運動繼續。
    // =====================================================

    override fun onTaskRemoved(
        rootIntent: Intent?
    ) {


        persistProgress()


        super.onTaskRemoved(
            rootIntent
        )
    }


    // =====================================================
    // Service Destroy
    // =====================================================

    override fun onDestroy() {


        if (
            isTracking
        ) {


            persistProgress()
        }


        cleanupTracking()


        super.onDestroy()
    }


    override fun onBind(
        intent: Intent?
    ): IBinder? {


        return null
    }
}