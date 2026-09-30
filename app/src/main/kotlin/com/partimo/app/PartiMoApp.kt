package com.partimo.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.partimo.app.di.AppContainer
import com.partimo.app.di.DefaultAppContainer

class PartiMoApp : Application(), SingletonImageLoader.Factory {

    /** Contenitore delle dipendenze condiviso da tutta l'app (DI manuale). */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this).also { it.onAppStart() }
    }

    /** ImageLoader globale di Coil, usato da tutte le AsyncImage. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .build()
}
