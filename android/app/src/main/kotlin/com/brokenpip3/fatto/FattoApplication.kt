package com.brokenpip3.fatto

import android.app.Application
import android.util.Log
import androidx.work.Configuration

class FattoApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .build()
}
