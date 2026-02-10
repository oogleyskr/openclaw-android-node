package com.billbot.node.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class NodeSettings(
    val gatewayHost: String = "",
    val gatewayPort: Int = 18789,
    val gatewayToken: String = "",
    val displayName: String = "Android Node",
    val deviceId: String = "",
    val deviceToken: String = ""
)

class PreferencesManager(private val context: Context) {
    
    companion object {
        val GATEWAY_HOST = stringPreferencesKey("gateway_host")
        val GATEWAY_PORT = intPreferencesKey("gateway_port")
        val GATEWAY_TOKEN = stringPreferencesKey("gateway_token")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_TOKEN = stringPreferencesKey("device_token")
    }
    
    fun getSettings(): Flow<NodeSettings> = context.dataStore.data.map { preferences ->
        NodeSettings(
            gatewayHost = preferences[GATEWAY_HOST] ?: "",
            gatewayPort = preferences[GATEWAY_PORT] ?: 18789,
            gatewayToken = preferences[GATEWAY_TOKEN] ?: "",
            displayName = preferences[DISPLAY_NAME] ?: "Android Node",
            deviceId = preferences[DEVICE_ID] ?: "",
            deviceToken = preferences[DEVICE_TOKEN] ?: ""
        )
    }
    
    suspend fun saveSettings(
        gatewayHost: String,
        gatewayPort: Int,
        gatewayToken: String,
        displayName: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[GATEWAY_HOST] = gatewayHost
            preferences[GATEWAY_PORT] = gatewayPort
            preferences[GATEWAY_TOKEN] = gatewayToken
            preferences[DISPLAY_NAME] = displayName
        }
    }
    
    suspend fun saveDeviceInfo(deviceId: String, deviceToken: String) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_ID] = deviceId
            preferences[DEVICE_TOKEN] = deviceToken
        }
    }
    
    suspend fun getDeviceId(): String {
        var deviceId = ""
        context.dataStore.data.collect { preferences ->
            deviceId = preferences[DEVICE_ID] ?: ""
        }
        return deviceId
    }
    
    suspend fun getDeviceToken(): String {
        var deviceToken = ""
        context.dataStore.data.collect { preferences ->
            deviceToken = preferences[DEVICE_TOKEN] ?: ""
        }
        return deviceToken
    }
}