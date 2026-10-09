package com.example.project.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper


class WorkoutDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {


    companion object {

        private const val DATABASE_NAME =
            "workout_history.db"

        private const val DATABASE_VERSION =
            3

        private const val TABLE_WORKOUT =
            "workout_records"

        private const val TABLE_ROUTE =
            "workout_route_points"

        private const val TABLE_ACTIVE_SESSION =
            "active_workout_session"

        private const val TABLE_ACTIVE_ROUTE =
            "active_workout_route_points"

        private const val MAX_RECORDS =
            20
    }


    override fun onConfigure(
        db: SQLiteDatabase
    ) {

        super.onConfigure(
            db
        )

        db.setForeignKeyConstraintsEnabled(
            true
        )
    }


    override fun onCreate(
        db: SQLiteDatabase
    ) {

        createWorkoutTable(
            db
        )

        createRouteTable(
            db
        )

        createActiveWorkoutTables(
            db
        )
    }


    // =====================================================
    // 已完成運動
    // =====================================================

    private fun createWorkoutTable(
        db: SQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_WORKOUT (

                id INTEGER PRIMARY KEY AUTOINCREMENT,

                activity_type TEXT NOT NULL,

                start_time INTEGER NOT NULL,

                end_time INTEGER NOT NULL,

                duration_seconds INTEGER NOT NULL,

                steps INTEGER NOT NULL,

                distance_meters REAL NOT NULL,

                mode_id INTEGER,

                owner_username TEXT,

                server_activity_id INTEGER,

                sync_status TEXT NOT NULL DEFAULT 'pending',

                sync_error TEXT
            )
            """.trimIndent()
        )
    }


    private fun createRouteTable(
        db: SQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ROUTE (

                id INTEGER PRIMARY KEY AUTOINCREMENT,

                workout_id INTEGER NOT NULL,

                latitude REAL NOT NULL,

                longitude REAL NOT NULL,

                altitude REAL,

                speed_ms REAL,

                accuracy_meters REAL,

                heading REAL,

                timestamp_millis INTEGER NOT NULL,

                sequence_order INTEGER NOT NULL,

                FOREIGN KEY(workout_id)
                    REFERENCES $TABLE_WORKOUT(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )


        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            idx_route_workout_sequence

            ON $TABLE_ROUTE (
                workout_id,
                sequence_order
            )
            """.trimIndent()
        )
    }


    // =====================================================
    // 正在進行中的運動
    // =====================================================

    private fun createActiveWorkoutTables(
        db: SQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ACTIVE_SESSION (

                id INTEGER PRIMARY KEY CHECK (id = 1),

                activity_type TEXT NOT NULL,

                owner_username TEXT NOT NULL,

                start_wall_clock INTEGER NOT NULL,

                start_elapsed_realtime INTEGER NOT NULL,

                elapsed_seconds INTEGER NOT NULL DEFAULT 0,

                steps INTEGER NOT NULL DEFAULT 0,

                distance_meters REAL NOT NULL DEFAULT 0,

                starting_steps REAL,

                detector_steps INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )


        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ACTIVE_ROUTE (

                id INTEGER PRIMARY KEY AUTOINCREMENT,

                latitude REAL NOT NULL,

                longitude REAL NOT NULL,

                altitude REAL,

                speed_ms REAL,

                accuracy_meters REAL,

                heading REAL,

                timestamp_millis INTEGER NOT NULL,

                sequence_order INTEGER NOT NULL UNIQUE
            )
            """.trimIndent()
        )
    }


    // =====================================================
    // DB Upgrade
    // =====================================================

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {


        if (
            oldVersion < 2
        ) {

            db.execSQL(
                """
                ALTER TABLE $TABLE_WORKOUT
                ADD COLUMN mode_id INTEGER
                """.trimIndent()
            )


            db.execSQL(
                """
                ALTER TABLE $TABLE_WORKOUT
                ADD COLUMN owner_username TEXT
                """.trimIndent()
            )


            db.execSQL(
                """
                ALTER TABLE $TABLE_WORKOUT
                ADD COLUMN server_activity_id INTEGER
                """.trimIndent()
            )


            db.execSQL(
                """
                ALTER TABLE $TABLE_WORKOUT
                ADD COLUMN sync_status TEXT
                NOT NULL
                DEFAULT 'legacy'
                """.trimIndent()
            )


            db.execSQL(
                """
                ALTER TABLE $TABLE_WORKOUT
                ADD COLUMN sync_error TEXT
                """.trimIndent()
            )


            createRouteTable(
                db
            )
        }


        if (
            oldVersion < 3
        ) {

            createActiveWorkoutTables(
                db
            )
        }
    }


    // =====================================================
    // 舊版新增紀錄
    // =====================================================

    fun insertRecord(
        record: WorkoutRecord
    ): Long {


        val db =
            writableDatabase


        db.beginTransaction()


        return try {


            val recordId =
                insertRecordInternal(
                    db,
                    record
                )


            if (
                recordId != -1L
            ) {

                deleteOldRecords(
                    db
                )

                db.setTransactionSuccessful()
            }


            recordId


        } finally {

            db.endTransaction()
        }
    }


    // =====================================================
    // 已完成運動 + GPS
    // =====================================================

    fun insertRecordWithRoute(

        record: WorkoutRecord,

        routePoints: List<RoutePoint>

    ): Long {


        val db =
            writableDatabase


        db.beginTransaction()


        return try {


            val recordId =
                insertRecordInternal(
                    db,
                    record
                )


            if (
                recordId == -1L
            ) {


                -1L


            } else if (
                !insertRoutePointsInternal(

                    db =
                        db,

                    workoutId =
                        recordId,

                    routePoints =
                        routePoints
                )
            ) {


                -1L


            } else {


                deleteOldRecords(
                    db
                )


                db.setTransactionSuccessful()


                recordId
            }


        } finally {


            db.endTransaction()
        }
    }


    // =====================================================
    // 結束「正在進行中的運動」
    //
    // 這裡使用同一個 Transaction：
    //
    // 1. 寫正式運動紀錄
    // 2. 寫正式 GPS
    // 3. 清 active route
    // 4. 清 active session
    //
    // 如果中間失敗，
    // 整筆 rollback。
    // =====================================================

    fun finalizeActiveWorkout(

        record: WorkoutRecord,

        routePoints: List<RoutePoint>

    ): Long {


        val db =
            writableDatabase


        db.beginTransaction()


        return try {


            val recordId =
                insertRecordInternal(
                    db,
                    record
                )


            if (
                recordId == -1L
            ) {


                -1L


            } else if (
                !insertRoutePointsInternal(

                    db =
                        db,

                    workoutId =
                        recordId,

                    routePoints =
                        routePoints
                )
            ) {


                -1L


            } else {


                db.delete(
                    TABLE_ACTIVE_ROUTE,
                    null,
                    null
                )


                db.delete(
                    TABLE_ACTIVE_SESSION,
                    null,
                    null
                )


                deleteOldRecords(
                    db
                )


                db.setTransactionSuccessful()


                recordId
            }


        } finally {


            db.endTransaction()
        }
    }


    // =====================================================
    // 寫入 WorkoutRecord
    // =====================================================

    private fun insertRecordInternal(

        db: SQLiteDatabase,

        record: WorkoutRecord

    ): Long {


        val values =
            ContentValues().apply {


                put(
                    "activity_type",
                    record.activityType
                )


                put(
                    "start_time",
                    record.startTimeMillis
                )


                put(
                    "end_time",
                    record.endTimeMillis
                )


                put(
                    "duration_seconds",
                    record.durationSeconds
                )


                put(
                    "steps",
                    record.steps
                )


                put(
                    "distance_meters",
                    record.distanceMeters
                )


                putNullableInt(
                    "mode_id",
                    record.modeId
                )


                putNullableString(
                    "owner_username",
                    record.ownerUsername
                )


                putNullableLong(
                    "server_activity_id",
                    record.serverActivityId
                )


                put(
                    "sync_status",
                    record.syncStatus
                )


                putNullableString(
                    "sync_error",
                    record.syncError
                )
            }


        return db.insert(
            TABLE_WORKOUT,
            null,
            values
        )
    }


    // =====================================================
    // 正式 GPS
    // =====================================================

    private fun insertRoutePointsInternal(

        db: SQLiteDatabase,

        workoutId: Long,

        routePoints: List<RoutePoint>

    ): Boolean {


        routePoints
            .forEachIndexed {
                    index,
                    point ->


                val values =
                    routePointValues(

                        point =
                            point,

                        sequenceOrder =
                            index

                    ).apply {


                        put(
                            "workout_id",
                            workoutId
                        )
                    }


                val result =
                    db.insert(

                        TABLE_ROUTE,

                        null,

                        values
                    )


                if (
                    result == -1L
                ) {


                    return false
                }
            }


        return true
    }


    // =====================================================
    // 最近 20 筆
    // =====================================================

    fun getLatestRecords():
            List<WorkoutRecord> {


        val records =
            mutableListOf<WorkoutRecord>()


        val cursor =
            readableDatabase.query(

                TABLE_WORKOUT,

                null,

                null,

                null,

                null,

                null,

                "start_time DESC, id DESC",

                MAX_RECORDS.toString()
            )


        cursor.use {


            while (
                it.moveToNext()
            ) {


                records.add(
                    cursorToRecord(
                        it
                    )
                )
            }
        }


        return records
    }


    fun getRecordsBetween(

        startMillis: Long,

        endMillis: Long

    ): List<WorkoutRecord> {


        val records =
            mutableListOf<WorkoutRecord>()


        val cursor =
            readableDatabase.query(

                TABLE_WORKOUT,

                null,

                "start_time >= ? AND start_time < ?",

                arrayOf(
                    startMillis.toString(),
                    endMillis.toString()
                ),

                null,

                null,

                "start_time DESC"
            )


        cursor.use {


            while (
                it.moveToNext()
            ) {


                records.add(
                    cursorToRecord(
                        it
                    )
                )
            }
        }


        return records
    }


    fun getRecordById(
        recordId: Long
    ): WorkoutRecord? {


        val cursor =
            readableDatabase.query(

                TABLE_WORKOUT,

                null,

                "id = ?",

                arrayOf(
                    recordId.toString()
                ),

                null,

                null,

                null,

                "1"
            )


        cursor.use {


            if (
                it.moveToFirst()
            ) {


                return cursorToRecord(
                    it
                )
            }
        }


        return null
    }


    // =====================================================
    // 正式歷史 GPS
    // =====================================================

    fun getRoutePoints(
        recordId: Long
    ): List<RoutePoint> {


        val points =
            mutableListOf<RoutePoint>()


        val cursor =
            readableDatabase.query(

                TABLE_ROUTE,

                null,

                "workout_id = ?",

                arrayOf(
                    recordId.toString()
                ),

                null,

                null,

                "sequence_order ASC"
            )


        cursor.use {


            while (
                it.moveToNext()
            ) {


                points.add(
                    cursorToRoutePoint(
                        it
                    )
                )
            }
        }


        return points
    }


    // =====================================================
    // 所有 pending / failed
    // =====================================================

    fun getUnsyncedRecords():
            List<WorkoutRecord> {


        val records =
            mutableListOf<WorkoutRecord>()


        val cursor =
            readableDatabase.query(

                TABLE_WORKOUT,

                null,

                "sync_status = ? OR sync_status = ?",

                arrayOf(
                    WorkoutRecord.SYNC_PENDING,
                    WorkoutRecord.SYNC_FAILED
                ),

                null,

                null,

                "start_time ASC"
            )


        cursor.use {


            while (
                it.moveToNext()
            ) {


                records.add(
                    cursorToRecord(
                        it
                    )
                )
            }
        }


        return records
    }


    // =====================================================
    // 指定帳號 pending / failed
    // =====================================================

    fun getUnsyncedRecordsForOwner(
        username: String
    ): List<WorkoutRecord> {


        val records =
            mutableListOf<WorkoutRecord>()


        val cursor =
            readableDatabase.query(

                TABLE_WORKOUT,

                null,

                """
                owner_username = ?
                AND (
                    sync_status = ?
                    OR sync_status = ?
                )
                """.trimIndent(),

                arrayOf(
                    username,
                    WorkoutRecord.SYNC_PENDING,
                    WorkoutRecord.SYNC_FAILED
                ),

                null,

                null,

                "start_time ASC"
            )


        cursor.use {


            while (
                it.moveToNext()
            ) {


                records.add(
                    cursorToRecord(
                        it
                    )
                )
            }
        }


        return records
    }


    fun saveServerActivityId(

        recordId: Long,

        serverActivityId: Long

    ) {


        val values =
            ContentValues().apply {


                put(
                    "server_activity_id",
                    serverActivityId
                )


                put(
                    "sync_status",
                    WorkoutRecord.SYNC_PENDING
                )


                putNull(
                    "sync_error"
                )
            }


        writableDatabase.update(

            TABLE_WORKOUT,

            values,

            "id = ?",

            arrayOf(
                recordId.toString()
            )
        )
    }


    fun markSyncSuccess(

        recordId: Long,

        serverActivityId: Long

    ) {


        val values =
            ContentValues().apply {


                put(
                    "server_activity_id",
                    serverActivityId
                )


                put(
                    "sync_status",
                    WorkoutRecord.SYNC_SYNCED
                )


                putNull(
                    "sync_error"
                )
            }


        writableDatabase.update(

            TABLE_WORKOUT,

            values,

            "id = ?",

            arrayOf(
                recordId.toString()
            )
        )
    }


    fun markSyncFailed(

        recordId: Long,

        errorMessage: String

    ) {


        val values =
            ContentValues().apply {


                put(
                    "sync_status",
                    WorkoutRecord.SYNC_FAILED
                )


                put(
                    "sync_error",
                    errorMessage
                )
            }


        writableDatabase.update(

            TABLE_WORKOUT,

            values,

            "id = ?",

            arrayOf(
                recordId.toString()
            )
        )
    }


    fun markSyncPending(
        recordId: Long
    ) {


        val values =
            ContentValues().apply {


                put(
                    "sync_status",
                    WorkoutRecord.SYNC_PENDING
                )


                putNull(
                    "sync_error"
                )
            }


        writableDatabase.update(

            TABLE_WORKOUT,

            values,

            "id = ?",

            arrayOf(
                recordId.toString()
            )
        )
    }


    // =====================================================
    // 建立「進行中」Session
    // =====================================================

    fun beginActiveWorkout(
        session: ActiveWorkoutSession
    ): Boolean {


        val db =
            writableDatabase


        db.beginTransaction()


        return try {


            db.delete(
                TABLE_ACTIVE_ROUTE,
                null,
                null
            )


            db.delete(
                TABLE_ACTIVE_SESSION,
                null,
                null
            )


            val values =
                ContentValues().apply {


                    put(
                        "id",
                        1
                    )


                    put(
                        "activity_type",
                        session.activityType
                    )


                    put(
                        "owner_username",
                        session.ownerUsername
                    )


                    put(
                        "start_wall_clock",
                        session.startWallClockMillis
                    )


                    put(
                        "start_elapsed_realtime",
                        session.startElapsedRealtime
                    )


                    put(
                        "elapsed_seconds",
                        session.elapsedSeconds
                    )


                    put(
                        "steps",
                        session.steps
                    )


                    put(
                        "distance_meters",
                        session.distanceMeters
                    )


                    if (
                        session.startingSteps != null
                    ) {


                        put(
                            "starting_steps",
                            session.startingSteps
                        )


                    } else {


                        putNull(
                            "starting_steps"
                        )
                    }


                    put(
                        "detector_steps",
                        session.detectorSteps
                    )
                }


            val result =
                db.insert(

                    TABLE_ACTIVE_SESSION,

                    null,

                    values
                )


            if (
                result != -1L
            ) {


                db.setTransactionSuccessful()


                true


            } else {


                false
            }


        } finally {


            db.endTransaction()
        }
    }


    fun hasActiveWorkoutSession():
            Boolean {


        return getActiveWorkoutSession() !=
                null
    }


    fun getActiveWorkoutSession():
            ActiveWorkoutSession? {


        val cursor =
            readableDatabase.query(

                TABLE_ACTIVE_SESSION,

                null,

                "id = 1",

                null,

                null,

                null,

                null,

                "1"
            )


        cursor.use {


            if (
                !it.moveToFirst()
            ) {


                return null
            }


            val startingStepsIndex =
                it.getColumnIndexOrThrow(
                    "starting_steps"
                )


            return ActiveWorkoutSession(


                activityType =
                    it.getString(
                        it.getColumnIndexOrThrow(
                            "activity_type"
                        )
                    ),


                ownerUsername =
                    it.getString(
                        it.getColumnIndexOrThrow(
                            "owner_username"
                        )
                    ),


                startWallClockMillis =
                    it.getLong(
                        it.getColumnIndexOrThrow(
                            "start_wall_clock"
                        )
                    ),


                startElapsedRealtime =
                    it.getLong(
                        it.getColumnIndexOrThrow(
                            "start_elapsed_realtime"
                        )
                    ),


                elapsedSeconds =
                    it.getLong(
                        it.getColumnIndexOrThrow(
                            "elapsed_seconds"
                        )
                    ),


                steps =
                    it.getInt(
                        it.getColumnIndexOrThrow(
                            "steps"
                        )
                    ),


                distanceMeters =
                    it.getFloat(
                        it.getColumnIndexOrThrow(
                            "distance_meters"
                        )
                    ),


                startingSteps =

                    if (
                        it.isNull(
                            startingStepsIndex
                        )
                    ) {


                        null


                    } else {


                        it.getFloat(
                            startingStepsIndex
                        )
                    },


                detectorSteps =
                    it.getInt(
                        it.getColumnIndexOrThrow(
                            "detector_steps"
                        )
                    )
            )
        }
    }


    // =====================================================
    // 更新進行中運動
    // =====================================================

    fun updateActiveWorkoutProgress(

        elapsedSeconds: Long,

        steps: Int,

        distanceMeters: Float,

        startingSteps: Float?,

        detectorSteps: Int

    ) {


        val values =
            ContentValues().apply {


                put(
                    "elapsed_seconds",
                    elapsedSeconds.coerceAtLeast(
                        0L
                    )
                )


                put(
                    "steps",
                    steps.coerceAtLeast(
                        0
                    )
                )


                put(
                    "distance_meters",
                    distanceMeters.coerceAtLeast(
                        0f
                    )
                )


                if (
                    startingSteps != null
                ) {


                    put(
                        "starting_steps",
                        startingSteps
                    )


                } else {


                    putNull(
                        "starting_steps"
                    )
                }


                put(
                    "detector_steps",
                    detectorSteps.coerceAtLeast(
                        0
                    )
                )
            }


        writableDatabase.update(

            TABLE_ACTIVE_SESSION,

            values,

            "id = 1",

            null
        )
    }


    // =====================================================
    // 每一個有效 GPS 點立即保存
    // =====================================================

    fun appendActiveRoutePoint(
        point: RoutePoint
    ): Long {


        val db =
            writableDatabase


        val nextSequence =
            getNextActiveRouteSequence(
                db
            )


        val values =
            routePointValues(

                point =
                    point,

                sequenceOrder =
                    nextSequence
            )


        return db.insert(

            TABLE_ACTIVE_ROUTE,

            null,

            values
        )
    }


    fun getActiveRoutePoints():
            List<RoutePoint> {


        val points =
            mutableListOf<RoutePoint>()


        val cursor =
            readableDatabase.query(

                TABLE_ACTIVE_ROUTE,

                null,

                null,

                null,

                null,

                null,

                "sequence_order ASC"
            )


        cursor.use {


            while (
                it.moveToNext()
            ) {


                points.add(
                    cursorToRoutePoint(
                        it
                    )
                )
            }
        }


        return points
    }


    fun clearActiveWorkout() {


        val db =
            writableDatabase


        db.beginTransaction()


        try {


            db.delete(
                TABLE_ACTIVE_ROUTE,
                null,
                null
            )


            db.delete(
                TABLE_ACTIVE_SESSION,
                null,
                null
            )


            db.setTransactionSuccessful()


        } finally {


            db.endTransaction()
        }
    }


    private fun getNextActiveRouteSequence(
        db: SQLiteDatabase
    ): Int {


        val cursor =
            db.rawQuery(
                """
                SELECT COALESCE(
                    MAX(sequence_order),
                    -1
                ) + 1
                FROM $TABLE_ACTIVE_ROUTE
                """.trimIndent(),
                null
            )


        cursor.use {


            return if (
                it.moveToFirst()
            ) {


                it.getInt(
                    0
                )


            } else {


                0
            }
        }
    }


    // =====================================================
    // 只保留「畫面上的」最近資料
    //
    // pending / failed 絕對不能自動刪除。
    // =====================================================

    private fun deleteOldRecords(
        db: SQLiteDatabase
    ) {


        db.execSQL(
            """
            DELETE FROM $TABLE_WORKOUT

            WHERE sync_status IN (
                '${WorkoutRecord.SYNC_SYNCED}',
                '${WorkoutRecord.SYNC_LEGACY}'
            )

            AND id NOT IN (

                SELECT id

                FROM $TABLE_WORKOUT

                ORDER BY start_time DESC, id DESC

                LIMIT $MAX_RECORDS
            )
            """.trimIndent()
        )
    }


    // =====================================================
    // Cursor → WorkoutRecord
    // =====================================================

    private fun cursorToRecord(
        cursor: Cursor
    ): WorkoutRecord {


        val modeIdIndex =
            cursor.getColumnIndex(
                "mode_id"
            )


        val ownerUsernameIndex =
            cursor.getColumnIndex(
                "owner_username"
            )


        val serverActivityIdIndex =
            cursor.getColumnIndex(
                "server_activity_id"
            )


        val syncStatusIndex =
            cursor.getColumnIndex(
                "sync_status"
            )


        val syncErrorIndex =
            cursor.getColumnIndex(
                "sync_error"
            )


        return WorkoutRecord(


            id =
                cursor.getLong(
                    cursor.getColumnIndexOrThrow(
                        "id"
                    )
                ),


            activityType =
                cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        "activity_type"
                    )
                ),


            startTimeMillis =
                cursor.getLong(
                    cursor.getColumnIndexOrThrow(
                        "start_time"
                    )
                ),


            endTimeMillis =
                cursor.getLong(
                    cursor.getColumnIndexOrThrow(
                        "end_time"
                    )
                ),


            durationSeconds =
                cursor.getLong(
                    cursor.getColumnIndexOrThrow(
                        "duration_seconds"
                    )
                ),


            steps =
                cursor.getInt(
                    cursor.getColumnIndexOrThrow(
                        "steps"
                    )
                ),


            distanceMeters =
                cursor.getFloat(
                    cursor.getColumnIndexOrThrow(
                        "distance_meters"
                    )
                ),


            modeId =

                if (
                    modeIdIndex >= 0 &&
                    !cursor.isNull(
                        modeIdIndex
                    )
                ) {


                    cursor.getInt(
                        modeIdIndex
                    )


                } else {


                    null
                },


            ownerUsername =

                if (
                    ownerUsernameIndex >= 0 &&
                    !cursor.isNull(
                        ownerUsernameIndex
                    )
                ) {


                    cursor.getString(
                        ownerUsernameIndex
                    )


                } else {


                    null
                },


            serverActivityId =

                if (
                    serverActivityIdIndex >= 0 &&
                    !cursor.isNull(
                        serverActivityIdIndex
                    )
                ) {


                    cursor.getLong(
                        serverActivityIdIndex
                    )


                } else {


                    null
                },


            syncStatus =

                if (
                    syncStatusIndex >= 0 &&
                    !cursor.isNull(
                        syncStatusIndex
                    )
                ) {


                    cursor.getString(
                        syncStatusIndex
                    )


                } else {


                    WorkoutRecord.SYNC_LEGACY
                },


            syncError =

                if (
                    syncErrorIndex >= 0 &&
                    !cursor.isNull(
                        syncErrorIndex
                    )
                ) {


                    cursor.getString(
                        syncErrorIndex
                    )


                } else {


                    null
                }
        )
    }


    // =====================================================
    // Cursor → RoutePoint
    // =====================================================

    private fun cursorToRoutePoint(
        cursor: Cursor
    ): RoutePoint {


        val altitudeIndex =
            cursor.getColumnIndexOrThrow(
                "altitude"
            )


        val speedIndex =
            cursor.getColumnIndexOrThrow(
                "speed_ms"
            )


        val accuracyIndex =
            cursor.getColumnIndexOrThrow(
                "accuracy_meters"
            )


        val headingIndex =
            cursor.getColumnIndexOrThrow(
                "heading"
            )


        return RoutePoint(


            latitude =
                cursor.getDouble(
                    cursor.getColumnIndexOrThrow(
                        "latitude"
                    )
                ),


            longitude =
                cursor.getDouble(
                    cursor.getColumnIndexOrThrow(
                        "longitude"
                    )
                ),


            altitude =

                if (
                    cursor.isNull(
                        altitudeIndex
                    )
                ) {


                    null


                } else {


                    cursor.getDouble(
                        altitudeIndex
                    )
                },


            speedMs =

                if (
                    cursor.isNull(
                        speedIndex
                    )
                ) {


                    null


                } else {


                    cursor.getDouble(
                        speedIndex
                    )
                },


            accuracyMeters =

                if (
                    cursor.isNull(
                        accuracyIndex
                    )
                ) {


                    null


                } else {


                    cursor.getDouble(
                        accuracyIndex
                    )
                },


            heading =

                if (
                    cursor.isNull(
                        headingIndex
                    )
                ) {


                    null


                } else {


                    cursor.getDouble(
                        headingIndex
                    )
                },


            timestampMillis =
                cursor.getLong(
                    cursor.getColumnIndexOrThrow(
                        "timestamp_millis"
                    )
                )
        )
    }


    // =====================================================
    // RoutePoint → ContentValues
    // =====================================================

    private fun routePointValues(

        point: RoutePoint,

        sequenceOrder: Int

    ): ContentValues {


        return ContentValues().apply {


            put(
                "latitude",
                point.latitude
            )


            put(
                "longitude",
                point.longitude
            )


            putNullableDouble(
                "altitude",
                point.altitude
            )


            putNullableDouble(
                "speed_ms",
                point.speedMs
            )


            putNullableDouble(
                "accuracy_meters",
                point.accuracyMeters
            )


            putNullableDouble(
                "heading",
                point.heading
            )


            put(
                "timestamp_millis",
                point.timestampMillis
            )


            put(
                "sequence_order",
                sequenceOrder
            )
        }
    }


    private fun ContentValues.putNullableInt(
        key: String,
        value: Int?
    ) {


        if (
            value == null
        ) {


            putNull(
                key
            )


        } else {


            put(
                key,
                value
            )
        }
    }


    private fun ContentValues.putNullableLong(
        key: String,
        value: Long?
    ) {


        if (
            value == null
        ) {


            putNull(
                key
            )


        } else {


            put(
                key,
                value
            )
        }
    }


    private fun ContentValues.putNullableDouble(
        key: String,
        value: Double?
    ) {


        if (
            value == null
        ) {


            putNull(
                key
            )


        } else {


            put(
                key,
                value
            )
        }
    }


    private fun ContentValues.putNullableString(
        key: String,
        value: String?
    ) {


        if (
            value == null
        ) {


            putNull(
                key
            )


        } else {


            put(
                key,
                value
            )
        }
    }
}