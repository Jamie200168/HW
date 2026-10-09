package com.example.project.util

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs


class SwipeRightListener(

    context: Context,

    private val onSwipeRight: () -> Unit

) : View.OnTouchListener {


    private val gestureDetector =

        GestureDetector(

            context,

            object :
                GestureDetector.SimpleOnGestureListener() {


                override fun onDown(
                    e: MotionEvent
                ): Boolean {

                    /*
                     * 一定要回傳 true
                     * 才能繼續收到後面的滑動事件
                     */

                    return true
                }


                override fun onFling(

                    e1: MotionEvent?,

                    e2: MotionEvent,

                    velocityX: Float,

                    velocityY: Float

                ): Boolean {


                    if (
                        e1 == null
                    ) {

                        return false
                    }


                    /*
                     * X 軸移動距離
                     *
                     * 正數 = 往右
                     */

                    val diffX =

                        e2.x -
                                e1.x


                    /*
                     * Y 軸移動距離
                     */

                    val diffY =

                        e2.y -
                                e1.y


                    /*
                     * 判斷是不是「主要往左右滑」
                     *
                     * 避免使用者上下滑歷史紀錄時
                     * 被誤判成回首頁
                     */

                    if (
                        abs(diffX) >
                        abs(diffY)
                    ) {


                        /*
                         * 往右至少滑 120 像素
                         */

                        if (
                            diffX >
                            120
                        ) {


                            /*
                             * 滑動速度也必須夠
                             */

                            if (
                                abs(velocityX) >
                                120
                            ) {


                                onSwipeRight()

                                return true
                            }
                        }
                    }


                    return false
                }
            }
        )


    override fun onTouch(

        view: View,

        event: MotionEvent

    ): Boolean {


        /*
         * 把觸控事件交給 GestureDetector
         */

        gestureDetector
            .onTouchEvent(
                event
            )


        /*
         * 回傳 false
         *
         * 很重要：
         * 這樣 History 頁仍然可以正常上下捲動
         */

        return false
    }
}