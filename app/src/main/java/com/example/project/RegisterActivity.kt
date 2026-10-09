package com.example.project


import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity

import com.example.project.network.ApiClient
import com.example.project.network.RegisterRequest

import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

import okhttp3.ResponseBody

import org.json.JSONArray
import org.json.JSONObject

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class RegisterActivity :
    AppCompatActivity() {


    private lateinit var tilUsername:
            TextInputLayout

    private lateinit var tilEmail:
            TextInputLayout

    private lateinit var tilRegisterPassword:
            TextInputLayout


    private lateinit var etUsername:
            TextInputEditText

    private lateinit var etEmail:
            TextInputEditText

    private lateinit var etPassword:
            TextInputEditText


    private lateinit var btnSubmitRegister:
            Button

    private lateinit var btnBackLogin:
            Button


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )


        setContentView(
            R.layout.activity_register
        )


        // =================================================
        // TextInputLayout
        // =================================================

        tilUsername =
            findViewById(
                R.id.tilUsername
            )


        tilEmail =
            findViewById(
                R.id.tilEmail
            )


        tilRegisterPassword =
            findViewById(
                R.id.tilRegisterPassword
            )


        // =================================================
        // EditText
        // =================================================

        etUsername =
            findViewById(
                R.id.etUsername
            )


        etEmail =
            findViewById(
                R.id.etEmail
            )


        etPassword =
            findViewById(
                R.id.etPassword
            )


        // =================================================
        // Button
        // =================================================

        btnSubmitRegister =
            findViewById(
                R.id.btnSubmitRegister
            )


        btnBackLogin =
            findViewById(
                R.id.btnBackLogin
            )


        btnSubmitRegister
            .setOnClickListener {

                registerUser()
            }


        btnBackLogin
            .setOnClickListener {

                finish()
            }
    }


    // =====================================================
    // 註冊
    // =====================================================

    private fun registerUser() {


        clearErrors()


        val username =
            etUsername
                .text
                ?.toString()
                ?.trim()
                .orEmpty()


        val email =
            etEmail
                .text
                ?.toString()
                ?.trim()
                .orEmpty()


        val password =
            etPassword
                .text
                ?.toString()
                .orEmpty()


        // =================================================
        // Username
        //
        // 與後端規則同步：
        // 至少 3 個字元
        // =================================================

        if (
            username.isBlank()
        ) {

            tilUsername.error =
                "請輸入使用者名稱"

            etUsername.requestFocus()

            return
        }


        if (
            username.length <
            3
        ) {

            tilUsername.error =
                "使用者名稱至少需要 3 個字元"

            etUsername.requestFocus()

            return
        }


        // =================================================
        // Email
        // =================================================

        if (
            email.isBlank()
        ) {

            tilEmail.error =
                "請輸入電子郵件"

            etEmail.requestFocus()

            return
        }


        if (
            !Patterns.EMAIL_ADDRESS
                .matcher(
                    email
                )
                .matches()
        ) {

            tilEmail.error =
                "電子郵件格式不正確"

            etEmail.requestFocus()

            return
        }


        // =================================================
        // Password
        //
        // 與後端規則同步：
        // 至少 8 個字元
        // =================================================

        if (
            password.isBlank()
        ) {

            tilRegisterPassword.error =
                "請輸入密碼"

            etPassword.requestFocus()

            return
        }


        if (
            password.length <
            8
        ) {

            tilRegisterPassword.error =
                "密碼至少需要 8 個字元"

            etPassword.requestFocus()

            return
        }


        val request =
            RegisterRequest(

                username =
                    username,

                email =
                    email,

                password =
                    password
            )


        btnSubmitRegister.isEnabled =
            false

        btnSubmitRegister.text =
            "註冊中..."


        // =================================================
        // POST /api/v1/users/register
        // =================================================

        ApiClient
            .userApi
            .register(
                request
            )
            .enqueue(

                object :
                    Callback<ResponseBody> {


                    override fun onResponse(

                        call:
                        Call<ResponseBody>,

                        response:
                        Response<ResponseBody>

                    ) {


                        resetRegisterButton()


                        // =====================================
                        // HTTP 本身失敗
                        //
                        // 例如 FastAPI 422
                        // =====================================

                        if (
                            !response.isSuccessful
                        ) {

                            handleHttpError(
                                response
                            )

                            return
                        }


                        // =====================================
                        // 非常重要：
                        //
                        // HTTP 200 不代表註冊成功。
                        //
                        // 後端目前可能回：
                        //
                        // {
                        //   "status":"error",
                        //   "message":"使用者名稱已被註冊。"
                        // }
                        //
                        // 所以一定還要解析 body。
                        // =====================================

                        val rawBody =
                            response
                                .body()
                                ?.string()
                                ?.trim()


                        if (
                            rawBody.isNullOrBlank()
                        ) {

                            Toast.makeText(

                                this@RegisterActivity,

                                "伺服器沒有回傳註冊結果",

                                Toast.LENGTH_LONG

                            ).show()

                            return
                        }


                        handleSuccessfulHttpBody(

                            rawBody =
                                rawBody,

                            username =
                                username
                        )
                    }


                    override fun onFailure(

                        call:
                        Call<ResponseBody>,

                        t:
                        Throwable

                    ) {


                        resetRegisterButton()


                        Toast.makeText(

                            this@RegisterActivity,

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


    // =====================================================
    // HTTP 200 / 201 的 Body
    //
    // 還要檢查 status。
    // =====================================================

    private fun handleSuccessfulHttpBody(

        rawBody: String,

        username: String

    ) {


        try {


            val json =
                JSONObject(
                    rawBody
                )


            val status =
                json
                    .optString(
                        "status"
                    )
                    .trim()


            val message =
                json
                    .optString(
                        "message"
                    )
                    .trim()


            // =================================================
            // 後端明確說 success
            // =================================================

            if (
                status.equals(
                    "success",
                    ignoreCase =
                        true
                )
            ) {


                Toast.makeText(

                    this,

                    message.ifBlank {
                        "註冊成功"
                    },

                    Toast.LENGTH_LONG

                ).show()


                openLoginPage(
                    username
                )


                return
            }


            // =================================================
            // HTTP 200
            // 但後端說 error
            // =================================================

            val errorMessage =
                message.ifBlank {
                    "註冊失敗"
                }


            applyServerMessageToField(
                errorMessage
            )


            Toast.makeText(

                this,

                errorMessage,

                Toast.LENGTH_LONG

            ).show()


        } catch (
            _: Exception
        ) {


            // =================================================
            // Swagger 目前沒有正式固定 Response Model。
            //
            // 如果某次後端不是回 JSON Object，
            // 不可以擅自當成功。
            // =================================================

            Toast.makeText(

                this,

                "無法確認註冊結果：$rawBody",

                Toast.LENGTH_LONG

            ).show()
        }
    }


    // =====================================================
    // HTTP Error
    //
    // 例如：
    // 400 / 409 / 422 / 500
    // =====================================================

    private fun handleHttpError(
        response: Response<ResponseBody>
    ) {


        val rawBody =
            response
                .errorBody()
                ?.string()
                ?.trim()


        if (
            !rawBody.isNullOrBlank()
        ) {


            try {


                val json =
                    JSONObject(
                        rawBody
                    )


                // =============================================
                // 自訂後端 message
                // =============================================

                val message =
                    json
                        .optString(
                            "message"
                        )
                        .trim()


                if (
                    message.isNotBlank()
                ) {


                    applyServerMessageToField(
                        message
                    )


                    Toast.makeText(

                        this,

                        message,

                        Toast.LENGTH_LONG

                    ).show()


                    return
                }


                // =============================================
                // FastAPI 422
                //
                // {
                //   "detail":[
                //      {
                //        "loc":["body","username"],
                //        "msg":"...",
                //        "type":"..."
                //      }
                //   ]
                // }
                // =============================================

                val detail =
                    json.opt(
                        "detail"
                    )


                if (
                    detail is JSONArray
                ) {


                    val messages =
                        mutableListOf<String>()


                    for (
                    index in
                    0 until detail.length()
                    ) {


                        val item =
                            detail
                                .optJSONObject(
                                    index
                                )
                                ?: continue


                        val msg =
                            item
                                .optString(
                                    "msg"
                                )
                                .trim()


                        val loc =
                            item
                                .optJSONArray(
                                    "loc"
                                )


                        val fieldName =

                            if (
                                loc !=
                                null &&
                                loc.length() >
                                0
                            ) {

                                loc
                                    .optString(
                                        loc.length() -
                                                1
                                    )

                            } else {

                                ""
                            }


                        if (
                            msg.isNotBlank()
                        ) {


                            messages.add(
                                msg
                            )


                            applyFieldError(

                                fieldName =
                                    fieldName,

                                message =
                                    msg
                            )
                        }
                    }


                    if (
                        messages.isNotEmpty()
                    ) {


                        Toast.makeText(

                            this,

                            messages.joinToString(
                                "\n"
                            ),

                            Toast.LENGTH_LONG

                        ).show()


                        return
                    }
                }


                if (
                    detail != null
                ) {


                    val detailText =
                        detail
                            .toString()
                            .trim()


                    if (
                        detailText.isNotBlank()
                    ) {


                        Toast.makeText(

                            this,

                            detailText,

                            Toast.LENGTH_LONG

                        ).show()


                        return
                    }
                }


            } catch (
                _: Exception
            ) {


                // 使用下方 HTTP fallback。
            }
        }


        val fallbackMessage =

            when (
                response.code()
            ) {


                400 ->
                    "註冊資料格式錯誤"


                409 ->
                    "使用者名稱或電子郵件已被註冊"


                422 ->
                    "輸入資料不符合註冊規則"


                500 ->
                    "伺服器發生錯誤"


                else ->
                    "註冊失敗（HTTP ${response.code()}）"
            }


        Toast.makeText(

            this,

            fallbackMessage,

            Toast.LENGTH_LONG

        ).show()
    }


    // =====================================================
    // 根據後端 Message
    // 把錯誤放到對應輸入框
    // =====================================================

    private fun applyServerMessageToField(
        message: String
    ) {


        val lowerMessage =
            message.lowercase()


        when {


            message.contains(
                "使用者名稱"
            ) ||
                    message.contains(
                        "用戶名"
                    ) ||
                    lowerMessage.contains(
                        "username"
                    ) -> {


                tilUsername.error =
                    message
            }


            message.contains(
                "電子郵件"
            ) ||
                    message.contains(
                        "信箱"
                    ) ||
                    lowerMessage.contains(
                        "email"
                    ) -> {


                tilEmail.error =
                    message
            }


            message.contains(
                "密碼"
            ) ||
                    lowerMessage.contains(
                        "password"
                    ) -> {


                tilRegisterPassword.error =
                    message
            }
        }
    }


    private fun applyFieldError(

        fieldName: String,

        message: String

    ) {


        when (
            fieldName.lowercase()
        ) {


            "username" ->

                tilUsername.error =
                    message


            "email" ->

                tilEmail.error =
                    message


            "password" ->

                tilRegisterPassword.error =
                    message
        }
    }


    // =====================================================
    // 清除舊錯誤
    // =====================================================

    private fun clearErrors() {


        tilUsername.error =
            null

        tilEmail.error =
            null

        tilRegisterPassword.error =
            null
    }


    // =====================================================
    // 真正註冊成功才回登入頁
    // =====================================================

    private fun openLoginPage(
        username: String
    ) {


        val intent =
            Intent(
                this,
                LoginActivity::class.java
            )


        intent.putExtra(

            "registered_username",

            username
        )


        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        )


        startActivity(
            intent
        )


        finish()
    }


    private fun resetRegisterButton() {


        btnSubmitRegister.isEnabled =
            true


        btnSubmitRegister.text =
            "註冊"
    }
}