package com.example.project.ui.workout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

import com.example.project.data.RoutePoint


class RouteView @JvmOverloads constructor(

    context: Context,

    attrs: AttributeSet? = null,

    defStyleAttr: Int = 0

) : View(
    context,
    attrs,
    defStyleAttr
) {


    private var points:
            List<RoutePoint> =
        emptyList()


    private val routePaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color =
                Color.rgb(
                    103,
                    80,
                    164
                )

            strokeWidth =
                10f

            style =
                Paint.Style.STROKE

            strokeCap =
                Paint.Cap.ROUND

            strokeJoin =
                Paint.Join.ROUND
        }


    private val startPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color =
                Color.GREEN

            style =
                Paint.Style.FILL
        }


    private val endPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color =
                Color.RED

            style =
                Paint.Style.FILL
        }


    fun setRoute(
        routePoints: List<RoutePoint>
    ) {

        points =
            routePoints.toList()

        invalidate()
    }


    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(
            canvas
        )


        if (
            points.isEmpty()
        ) {

            return
        }


        val padding =
            40f


        val minLat =
            points.minOf {
                it.latitude
            }


        val maxLat =
            points.maxOf {
                it.latitude
            }


        val minLng =
            points.minOf {
                it.longitude
            }


        val maxLng =
            points.maxOf {
                it.longitude
            }


        val latRange =

            if (
                maxLat - minLat >
                0.000001
            ) {

                maxLat - minLat

            } else {

                0.000001
            }


        val lngRange =

            if (
                maxLng - minLng >
                0.000001
            ) {

                maxLng - minLng

            } else {

                0.000001
            }


        val drawWidth =
            (
                    width -
                            padding * 2
                    ).coerceAtLeast(
                    1f
                )


        val drawHeight =
            (
                    height -
                            padding * 2
                    ).coerceAtLeast(
                    1f
                )


        fun convertX(
            longitude: Double
        ): Float {

            return (
                    padding +
                            (
                                    longitude -
                                            minLng
                                    ) /
                            lngRange *
                            drawWidth
                    ).toFloat()
        }


        fun convertY(
            latitude: Double
        ): Float {

            return (
                    height -
                            padding -
                            (
                                    latitude -
                                            minLat
                                    ) /
                            latRange *
                            drawHeight
                    ).toFloat()
        }


        val first =
            points.first()


        val path =
            Path()


        path.moveTo(

            convertX(
                first.longitude
            ),

            convertY(
                first.latitude
            )
        )


        points
            .drop(1)
            .forEach { point ->

                path.lineTo(

                    convertX(
                        point.longitude
                    ),

                    convertY(
                        point.latitude
                    )
                )
            }


        canvas.drawPath(
            path,
            routePaint
        )


        canvas.drawCircle(

            convertX(
                first.longitude
            ),

            convertY(
                first.latitude
            ),

            14f,

            startPaint
        )


        val last =
            points.last()


        canvas.drawCircle(

            convertX(
                last.longitude
            ),

            convertY(
                last.latitude
            ),

            14f,

            endPaint
        )
    }
}