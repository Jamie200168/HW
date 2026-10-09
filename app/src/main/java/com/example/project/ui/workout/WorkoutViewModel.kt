package com.example.project.ui.workout

import androidx.lifecycle.ViewModel

import com.example.project.data.TrackingRepository


class WorkoutViewModel :
    ViewModel() {


    val workoutState =
        TrackingRepository.state
}