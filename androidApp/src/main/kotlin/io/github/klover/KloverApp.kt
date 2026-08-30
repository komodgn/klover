package io.github.klover

import android.app.Application
import io.github.klover.di.initKoin

class KloverApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}
