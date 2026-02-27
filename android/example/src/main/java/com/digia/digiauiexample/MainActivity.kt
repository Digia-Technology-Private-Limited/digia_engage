package com.digia.digiauiexample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.digia.digiaui.app.DigiaUIApp
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.message.MessageBus
import com.digia.digiaui.init.DigiaUI
import com.digia.digiaui.init.DigiaUIOptions
import com.digia.digiaui.init.Flavor
//import com.digia.digiaui.framework.message.ObserveMessages
import com.digia.digiaui.utils.DeveloperConfig


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {

                val appConfigState = remember { mutableStateOf<DigiaUI?>(null) }
                if (appConfigState.value != null) {
                    DigiaUIApp(
                        digiaUI = appConfigState.value!!,

                        content = {
//
//                            ObserveMessages( "Test1") { message ->
//                                println("vdfvfdvfd")
//                            }

//                            Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
//                               Box (modifier = Modifier.padding(paddingValues).fillMaxSize()){
                                   DUIFactory.getInstance().CreateNavHost()
//                               }
//
//                            }
                        }
                    )
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Text(
                             "Loading...",
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
                LaunchedEffect(Unit) {
                    try {
                        val appConfig = DigiaUI.initialize(options = DigiaUIOptions(
                            context = this@MainActivity,
                            flavor = Flavor.Debug(),
//                            developerConfig = DeveloperConfig(
//                                baseUrl = "https://app.digia.tech/api/v1"
//                            ),
                            accessKey = "69786962fe19ceddd06eade6"  // Replace with actual access key
                        )
                        )
                        appConfigState.value = appConfig
                    } catch (e: Exception) {
                        appConfigState.value = null
                    }
                }
            }
        }
}

