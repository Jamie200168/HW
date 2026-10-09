package com.example.project.ui.workout

import android.os.Bundle
import android.view.View
import android.widget.Button

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

import com.example.project.R


class WorkoutChoiceFragment :
    Fragment(
        R.layout.fragment_workout_choice
    ) {


    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )


        val btnWalk =
            view.findViewById<Button>(
                R.id.btnWalk
            )


        val btnHiking =
            view.findViewById<Button>(
                R.id.btnHiking
            )


        val btnBike =
            view.findViewById<Button>(
                R.id.btnBike
            )


        btnWalk.setOnClickListener {

            openWorkout(
                "走路"
            )
        }


        btnHiking.setOnClickListener {

            openWorkout(
                "登山"
            )
        }


        btnBike.setOnClickListener {

            openWorkout(
                "騎自行車"
            )
        }
    }


    private fun openWorkout(
        activityType: String
    ) {

        findNavController()
            .navigate(

                R.id.workoutFragment,

                bundleOf(

                    "activityType"
                            to activityType
                )
            )
    }
}