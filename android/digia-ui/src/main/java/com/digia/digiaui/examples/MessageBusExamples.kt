//package com.digia.digiaui.examples
//
//import android.content.Context
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import com.digia.digiaui.framework.message.*
//
///**
// * Example demonstrating MessageBus integration patterns
// */
//
//// Example 1: Simple Send and Receive
//@Composable
//fun SimpleSendReceiveExample() {
//    var receivedMessage by remember { mutableStateOf("No message yet") }
//    val sendMessage = rememberMessageSender()
//
//    // Observer
//    ObserveMessages("simpleChannel") { message ->
//        receivedMessage = message.payload?.toString() ?: "Empty message"
//    }
//
//    Column(modifier = Modifier.padding(16.dp)) {
//        Text("Received: $receivedMessage")
//        Spacer(modifier = Modifier.height(8.dp))
//        Button(onClick = {
//            sendMessage("simpleChannel", "Hello at ${System.currentTimeMillis()}")
//        }) {
//            Text("Send Message")
//        }
//    }
//}
//
//// Example 2: Multiple Components Communication
//@Composable
//fun MultiComponentExample() {
//    Column(modifier = Modifier.padding(16.dp)) {
//        Text("Multiple Components Example", style = MaterialTheme.typography.headlineSmall)
//        Spacer(modifier = Modifier.height(16.dp))
//
//        // Component 1: Counter
//        CounterComponent()
//        Divider(modifier = Modifier.padding(vertical = 8.dp))
//
//        // Component 2: Display
//        CounterDisplayComponent()
//    }
//}
//
//@Composable
//fun CounterComponent() {
//    var count by remember { mutableStateOf(0) }
//    val sendMessage = rememberMessageSender()
//
//    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//        Button(onClick = {
//            count++
//            sendMessage("counter:update", count)
//        }) {
//            Text("Increment")
//        }
//        Text("Local Count: $count")
//    }
//}
//
//@Composable
//fun CounterDisplayComponent() {
//    var displayCount by remember { mutableStateOf(0) }
//
//    ObserveMessages("counter:update") { message ->
//        displayCount = (message.payload as? Int) ?: 0
//    }
//
//    Card(
//        modifier = Modifier.fillMaxWidth()
//    ) {
//        Text(
//            text = "Received Count: $displayCount",
//            modifier = Modifier.padding(16.dp)
//        )
//    }
//}
//
//// Example 3: State Synchronization
//@Composable
//fun StateSyncExample() {
//    Column(modifier = Modifier.padding(16.dp)) {
//        Text("State Sync Example", style = MaterialTheme.typography.headlineSmall)
//        Spacer(modifier = Modifier.height(16.dp))
//
//        SyncedTextField(id = "field1")
//        Spacer(modifier = Modifier.height(8.dp))
//        SyncedTextField(id = "field2")
//    }
//}
//
//@Composable
//fun SyncedTextField(id: String) {
//    var text by remember { mutableStateOf("") }
//    val sendMessage = rememberMessageSender()
//
//    // Listen for updates from other fields
//    ObserveMessages("textSync") { message ->
//        val payload = message.payload as? Map<*, *>
//        if (payload?.get("senderId") != id) {
//            text = payload?.get("text")?.toString() ?: ""
//        }
//    }
//
//    OutlinedTextField(
//        value = text,
//        onValueChange = { newText ->
//            text = newText
//            sendMessage("textSync", mapOf(
//                "senderId" to id,
//                "text" to newText
//            ))
//        },
//        label = { Text("Field $id") },
//        modifier = Modifier.fillMaxWidth()
//    )
//}
//
//// Example 4: Request-Response Pattern
//@Composable
//fun RequestResponseExample() {
//    var response by remember { mutableStateOf("No response") }
//    val sendMessage = rememberMessageSender()
//
//    // Listen for responses
//    ObserveMessages("dataResponse") { message ->
//        response = message.payload?.toString() ?: "Empty response"
//    }
//
//    // Simulate a data provider
//    DataProviderComponent()
//
//    Column(modifier = Modifier.padding(16.dp)) {
//        Text("Response: $response")
//        Spacer(modifier = Modifier.height(8.dp))
//        Button(onClick = {
//            sendMessage("dataRequest", mapOf("id" to (1..100).random()))
//        }) {
//            Text("Request Data")
//        }
//    }
//}
//
//@Composable
//fun DataProviderComponent() {
//    val sendMessage = rememberMessageSender()
//
//    ObserveMessages("dataRequest") { message ->
//        val id = (message.payload as? Map<*, *>)?.get("id")
//        // Simulate data fetching
//        val data = "Data for ID: $id - ${System.currentTimeMillis()}"
//        sendMessage("dataResponse", data)
//    }
//}
//
//// Example 5: Using Context
//@Composable
//fun ContextAwareExample() {
//    val context = LocalContext.current
//    val sendMessage = rememberMessageSenderWithContext(context)
//
//    // Listen for messages with context
//    ObserveMessages("showToast") { message ->
//        val context = message.context
//        val text = message.payload?.toString() ?: "No message"
//        // You could show a toast here if you had a toast utility
//        println("Would show toast: $text in context: $context")
//    }
//
//    Column(modifier = Modifier.padding(16.dp)) {
//        Button(onClick = {
//            sendMessage("showToast", "Hello from context!")
//        }) {
//            Text("Send with Context")
//        }
//    }
//}
//
//// Example 6: Multiple Channels
//@Composable
//fun MultiChannelExample() {
//    var events by remember { mutableStateOf(listOf<String>()) }
//
//    ObserveMultipleChannels(
//        channels = listOf("event1", "event2", "event3")
//    ) { message ->
//        events = events + "${message.name}: ${message.payload}"
//    }
//
//    val sendMessage = rememberMessageSender()
//
//    Column(modifier = Modifier.padding(16.dp)) {
//        Text("Events:", style = MaterialTheme.typography.titleMedium)
//        events.takeLast(5).forEach { event ->
//            Text(event, style = MaterialTheme.typography.bodySmall)
//        }
//
//        Spacer(modifier = Modifier.height(8.dp))
//
//        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//            Button(onClick = { sendMessage("event1", "Click 1") }) {
//                Text("Event 1")
//            }
//            Button(onClick = { sendMessage("event2", "Click 2") }) {
//                Text("Event 2")
//            }
//            Button(onClick = { sendMessage("event3", "Click 3") }) {
//                Text("Event 3")
//            }
//        }
//    }
//}
//
//// Example 7: Latest Message as State
//@Composable
//fun LatestMessageExample() {
//    val latestMessage = rememberLatestMessage("notifications")
//    val sendMessage = rememberMessageSender()
//
//    Column(modifier = Modifier.padding(16.dp)) {
//        Card(
//            modifier = Modifier.fillMaxWidth(),
//            colors = CardDefaults.cardColors(
//                containerColor = MaterialTheme.colorScheme.primaryContainer
//            )
//        ) {
//            Column(modifier = Modifier.padding(16.dp)) {
//                Text(
//                    "Latest Notification:",
//                    style = MaterialTheme.typography.titleMedium
//                )
//                Text(
//                    latestMessage.value?.payload?.toString() ?: "No notifications",
//                    style = MaterialTheme.typography.bodyMedium
//                )
//            }
//        }
//
//        Spacer(modifier = Modifier.height(8.dp))
//
//        Button(onClick = {
//            sendMessage("notifications", "Notification at ${System.currentTimeMillis()}")
//        }) {
//            Text("Send Notification")
//        }
//    }
//}
//
//// Example 8: Non-Composable Sending (from ViewModel/Repository)
//class ExampleViewModel {
//    fun performAction() {
//        // Send message from non-composable code
//        sendMessage(
//            channelName = "viewModelEvent",
//            payload = mapOf(
//                "action" to "dataLoaded",
//                "timestamp" to System.currentTimeMillis()
//            )
//        )
//    }
//
//    fun performActionWithContext(context: Context) {
//        sendMessage(
//            channelName = "viewModelEventWithContext",
//            payload = "Action completed",
//            context = context
//        )
//    }
//}
//
//// Full example screen combining patterns
//@Composable
//fun MessageBusExamplesScreen() {
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        verticalArrangement = Arrangement.spacedBy(16.dp)
//    ) {
//        Text(
//            "MessageBus Examples",
//            style = MaterialTheme.typography.headlineMedium
//        )
//
//        Divider()
//
//        // You can uncomment and try each example
//        SimpleSendReceiveExample()
//        // MultiComponentExample()
//        // StateSyncExample()
//        // RequestResponseExample()
//        // ContextAwareExample()
//        // MultiChannelExample()
//        // LatestMessageExample()
//    }
//}
//
