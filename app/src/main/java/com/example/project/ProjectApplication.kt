package com.example.project

import android.app.Application
import org.maplibre.android.MapLibre

class ProjectApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        MapLibre.getInstance(this)
    }
}