package com.openclaw.node.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.openclaw.node.models.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

object CryptoUtils {
    
    init {
        Security.addProvider(BouncyCastleProvider())
    }
    
    private const val KEY_ALGORITHM = "RSA"
    private const val KEY_SIZE = 2048
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    
    suspend fun generateDeviceIdentity(context: Context): DeviceInfo = withContext(Dispatchers.IO) {
        val preferencesManager = PreferencesManager(context)
        
        // Generate or retrieve device ID
        val deviceId = getOrCreateDeviceId(context, preferencesManager)
        
        // Generate or retrieve keypair
        val keyPair = getOrCreateKeyPair(context)
        val publicKeyString = encodePublicKey(keyPair.public as RSAPublicKey)
        
        // Create nonce for challenge-response
        val nonce = generateNonce()
        val timestamp = System.currentTimeMillis()
        
        // Create signature
        val dataToSign = "$deviceId:$nonce:$timestamp"
        val signature = signData(dataToSign, keyPair.private)
        
        DeviceInfo(
            id = deviceId,
            publicKey = publicKeyString,
            signature = signature,
            signedAt = timestamp,
            nonce = nonce
        )
    }
    
    private suspend fun getOrCreateDeviceId(context: Context, preferencesManager: PreferencesManager): String {
        var deviceId = preferencesManager.getDeviceId()
        if (deviceId.isEmpty()) {
            // Generate a device ID based on device characteristics
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val deviceModel = Build.MODEL.replace(" ", "-").lowercase()
            val manufacturer = Build.MANUFACTURER.replace(" ", "-").lowercase()
            
            deviceId = "android-$manufacturer-$deviceModel-${androidId.take(8)}"
            preferencesManager.saveDeviceInfo(deviceId, "")
        }
        return deviceId
    }
    
    private fun getOrCreateKeyPair(context: Context): KeyPair {
        val sharedPrefs = context.getSharedPreferences("openclaw_crypto", Context.MODE_PRIVATE)
        
        // Try to load existing keypair
        val privateKeyString = sharedPrefs.getString("private_key", null)
        val publicKeyString = sharedPrefs.getString("public_key", null)
        
        if (privateKeyString != null && publicKeyString != null) {
            try {
                val privateKey = decodePrivateKey(privateKeyString)
                val publicKey = decodePublicKey(publicKeyString)
                return KeyPair(publicKey, privateKey)
            } catch (e: Exception) {
                // If loading fails, generate new keypair
            }
        }
        
        // Generate new keypair
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        keyPairGenerator.initialize(KEY_SIZE)
        val keyPair = keyPairGenerator.generateKeyPair()
        
        // Save the keypair
        val encodedPrivateKey = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        val encodedPublicKey = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        
        sharedPrefs.edit()
            .putString("private_key", encodedPrivateKey)
            .putString("public_key", encodedPublicKey)
            .apply()
        
        return keyPair
    }
    
    private fun encodePublicKey(publicKey: RSAPublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }
    
    private fun decodePrivateKey(encodedKey: String): PrivateKey {
        val keyBytes = Base64.decode(encodedKey, Base64.NO_WRAP)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        return keyFactory.generatePrivate(keySpec)
    }
    
    private fun decodePublicKey(encodedKey: String): PublicKey {
        val keyBytes = Base64.decode(encodedKey, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        return keyFactory.generatePublic(keySpec)
    }
    
    private fun signData(data: String, privateKey: PrivateKey): String {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey)
        signature.update(data.toByteArray())
        val signatureBytes = signature.sign()
        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }
    
    private fun generateNonce(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    
    fun verifySignature(data: String, signature: String, publicKey: PublicKey): Boolean {
        return try {
            val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
            sig.initVerify(publicKey)
            sig.update(data.toByteArray())
            val signatureBytes = Base64.decode(signature, Base64.NO_WRAP)
            sig.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
}