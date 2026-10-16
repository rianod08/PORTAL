package com.venuelink.studio.capture

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.TimeUnit

class PortalApiError(val status: Int, message: String) : IllegalStateException(message)

class PortalAccount(context: Context) {
    private val prefs = context.getSharedPreferences("portal-account", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
    var baseUrl: String
        get() = PortalEndpoint.address(prefs.getString("baseUrl", "")!!, com.venuelink.studio.BuildConfig.PORTAL_BASE_URL)
        set(value) {
            val url = value.trim().trimEnd('/')
            val bundled = com.venuelink.studio.BuildConfig.PORTAL_BASE_URL
            require(bundled.isBlank() || url == bundled) { "This app uses the online Portal service." }
            val host = android.net.Uri.parse(url).host.orEmpty()
            val local = host == "localhost" || host == "127.0.0.1" || host.startsWith("192.168.") || host.startsWith("10.") || Regex("172\\.(1[6-9]|2[0-9]|3[01])\\..+").matches(host)
            require(url.startsWith("https://") || (com.venuelink.studio.BuildConfig.DEBUG && url.startsWith("http://") && local)) { "Use HTTPS, or a local network address in the debug build." }
            prefs.edit().putString("baseUrl", url).commit()
        }
    val ownerId: String get() = prefs.getString("ownerId", "")!!
    val signedIn: Boolean get() = ownerId.isNotBlank() && prefs.contains("tokens")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("portal-auth", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("portal-auth", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun tokens(): JSONObject {
        val bytes = Base64.decode(prefs.getString("tokens", "")!!, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8))
    }
    private fun store(data: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(JSONObject().put("access_token", data.getString("access_token")).put("refresh_token", data.getString("refresh_token")).toString().toByteArray())
        prefs.edit().putString("tokens", Base64.encodeToString(encrypted,Base64.NO_WRAP)).putString("ownerId",data.getJSONObject("user").getString("id")).commit()
    }
    private fun accessTokenExpiresSoon(accessToken: String): Boolean = runCatching {
        val payload = accessToken.split('.')[1]
        val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        JSONObject(decoded).getLong("exp") <= System.currentTimeMillis() / 1_000L + 90L
    }.getOrDefault(true)

    private fun refreshTokens() {
        val refreshToken = tokens().getString("refresh_token")
        val data = JSONObject(call("auth", JSONObject().put("action", "refresh").put("refresh_token", refreshToken), false, false))
        store(data)
    }
    fun authenticate(email: String, password: String, signup: Boolean = false): String {
        val data = JSONObject(call("auth", JSONObject().put("action",if(signup) "signup" else "login").put("email",email).put("password",password).put("native",true), authenticated=false))
        if(data.has("access_token")) { store(data); return "Signed in" }
        return "Check your email to confirm your account, then sign in."
    }
    @Synchronized fun call(path: String, body: JSONObject? = null, authenticated: Boolean = true, retry: Boolean = true, method: String = if(body==null) "GET" else "POST"): String {
        require(baseUrl.isNotBlank()) { "Set your Portal website address first." }
        if (authenticated && retry && accessTokenExpiresSoon(tokens().getString("access_token"))) refreshTokens()
        val builder = Request.Builder().url("$baseUrl/api/v1/$path")
        if(authenticated) builder.header("Authorization", "Bearer ${tokens().getString("access_token")}")
        builder.method(method,body?.toString()?.toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val expiredJwt = response.code == 403 && text.contains("token is expired", ignoreCase = true)
            if((response.code==401 || expiredJwt) && authenticated && retry) {
                refreshTokens()
                return call(path,body,true,false,method)
            }
            if(!response.isSuccessful) throw PortalApiError(response.code,runCatching{JSONObject(text).optString("error")}.getOrDefault("Portal request failed (${response.code})."))
            return text
        }
    }
    fun signOut() { prefs.edit().remove("tokens").remove("ownerId").commit() }
}
