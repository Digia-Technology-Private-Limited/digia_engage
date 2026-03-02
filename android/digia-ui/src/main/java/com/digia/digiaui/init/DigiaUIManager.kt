package com.digia.digiaui.init

import com.digia.digiaexpr.ast.ASTNode
import com.digia.digiaexpr.callable.ExprCallableImpl
import com.digia.digiaexpr.callable.ExprClass
import com.digia.digiaexpr.callable.ExprClassInstance
import com.digia.digiaui.config.model.DUIConfig
import com.digia.digiaui.framework.datatype.Variable
import com.digia.digiaui.framework.logging.Logger
import com.digia.digiaui.framework.message.MessageBus
import com.digia.digiaui.framework.models.LocalAsset
import com.digia.digiaui.network.NetworkClient
import com.digia.digiaui.utils.DigiaInspector
import com.digia.digiaui.utils.DigiaUIHost
import com.google.gson.Gson
import kotlin.collections.map
import kotlin.collections.mapOf

internal class DigiaUIManager private constructor() {

    private var _digiaUI: DigiaUI? = null

    /** The current DigiaUI instance, if initialized */
    val safeInstance: DigiaUI?
        get() = _digiaUI

    /** Gets the DigiaUI instance, throwing if not initialized */
    private val instance: DigiaUI
        get() =
                _digiaUI
                        ?: throw IllegalStateException(
                                "DigiaUI not initialized. Call DigiaUIManager.initialize() first."
                        )

    /** The current theme mode (Light, Dark, System) */
    val themeMode: ThemeMode
        get() = instance.initConfig.themeMode

    /** The access key used for authentication */
    val accessKey: String
        get() = instance.initConfig.accessKey

    /** The inspector instance for debugging and network monitoring */
    val inspector: DigiaInspector?
        get() = instance.initConfig.developerConfig?.inspector

    /** Whether the inspector is enabled */
    val isInspectorEnabled: Boolean
        get() = instance.initConfig.developerConfig?.inspector != null

    /** The network client for API communications */
    val networkClient: NetworkClient
        get() = instance.networkClient

    /** The application configuration */
    val config: DUIConfig
        get() = instance.dslConfig

    /** The hosting configuration (Dashboard, Custom, etc.) */
    val host: DigiaUIHost?
        get() = instance.initConfig.developerConfig?.host

    /** Environment variables from the configuration */
    val environmentVariables: Map<String, Variable>
        get() = config.getEnvironmentVariables()

    /** The message bus for inter-component communication */
    var messageBus: MessageBus = MessageBus()

    /** The bottom sheet manager for displaying modal bottom sheets */
    var bottomSheetManager: com.digia.digiaui.framework.bottomsheet.BottomSheetManager? = null

    /** The dialog manager for displaying dialogs */
    var dialogManager: com.digia.digiaui.framework.dialog.DialogManager? = null

    /** Asset images declared in the config, parsed into LocalAsset objects */
    val assetImages: List<LocalAsset>
        get() {
            val raw = safeInstance?.dslConfig?.appAssets ?: return emptyList()
            val gson = Gson()
            return raw.mapNotNull { item ->
                try {
                    // item might be Map<String, Any> or a JSON string
                    val json = when (item) {
                        is String -> item
                        else -> gson.toJson(item)
                    }
                    gson.fromJson(json, LocalAsset::class.java)
                } catch (t: Throwable) {
                    Logger.log("Failed to parse asset item: ${t.message}", tag = "DigiaUIManager")
                    null
                }
            }
        }

    /** JavaScript variables for expression evaluation. */
    val jsVars: Map<String, Any>
        get() {
            return mapOf(
                "js" to ExprClassInstance(
                    ExprClass(
                        name = "js",
                        fields = mutableMapOf(),
                        methods = mapOf(
                            "eval" to ExprCallableImpl(
                                _arity = 2,
                                fn = { evaluator, arguments ->
                                    val first = _toValue<String>(evaluator, arguments.getOrNull(0))
                                        ?: arguments.getOrNull(0)?.toString()
                                        ?: ""
                                    val rest: List<Any?> = arguments
                                        .drop(1)
                                        .map { arg -> _toValue(evaluator, arg) }
                                    safeInstance?.dslConfig?.jsFunctions?.callJs(first, rest)
                                }
                            )
                        )
                    )
                )
            )
        }


    private fun <T> _toValue(evaluator: Any?, obj: Any?): T? {
        if (obj == null) return null

        if (obj is ASTNode) {
            try {
                val evalMethod = evaluator?.javaClass?.methods?.firstOrNull { it.name == "eval" && it.parameterCount == 1 }
                val result = evalMethod?.invoke(evaluator, obj)
                @Suppress("UNCHECKED_CAST")
                return result as T?
            } catch (t: Throwable) {
                return null
            }
        }

        @Suppress("UNCHECKED_CAST")
        return obj as T?
    }


    companion object {
        @Volatile private var INSTANCE: DigiaUIManager? = null

        /** Gets the singleton instance of DigiaUIManager */
        fun getInstance(): DigiaUIManager {
            return INSTANCE
                    ?: synchronized(this) { INSTANCE ?: DigiaUIManager().also { INSTANCE = it } }
        }

        /**
         * Initializes the manager with a DigiaUI instance
         *
         * @param digiaUI The initialized DigiaUI instance
         */
        fun initialize(digiaUI: DigiaUI) {
            Logger.log("DigiaUIManager initialized")
            getInstance()._digiaUI = digiaUI
        }

        /** Destroys the manager and cleans up resources */
        fun destroy() {
            Logger.log("DigiaUIManager destroyed")
            getInstance()._digiaUI = null
        }
    }
}
