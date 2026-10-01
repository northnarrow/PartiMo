package com.partimo.app.testing

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.serviceLoaderEnabled

/**
 * Applicazione dei test UI con Robolectric: le immagini (foto dei luoghi, loghi delle compagnie) non si
 * scaricano. Un'immagine decodificata su un thread di Coil mentre il test si chiude fa cadere la JVM dei test
 * con la grafica nativa (SIGABRT in JniConstants); le schermate si provano bene anche senza, e i test non
 * dipendono dalla rete.
 */
class UiTestApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        // Senza i componenti trovati con il ServiceLoader manca il fetcher di rete: gli URL http falliscono subito.
        ImageLoader.Builder(context).serviceLoaderEnabled(false).build()
}
