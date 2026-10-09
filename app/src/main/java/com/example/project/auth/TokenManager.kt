package com.example.project.auth


import android.content.Context


object TokenManager {


    // =====================================================
    // SharedPreferences 名稱
    // =====================================================

    private const val PREF_NAME =
        "auth_preferences"


    // =====================================================
    // Key
    // =====================================================

    private const val KEY_ACCESS_TOKEN =
        "access_token"


    private const val KEY_REFRESH_TOKEN =
        "refresh_token"


    private const val KEY_TOKEN_TYPE =
        "token_type"


    private const val KEY_USERNAME =
        "username"


    private const val KEY_ACCESS_TOKEN_EXPIRES_AT =
        "access_token_expires_at"


    // =====================================================
    // 儲存登入資料
    // =====================================================

    fun saveLogin(

        context: Context,

        accessToken: String,

        refreshToken: String,

        tokenType: String,

        expiresIn: Int,

        username: String

    ) {


        val preferences =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )


        // ---------------------------------------------
        // 後端 expires_in 是「幾秒後到期」
        //
        // 例如：
        // expires_in = 1800
        //
        // = 30 分鐘
        //
        // 我們轉成真正的到期時間。
        // ---------------------------------------------

        val expiresAt =
            System.currentTimeMillis() +
                    (
                            expiresIn.toLong() *
                                    1000L
                            )


        preferences
            .edit()

            .putString(
                KEY_ACCESS_TOKEN,
                accessToken
            )

            .putString(
                KEY_REFRESH_TOKEN,
                refreshToken
            )

            .putString(
                KEY_TOKEN_TYPE,
                tokenType
            )

            .putString(
                KEY_USERNAME,
                username
            )

            .putLong(
                KEY_ACCESS_TOKEN_EXPIRES_AT,
                expiresAt
            )

            .apply()
    }

    // =====================================================
// Refresh 成功後
// 只更新新的 Access Token
//
// Refresh Token、username 等其他資料繼續保留。
// =====================================================

    fun saveAccessToken(

        context: Context,

        accessToken: String,

        tokenType: String,

        expiresIn: Int

    ) {


        val preferences =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )


        // expires_in 是秒數
        //
        // 例如後端回：
        // 1800
        //
        // = 30 分鐘
        val expiresAt =
            System.currentTimeMillis() +
                    (
                            expiresIn.toLong() *
                                    1000L
                            )


        preferences
            .edit()

            .putString(
                KEY_ACCESS_TOKEN,
                accessToken
            )

            .putString(
                KEY_TOKEN_TYPE,
                tokenType
            )

            .putLong(
                KEY_ACCESS_TOKEN_EXPIRES_AT,
                expiresAt
            )

            .apply()
    }

    // =====================================================
    // 取得 Access Token
    // =====================================================

    fun getAccessToken(
        context: Context
    ): String? {


        return context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_ACCESS_TOKEN,
                null
            )
    }


    // =====================================================
    // 取得 Refresh Token
    // =====================================================

    fun getRefreshToken(
        context: Context
    ): String? {


        return context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_REFRESH_TOKEN,
                null
            )
    }


    // =====================================================
    // 取得 Token Type
    // =====================================================

    fun getTokenType(
        context: Context
    ): String {


        return context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_TOKEN_TYPE,
                "bearer"
            )
            ?: "bearer"
    }


    // =====================================================
    // 取得目前登入 username
    // =====================================================

    fun getUsername(
        context: Context
    ): String? {


        return context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_USERNAME,
                null
            )
    }


    // =====================================================
    // Access Token 是否存在
    // =====================================================

    fun hasAccessToken(
        context: Context
    ): Boolean {


        return !getAccessToken(
            context
        ).isNullOrBlank()
    }


    // =====================================================
    // Access Token 是否已過期
    // =====================================================

    fun isAccessTokenExpired(
        context: Context
    ): Boolean {


        val expiresAt =
            context
                .getSharedPreferences(
                    PREF_NAME,
                    Context.MODE_PRIVATE
                )
                .getLong(
                    KEY_ACCESS_TOKEN_EXPIRES_AT,
                    0L
                )


        if (expiresAt <= 0L) {

            return true
        }


        return System.currentTimeMillis() >=
                expiresAt
    }


    // =====================================================
    // 是否有登入資料
    // =====================================================

    fun isLoggedIn(
        context: Context
    ): Boolean {


        return !getAccessToken(
            context
        ).isNullOrBlank() &&
                !getRefreshToken(
                    context
                ).isNullOrBlank()
    }


    // =====================================================
    // 清除登入資料
    //
    // 之後 Logout 時會用到。
    // =====================================================

    fun clear(
        context: Context
    ) {


        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .clear()
            .apply()
    }
}