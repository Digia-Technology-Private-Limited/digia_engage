package com.digia.digiauiexample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.digia.engage.Digia
import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaHost
import com.digia.engage.DigiaInitialPage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Digia.initialize(
                context = applicationContext,
                config = DigiaConfig(apiKey = "69d3dc5e4d3eed4271b8c259"),
        )
        setContent {
            DigiaHost { DigiaInitialPage() }
        }
    }
}
