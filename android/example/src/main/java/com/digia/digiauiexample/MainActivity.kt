package com.digia.digiauiexample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.digia.engage.Digia
import com.digia.engage.DigiaConfig
import com.digia.engage.DigiaHost
import com.digia.engage.DigiaScreen
import com.digia.engage.DigiaSlot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Digia.initialize(
            context = applicationContext,
            config = DigiaConfig(apiKey = "69786962fe19ceddd06eade6"),
        )
        setContent {
            DigiaHost {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        DigiaScreen(name = "example_home")
                        Text("Digia initialized")
                        DigiaSlot(placementKey = "home_banner")
                    }
                }
            }
        }
    }
}
