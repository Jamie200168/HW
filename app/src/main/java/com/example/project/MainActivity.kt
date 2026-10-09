package com.example.project


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

import com.example.project.auth.TokenManager
import com.example.project.data.PendingWorkoutSyncRepository


class MainActivity :
    AppCompatActivity() {


    // =====================================================
    // 避免同一時間重複導向 LoginActivity
    // =====================================================

    private var isRedirectingToLogin =
        false


    // =====================================================
    // 定位權限 Launcher
    //
    // 登入成功進首頁後，如果還沒有定位權限，
    // 就會從 MainActivity 詢問。
    // =====================================================

    private val locationPermissionLauncher =
        registerForActivityResult(

            ActivityResultContracts
                .RequestMultiplePermissions()

        ) {
                permissions ->


            val fineGranted =
                permissions[
                    Manifest.permission
                        .ACCESS_FINE_LOCATION
                ] == true ||
                        ContextCompat
                            .checkSelfPermission(

                                this,

                                Manifest.permission
                                    .ACCESS_FINE_LOCATION

                            ) ==
                        PackageManager
                            .PERMISSION_GRANTED


            val coarseGranted =
                permissions[
                    Manifest.permission
                        .ACCESS_COARSE_LOCATION
                ] == true ||
                        ContextCompat
                            .checkSelfPermission(

                                this,

                                Manifest.permission
                                    .ACCESS_COARSE_LOCATION

                            ) ==
                        PackageManager
                            .PERMISSION_GRANTED


            // =================================================
            // 至少允許一種定位
            // =================================================

            if (
                fineGranted ||
                coarseGranted
            ) {


                Toast.makeText(

                    this,

                    "已允許定位，可自動取得所在地天氣",

                    Toast.LENGTH_SHORT

                ).show()


            } else {


                // =============================================
                // 不允許定位也不強迫離開 APP。
                //
                // 天氣仍然可以手動選縣市 / 地區。
                // =============================================

                Toast.makeText(

                    this,

                    "未允許定位，天氣頁仍可手動選擇地區",

                    Toast.LENGTH_LONG

                ).show()
            }
        }


    // =====================================================
    // onCreate
    // =====================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {


        super.onCreate(
            savedInstanceState
        )


        // =================================================
        // 進首頁以前先確認登入狀態
        //
        // 沒有 Access Token / Refresh Token
        // 就不能直接進首頁。
        // =================================================

        if (
            !checkLoginState()
        ) {


            return
        }


        setContentView(
            R.layout.activity_main
        )


        // =================================================
        // 登入成功後第一次進 MainActivity：
        //
        // 如果還沒有定位權限，
        // 立即詢問定位。
        //
        // savedInstanceState == null：
        // 避免手機旋轉時一直重新詢問。
        // =================================================

        if (
            savedInstanceState ==
            null
        ) {


            requestLocationPermissionAfterLogin()
        }


        // =================================================
        // 首頁三個按鈕
        // =================================================

        val buttonSport =
            findViewById<Button>(
                R.id.button
            )


        val buttonHistory =
            findViewById<Button>(
                R.id.button2
            )


        val buttonWeather =
            findViewById<Button>(
                R.id.button3
            )


        // =====================================================
        // 運動方式
        // =====================================================

        buttonSport
            .setOnClickListener {


                if (
                    !checkLoginState()
                ) {


                    return@setOnClickListener
                }


                openFeature(
                    FeatureActivity.PAGE_WORKOUT
                )
            }


        // =====================================================
        // 歷史紀錄
        // =====================================================

        buttonHistory
            .setOnClickListener {


                if (
                    !checkLoginState()
                ) {


                    return@setOnClickListener
                }


                openFeature(
                    FeatureActivity.PAGE_HISTORY
                )
            }


        // =====================================================
        // 地區天氣
        // =====================================================

        buttonWeather
            .setOnClickListener {


                if (
                    !checkLoginState()
                ) {


                    return@setOnClickListener
                }


                openFeature(
                    FeatureActivity.PAGE_WEATHER
                )
            }
    }


    // =====================================================
    // 每次回到首頁
    // =====================================================

    override fun onResume() {


        super.onResume()


        // =================================================
        // 某個 API 有可能在其他頁面發現：
        //
        // Refresh Token 已失效
        // ↓
        // TokenManager.clear()
        //
        // 回首頁時再次確認登入狀態。
        // =================================================

        if (
            !checkLoginState()
        ) {


            return
        }


        // =================================================
        // 嘗試重新同步：
        //
        // pending
        // failed
        //
        // 的本機運動紀錄。
        // =================================================

        syncPendingWorkouts()
    }


    // =====================================================
    // 登入後詢問定位權限
    // =====================================================

    private fun requestLocationPermissionAfterLogin() {


        // =================================================
        // 已經有權限
        //
        // 不再跳詢問視窗。
        // =================================================

        if (
            hasLocationPermission()
        ) {


            return
        }


        // =================================================
        // 同時詢問：
        //
        // 精確位置
        // ACCESS_FINE_LOCATION
        //
        // 大概位置
        // ACCESS_COARSE_LOCATION
        // =================================================

        locationPermissionLauncher
            .launch(

                arrayOf(

                    Manifest.permission
                        .ACCESS_FINE_LOCATION,

                    Manifest.permission
                        .ACCESS_COARSE_LOCATION
                )
            )
    }


    // =====================================================
    // 是否已有定位權限
    // =====================================================

    private fun hasLocationPermission():
            Boolean {


        val fineGranted =
            ContextCompat
                .checkSelfPermission(

                    this,

                    Manifest.permission
                        .ACCESS_FINE_LOCATION

                ) ==
                    PackageManager
                        .PERMISSION_GRANTED


        val coarseGranted =
            ContextCompat
                .checkSelfPermission(

                    this,

                    Manifest.permission
                        .ACCESS_COARSE_LOCATION

                ) ==
                    PackageManager
                        .PERMISSION_GRANTED


        // =================================================
        // 至少一個有授權即可。
        //
        // 天氣所在地最好使用 Fine Location，
        // 但使用者只允許 Approximate Location
        // 仍然可以繼續使用 APP。
        // =================================================

        return fineGranted ||
                coarseGranted
    }


    // =====================================================
    // 檢查登入狀態
    //
    // true
    // → 可以繼續使用
    //
    // false
    // → 導回 LoginActivity
    // =====================================================

    private fun checkLoginState():
            Boolean {


        if (
            TokenManager
                .isLoggedIn(
                    applicationContext
                )
        ) {


            return true
        }


        redirectToLogin()


        return false
    }


    // =====================================================
    // 回登入畫面
    //
    // NEW_TASK + CLEAR_TASK
    //
    // 清掉舊的：
    //
    // MainActivity
    // FeatureActivity
    // History
    // Workout
    // Weather
    //
    // 避免按返回鍵回到失效的登入後頁面。
    // =====================================================

    private fun redirectToLogin() {


        if (
            isRedirectingToLogin
        ) {


            return
        }


        isRedirectingToLogin =
            true


        val intent =
            Intent(

                this,

                LoginActivity::class.java
            )


        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK


        startActivity(
            intent
        )


        finish()
    }


    // =====================================================
    // 開啟 FeatureActivity
    //
    // page 決定：
    //
    // Workout
    // History
    // Weather
    // =====================================================

    private fun openFeature(
        page: String
    ) {


        val intent =
            Intent(

                this,

                FeatureActivity::class.java
            )


        intent.putExtra(

            FeatureActivity.EXTRA_START_PAGE,

            page
        )


        startActivity(
            intent
        )
    }


    // =====================================================
    // 自動重新同步未同步運動紀錄
    // =====================================================

    private fun syncPendingWorkouts() {


        PendingWorkoutSyncRepository
            .syncPendingWorkouts(

                context =
                    applicationContext,


                onComplete =
                    syncCallback@{
                            successCount,
                            failedCount ->


                        // =====================================
                        // 同步期間可能發生：
                        //
                        // 401
                        // ↓
                        // Refresh Token 失效
                        // ↓
                        // TokenManager.clear()
                        //
                        // 因此 callback 回來時
                        // 再確認登入狀態。
                        // =====================================

                        if (
                            !TokenManager
                                .isLoggedIn(
                                    applicationContext
                                )
                        ) {


                            if (
                                !isFinishing &&
                                !isDestroyed
                            ) {


                                redirectToLogin()
                            }


                            return@syncCallback
                        }


                        // =====================================
                        // Activity 已離開
                        // 不顯示舊 Toast。
                        // =====================================

                        if (
                            isFinishing ||
                            isDestroyed
                        ) {


                            return@syncCallback
                        }


                        // =====================================
                        // 完全沒有紀錄需要同步
                        // =====================================

                        if (
                            successCount ==
                            0 &&
                            failedCount ==
                            0
                        ) {


                            return@syncCallback
                        }


                        // =====================================
                        // 全部同步成功
                        // =====================================

                        if (
                            successCount >
                            0 &&
                            failedCount ==
                            0
                        ) {


                            Toast.makeText(

                                this@MainActivity,

                                "已自動同步 $successCount 筆運動紀錄",

                                Toast.LENGTH_LONG

                            ).show()


                            return@syncCallback
                        }


                        // =====================================
                        // 有成功也有失敗
                        // =====================================

                        if (
                            successCount >
                            0 &&
                            failedCount >
                            0
                        ) {


                            Toast.makeText(

                                this@MainActivity,

                                "同步完成\n" +
                                        "成功：$successCount 筆\n" +
                                        "失敗：$failedCount 筆",

                                Toast.LENGTH_LONG

                            ).show()


                            return@syncCallback
                        }


                        // =====================================
                        // 全部失敗
                        // =====================================

                        if (
                            failedCount >
                            0
                        ) {


                            Toast.makeText(

                                this@MainActivity,

                                "有 $failedCount 筆運動紀錄尚未同步，之後會再嘗試",

                                Toast.LENGTH_LONG

                            ).show()
                        }
                    }
            )
    }
}