package com.example.project.ui.workout


import android.content.Context
import android.util.AttributeSet

import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

import com.example.project.data.TrackingRepository


class BikeMetricTitleTextView
@JvmOverloads
constructor(

    context: Context,

    attrs: AttributeSet? =
        null,

    defStyleAttr: Int =
        android.R.attr.textViewStyle

) :
    AppCompatTextView(
        context,
        attrs,
        defStyleAttr
    ) {


    override fun onAttachedToWindow() {

        super.onAttachedToWindow()


        post {

            updateTitle()
        }
    }


    private fun updateTitle() {


        text =

            if (
                isCyclingMode()
            ) {

                "🚴 時速"

            } else {

                "👟 步數"
            }
    }


    private fun isCyclingMode():
            Boolean {


        val activityType =
            try {


                val fragment =
                    FragmentManager
                        .findFragment<Fragment>(
                            this
                        )


                fragment
                    .arguments
                    ?.getString(
                        "activityType"
                    )


            } catch (
                _: Exception
            ) {


                TrackingRepository
                    .activeActivityType
                    .value
            }


        return activityType ==
                "騎自行車" ||
                activityType ==
                "單車"
    }
}