package com.billbot.node.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.billbot.node.models.*
import com.billbot.node.utils.CryptoUtils
import com.billbot.node.utils.PreferencesManager
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.*

class NodeConnectionService : Service() {
    
    companion object {
        private const val TAG = "NodeConnectionService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "node_connection_channel"
        
        private var instance: NodeConnectionService? = null
        
        fun getInstance(): NodeConnectionService? = instance
        
        fun isServiceRunning(): Boolean = instance != null
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: HttpClient
    private lateinit var preferencesManager: PreferencesManager
    private var webSocketSession: WebSocketSession? = null
    private var isConnected = false
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        preferencesManager = PreferencesManager(this)
        
        client = HttpClient(CIO) {
            install(WebSockets)
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
        
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "CONNECT" -> {
                serviceScope.launch {
                    connectToGateway()
                }
            }
            "DISCONNECT" -> {
                disconnect()
                stopSelf()
            }
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        client.close()
        serviceScope.cancel()
        instance = null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Node Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "BillBot Node gateway connection"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private suspend fun connectToGateway() {
        try {
            val settings = preferencesManager.getSettings().first()
            val deviceInfo = CryptoUtils.generateDeviceIdentity(this)
            
            val wsUrl = if (settings.gatewayToken.isNotEmpty()) {
                "ws://${settings.gatewayHost}:${settings.gatewayPort}/?token=${settings.gatewayToken}"
            } else {
                "ws://${settings.gatewayHost}:${settings.gatewayPort}/"
            }
            
            Log.d(TAG, "Connecting to: $wsUrl")
            
            client.webSocket(wsUrl) {
                webSocketSession = this
                isConnected = true
                Log.d(TAG, "WebSocket connected")
                
                // Wait for challenge
                val challengeFrame = incoming.receive() as Frame.Text
                val challengeText = challengeFrame.readText()
                Log.d(TAG, "Received challenge: $challengeText")
                
                // Send connect request
                val connectRequest = buildConnectRequest(settings, deviceInfo, challengeText)
                val connectJson = json.encodeToString(connectRequest)
                send(connectJson)
                Log.d(TAG, "Sent connect request")
                
                // Handle messages
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val message = frame.readText()
                            handleMessage(message)
                        }
                        is Frame.Close -> {
                            Log.d(TAG, "WebSocket closed")
                            break
                        }
                        else -> {
                            // Handle other frame types if needed
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            isConnected = false
        }
    }
    
    private suspend fun buildConnectRequest(
        settings: com.billbot.node.utils.NodeSettings,
        deviceInfo: DeviceInfo,
        challengeText: String
    ): ConnectRequest {
        return ConnectRequest(
            id = UUID.randomUUID().toString(),
            params = ConnectParams(
                client = ClientInfo(
                    version = "1.0.0"
                ),
                caps = listOf("accessibility", "screen", "system"),
                commands = listOf(
                    "screenshot",
                    "ui_tree", 
                    "tap",
                    "swipe",
                    "type",
                    "press",
                    "launch"
                ),
                permissions = mapOf(
                    "accessibility" to BillBotAccessibilityService.isServiceEnabled(),
                    "screen.capture" to ScreenCaptureService.isServiceRunning()
                ),
                auth = if (settings.gatewayToken.isNotEmpty()) {
                    AuthInfo(token = settings.gatewayToken)
                } else null,
                userAgent = "billbot-android/1.0.0",
                device = deviceInfo
            )
        )
    }
    
    private suspend fun handleMessage(message: String) {
        try {
            Log.d(TAG, "Received message: $message")
            
            // Parse message to determine type
            val messageMap = json.parseToJsonElement(message).jsonObject
            val type = messageMap["type"]?.toString()?.removeSurrounding("\"")
            
            when (type) {
                "res" -> {
                    // Handle response to our connect request
                    val response = json.decodeFromString<ConnectResponse>(message)
                    if (response.ok) {
                        Log.d(TAG, "Connected successfully")
                        // Save device token if provided
                        response.payload?.auth?.let { auth ->
                            preferencesManager.saveDeviceInfo(
                                deviceId = "android-${Build.MODEL.replace(" ", "-").lowercase()}",
                                deviceToken = auth.deviceToken
                            )
                        }
                    } else {
                        Log.e(TAG, "Connection failed: ${response.error}")
                    }
                }
                "req" -> {
                    // Handle incoming command request
                    val request = json.decodeFromString<InvokeRequest>(message)
                    val response = handleCommandRequest(request)
                    val responseJson = json.encodeToString(response)
                    webSocketSession?.send(responseJson)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }
    
    private suspend fun handleCommandRequest(request: InvokeRequest): InvokeResponse {
        return try {
            val result = when (request.method) {
                "screenshot" -> {
                    val screenshotData = captureScreenshot()
                    if (screenshotData != null) {
                        mapOf("format" to "png", "base64" to screenshotData)
                    } else {
                        return InvokeResponse(
                            id = request.id,
                            ok = false,
                            error = "Failed to capture screenshot"
                        )
                    }
                }
                "ui_tree" -> {
                    val uiTree = getUITree()
                    mapOf("tree" to json.encodeToString(uiTree))
                }
                "tap" -> {
                    val x = request.params["x"]?.toFloatOrNull() ?: 0f
                    val y = request.params["y"]?.toFloatOrNull() ?: 0f
                    val success = performTap(x, y)
                    mapOf("success" to success.toString())
                }
                "swipe" -> {
                    val x1 = request.params["x1"]?.toFloatOrNull() ?: 0f
                    val y1 = request.params["y1"]?.toFloatOrNull() ?: 0f
                    val x2 = request.params["x2"]?.toFloatOrNull() ?: 0f
                    val y2 = request.params["y2"]?.toFloatOrNull() ?: 0f
                    val duration = request.params["durationMs"]?.toLongOrNull() ?: 500L
                    val success = performSwipe(x1, y1, x2, y2, duration)
                    mapOf("success" to success.toString())
                }
                "type" -> {
                    val text = request.params["text"] ?: ""
                    val success = performType(text)
                    mapOf("success" to success.toString())
                }
                "press" -> {
                    val key = request.params["key"] ?: ""
                    val success = performPress(key)
                    mapOf("success" to success.toString())
                }
                "launch" -> {
                    val packageName = request.params["package"] ?: ""
                    val success = launchApp(packageName)
                    mapOf("success" to success.toString())
                }
                else -> {
                    return InvokeResponse(
                        id = request.id,
                        ok = false,
                        error = "Unknown command: ${request.method}"
                    )
                }
            }
            
            InvokeResponse(
                id = request.id,
                ok = true,
                payload = result
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${request.method}", e)
            InvokeResponse(
                id = request.id,
                ok = false,
                error = "Command execution failed: ${e.message}"
            )
        }
    }
    
    private suspend fun captureScreenshot(): String? {
        return ScreenCaptureService.getInstance()?.captureScreenshot()
    }
    
    private fun getUITree(): List<UINode> {
        return BillBotAccessibilityService.getInstance()?.getUITree() ?: emptyList()
    }
    
    private suspend fun performTap(x: Float, y: Float): Boolean {
        return BillBotAccessibilityService.getInstance()?.performTap(x, y) ?: false
    }
    
    private suspend fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        return BillBotAccessibilityService.getInstance()?.performSwipe(x1, y1, x2, y2, duration) ?: false
    }
    
    private fun performType(text: String): Boolean {
        return BillBotAccessibilityService.getInstance()?.performType(text) ?: false
    }
    
    private fun performPress(key: String): Boolean {
        val accessibilityService = BillBotAccessibilityService.getInstance() ?: return false
        return when (key) {
            "back" -> accessibilityService.performBack()
            "home" -> accessibilityService.performHome()
            "recents" -> accessibilityService.performRecents()
            else -> false
        }
    }
    
    private fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: $packageName", e)
            false
        }
    }
    
    private fun disconnect() {
        isConnected = false
        serviceScope.launch {
            webSocketSession?.close()
            webSocketSession = null
        }
    }
    
    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("BillBot Node")
                .setContentText(if (isConnected) "Connected to gateway" else "Connecting to gateway")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("BillBot Node")
                .setContentText(if (isConnected) "Connected to gateway" else "Connecting to gateway")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .build()
        }
    }
}