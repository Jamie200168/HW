package com.example.project.ui.summary


import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView

import androidx.fragment.app.Fragment

import com.example.project.R


class SummaryFragment :
    Fragment(
        R.layout.fragment_summary
    ) {


    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )


        // =========================================
        // 接收運動種類
        // =========================================

        val activityType =

            arguments
                ?.getString(
                    "activityType"
                )

                ?: "未知"


        // =========================================
        // 接收步數
        // =========================================

        val steps =

            arguments
                ?.getInt(
                    "steps"
                )

                ?: 0


        // =========================================
        // 接收距離
        //
        // 單位：公尺
        // =========================================

        val distance =

            arguments
                ?.getFloat(
                    "distance"
                )

                ?: 0f


        // =========================================
        // 接收運動時間
        //
        // 這裡一定是「秒」
        //
        // 例如：
        //
        // 10 = 10 秒
        //
        // 65 = 1分5秒
        //
        // 3600 = 1小時
        // =========================================

        val elapsedSeconds =

            arguments
                ?.getLong(
                    "time"
                )

                ?: 0L


        // =========================================
        // 找 XML 元件
        // =========================================

        val tvType =

            view.findViewById<TextView>(
                R.id.tvSummaryType
            )


        val tvTime =

            view.findViewById<TextView>(
                R.id.tvSummaryTime
            )


        val tvDistance =

            view.findViewById<TextView>(
                R.id.tvSummaryDistance
            )


        val tvSteps =

            view.findViewById<TextView>(
                R.id.tvSummarySteps
            )


        val btnBackHome =

            view.findViewById<Button>(
                R.id.btnBackHome
            )


        // =========================================
        // 顯示運動方式
        // =========================================

        tvType.text =

            "活動：$activityType"


        // =========================================
        // 顯示運動時間
        // =========================================

        tvTime.text =

            "時間：${formatDuration(elapsedSeconds)}"


        // =========================================
        // 顯示距離
        // =========================================

        tvDistance.text =

            String.format(

                "距離：%.2f km",

                distance / 1000f
            )


        // =========================================
        // 顯示步數
        // =========================================

        if (
            activityType ==
            "騎自行車"
        ) {

            tvSteps.text =
                "步數：不記錄"

        } else {

            tvSteps.text =
                "步數：$steps"
        }


        // =========================================
        // 回首頁
        // =========================================

        btnBackHome
            .setOnClickListener {

                requireActivity()
                    .finish()
            }
    }


    // =============================================
    // 秒數 → HH:mm:ss
    // =============================================

    private fun formatDuration(
        totalSeconds: Long
    ): String {


        /*
         * 小時
         */

        val hours =

            totalSeconds /
                    3600L


        /*
         * 分鐘
         */

        val minutes =

            (
                    totalSeconds %
                            3600L

                    ) / 60L


        /*
         * 秒
         */

        val seconds =

            totalSeconds %
                    60L


        return String.format(

            "%02d:%02d:%02d",

            hours,

            minutes,

            seconds
        )
    }
}