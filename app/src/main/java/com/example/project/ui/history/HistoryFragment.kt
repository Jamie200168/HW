package com.example.project.ui.history

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

import com.example.project.R
import com.example.project.auth.TokenManager
import com.example.project.data.ActivityHistoryRepository
import com.example.project.data.PendingWorkoutSyncRepository
import com.example.project.data.WorkoutDatabaseHelper
import com.example.project.data.WorkoutRecord
import com.example.project.network.ActivitySummary

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone


class HistoryFragment :
    Fragment(
        R.layout.fragment_history
    ) {


    private lateinit var database:
            WorkoutDatabaseHelper


    private lateinit var calendarGrid:
            GridLayout


    private lateinit var tvMonthTitle:
            TextView


    private lateinit var tvSelectedDate:
            TextView


    private lateinit var recordsContainer:
            LinearLayout


    private lateinit var btnSyncPending:
            Button


    private val currentMonth =
        Calendar.getInstance()


    private var selectedDay:
            Int? =
        null


    private var historyItems:
            List<HistoryItem> =
        emptyList()


    private enum class HistorySource {

        SERVER,

        LOCAL
    }


    private data class HistoryItem(

        val source:
        HistorySource,

        val localRecordId:
        Long?,

        val serverActivityId:
        Long?,

        val activityType:
        String,

        val modeId:
        Int?,

        val startTimeMillis:
        Long,

        val endTimeMillis:
        Long?,

        val durationSeconds:
        Long?,

        val steps:
        Int?,

        val distanceMeters:
        Double,

        val syncStatus:
        String?,

        val backendStatus:
        String? = null
    )


    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {


        super.onViewCreated(
            view,
            savedInstanceState
        )


        database =
            WorkoutDatabaseHelper(
                requireContext()
            )


        calendarGrid =
            view.findViewById(
                R.id.calendarGrid
            )


        tvMonthTitle =
            view.findViewById(
                R.id.tvMonthTitle
            )


        tvSelectedDate =
            view.findViewById(
                R.id.tvSelectedDate
            )


        recordsContainer =
            view.findViewById(
                R.id.recordsContainer
            )


        btnSyncPending =
            view.findViewById(
                R.id.btnSyncPending
            )


        val btnBack =
            view.findViewById<Button>(
                R.id.btnBack
            )


        val btnPreviousMonth =
            view.findViewById<Button>(
                R.id.btnPreviousMonth
            )


        val btnNextMonth =
            view.findViewById<Button>(
                R.id.btnNextMonth
            )


        btnBack
            .setOnClickListener {


                requireActivity()
                    .finish()
            }


        btnPreviousMonth
            .setOnClickListener {


                currentMonth.add(
                    Calendar.MONTH,
                    -1
                )


                selectedDay =
                    null


                renderCalendar()


                clearSelectedRecords()
            }


        btnNextMonth
            .setOnClickListener {


                currentMonth.add(
                    Calendar.MONTH,
                    1
                )


                selectedDay =
                    null


                renderCalendar()


                clearSelectedRecords()
            }


        btnSyncPending
            .setOnClickListener {


                manualSyncPendingRecords()
            }


        loadHistoryData()
    }


    // =====================================================
    // 手動重新上傳
    // =====================================================

    private fun manualSyncPendingRecords() {


        if (
            !TokenManager
                .isLoggedIn(
                    requireContext()
                )
        ) {


            Toast.makeText(

                requireContext(),

                "請先重新登入，再上傳未同步的運動紀錄",

                Toast.LENGTH_LONG

            ).show()


            return
        }


        if (
            PendingWorkoutSyncRepository
                .isSyncInProgress()
        ) {


            Toast.makeText(

                requireContext(),

                "目前已有同步作業正在進行",

                Toast.LENGTH_SHORT

            ).show()


            return
        }


        btnSyncPending.isEnabled =
            false


        btnSyncPending.text =
            "同步中..."


        PendingWorkoutSyncRepository
            .syncPendingWorkouts(


                context =
                    requireContext()
                        .applicationContext,


                onComplete =
                    syncCallback@{
                            successCount,
                            failedCount ->


                        if (
                            !isAdded
                        ) {


                            return@syncCallback
                        }


                        btnSyncPending.isEnabled =
                            true


                        btnSyncPending.text =
                            "重新上傳未同步紀錄"


                        if (
                            !TokenManager
                                .isLoggedIn(
                                    requireContext()
                                )
                        ) {


                            Toast.makeText(

                                requireContext(),

                                "登入狀態已失效，請重新登入後再同步",

                                Toast.LENGTH_LONG

                            ).show()


                            return@syncCallback
                        }


                        val message =

                            when {


                                successCount == 0 &&
                                        failedCount == 0 ->


                                    "目前沒有需要重新上傳的運動紀錄"


                                successCount > 0 &&
                                        failedCount == 0 ->


                                    "已成功上傳 $successCount 筆運動紀錄"


                                successCount > 0 ->


                                    "同步完成\n" +
                                            "成功：$successCount 筆\n" +
                                            "失敗：$failedCount 筆"


                                else ->


                                    "仍有 $failedCount 筆運動紀錄上傳失敗"
                            }


                        Toast.makeText(

                            requireContext(),

                            message,

                            Toast.LENGTH_LONG

                        ).show()


                        loadHistoryData()
                    }
            )
    }


    // =====================================================
    // Server + Local History
    // =====================================================

    private fun loadHistoryData() {


        val localRecords =
            getVisibleLocalRecords()


        ActivityHistoryRepository
            .loadActivities(


                context =
                    requireContext()
                        .applicationContext,


                page =
                    1,


                pageSize =
                    20,


                onSuccess = {
                        response ->


                    if (
                        !isAdded
                    ) {


                        return@loadActivities
                    }


                    historyItems =
                        mergeHistoryRecords(

                            localRecords =
                                localRecords,

                            serverRecords =
                                response.activities
                        )


                    refreshHistoryScreen()
                },


                onError = {
                        message ->


                    if (
                        !isAdded
                    ) {


                        return@loadActivities
                    }


                    historyItems =
                        localRecords
                            .map {
                                    record ->


                                localRecordToHistoryItem(

                                    record =
                                        record,

                                    source =
                                        HistorySource.LOCAL
                                )
                            }
                            .sortedByDescending {
                                it.startTimeMillis
                            }
                            .take(
                                20
                            )


                    refreshHistoryScreen()


                    Toast.makeText(

                        requireContext(),

                        "目前無法取得雲端紀錄，先顯示手機本機資料\n$message",

                        Toast.LENGTH_LONG

                    ).show()
                }
            )
    }


    private fun getVisibleLocalRecords():
            List<WorkoutRecord> {


        val username =
            TokenManager
                .getUsername(
                    requireContext()
                )


        return database
            .getLatestRecords()
            .filter {
                    record ->


                val belongsToCurrentUser =
                    !username.isNullOrBlank() &&
                            record.ownerUsername ==
                            username


                val isLegacyRecord =
                    record.ownerUsername == null &&
                            record.syncStatus ==
                            WorkoutRecord.SYNC_LEGACY


                belongsToCurrentUser ||
                        isLegacyRecord
            }
    }


    private fun mergeHistoryRecords(

        localRecords:
        List<WorkoutRecord>,

        serverRecords:
        List<ActivitySummary>

    ): List<HistoryItem> {


        val remainingServerRecords =
            serverRecords
                .associateBy {
                    it.activityId
                }
                .toMutableMap()


        val merged =
            mutableListOf<HistoryItem>()


        localRecords
            .forEach {
                    localRecord ->


                val serverActivityId =
                    localRecord.serverActivityId


                val matchingServer =

                    if (
                        serverActivityId != null
                    ) {


                        remainingServerRecords
                            .remove(
                                serverActivityId
                            )


                    } else {


                        null
                    }


                if (
                    localRecord.syncStatus ==
                    WorkoutRecord.SYNC_SYNCED &&
                    matchingServer != null
                ) {


                    merged.add(

                        localRecordToHistoryItem(

                            record =
                                localRecord,

                            source =
                                HistorySource.SERVER,

                            serverSummary =
                                matchingServer
                        )
                    )


                } else {


                    merged.add(

                        localRecordToHistoryItem(

                            record =
                                localRecord,

                            source =
                                HistorySource.LOCAL,

                            serverSummary =
                                matchingServer
                        )
                    )
                }
            }


        remainingServerRecords
            .values
            .forEach {
                    serverRecord ->


                serverRecordToHistoryItem(
                    serverRecord
                )
                    ?.let {
                            historyItem ->


                        merged.add(
                            historyItem
                        )
                    }
            }


        return merged
            .sortedByDescending {
                it.startTimeMillis
            }
            .take(
                20
            )
    }


    private fun localRecordToHistoryItem(

        record: WorkoutRecord,

        source: HistorySource,

        serverSummary:
        ActivitySummary? = null

    ): HistoryItem {


        val displayName =
            serverSummary
                ?.title
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: record.activityType


        return HistoryItem(


            source =
                source,


            localRecordId =
                record.id,


            serverActivityId =
                record.serverActivityId
                    ?: serverSummary
                        ?.activityId,


            activityType =
                displayName,


            modeId =
                record.modeId
                    ?: serverSummary
                        ?.modeId,


            startTimeMillis =
                record.startTimeMillis,


            endTimeMillis =
                record.endTimeMillis,


            durationSeconds =
                record.durationSeconds,


            steps =
                record.steps,


            distanceMeters =
                record
                    .distanceMeters
                    .toDouble(),


            syncStatus =
                record.syncStatus,


            backendStatus =
                serverSummary
                    ?.status
        )
    }


    private fun serverRecordToHistoryItem(
        record: ActivitySummary
    ): HistoryItem? {


        val startMillis =
            parseBackendTime(
                record.startTime
            )
                ?: return null


        val endMillis =
            parseBackendTime(
                record.endTime
            )


        val durationSeconds =

            if (
                endMillis != null
            ) {


                (
                        endMillis -
                                startMillis
                        )
                    .coerceAtLeast(
                        0L
                    ) /
                        1000L


            } else {


                null
            }


        val activityName =
            record.title
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: modeIdToName(
                    record.modeId
                )


        return HistoryItem(


            source =
                HistorySource.SERVER,


            localRecordId =
                null,


            serverActivityId =
                record.activityId,


            activityType =
                activityName,


            modeId =
                record.modeId,


            startTimeMillis =
                startMillis,


            endTimeMillis =
                endMillis,


            durationSeconds =
                durationSeconds,


            steps =
                null,


            distanceMeters =
                record.totalDistanceMeters
                    ?: 0.0,


            syncStatus =
                WorkoutRecord.SYNC_SYNCED,


            backendStatus =
                record.status
        )
    }


    private fun modeIdToName(
        modeId: Int?
    ): String {


        return when (
            modeId
        ) {


            1 ->
                "登山"


            2 ->
                "走路"


            3 ->
                "騎自行車"


            else ->
                "運動"
        }
    }


    private fun parseBackendTime(
        value: String?
    ): Long? {


        if (
            value.isNullOrBlank()
        ) {


            return null
        }


        val patterns =
            listOf(

                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",

                "yyyy-MM-dd'T'HH:mm:ssXXX",

                "yyyy-MM-dd'T'HH:mm:ss.SSS",

                "yyyy-MM-dd'T'HH:mm:ss"
            )


        patterns.forEach {
                pattern ->


            try {


                val formatter =
                    SimpleDateFormat(

                        pattern,

                        Locale.US
                    )


                formatter.isLenient =
                    false


                formatter.timeZone =
                    TimeZone.getDefault()


                val date =
                    formatter.parse(
                        value
                    )


                if (
                    date != null
                ) {


                    return date.time
                }


            } catch (
                _: Exception
            ) {


                // 下一個格式
            }
        }


        return null
    }


    private fun refreshHistoryScreen() {


        renderCalendar()


        val day =
            selectedDay


        if (
            day != null
        ) {


            showRecordsForDay(
                day
            )


        } else {


            clearSelectedRecords()
        }
    }


    private fun renderCalendar() {


        calendarGrid
            .removeAllViews()


        tvMonthTitle.text =
            String.format(

                Locale.TAIWAN,

                "%d 年 %d 月",

                currentMonth.get(
                    Calendar.YEAR
                ),

                currentMonth.get(
                    Calendar.MONTH
                ) + 1
            )


        val year =
            currentMonth.get(
                Calendar.YEAR
            )


        val month =
            currentMonth.get(
                Calendar.MONTH
            )


        val activeDays =
            mutableSetOf<Int>()


        historyItems
            .forEach {
                    record ->


                val calendar =
                    Calendar.getInstance()


                calendar.timeInMillis =
                    record.startTimeMillis


                if (
                    calendar.get(
                        Calendar.YEAR
                    ) == year &&
                    calendar.get(
                        Calendar.MONTH
                    ) == month
                ) {


                    activeDays.add(

                        calendar.get(
                            Calendar.DAY_OF_MONTH
                        )
                    )
                }
            }


        val firstDay =
            Calendar.getInstance()


        firstDay.set(
            year,
            month,
            1
        )


        val firstDayOfWeek =
            firstDay.get(
                Calendar.DAY_OF_WEEK
            )


        val daysInMonth =
            firstDay.getActualMaximum(
                Calendar.DAY_OF_MONTH
            )


        for (
        i in 1 until firstDayOfWeek
        ) {


            calendarGrid.addView(

                TextView(
                    requireContext()
                ),

                createCalendarLayoutParams()
            )
        }


        for (
        day in 1..daysInMonth
        ) {


            val button =
                Button(
                    requireContext()
                )


            val hasRecord =
                activeDays.contains(
                    day
                )


            val isSelected =
                selectedDay ==
                        day


            button.text =

                when {


                    isSelected &&
                            hasRecord ->

                        "[$day]\n●"


                    isSelected ->

                        "[$day]"


                    hasRecord ->

                        "$day\n●"


                    else ->

                        day.toString()
                }


            button.textSize =
                13f


            button.gravity =
                Gravity.CENTER


            button.setPadding(
                0,
                0,
                0,
                0
            )


            button
                .setOnClickListener {


                    selectedDay =
                        day


                    renderCalendar()


                    showRecordsForDay(
                        day
                    )
                }


            calendarGrid.addView(

                button,

                createCalendarLayoutParams()
            )
        }
    }


    private fun createCalendarLayoutParams():
            GridLayout.LayoutParams {


        return GridLayout
            .LayoutParams()
            .apply {


                width =
                    0


                height =
                    dpToPx(
                        65
                    )


                columnSpec =
                    GridLayout.spec(

                        GridLayout.UNDEFINED,

                        1f
                    )


                setMargins(
                    2,
                    2,
                    2,
                    2
                )
            }
    }


    private fun showRecordsForDay(
        day: Int
    ) {


        recordsContainer
            .removeAllViews()


        val year =
            currentMonth.get(
                Calendar.YEAR
            )


        val month =
            currentMonth.get(
                Calendar.MONTH
            )


        val start =
            Calendar.getInstance()


        start.set(
            year,
            month,
            day,
            0,
            0,
            0
        )


        start.set(
            Calendar.MILLISECOND,
            0
        )


        val end =
            start.clone() as Calendar


        end.add(
            Calendar.DAY_OF_MONTH,
            1
        )


        val records =
            historyItems
                .filter {
                        record ->


                    record.startTimeMillis >=
                            start.timeInMillis &&
                            record.startTimeMillis <
                            end.timeInMillis
                }
                .sortedByDescending {
                    it.startTimeMillis
                }


        tvSelectedDate.text =
            String.format(

                Locale.TAIWAN,

                "%d/%02d/%02d",

                year,

                month + 1,

                day
            )


        if (
            records.isEmpty()
        ) {


            val emptyView =
                TextView(
                    requireContext()
                )


            emptyView.text =
                "這一天沒有運動紀錄"


            emptyView.textSize =
                17f


            emptyView.setPadding(
                10,
                25,
                10,
                25
            )


            recordsContainer
                .addView(
                    emptyView
                )


            return
        }


        records
            .forEach {
                    record ->


                addRecordCard(
                    record
                )
            }
    }


    private fun addRecordCard(
        record: HistoryItem
    ) {


        val container =
            LinearLayout(
                requireContext()
            )


        container.orientation =
            LinearLayout.VERTICAL


        container.setPadding(

            dpToPx(
                16
            ),

            dpToPx(
                16
            ),

            dpToPx(
                16
            ),

            dpToPx(
                16
            )
        )


        val layoutParams =
            LinearLayout.LayoutParams(

                LinearLayout.LayoutParams.MATCH_PARENT,

                LinearLayout.LayoutParams.WRAP_CONTENT
            )


        layoutParams.setMargins(

            0,

            0,

            0,

            dpToPx(
                18
            )
        )


        container.layoutParams =
            layoutParams


        val info =
            TextView(
                requireContext()
            )


        val timeFormat =
            SimpleDateFormat(

                "HH:mm",

                Locale.TAIWAN
            )


        val startTime =
            timeFormat.format(

                Date(
                    record.startTimeMillis
                )
            )


        val distanceKm =
            record.distanceMeters /
                    1000.0


        val stepsText =

            if (
                record.modeId == 3 ||
                record.activityType ==
                "騎自行車" ||
                record.activityType ==
                "單車"
            ) {


                "不記錄"


            } else {


                record.steps
                    ?.toString()
                    ?: "—"
            }


        val durationText =
            record.durationSeconds
                ?.let {
                    formatDuration(
                        it
                    )
                }
                ?: "—"


        val syncText =
            getSyncStatusText(
                record
            )


        info.text =
            """
            ${record.activityType}
            
            開始時間：$startTime
            運動時間：$durationText
            距離：${String.format(Locale.TAIWAN, "%.2f", distanceKm)} km
            步數：$stepsText
            同步狀態：$syncText
            """.trimIndent()


        info.textSize =
            17f


        val detailButton =
            Button(
                requireContext()
            )


        detailButton.text =
            "查看詳細資料"


        detailButton
            .setOnClickListener {


                val sourceText =

                    when (
                        record.source
                    ) {


                        HistorySource.SERVER ->

                            "server"


                        HistorySource.LOCAL ->

                            "local"
                    }


                findNavController()
                    .navigate(

                        R.id.historyDetailFragment,

                        bundleOf(

                            "historySource"
                                    to sourceText,

                            "recordId"
                                    to (
                                    record.localRecordId
                                        ?: -1L
                                    ),

                            "activityId"
                                    to (
                                    record.serverActivityId
                                        ?: -1L
                                    )
                        )
                    )
            }


        container.addView(
            info
        )


        container.addView(
            detailButton
        )


        recordsContainer
            .addView(
                container
            )
    }


    private fun getSyncStatusText(
        record: HistoryItem
    ): String {


        if (
            record.source ==
            HistorySource.SERVER
        ) {


            return "已同步"
        }


        return when (
            record.syncStatus
        ) {


            WorkoutRecord.SYNC_PENDING ->

                "等待同步"


            WorkoutRecord.SYNC_FAILED ->

                "同步失敗，可按上方按鈕重試"


            WorkoutRecord.SYNC_SYNCED ->

                "已同步，本機備份"


            WorkoutRecord.SYNC_LEGACY ->

                "本機舊紀錄"


            else ->

                "本機紀錄"
        }
    }


    private fun clearSelectedRecords() {


        tvSelectedDate.text =
            "請選擇日期"


        recordsContainer
            .removeAllViews()
    }


    private fun formatDuration(
        totalSeconds: Long
    ): String {


        val hours =
            totalSeconds /
                    3600L


        val minutes =
            (
                    totalSeconds %
                            3600L
                    ) /
                    60L


        val seconds =
            totalSeconds %
                    60L


        return String.format(

            Locale.TAIWAN,

            "%02d:%02d:%02d",

            hours,

            minutes,

            seconds
        )
    }


    private fun dpToPx(
        dp: Int
    ): Int {


        return (
                dp *
                        resources
                            .displayMetrics
                            .density
                )
            .toInt()
    }
}