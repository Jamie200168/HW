package com.example.project.data

import com.example.project.R


object WeatherIconMapper {


    // =====================================================
    // weather_desc → 中央氣象署分類代碼
    // =====================================================

    fun getWeatherCode(
        weatherDescription: String?
    ): Int {

        if (weatherDescription.isNullOrBlank()) {
            return 0
        }


        val weather =

            weatherDescription
                .trim()
                .replace(" ", "")
                .replace("　", "")


        return when {


            // =================================================
            // 42：下雪 / 積冰 / 暴風雪
            // =================================================

            weather == "下雪" ||
                    weather == "積冰" ||
                    weather == "暴風雪" -> {

                42
            }


            // =================================================
            // 41：雷雨 + 霧
            //
            // 一定放在 35、36 前面
            // =================================================

            weather == "短暫陣雨或雷雨有霧" ||
                    weather == "陣雨或雷雨有霧" -> {

                41
            }


            // =================================================
            // 39：雨 + 霧
            // =================================================

            weather == "有雨有霧" ||
                    weather == "陣雨有霧" -> {

                39
            }


            // =================================================
            // 38：短暫雨 / 短暫陣雨 + 霧
            // =================================================

            weather == "短暫陣雨有霧" ||
                    weather == "短暫陣雨晨霧" ||
                    weather == "短暫雨有霧" ||
                    weather == "短暫雨晨霧" -> {

                38
            }


            // =================================================
            // 37：雨雪 + 霧
            // =================================================

            weather.contains("雪") &&
                    (
                            weather.contains("霧") ||
                                    weather.contains("晨霧")
                            ) -> {

                37
            }


            // =================================================
            // 36：偏陰 + 雷雨 + 霧
            // =================================================

            hasFog(weather) &&
                    hasThunder(weather) &&
                    isDarkCloud(weather) -> {

                36
            }


            // =================================================
            // 35：多雲 + 雷雨 + 霧
            // =================================================

            hasFog(weather) &&
                    hasThunder(weather) -> {

                35
            }


            // =================================================
            // 34：偏陰 + 局部雷雨
            // =================================================

            weather.contains("局部") &&
                    hasThunder(weather) &&
                    isDarkCloud(weather) -> {

                34
            }


            // =================================================
            // 33：多雲 + 局部雷雨
            // =================================================

            weather.contains("局部") &&
                    hasThunder(weather) -> {

                33
            }


            // =================================================
            // 32：偏陰 + 雨 + 霧
            // =================================================

            hasFog(weather) &&
                    hasRain(weather) &&
                    isDarkCloud(weather) -> {

                32
            }


            // =================================================
            // 31：多雲 + 雨 + 霧
            // =================================================

            hasFog(weather) &&
                    hasRain(weather) -> {

                31
            }


            // =================================================
            // 30：偏陰 + 局部雨
            // =================================================

            weather.contains("局部") &&
                    hasRain(weather) &&
                    isDarkCloud(weather) -> {

                30
            }


            // 特殊官方描述：
            // 晴午後陰局部雨...
            weather.startsWith("晴午後陰") &&
                    weather.contains("局部") &&
                    hasRain(weather) -> {

                30
            }


            // =================================================
            // 29：多雲局部雨
            // =================================================

            weather.contains("局部") &&
                    hasRain(weather) -> {

                29
            }


            // =================================================
            // 28：陰 / 多雲時陰 + 霧
            // =================================================

            hasFog(weather) &&
                    isDarkCloud(weather) -> {

                28
            }


            // =================================================
            // 27：多雲 / 一般有霧
            // =================================================

            weather == "多雲有霧" ||
                    weather == "多雲晨霧" ||
                    weather == "有霧" ||
                    weather == "晨霧" -> {

                27
            }


            // =================================================
            // 26：多雲時晴 + 霧
            // =================================================

            weather.startsWith("多雲時晴") &&
                    hasFog(weather) -> {

                26
            }


            // =================================================
            // 25：晴時多雲 + 霧
            // =================================================

            weather.startsWith("晴時多雲") &&
                    hasFog(weather) -> {

                25
            }


            // =================================================
            // 24：晴 + 霧
            // =================================================

            weather.startsWith("晴") &&
                    hasFog(weather) -> {

                24
            }


            // =================================================
            // 23：雨雪 / 雪
            //
            // 注意要放在其他下雨判斷前面
            // =================================================

            weather.contains("雪") -> {

                23
            }


            // =================================================
            // 21 / 22：午後雷雨
            // =================================================

            weather.contains("午後") &&
                    hasThunder(weather) &&
                    weather.startsWith("晴") -> {

                21
            }


            weather.contains("午後") &&
                    hasThunder(weather) -> {

                22
            }


            // =================================================
            // 19 / 20：午後雨
            // =================================================

            weather.contains("午後") &&
                    hasRain(weather) &&
                    weather.startsWith("晴") &&
                    !weather.startsWith("晴午後陰") -> {

                19
            }


            weather.contains("午後") &&
                    hasRain(weather) -> {

                20
            }


            // =================================================
            // 17：陰時多雲雷雨
            // =================================================

            hasThunder(weather) &&
                    weather.startsWith("陰時多雲") -> {

                17
            }


            // =================================================
            // 16：多雲時陰 / 晴間雷雨
            // =================================================

            hasThunder(weather) &&
                    weather.startsWith("多雲時陰") -> {

                16
            }


            hasThunder(weather) &&
                    (
                            weather.startsWith("晴陣雨") ||
                                    weather.startsWith("晴時多雲陣雨") ||
                                    weather.startsWith("多雲時晴陣雨")
                            ) -> {

                16
            }


            // =================================================
            // 18：陰天 / 一般雷雨
            // =================================================

            hasThunder(weather) &&
                    (
                            weather.startsWith("陰") ||
                                    weather == "雷雨" ||
                                    weather == "雷陣雨" ||
                                    weather == "午後雷陣雨" ||
                                    weather == "陣雨或雷雨"
                            ) -> {

                18
            }


            // =================================================
            // 15：多雲短暫雷雨
            // =================================================

            hasThunder(weather) -> {

                15
            }


            // =================================================
            // 11：特殊晴轉陰短暫雨
            // =================================================

            weather == "晴午後陰短暫雨" ||
                    weather == "晴午後陰短暫陣雨" -> {

                11
            }


            // =================================================
            // 9：多雲時陰短暫雨
            // =================================================

            weather.startsWith("多雲時陰") &&
                    weather.contains("短暫") &&
                    hasRain(weather) -> {

                9
            }


            // =================================================
            // 10：陰時多雲短暫雨
            // =================================================

            weather.startsWith("陰時多雲") &&
                    weather.contains("短暫") &&
                    hasRain(weather) -> {

                10
            }


            // =================================================
            // 11：陰短暫雨 / 雨天
            // =================================================

            weather == "雨天" -> {

                11
            }


            weather.startsWith("陰") &&
                    weather.contains("短暫") &&
                    hasRain(weather) -> {

                11
            }


            // =================================================
            // 8：多雲 / 晴間短暫雨
            // =================================================

            weather.contains("短暫") &&
                    hasRain(weather) -> {

                8
            }


            weather == "多雲陣雨" -> {

                8
            }


            // =================================================
            // 13：陰時多雲有雨
            // =================================================

            weather.startsWith("陰時多雲") &&
                    hasRain(weather) -> {

                13
            }


            // =================================================
            // 12：多雲時陰有雨
            // =================================================

            weather.startsWith("多雲時陰") &&
                    hasRain(weather) -> {

                12
            }


            weather.startsWith("晴時多雲") &&
                    weather.contains("陣雨") -> {

                12
            }


            weather.startsWith("多雲時晴") &&
                    weather.contains("陣雨") -> {

                12
            }


            // =================================================
            // 14：陰雨 / 有雨 / 陣雨
            // =================================================

            hasRain(weather) -> {

                14
            }


            // =================================================
            // 1～7：基本晴陰
            // =================================================

            weather == "晴" ||
                    weather == "晴天" -> {

                1
            }


            weather == "晴時多雲" -> {

                2
            }


            weather == "多雲時晴" -> {

                3
            }


            weather == "多雲" -> {

                4
            }


            weather == "多雲時陰" -> {

                5
            }


            weather == "陰時多雲" -> {

                6
            }


            weather == "陰" ||
                    weather == "陰天" -> {

                7
            }


            // =================================================
            // 找不到
            // =================================================

            else -> {

                0
            }
        }
    }


