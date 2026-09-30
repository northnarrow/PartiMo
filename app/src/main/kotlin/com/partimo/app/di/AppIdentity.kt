package com.partimo.app.di

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.partimo.app.BuildConfig
import com.partimo.data.config.AndroidAppIdentity
import com.partimo.data.network.AndroidAppHeaders
import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest

/**
 * User-Agent di PartiMo per i servizi pubblici senza chiave (API e foto di Wikipedia).
 *
 * Wikimedia chiede di identificare l'app con un contatto e rifiuta le foto richieste con lo
 * User-Agent generico di OkHttp (HTTP 403): senza questa intestazione le immagini reali dei luoghi
 * non si caricherebbero. https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy
 */
val PARTIMO_USER_AGENT: String = "PartiMo/${BuildConfig.VERSION_NAME} (https://github.com/northnarrow/partimo; Android)"

/**
 * Package e impronta SHA-1 del certificato con cui è firmata l'app, per le chiavi Google limitate alle
 * app Android; `null` se non si riesce a leggere il certificato.
 */
fun androidAppIdentity(context: Context): AndroidAppIdentity? {
    val signature = runCatching { signingCertificate(context) }.getOrNull() ?: return null
    return AndroidAppIdentity(packageName = context.packageName, certificateSha1 = certificateSha1(signature.toByteArray()))
}

/** Impronta SHA-1 nel formato atteso da Google: esadecimale maiuscolo senza separatori. */
fun certificateSha1(certificate: ByteArray): String =
    MessageDigest.getInstance("SHA-1").digest(certificate).joinToString(separator = "") { byte -> "%02X".format(byte) }

private fun signingCertificate(context: Context): Signature? {
    val packageManager = context.packageManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners?.firstOrNull()
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
    }
}

/**
 * Intestazioni del client OkHttp usato da Coil per le foto: lo User-Agent di PartiMo sempre e, verso
 * le API Google (foto di Places), package e certificato dell'app per le chiavi con restrizione Android.
 */
class AppIdentityInterceptor(
    private val userAgent: String = PARTIMO_USER_AGENT,
    private val androidApp: AndroidAppIdentity? = null,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder().header(USER_AGENT_HEADER, userAgent)
        if (androidApp != null && request.url.host.endsWith(GOOGLE_APIS_DOMAIN)) {
            builder.header(AndroidAppHeaders.PACKAGE, androidApp.packageName)
            builder.header(AndroidAppHeaders.CERTIFICATE, androidApp.certificateSha1)
        }
        return chain.proceed(builder.build())
    }

    private companion object {
        const val USER_AGENT_HEADER = "User-Agent"
        const val GOOGLE_APIS_DOMAIN = ".googleapis.com"
    }
}
