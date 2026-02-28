package com.digia.engage

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class DigiaComposableSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun digiaScreenAndSlotComposeWithoutCrash() {
        composeRule.setContent {
            DigiaHost {
                DigiaScreen(name = "home")
                DigiaSlot(placementKey = "hero_banner")
                Text("content")
            }
        }
        composeRule.onNodeWithText("content").assertExists()
    }
}
