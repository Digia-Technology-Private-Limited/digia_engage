package com.digia.digiaui.init

enum class FlavorOption(val optionName: String) {
    DEBUG("debug"),
    STAGING("staging"),
    RELEASE("release"),
    VERSION("version")
}

sealed class Flavor {
    abstract val value: FlavorOption
    abstract val environment: Environment

    data class Debug(
            val branchName: String? = null,
            override val environment: Environment = Environment.Production
    ) : Flavor() {
        override val value = FlavorOption.DEBUG
    }

    data class Staging(override val environment: Environment = Environment.Production) : Flavor() {
        override val value = FlavorOption.STAGING
    }

    data class Release(
            val initStrategy: DSLInitStrategy,
            val appConfigPath: String,
            val functionsPath: String
    ) : Flavor() {
        override val value = FlavorOption.RELEASE
        override val environment = Environment.Production
    }

    data class Versioned(
            val version: Int,
            override val environment: Environment = Environment.Production
    ) : Flavor() {
        override val value = FlavorOption.VERSION
    }
}

enum class EnvType {
    PRODUCTION,
    DEVELOPMENT
}

sealed class DSLInitStrategy

class NetworkFirstStrategy(val timeout: Int) : DSLInitStrategy()

class CacheFirstStrategy : DSLInitStrategy()

class LocalFirstStrategy : DSLInitStrategy()
