package com.example.project.data

import android.content.Context

import com.example.project.auth.TokenManager


object PendingWorkoutSyncRepository {


    @Volatile
    private var isSyncing =
        false


    fun isSyncInProgress():
            Boolean {


        return isSyncing
    }


    fun syncPendingWorkouts(

        context: Context,

        onComplete: (
            successCount: Int,
            failedCount: Int
        ) -> Unit = { _, _ -> }

    ) {


        if (
            isSyncing
        ) {


            onComplete(
                0,
                0
            )


            return
        }


        val applicationContext =
            context.applicationContext


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
            username == null ||
            !TokenManager
                .isLoggedIn(
                    applicationContext
                )
        ) {


            onComplete(
                0,
                0
            )


            return
        }


        val database =
            WorkoutDatabaseHelper(
                applicationContext
            )


        val records =
            database
                .getUnsyncedRecordsForOwner(
                    username
                )


        if (
            records.isEmpty()
        ) {


            onComplete(
                0,
                0
            )


            return
        }


        isSyncing =
            true


        syncNext(

            context =
                applicationContext,

            database =
                database,

            records =
                records,

            index =
                0,

            successCount =
                0,

            failedCount =
                0,

            expectedUsername =
                username,

            onComplete =
                onComplete
        )
    }


    private fun syncNext(

        context: Context,

        database: WorkoutDatabaseHelper,

        records: List<WorkoutRecord>,

        index: Int,

        successCount: Int,

        failedCount: Int,

        expectedUsername: String,

        onComplete: (
            successCount: Int,
            failedCount: Int
        ) -> Unit

    ) {


        val currentUsername =
            TokenManager
                .getUsername(
                    context
                )
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }


        val stillLoggedIn =
            TokenManager
                .isLoggedIn(
                    context
                )


        if (
            !stillLoggedIn ||
            currentUsername !=
            expectedUsername
        ) {


            isSyncing =
                false


            onComplete(
                successCount,
                failedCount
            )


            return
        }


        if (
            index >= records.size
        ) {


            isSyncing =
                false


            onComplete(
                successCount,
                failedCount
            )


            return
        }


        val record =
            records[index]


        if (
            record.ownerUsername !=
            expectedUsername
        ) {


            syncNext(

                context =
                    context,

                database =
                    database,

                records =
                    records,

                index =
                    index + 1,

                successCount =
                    successCount,

                failedCount =
                    failedCount,

                expectedUsername =
                    expectedUsername,

                onComplete =
                    onComplete
            )


            return
        }


        val modeId =
            record.modeId


        if (
            modeId == null
        ) {


            database.markSyncFailed(

                recordId =
                    record.id,

                errorMessage =
                    "本機紀錄缺少 mode_id"
            )


            syncNext(

                context =
                    context,

                database =
                    database,

                records =
                    records,

                index =
                    index + 1,

                successCount =
                    successCount,

                failedCount =
                    failedCount + 1,

                expectedUsername =
                    expectedUsername,

                onComplete =
                    onComplete
            )


            return
        }


        val routePoints =
            database
                .getRoutePoints(
                    record.id
                )


        if (
            routePoints.isEmpty()
        ) {


            database.markSyncFailed(

                recordId =
                    record.id,

                errorMessage =
                    "本機紀錄沒有 GPS 軌跡"
            )


            syncNext(

                context =
                    context,

                database =
                    database,

                records =
                    records,

                index =
                    index + 1,

                successCount =
                    successCount,

                failedCount =
                    failedCount + 1,

                expectedUsername =
                    expectedUsername,

                onComplete =
                    onComplete
            )


            return
        }


        val usernameBeforeUpload =
            TokenManager
                .getUsername(
                    context
                )
                ?.trim()


        if (
            usernameBeforeUpload !=
            expectedUsername ||
            !TokenManager
                .isLoggedIn(
                    context
                )
        ) {


            isSyncing =
                false


            onComplete(
                successCount,
                failedCount
            )


            return
        }


        database.markSyncPending(
            record.id
        )


        ActivityUploadRepository
            .uploadWorkout(


                context =
                    context,


                modeId =
                    modeId,


                title =
                    record.activityType,


                startTimeMillis =
                    record.startTimeMillis,


                endTimeMillis =
                    record.endTimeMillis,


                durationSeconds =
                    record.durationSeconds,


                distanceMeters =
                    record
                        .distanceMeters
                        .toDouble(),


                routePoints =
                    routePoints,


                existingServerActivityId =
                    record.serverActivityId,


                onServerActivityCreated = {
                        activityId ->


                    database.saveServerActivityId(

                        recordId =
                            record.id,

                        serverActivityId =
                            activityId
                    )
                },


                onSuccess = {
                        activityId,
                        _ ->


                    database.markSyncSuccess(

                        recordId =
                            record.id,

                        serverActivityId =
                            activityId
                    )


                    syncNext(

                        context =
                            context,

                        database =
                            database,

                        records =
                            records,

                        index =
                            index + 1,

                        successCount =
                            successCount + 1,

                        failedCount =
                            failedCount,

                        expectedUsername =
                            expectedUsername,

                        onComplete =
                            onComplete
                    )
                },


                onError = {
                        message ->


                    database.markSyncFailed(

                        recordId =
                            record.id,

                        errorMessage =
                            message
                    )


                    syncNext(

                        context =
                            context,

                        database =
                            database,

                        records =
                            records,

                        index =
                            index + 1,

                        successCount =
                            successCount,

                        failedCount =
                            failedCount + 1,

                        expectedUsername =
                            expectedUsername,

                        onComplete =
                            onComplete
                    )
                }
            )
    }
}