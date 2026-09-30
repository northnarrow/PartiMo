package com.partimo.data.network

import com.partimo.data.config.AndroidAppIdentity
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header

/**
 * Intestazioni con cui le API Google (Places, Routes) verificano una chiave limitata alle app Android
 * quando la si usa via REST: senza, le richieste di una chiave con restrizione Android sono rifiutate.
 * https://cloud.google.com/docs/authentication/api-keys#adding-application-restrictions
 */
object AndroidAppHeaders {
    const val PACKAGE = "X-Android-Package"
    const val CERTIFICATE = "X-Android-Cert"
}

internal fun HttpRequestBuilder.androidAppHeaders(identity: AndroidAppIdentity?) {
    if (identity == null) return
    header(AndroidAppHeaders.PACKAGE, identity.packageName)
    header(AndroidAppHeaders.CERTIFICATE, identity.certificateSha1)
}
