package com.ai.guardian

import android.app.Application
import com.ai.guardian.di.AppContainer
import com.ai.guardian.di.DefaultAppContainer

class GuardianApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
