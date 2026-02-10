package com.openclaw.node.models

import kotlinx.serialization.Serializable

@Serializable
data class ConnectRequest(
    val type: String = "req",
    val id: String,
    val method: String = "connect",
    val params: ConnectParams
)

@Serializable
data class ConnectParams(
    val minProtocol: Int = 3,
    val maxProtocol: Int = 3,
    val client: ClientInfo,
    val role: String = "node",
    val scopes: List<String> = emptyList(),
    val caps: List<String>,
    val commands: List<String>,
    val permissions: Map<String, Boolean>,
    val auth: AuthInfo? = null,
    val locale: String = "en-US",
    val userAgent: String,
    val device: DeviceInfo
)

@Serializable
data class ClientInfo(
    val id: String = "openclaw-android",
    val version: String,
    val platform: String = "android",
    val mode: String = "node"
)

@Serializable
data class AuthInfo(
    val token: String? = null
)

@Serializable
data class DeviceInfo(
    val id: String,
    val publicKey: String,
    val signature: String,
    val signedAt: Long,
    val nonce: String
)

@Serializable
data class ConnectResponse(
    val type: String,
    val id: String,
    val ok: Boolean,
    val payload: ConnectPayload? = null,
    val error: String? = null
)

@Serializable
data class ConnectPayload(
    val type: String,
    val protocol: Int,
    val policy: Map<String, Long> = emptyMap(),
    val auth: AuthResponseInfo? = null
)

@Serializable
data class AuthResponseInfo(
    val deviceToken: String,
    val role: String,
    val scopes: List<String>
)

@Serializable
data class InvokeRequest(
    val type: String = "req",
    val id: String,
    val method: String,
    val params: Map<String, String>
)

@Serializable
data class InvokeResponse(
    val type: String = "res",
    val id: String,
    val ok: Boolean,
    val payload: Map<String, String>? = null,
    val error: String? = null
)

@Serializable
data class CommandRequest(
    val command: String,
    val params: Map<String, String> = emptyMap()
)

// Node Commands
@Serializable
data class TapCommand(
    val x: Float,
    val y: Float
)

@Serializable
data class SwipeCommand(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val durationMs: Long = 500
)

@Serializable
data class TypeCommand(
    val text: String
)

@Serializable
data class PressCommand(
    val key: String // "back", "home", "recents"
)

@Serializable
data class LaunchCommand(
    val packageName: String
)

@Serializable
data class ScreenshotResponse(
    val format: String = "png",
    val base64: String
)

@Serializable
data class UITreeResponse(
    val nodes: List<UINode>
)

@Serializable
data class UINode(
    val id: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val bounds: Bounds,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val selected: Boolean = false,
    val children: List<UINode> = emptyList()
)

@Serializable
data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)