package com.digia.engage

import android.content.Context
import com.digia.engage.internal.DigiaInstance

object Digia {
    fun initialize(context: Context, config: DigiaConfig) {
        DigiaInstance.initialize(context = context, config = config)
    }

    fun register(plugin: DigiaCEPPlugin) {
        DigiaInstance.register(plugin)
    }

    fun setCurrentScreen(name: String) {
        DigiaInstance.setCurrentScreen(name)
    }
}
