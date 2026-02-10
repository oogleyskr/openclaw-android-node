package com.billbot.node.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billbot.node.services.NodeConnectionService
import com.billbot.node.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val gatewayHost: String = "",
    val gatewayPort: String = "18789",
    val gatewayToken: String = "",
    val displayName: String = "Android Node",
    val connectionStatus: String = "Disconnected",
    val lastError: String = ""
)

class MainViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    private lateinit var preferencesManager: PreferencesManager
    
    fun initialize(context: Context) {
        preferencesManager = PreferencesManager(context)
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            preferencesManager.getSettings().collect { settings ->
                _uiState.value = _uiState.value.copy(
                    gatewayHost = settings.gatewayHost,
                    gatewayPort = settings.gatewayPort.toString(),
                    gatewayToken = settings.gatewayToken,
                    displayName = settings.displayName
                )
            }
        }
    }
    
    fun updateGatewayHost(host: String) {
        _uiState.value = _uiState.value.copy(gatewayHost = host)
    }
    
    fun updateGatewayPort(port: String) {
        _uiState.value = _uiState.value.copy(gatewayPort = port)
    }
    
    fun updateGatewayToken(token: String) {
        _uiState.value = _uiState.value.copy(gatewayToken = token)
    }
    
    fun updateDisplayName(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name)
    }
    
    fun connect(context: Context) {
        val intent = Intent(context, NodeConnectionService::class.java)
        intent.action = "CONNECT"
        context.startForegroundService(intent)
        _uiState.value = _uiState.value.copy(connectionStatus = "Connecting")
    }
    
    fun disconnect() {
        _uiState.value = _uiState.value.copy(connectionStatus = "Disconnected")
    }
    
    fun saveSettings(context: Context) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val port = currentState.gatewayPort.toIntOrNull() ?: 18789
            
            preferencesManager.saveSettings(
                gatewayHost = currentState.gatewayHost,
                gatewayPort = port,
                gatewayToken = currentState.gatewayToken,
                displayName = currentState.displayName
            )
        }
    }
    
    fun updateConnectionStatus(status: String) {
        _uiState.value = _uiState.value.copy(connectionStatus = status)
    }
    
    fun updateError(error: String) {
        _uiState.value = _uiState.value.copy(lastError = error)
    }
}