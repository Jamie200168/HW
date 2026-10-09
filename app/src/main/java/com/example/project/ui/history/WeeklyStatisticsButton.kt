package com.example.project.ui.history


import android.content.Context
import android.util.AttributeSet

import androidx.navigation.Navigation

import com.example.project.R

import com.google.android.material.button.MaterialButton


class WeeklyStatisticsButton
@JvmOverloads
constructor(

    context: Context,

    attrs: AttributeSet? =
        null,

    defStyleAttr: Int =
        com.google.android.material.R
            .attr
            .materialButtonStyle

) :
    MaterialButton(
        context,
        attrs,
        defStyleAttr
    ) {


    override fun onAttachedToWindow() {


        super.onAttachedToWindow()


        setOnClickListener {


            val navController =
                Navigation
                    .findNavController(
                        this
                    )


            if (
                navController
                    .currentDestination
                    ?.id !=
                R.id.weeklyStatisticsFragment
            ) {


                navController.navigate(
                    R.id.weeklyStatisticsFragment
                )
            }
        }
    }
}