    // =====================================================
    // 代碼 → drawable
    // =====================================================

    fun getWeatherDrawable(
        weatherDescription: String?
    ): Int {


        return when (
            getWeatherCode(
                weatherDescription
            )
        ) {

            1 -> R.drawable.weather_01
            2 -> R.drawable.weather_02
            3 -> R.drawable.weather_03
            4 -> R.drawable.weather_04
            5 -> R.drawable.weather_05
            6 -> R.drawable.weather_06
            7 -> R.drawable.weather_07
            8 -> R.drawable.weather_08
            9 -> R.drawable.weather_09
            10 -> R.drawable.weather_10

            11 -> R.drawable.weather_11
            12 -> R.drawable.weather_12
            13 -> R.drawable.weather_13
            14 -> R.drawable.weather_14
            15 -> R.drawable.weather_15
            16 -> R.drawable.weather_16
            17 -> R.drawable.weather_17
            18 -> R.drawable.weather_18
            19 -> R.drawable.weather_19
            20 -> R.drawable.weather_20

            21 -> R.drawable.weather_21
            22 -> R.drawable.weather_22
            23 -> R.drawable.weather_23
            24 -> R.drawable.weather_24
            25 -> R.drawable.weather_25
            26 -> R.drawable.weather_26
            27 -> R.drawable.weather_27
            28 -> R.drawable.weather_28
            29 -> R.drawable.weather_29
            30 -> R.drawable.weather_30

            31 -> R.drawable.weather_31
            32 -> R.drawable.weather_32
            33 -> R.drawable.weather_33
            34 -> R.drawable.weather_34
            35 -> R.drawable.weather_35
            36 -> R.drawable.weather_36
            37 -> R.drawable.weather_37
            38 -> R.drawable.weather_38
            39 -> R.drawable.weather_39

            // 沒有 40

            41 -> R.drawable.weather_41
            42 -> R.drawable.weather_42


            else -> {

                R.drawable.weather_default
            }
        }
    }


    // =====================================================
    // Helper
    // =====================================================

    private fun hasRain(
        weather: String
    ): Boolean {

        return weather.contains("雨") ||
                weather.contains("陣雨")
    }


    private fun hasThunder(
        weather: String
    ): Boolean {

        return weather.contains("雷")
    }


    private fun hasFog(
        weather: String
    ): Boolean {

        return weather.contains("霧")
    }


    private fun isDarkCloud(
        weather: String
    ): Boolean {

        return weather.startsWith("多雲時陰") ||
                weather.startsWith("陰時多雲") ||
                weather.startsWith("陰") ||
                weather.startsWith("晴午後陰")
    }
}