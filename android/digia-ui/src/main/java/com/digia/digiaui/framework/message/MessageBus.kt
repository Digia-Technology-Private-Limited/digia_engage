package com.digia.digiaui.framework.message

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.digia.digiaui.framework.DUIFactory
import com.digia.digiaui.init.DigiaUIManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Message data class for inter-component communication
 */
data class Message(
    val name: String,
    val payload: Any?,
    val context: Context? = null
)

/**
 * Message bus for inter-component communication using Kotlin Flow
 * 
 * Provides a reactive, coroutine-based message bus system for Compose applications.
 * Messages are emitted as SharedFlow and can be collected in composables.
 */
class MessageBus {
    // SharedFlow for each message channel
    private val channels = mutableMapOf<String, MutableSharedFlow<Message>>()
    
    // Scope for internal operations
    private val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Main)

    /**
     * Get or create a SharedFlow for a specific message channel
     */
    private fun getChannel(name: String): MutableSharedFlow<Message> {
        return channels.getOrPut(name) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = 64
            )
        }
    }

    /**
     * Send a message to all subscribers of the specified channel
     * 
     * @param message The message to send
     */
    fun send(message: Message) {
        scope.launch {
            try {
                getChannel(message.name).emit(message)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Get a SharedFlow for observing messages on a specific channel
     * 
     * @param name The channel name
     * @return A SharedFlow that emits messages for this channel
     */
    fun observe(name: String): SharedFlow<Message> {
        return getChannel(name).asSharedFlow()
    }

    /**
     * Clear all channels
     */
    fun clear() {
        channels.clear()
    }

    /**
     * Check if a channel has any active subscribers
     */
    fun hasSubscribers(name: String): Boolean {
        return channels[name]?.subscriptionCount?.value?.let { it > 0 } ?: false
    }
}

/**
 * Composable function to observe messages from a specific channel
 * 
 * @param messageBus The message bus instance
 * @param channelName The channel name to observe
 * @param onMessage Callback invoked when a message is received
 *
 * Example usage:
 * ```kotlin
 * ObserveMessages(messageBus, "myChannel") { message ->
 *     // Handle message
 *     println("Received: ${message.payload}")
 * }
 * ```
 */
@Composable
fun ObserveMessages(
    channelName: String,
    onMessage: (Message) -> Unit
) {
    val messageBus= DigiaUIManager.getInstance().messageBus
    LaunchedEffect(messageBus, channelName) {
        messageBus.observe(channelName).collect { message ->
            onMessage(message)
        }
    }
}

/**
 * Composable function to get a message sender for a specific channel
 *
 * @param messageBus The message bus instance
 * @return A function to send messages
 *
 * Example usage:
 * ```kotlin
 * val sendMessage = rememberMessageSender(messageBus)
 * Button(onClick = { sendMessage("myChannel", "Hello!") }) {
 *     Text("Send")
 * }
 * ```
 */
@Composable
fun rememberMessageSender(messageBus: MessageBus): (String, Any?) -> Unit {
    val scope = rememberCoroutineScope()
    return { name: String, payload: Any? ->
        messageBus.send(Message(name = name, payload = payload))
    }
}

