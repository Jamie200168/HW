package com.example.project

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

import com.example.project.auth.TokenManager
import com.example.project.data.WorkoutDatabaseHelper
import com.example.project.network.ApiClient
import com.example.project.network.LoginRequest
import com.example.project.network.LoginResponse
import com.example.project.service.TrackingService

import org.json.JSONObject

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class LoginActivity :
    AppCompatActivity() {


    private lateinit var etAccount:
            EditText


    private lateinit var etPassword:
            EditText


    private lateinit var btnLogin:
            Button


    private lateinit var btnRegister:
            Button


    private lateinit var database:
            WorkoutDatabaseHelper


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


        val activeSession =
            database
                .getActiveWorkoutSession()


        // =====================================================
        // APP 新啟動時的規則
        // =====================================================

        if (
            activeSession != null
        ) {


            val savedUsername =
                TokenManager
                    .getUsername(
                        applicationContext
                    )
                    ?.trim()


            val refreshToken =
                TokenManager
                    .getRefreshToken(
                        applicationContext
                    )


            // 有運動 + 還保有同一使用者的 Refresh Token
            if (
                savedUsername ==
                activeSession.ownerUsername &&
                !refreshToken.isNullOrBlank()
            ) {


                resumeTrackingService()


                openMainActivity(
                    resumeActiveWorkout =
                        true
                )


                return
            }


            // active workout 仍保留。
            // 只清 Token，讓使用者重新登入。
            TokenManager.clear(
                applicationContext
            )


        } else {


            // =================================================
            // 沒有任何運動進行中：
            //
            // APP 被關閉 / 滑掉再開
            // 一律重新登入。
            // =================================================

            TokenManager.clear(
                applicationContext
            )
        }


        setContentView(
            R.layout.activity_login
        )


        etAccount =
            findViewById(
                R.id.etAccount
            )


        etPassword =
            findViewById(
                R.id.etPassword
            )


        btnLogin =
            findViewById(
                R.id.btnLogin
            )


        btnRegister =
            findViewById(
                R.id.btnRegister
            )


        intent
            .getStringExtra(
                "registered_username"
            )
            ?.let {
                    username ->


                etAccount.setText(
                    username
                )
            }


        btnLogin
            .setOnClickListener {


                login()
            }


        btnRegister
            .setOnClickListener {


                startActivity(

                    Intent(
                        this,
                        RegisterActivity::class.java
                    )
                )
            }
    }


    private fun login() {


        val account =
            etAccount
                .text
                .toString()
                .trim()


        val password =
            etPassword
                .text
                .toString()


        if (
            account.isBlank()
        ) {


            etAccount.error =
                "請輸入用戶名或電子郵件"


            etAccount.requestFocus()


            return
        }


        if (
            password.isBlank()
        ) {


            etPassword.error =
                "請輸入密碼"


            etPassword.requestFocus()


            return
        }


        val request =
            LoginRequest(

                account =
                    account,

                password =
                    password
            )


        btnLogin.isEnabled =
            false


        btnLogin.text =
            "登錄中..."


        ApiClient
            .userApi
            .login(
                request
            )
            .enqueue(


                object :
                    Callback<LoginResponse> {


                    override fun onResponse(

                        call:
                        Call<LoginResponse>,

                        response:
                        Response<LoginResponse>

                    ) {


                        resetLoginButton()


                        if (
                            !response.isSuccessful
                        ) {


                            Toast.makeText(

                                this@LoginActivity,

                                getHttpErrorMessage(
                                    response
                                ),

                                Toast.LENGTH_LONG

                            ).show()


                            return
                        }


                        val body =
                            response.body()


                        if (
                            body == null
                        ) {


                            Toast.makeText(

                                this@LoginActivity,

                                "登入失敗：伺服器沒有回傳資料",

                                Toast.LENGTH_LONG

                            ).show()


                            return
                        }


                        if (
                            !body.status.equals(

                                "success",

                                ignoreCase =
                                    true
                            )
                        ) {


                            Toast.makeText(

                                this@LoginActivity,

                                body.message
                                    ?: "帳號或密碼錯誤",

                                Toast.LENGTH_LONG

                            ).show()


                            return
                        }


                        val accessToken =
                            body.accessToken


                        val refreshToken =
                            body.refreshToken


                        if (
                            accessToken.isNullOrBlank() ||
                            refreshToken.isNullOrBlank()
                        ) {


                            Toast.makeText(

                                this@LoginActivity,

                                "登入成功，但伺服器沒有回傳完整 Token",

                                Toast.LENGTH_LONG

                            ).show()


                            return
                        }


                        val username =
                            body.username
                                ?.trim()
                                ?.takeIf {
                                    it.isNotBlank()
                                }


                        if (
                            username == null
                        ) {


                            Toast.makeText(

                                this@LoginActivity,

                                "登入成功，但伺服器沒有回傳使用者名稱",

                                Toast.LENGTH_LONG

                            ).show()


                            return
                        }


                        // =================================================
                        // 有未結束運動時：
                        //
                        // 只允許原本的 owner 登入。
                        // =================================================

                        val activeSession =
                            database
                                .getActiveWorkoutSession()


                        if (
                            activeSession != null &&
                            activeSession.ownerUsername !=
                            username
                        ) {


                            TokenManager.clear(
                                applicationContext
                            )


                            Toast.makeText(

                                this@LoginActivity,

                                "目前有另一個帳號尚未結束的運動，請登入開始該場運動的帳號",

                                Toast.LENGTH_LONG

                            ).show()


                            return
                        }


                        TokenManager.saveLogin(

                            context =
                                this@LoginActivity,

                            accessToken =
                                accessToken,

                            refreshToken =
                                refreshToken,

                            tokenType =
                                body.tokenType
                                    ?: "bearer",

                            expiresIn =
                                body.expiresIn
                                    ?: 1800,

                            username =
                                username
                        )


                        Toast.makeText(

                            this@LoginActivity,

                            "登錄成功",

                            Toast.LENGTH_SHORT

                        ).show()


                        val shouldResumeWorkout =
                            activeSession != null


                        if (
                            shouldResumeWorkout
                        ) {


                            resumeTrackingService()
                        }


                        openMainActivity(

                            resumeActiveWorkout =
                                shouldResumeWorkout
                        )
                    }


                    override fun onFailure(

                        call:
                        Call<LoginResponse>,

                        t:
                        Throwable

                    ) {


                        resetLoginButton()


                        Toast.makeText(

                            this@LoginActivity,

                            "無法連線伺服器：${
                                t.message
                                    ?: "未知錯誤"
                            }",

                            Toast.LENGTH_LONG

                        ).show()
                    }
                }
            )
    }


    private fun openMainActivity(
        resumeActiveWorkout: Boolean
    ) {


        val mainIntent =
            Intent(
                this,
                MainActivity::class.java
            )


        mainIntent.putExtra(
            FeatureActivity.EXTRA_RESUME_ACTIVE_WORKOUT,
            resumeActiveWorkout
        )


        mainIntent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK


        startActivity(
            mainIntent
        )


        finish()
    }


    private fun resumeTrackingService() {


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


    private fun getHttpErrorMessage(
        response: Response<LoginResponse>
    ): String {


        val errorBody =
            response
                .errorBody()
                ?.string()


        if (
            !errorBody.isNullOrBlank()
        ) {


            try {


                val json =
                    JSONObject(
                        errorBody
                    )


                if (
                    json.has(
                        "message"
                    )
                ) {


                    return json
                        .optString(

                            "message",

                            "登錄失敗"
                        )
                }


                if (
                    json.has(
                        "detail"
                    )
                ) {


                    return json
                        .optString(

                            "detail",

                            "登錄失敗"
                        )
                }


            } catch (
                _: Exception
            ) {


                // 改用 HTTP Code
            }
        }


        return when (
            response.code()
        ) {


            400 ->
                "登錄資料錯誤"


            401 ->
                "帳號或密碼錯誤"


            404 ->
                "找不到此使用者"


            422 ->
                "登錄資料格式錯誤"


            500 ->
                "伺服器發生錯誤"


            else ->
                "登錄失敗（HTTP ${response.code()}）"
        }
    }


    private fun resetLoginButton() {


        btnLogin.isEnabled =
            true


        btnLogin.text =
            "登錄"
    }
}