# BillBot Android Node

An Android app that enables LLM control of Android devices through the BillBot gateway. This node connects to a BillBot gateway via WebSocket and exposes device capabilities like screen capture, UI interaction, and app launching.

## Features

- **Accessibility Service**: Read UI hierarchy and inject touch events
- **Screen Capture**: Take screenshots using MediaProjection API
- **WebSocket Connection**: Connect to BillBot gateway as a node
- **Device Control**: Support for tap, swipe, type, press, and launch commands
- **Secure Authentication**: RSA key-based device identity and signing

## Supported Commands

| Command | Description | Parameters |
|---------|-------------|------------|
| `screenshot` | Capture screen as base64 PNG | None |
| `ui_tree` | Get accessibility node hierarchy | None |
| `tap(x, y)` | Perform tap at coordinates | x, y (float) |
| `swipe(x1, y1, x2, y2, duration)` | Perform swipe gesture | x1, y1, x2, y2 (float), durationMs (long) |
| `type(text)` | Type text into focused input | text (string) |
| `press(key)` | Press system keys | key: "back", "home", "recents" |
| `launch(package)` | Launch app by package name | packageName (string) |

## Requirements

- Android 8.0 (API level 26) or higher
- BillBot Gateway running on accessible network
- Device permissions for:
  - Accessibility Service (for UI control)
  - Screen Capture (for screenshots)
  - System Alert Window (for overlay permissions)

## Setup Instructions

### 1. Build and Install

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Configure Gateway Connection

1. Open the BillBot Node app
2. Enter your Gateway host (IP address)
3. Enter Gateway port (default: 18789)
4. Enter Gateway token if authentication is required
5. Set a display name for this node
6. Tap "Save Settings"

### 3. Grant Required Permissions

#### Accessibility Service
1. Tap "Enable Accessibility Service"
2. Find "BillBot Node" in the accessibility services list
3. Enable the service
4. Accept the permission dialog

#### Screen Capture
1. Tap "Grant Screen Capture Permission"
2. Accept the media projection permission dialog
3. The screen capture service will start in the background

### 4. Connect to Gateway

1. Tap "Connect" to establish WebSocket connection
2. Wait for "Connected" status
3. Check BillBot gateway logs for node pairing request
4. Approve the node from gateway CLI: `billbot nodes approve <requestId>`

### 5. Verify Connection

From your BillBot gateway, verify the node is connected:

```bash
billbot nodes status
billbot nodes describe --node "Android Node"
```

## Development

### Tech Stack

- **Kotlin** - Primary language
- **Jetpack Compose** - Modern UI toolkit
- **Ktor Client** - WebSocket communication
- **Kotlinx Serialization** - JSON parsing
- **BouncyCastle** - Cryptographic operations
- **DataStore** - Settings persistence

### Project Structure

```
app/src/main/java/com/billbot/node/
├── MainActivity.kt                 # Main UI activity
├── models/                         # Data models
│   └── Protocol.kt                 # Protocol models
├── services/                       # Background services
│   ├── BillBotAccessibilityService.kt   # UI interaction
│   ├── ScreenCaptureService.kt          # Screen capture
│   └── NodeConnectionService.kt         # WebSocket connection
├── ui/                            # UI components
│   ├── MainViewModel.kt           # UI state management
│   └── theme/                     # Material Design theme
└── utils/                         # Utility classes
    ├── PreferencesManager.kt      # Settings storage
    └── CryptoUtils.kt            # Device identity & signing
```

### Protocol

The node implements the WebSocket protocol v3:

1. **Handshake**: Connect with device identity and capabilities
2. **Authentication**: RSA signature-based device verification
3. **Command Processing**: Handle incoming RPC requests
4. **Response**: Return command results or errors

## Security

- Device identity generated from stable hardware characteristics
- RSA-2048 keypair for signing and verification
- Private keys stored in encrypted SharedPreferences
- Challenge-response authentication with gateway
- No sensitive data in backups (excluded via backup rules)

## Troubleshooting

### Connection Issues

1. **WebSocket connection failed**
   - Verify gateway host/port are correct
   - Check network connectivity
   - Ensure gateway is running and accessible
   - Check gateway logs for connection attempts

2. **Permission denied errors**
   - Verify accessibility service is enabled
   - Check screen capture permission was granted
   - Ensure app has overlay permission if needed

3. **Commands not working**
   - Check accessibility service is still enabled
   - Verify screen capture service is running
   - Review app logs for error messages
   - Test basic connectivity with gateway

### Debugging

Enable debug logging and check Android Studio Logcat:

```bash
adb logcat | grep -E "(BillBot|NodeConnection|ScreenCapture|AccessibilityService)"
```
