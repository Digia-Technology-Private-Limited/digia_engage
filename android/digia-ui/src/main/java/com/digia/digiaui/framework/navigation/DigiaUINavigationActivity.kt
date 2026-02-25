package com.digia.digiaui.framework.navigation

import ResourceProvider
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.framework.actions.ActionExecutor
import com.digia.digiaui.framework.actions.ActionProvider

/**
 * DigiaUINavigationActivity - SDK Activity for hosting Digia UI navigation
 *
 * This Activity is launched when the host app calls CreateNavHost().
 * It contains the entire Digia UI navigation system in a separate Activity context.
 *
 * Features:
 * - Full navigation stack management
 * - State preservation across configuration changes
 * - Result callback to host app when navigation completes
 *
 * Usage from host app:
 * ```kotlin
 * val launcher = rememberLauncherForActivityResult(
 *     contract = DigiaUINavigationContract()
 * ) { result ->
 *     // Handle result from Digia UI navigation
 * }
 *
 * Button(onClick = {
 *     launcher.launch(DigiaUINavigationInput(
 *         startPageId = "home",
 *         pageArgs = mapOf("userId" to "123")
 *     ))
 * }) {
 *     Text("Open Digia UI")
 * }
 * ```
 */
class DigiaUINavigationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_START_PAGE_ID = "start_page_id"
        const val EXTRA_PAGE_ARGS = "page_args"
        const val EXTRA_RESULT_DATA = "result_data"

        /**
         * Create an intent to launch this Activity
         */
        fun createIntent(
            context: Context,
            startPageId: String? = null,
            pageArgs: Map<String, Any?>? = null
        ): Intent {
            return Intent(context, DigiaUINavigationActivity::class.java).apply {
                startPageId?.let { putExtra(EXTRA_START_PAGE_ID, it) }
                pageArgs?.let { putExtra(EXTRA_PAGE_ARGS, HashMap(it)) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract navigation parameters from intent
        val startPageId = intent.getStringExtra(EXTRA_START_PAGE_ID)
        @Suppress("UNCHECKED_CAST")
        val pageArgs = intent.getSerializableExtra(EXTRA_PAGE_ARGS) as? Map<String, Any?>

        setContent {
            MaterialTheme {
                DigiaUINavigationContent(
                    startPageId = startPageId,
                    pageArgs = pageArgs,
                    onNavigationComplete = { result ->
                        // Return result to host app
                        val resultIntent = Intent().apply {
                            result?.let { putExtra(EXTRA_RESULT_DATA, HashMap(it)) }
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onBackPressed() {
        // Let DUINavHost handle back navigation first
        // If it can't handle it, finish the activity
        super.onBackPressed()
    }
}

/**
 * Composable content for Digia UI navigation
 *
 * This wraps the DUINavHost with necessary providers
 */
@Composable
private fun DigiaUINavigationContent(
    startPageId: String?,
    pageArgs: Map<String, Any?>?,
    onNavigationComplete: (Map<String, Any?>?) -> Unit
) {
    val factory = DUIFactory.getInstance()

    ActionProvider(
        actionExecutor = ActionExecutor()
    ) {
        ResourceProvider(
            resources = factory.getResources(),
            apiModels = factory.getConfigProvider().getAllApiModels()
        ) {
            DUINavHost(
                configProvider = factory.getConfigProvider(),
                startPageId = startPageId ?: factory.getConfigProvider().getInitialRoute(),
                startPageArgs = pageArgs,
                registry = factory.getWidgetRegistry()
            )
        }
    }
}

/**
 * Input data for launching Digia UI Navigation
 */
data class DigiaUINavigationInput(
    val startPageId: String? = null,
    val pageArgs: Map<String, Any?>? = null
)

/**
 * Result data from Digia UI Navigation
 */
data class DigiaUINavigationResult(
    val data: Map<String, Any?>? = null
)

/**
 * ActivityResultContract for launching Digia UI Navigation
 *
 * This provides a type-safe way to launch the navigation Activity and receive results.
 *
 * Usage:
 * ```kotlin
 * val launcher = rememberLauncherForActivityResult(
 *     contract = DigiaUINavigationContract()
 * ) { result ->
 *     result?.data?.let { data ->
 *         println("Received result: $data")
 *     }
 * }
 *
 * launcher.launch(DigiaUINavigationInput(
 *     startPageId = "checkout",
 *     pageArgs = mapOf("cartId" to "12345")
 * ))
 * ```
 */
class DigiaUINavigationContract : ActivityResultContract<DigiaUINavigationInput, DigiaUINavigationResult?>() {

    override fun createIntent(context: Context, input: DigiaUINavigationInput): Intent {
        return DigiaUINavigationActivity.createIntent(
            context = context,
            startPageId = input.startPageId,
            pageArgs = input.pageArgs
        )
    }

    override fun parseResult(resultCode: Int, intent: Intent?): DigiaUINavigationResult? {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val data = intent.getSerializableExtra(DigiaUINavigationActivity.EXTRA_RESULT_DATA) as? Map<String, Any?>
        return DigiaUINavigationResult(data)
    }
}
