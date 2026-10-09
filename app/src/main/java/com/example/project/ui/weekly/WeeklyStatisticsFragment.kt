package com.example.project.ui.weekly


import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

import com.example.project.R
import com.example.project.data.WeeklySummaryRepository
import com.example.project.network.WeeklyStatistic
import com.example.project.network.WeeklySummaryResponse

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class WeeklyStatisticsFragment :
    Fragment(
        R.layout.fragment_weekly_statistics
    ) {


    // =====================================================
    // 一個週別
    //
    // 例如：
    //
    // 2026/10/05 ～ 2026/10/11
    //
    // Backend：
    //
    // year  = 2026
    // month = 10
    // week  = 1
    // =====================================================

    private data class WeekOption(

        val year: Int,

        val month: Int,

        val week: Int,

        val label: String,

        val startMillis: Long
    )


    private data class SportOption(

        val name: String,

        val modeId: Int
    )


    private lateinit var spinnerWeek:
            Spinner

    private lateinit var spinnerSport:
            Spinner

    private lateinit var progressWeekly:
            ProgressBar

    private lateinit var tvSelectedSummary:
            TextView

    private lateinit var tvActivityCount:
            TextView

    private lateinit var tvTotalDistance:
            TextView

    private lateinit var tvTotalDuration:
            TextView

    private lateinit var layoutSteps:
            LinearLayout

    private lateinit var tvTotalSteps:
            TextView

    private lateinit var tvDataSource:
            TextView


    private val weekOptions =
        mutableListOf<WeekOption>()


    private val sportOptions =
        listOf(

            SportOption(
                name = "走路",
                modeId = 2
            ),

            SportOption(
                name = "騎自行車",
                modeId = 3
            ),

            SportOption(
                name = "登山",
                modeId = 1
            )
        )


    private var currentResponse:
            WeeklySummaryResponse? =
        null


    private var listenersReady =
        false


    override fun onViewCreated(

        view: View,

        savedInstanceState: Bundle?

    ) {


        super.onViewCreated(
            view,
            savedInstanceState
        )


        spinnerWeek =
            view.findViewById(
                R.id.spinnerWeek
            )


        spinnerSport =
            view.findViewById(
                R.id.spinnerSport
            )


        progressWeekly =
            view.findViewById(
                R.id.progressWeekly
            )


        tvSelectedSummary =
            view.findViewById(
                R.id.tvSelectedSummary
            )


        tvActivityCount =
            view.findViewById(
                R.id.tvActivityCount
            )


        tvTotalDistance =
            view.findViewById(
                R.id.tvTotalDistance
            )


        tvTotalDuration =
            view.findViewById(
                R.id.tvTotalDuration
            )


        layoutSteps =
            view.findViewById(
                R.id.layoutSteps
            )


        tvTotalSteps =
            view.findViewById(
                R.id.tvTotalSteps
            )


        tvDataSource =
            view.findViewById(
                R.id.tvDataSource
            )


        view
            .findViewById<View>(
                R.id.btnBackWeekly
            )
            .setOnClickListener {

                findNavController()
                    .popBackStack()
            }


        setupWeekSpinner()

        setupSportSpinner()

        setupSpinnerListeners()


        listenersReady =
            true


        loadSelectedWeek()
    }


    // =====================================================
    // 週別下拉選單
    // =====================================================

    private fun setupWeekSpinner() {


        weekOptions.clear()


        weekOptions.addAll(
            buildWeekOptions()
        )


        val adapter =
            ArrayAdapter(

                requireContext(),

                android.R.layout
                    .simple_spinner_item,

                weekOptions.map {
                    it.label
                }
            )


        adapter.setDropDownViewResource(

            android.R.layout
                .simple_spinner_dropdown_item
        )


        spinnerWeek.adapter =
            adapter
    }


    // =====================================================
    // 運動類型
    // =====================================================

    private fun setupSportSpinner() {


        val adapter =
            ArrayAdapter(

                requireContext(),

                android.R.layout
                    .simple_spinner_item,

                sportOptions.map {
                    it.name
                }
            )


        adapter.setDropDownViewResource(

            android.R.layout
                .simple_spinner_dropdown_item
        )


        spinnerSport.adapter =
            adapter
    }


    private fun setupSpinnerListeners() {


        spinnerWeek
            .onItemSelectedListener =
            object :
                AdapterView.OnItemSelectedListener {


                override fun onItemSelected(

                    parent: AdapterView<*>?,

                    view: View?,

                    position: Int,

                    id: Long

                ) {


                    if (
                        listenersReady
                    ) {

                        loadSelectedWeek()
                    }
                }


                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {

                    // 不處理
                }
            }


        spinnerSport
            .onItemSelectedListener =
            object :
                AdapterView.OnItemSelectedListener {


                override fun onItemSelected(

                    parent: AdapterView<*>?,

                    view: View?,

                    position: Int,

                    id: Long

                ) {


                    if (
                        listenersReady
                    ) {

                        // 同一週的 API 已經把各 mode
                        // 全部放在 statistics[] 裡。
                        //
                        // 所以切換運動類型時
                        // 不需要重新打 API。
                        renderSelectedSport()
                    }
                }


                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {

                    // 不處理
                }
            }
    }


    // =====================================================
    // 呼叫後端週報
    // =====================================================

    private fun loadSelectedWeek() {


        if (
            weekOptions.isEmpty()
        ) {

            return
        }


        val position =
            spinnerWeek
                .selectedItemPosition


        if (
            position !in
            weekOptions.indices
        ) {

            return
        }


        val selectedWeek =
            weekOptions[
                position
            ]


        currentResponse =
            null


        progressWeekly.visibility =
            View.VISIBLE


        clearSummary()


        tvSelectedSummary.text =
            selectedWeek.label


        tvDataSource.text =
            "正在讀取後端週報..."


        WeeklySummaryRepository
            .loadWeekReport(

                context =
                    requireContext()
                        .applicationContext,


                year =
                    selectedWeek.year,


                month =
                    selectedWeek.month,


                week =
                    selectedWeek.week,


                onSuccess = {
                        response ->


                    if (
                        !isAdded
                    ) {

                        return@loadWeekReport
                    }


                    progressWeekly.visibility =
                        View.GONE


                    currentResponse =
                        response


                    tvDataSource.text =
                        "資料來源：後端"


                    renderSelectedSport()
                },


                onError = {
                        message ->


                    if (
                        !isAdded
                    ) {

                        return@loadWeekReport
                    }


                    progressWeekly.visibility =
                        View.GONE


                    currentResponse =
                        null


                    clearSummary()


                    tvDataSource.text =
                        "讀取失敗：$message"


                    Toast.makeText(

                        requireContext(),

                        message,

                        Toast.LENGTH_LONG

                    ).show()
                }
            )
    }


    // =====================================================
    // 顯示現在選擇的運動
    // =====================================================

    private fun renderSelectedSport() {


        val response =
            currentResponse
                ?: return


        val sportPosition =
            spinnerSport
                .selectedItemPosition


        if (
            sportPosition !in
            sportOptions.indices
        ) {

            return
        }


        val sport =
            sportOptions[
                sportPosition
            ]


        // =================================================
        // 後端 statistics[]
        //
        // 例如：
        //
        // mode_id 1 = 爬山
        // mode_id 2 = 健走
        // mode_id 3 = 單車
        //
        // 為了容錯，
        // 就算後端同 mode 回多筆，
        // APP 仍全部加總。
        // =================================================

        val statistics =
            response
                .statistics
                .filter {

                    it.modeId ==
                            sport.modeId
                }


        val activitiesCount =
            statistics.sumOf {

                it.activitiesCount
            }


        val totalDistanceMeters =
            statistics.sumOf {

                it.totalDistanceMeters
            }


        val totalDurationSeconds =
            statistics.sumOf {

                it.totalDurationSeconds
            }


        val totalSteps =
            calculateTotalSteps(
                statistics
            )


        // =================================================
        // 日期使用後端正式回傳值
        // =================================================

        val dateRange =

            if (
                !response.weekStartDate
                    .isNullOrBlank() &&
                !response.weekEndDate
                    .isNullOrBlank()
            ) {

                "${
                    formatBackendDate(
                        response.weekStartDate
                    )
                } ～ ${
                    formatBackendDate(
                        response.weekEndDate
                    )
                }"

            } else {

                weekOptions[
                    spinnerWeek.selectedItemPosition
                ].label
            }


        tvSelectedSummary.text =
            "$dateRange｜${sport.name}"


        tvActivityCount.text =
            "本週共 $activitiesCount 次"


        tvTotalDistance.text =
            String.format(

                Locale.TAIWAN,

                "%.2f km",

                totalDistanceMeters /
                        1000.0
            )


        tvTotalDuration.text =
            formatDuration(
                totalDurationSeconds
            )


        // =================================================
        // 自行車
        //
        // 不顯示步數。
        // =================================================

        if (
            sport.modeId ==
            3
        ) {


            layoutSteps.visibility =
                View.GONE


        } else {


            layoutSteps.visibility =
                View.VISIBLE


            tvTotalSteps.text =

                when {


                    statistics.isEmpty() ->

                        "0 步"


                    totalSteps !=
                            null ->

                        String.format(

                            Locale.TAIWAN,

                            "%,d 步",

                            totalSteps
                        )


                    else ->

                        "後端尚未提供"
                }
        }


        if (
            sport.modeId !=
            3 &&
            statistics.isNotEmpty() &&
            totalSteps ==
            null
        ) {


            tvDataSource.text =
                "資料來源：後端\n" +
                        "目前 get_week_report 尚未提供 total_steps"
        }
    }


    // =====================================================
    // 步數加總
    // =====================================================

    private fun calculateTotalSteps(
        statistics:
        List<WeeklyStatistic>
    ): Long? {


        val values =
            statistics
                .mapNotNull {

                    it.totalSteps
                }


        if (
            values.isEmpty()
        ) {

            return null
        }


        return values.sum()
    }


    // =====================================================
    // 建立下拉選單週別
    //
    // 關鍵：
    //
    // Backend 定義：
    //
    // 某月份第 1 個星期一
    // = week 1
    //
    // 某月份第 2 個星期一
    // = week 2
    //
    // ...
    //
    // 例如：
    //
    // 2026/09 第4週：
    //
    // 09/28 ～ 10/04
    //
    // 2026/10 第1週：
    //
    // 10/05 ～ 10/11
    // =====================================================

    private fun buildWeekOptions():
            List<WeekOption> {


        val result =
            mutableListOf<WeekOption>()


        val now =
            Calendar.getInstance()


        val nowMillis =
            now.timeInMillis


        val monthCursor =
            Calendar.getInstance()


        monthCursor.set(
            Calendar.DAY_OF_MONTH,
            1
        )


        monthCursor.set(
            Calendar.HOUR_OF_DAY,
            0
        )


        monthCursor.set(
            Calendar.MINUTE,
            0
        )


        monthCursor.set(
            Calendar.SECOND,
            0
        )


        monthCursor.set(
            Calendar.MILLISECOND,
            0
        )


        val displayFormat =
            SimpleDateFormat(

                "yyyy/MM/dd",

                Locale.TAIWAN
            )


        // 最近 12 個月
        repeat(
            12
        ) {


            val year =
                monthCursor.get(
                    Calendar.YEAR
                )


            val monthIndex =
                monthCursor.get(
                    Calendar.MONTH
                )


            val month =
                monthIndex +
                        1


            // =============================================
            // 找這個月份第一個星期一
            // =============================================

            val firstMonday =
                monthCursor.clone()
                        as Calendar


            while (
                firstMonday.get(
                    Calendar.DAY_OF_WEEK
                ) !=
                Calendar.MONDAY
            ) {


                firstMonday.add(

                    Calendar.DAY_OF_MONTH,

                    1
                )
            }


            var weekNumber =
                1


            var weekStart =
                firstMonday.clone()
                        as Calendar


            // =============================================
            // 只要「星期一」仍然在這個月份，
            // 就仍然屬於這個月份的 week。
            // =============================================

            while (
                weekStart.get(
                    Calendar.MONTH
                ) ==
                monthIndex
            ) {


                val weekEnd =
                    weekStart.clone()
                            as Calendar


                weekEnd.add(

                    Calendar.DAY_OF_MONTH,

                    6
                )


                // 尚未開始的未來週不顯示
                if (
                    weekStart.timeInMillis <=
                    nowMillis
                ) {


                    result.add(

                        WeekOption(

                            year =
                                year,

                            month =
                                month,

                            week =
                                weekNumber,

                            label =
                                "${
                                    displayFormat.format(
                                        weekStart.time
                                    )
                                } ～ ${
                                    displayFormat.format(
                                        weekEnd.time
                                    )
                                }",

                            startMillis =
                                weekStart.timeInMillis
                        )
                    )
                }


                weekStart =
                    weekStart.clone()
                            as Calendar


                weekStart.add(

                    Calendar.DAY_OF_MONTH,

                    7
                )


                weekNumber++
            }


            monthCursor.add(

                Calendar.MONTH,

                -1
            )
        }


        return result
            .sortedByDescending {

                it.startMillis
            }
    }


    // =====================================================
    // 清除統計畫面
    // =====================================================

    private fun clearSummary() {


        tvActivityCount.text =
            "本週共 0 次"


        tvTotalDistance.text =
            "0.00 km"


        tvTotalDuration.text =
            "00:00:00"


        tvTotalSteps.text =
            "0 步"


        val sportPosition =
            spinnerSport
                .selectedItemPosition


        val isBike =

            sportPosition in
                    sportOptions.indices &&
                    sportOptions[
                        sportPosition
                    ].modeId ==
                    3


        layoutSteps.visibility =

            if (
                isBike
            ) {

                View.GONE

            } else {

                View.VISIBLE
            }
    }


    // =====================================================
    // YYYY-MM-DD
    // →
    // YYYY/MM/DD
    // =====================================================

    private fun formatBackendDate(
        value: String?
    ): String {


        if (
            value.isNullOrBlank()
        ) {

            return "--"
        }


        return value
            .take(
                10
            )
            .replace(
                "-",
                "/"
            )
    }


    // =====================================================
    // 秒 → HH:mm:ss
    // =====================================================

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
}