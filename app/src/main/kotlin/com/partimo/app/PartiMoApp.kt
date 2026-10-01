package com.partimo.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.partimo.app.di.AppContainer
import com.partimo.app.di.AppIdentityInterceptor
import com.partimo.app.di.DefaultAppContainer
import com.partimo.app.di.androidAppIdentity
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

class PartiMoApp : Application(), SingletonImageLoader.Factory {

    /** Contenitore delle dipendenze condiviso da tutta l'app (DI manuale). */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this).also { it.onAppStart() }
    }

    /**
     * ImageLoader globale di Coil, usato da tutte le AsyncImage. Le foto di Wikipedia richiedono uno
     * User-Agent identificabile (altrimenti HTTP 403) e al massimo poche richieste parallele per host;
     * le foto di Google Places le intestazioni dell'app, se la chiave è limitata alle app Android.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient() }))
            }
            .crossfade(true)
            .build()

    private fun imageHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AppIdentityInterceptor(androidApp = androidAppIdentity(this)))
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_IMAGE_REQUESTS_PER_HOST })
        .build()

    private companion object {
        /** Wikimedia chiede di non superare poche richieste contemporanee. */
        const val MAX_IMAGE_REQUESTS_PER_HOST = 3
    }
}
