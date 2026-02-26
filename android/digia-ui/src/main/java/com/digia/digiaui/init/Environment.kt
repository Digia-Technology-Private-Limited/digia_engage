package com.digia.digiaui.init

sealed class Environment(val name: String) {
        object Local : Environment("local")
        object Development : Environment("development")
        object Production : Environment("production")
        data class Custom(val customName: String, val apiBaseUrl: String) : Environment(customName)

        override fun toString(): String = name

        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Environment) return false
                return name == other.name
        }

        override fun hashCode(): Int = name.hashCode()

        companion object {
                fun fromString(name: String): Environment {
                        return when (name.lowercase()) {
                                "local" -> Local
                                "development" -> Development
                                "production" -> Production
                                else -> Custom(name, "https://api.digiastudio.com")
                        }
                }
        }
}
