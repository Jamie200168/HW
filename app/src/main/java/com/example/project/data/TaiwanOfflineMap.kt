package com.example.project.data


import android.content.Context

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.io.File
import java.io.FileOutputStream


object TaiwanOfflineMap {


    // =====================================================
    // APK assets 裡面的檔名
    // =====================================================

    private const val ASSET_FILE_NAME =
        "taiwan.pmtiles"


    // =====================================================
    // 複製到手機後的檔名
    // =====================================================

    private const val LOCAL_FILE_NAME =
        "taiwan.pmtiles"


    // =====================================================
    // 手機 App 內部地圖資料夾
    //
    // 最後會類似：
    //
    // /data/user/0/com.example.project/files/maps/
    // =====================================================

    private fun getMapDirectory(
        context: Context
    ): File {


        val directory =
            File(
                context.filesDir,
                "maps"
            )


        if (
            !directory.exists()
        ) {


            directory.mkdirs()
        }


        return directory
    }


    // =====================================================
    // MapLibre 最後真正讀取的 PMTiles 檔案
    // =====================================================

    fun getMapFile(
        context: Context
    ): File {


        return File(
            getMapDirectory(
                context
            ),
            LOCAL_FILE_NAME
        )
    }


    // =====================================================
    // 判斷地圖是否已經準備完成
    // =====================================================

    fun isMapReady(
        context: Context
    ): Boolean {


        val file =
            getMapFile(
                context
            )


        return file.exists() &&
                file.isFile &&
                file.length() > 0L
    }


    // =====================================================
    // 第一次開 App 時執行
    //
    // assets/taiwan.pmtiles
    //
    // ↓
    //
    // files/maps/taiwan.pmtiles
    // =====================================================

    suspend fun prepareMap(

        context: Context,

        onProgress:
        suspend (Int) -> Unit

    ): Result<File> {


        return withContext(
            Dispatchers.IO
        ) {


            val destinationFile =
                getMapFile(
                    context
                )


            // =============================================
            // 如果之前已經複製成功
            // 就直接使用
            // =============================================

            if (
                destinationFile.exists() &&
                destinationFile.length() > 0L
            ) {


                return@withContext Result.success(
                    destinationFile
                )
            }


            // =============================================
            // 複製過程先用暫存檔
            // =============================================

            val tempFile =
                File(
                    destinationFile.parentFile,
                    "taiwan.pmtiles.tmp"
                )


            try {


                if (
                    tempFile.exists()
                ) {


                    tempFile.delete()
                }


                // =========================================
                // 取得 assets 裡 PMTiles 的大小
                //
                // 因為 build.gradle.kts 已設定：
                //
                // noCompress += "pmtiles"
                //
                // 所以 openFd() 可以正常取得大小
                // =========================================

                val totalBytes =
                    context.assets
                        .openFd(
                            ASSET_FILE_NAME
                        )
                        .use {

                            it.length
                        }


                // =========================================
                // 開始 assets → 手機 files 複製
                // =========================================

                context.assets
                    .open(
                        ASSET_FILE_NAME
                    )
                    .buffered(
                        1024 * 1024
                    )
                    .use {
                            input ->


                        FileOutputStream(
                            tempFile
                        )
                            .buffered(
                                1024 * 1024
                            )
                            .use {
                                    output ->


                                val buffer =
                                    ByteArray(
                                        1024 * 1024
                                    )


                                var copiedBytes =
                                    0L


                                var lastProgress =
                                    -1


                                while (
                                    true
                                ) {


                                    val count =
                                        input.read(
                                            buffer
                                        )


                                    if (
                                        count == -1
                                    ) {


                                        break
                                    }


                                    output.write(
                                        buffer,
                                        0,
                                        count
                                    )


                                    copiedBytes +=
                                        count


                                    // =================================
                                    // 計算 0 ~ 100 %
                                    // =================================

                                    if (
                                        totalBytes > 0L
                                    ) {


                                        val progress =
                                            (
                                                    copiedBytes *
                                                            100L /
                                                            totalBytes
                                                    )
                                                .toInt()
                                                .coerceIn(
                                                    0,
                                                    100
                                                )


                                        if (
                                            progress !=
                                            lastProgress
                                        ) {


                                            lastProgress =
                                                progress


                                            withContext(
                                                Dispatchers.Main
                                            ) {


                                                onProgress(
                                                    progress
                                                )
                                            }
                                        }
                                    }
                                }


                                output.flush()
                            }
                    }


                // =========================================
                // 確認不是空檔案
                // =========================================

                if (
                    !tempFile.exists() ||
                    tempFile.length() <= 0L
                ) {


                    throw IllegalStateException(
                        "taiwan.pmtiles 複製失敗"
                    )
                }


                // =========================================
                // 如果舊檔存在先刪掉
                // =========================================

                if (
                    destinationFile.exists()
                ) {


                    destinationFile.delete()
                }


                // =========================================
                // 暫存檔 → 正式檔案
                // =========================================

                val renamed =
                    tempFile.renameTo(
                        destinationFile
                    )


                // rename 失敗時改用 copy
                if (
                    !renamed
                ) {


                    tempFile.copyTo(
                        destinationFile,
                        overwrite = true
                    )


                    tempFile.delete()
                }


                // =========================================
                // 完成
                // =========================================

                withContext(
                    Dispatchers.Main
                ) {


                    onProgress(
                        100
                    )
                }


                Result.success(
                    destinationFile
                )


            } catch (
                e: Exception
            ) {


                if (
                    tempFile.exists()
                ) {


                    tempFile.delete()
                }


                Result.failure(
                    e
                )
            }
        }
    }


    // =====================================================
    // 查看手機內地圖大小
    // =====================================================

    fun getFileSizeMb(
        context: Context
    ): Double {


        val file =
            getMapFile(
                context
            )


        if (
            !file.exists()
        ) {


            return 0.0
        }


        return file.length()
            .toDouble() /
                1024.0 /
                1024.0
    }


    // =====================================================
    // 測試時使用：
    //
    // 刪除手機裡已複製的 PMTiles
    //
    // 正式 App 不一定會用到
    // =====================================================

    fun deleteLocalMap(
        context: Context
    ): Boolean {


        val file =
            getMapFile(
                context
            )


        return !file.exists() ||
                file.delete()
    }
}