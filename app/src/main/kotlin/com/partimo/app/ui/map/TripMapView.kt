package com.partimo.app.ui.map

import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.partimo.app.R
import com.partimo.domain.model.GeoPoint
import com.partimo.domain.model.saved.FavoriteKind
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Cosa disegna la mappa: i punti visibili, i preferiti, il punto toccato e la richiesta di inquadratura. */
data class MapContent(
    val center: GeoPoint,
    val points: List<MapPoint>,
    val favoriteKeys: Set<String> = emptySet(),
    val selectedKey: String? = null,
    val fitRequest: Int = 0,
    val onPointSelected: (String?) -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
)

/** Colore dei punti di ogni tipo, lo stesso sulla mappa e nei filtri che fanno da legenda. */
fun FavoriteKind.pinColor(): Color = when (this) {
    FavoriteKind.PLACE -> Color(0xFF1E88E5)
    FavoriteKind.EVENT -> Color(0xFFE53935)
    FavoriteKind.RESTAURANT -> Color(0xFFF57C00)
    FavoriteKind.LODGING -> Color(0xFF8E24AA)
}

/** Bordo dei punti salvati tra i preferiti: lo stesso giallo delle stelle. */
private val FavoriteStroke = Color(0xFFF2A900)

/** Pagina sui diritti dei dati di OpenStreetMap, aperta dall'attribuzione sulla mappa. */
const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

/** Tag dello schema dei punti mostrato quando la mappa vettoriale non è disponibile. */
const val SCHEMATIC_MAP_TAG = "schematic_map"

/**
 * Mappa del viaggio: mappa vettoriale MapLibre con le mappe gratuite di OpenFreeMap (dati
 * OpenStreetMap, nessuna chiave). Dove la libreria nativa non si può caricare (processori non
 * supportati, test sulla JVM) mostra uno schema semplificato degli stessi punti.
 */
@Composable
fun TripMap(content: MapContent, modifier: Modifier = Modifier) {
    if (MapLibreSupport.isAvailable) MapLibreTripMap(content, modifier) else SchematicTripMap(content, modifier)
}

internal object MapLibreSupport {
    /** `true` se la libreria nativa di MapLibre si carica su questo dispositivo. */
    val isAvailable: Boolean by lazy {
        runCatching { System.loadLibrary("maplibre") }
            .onFailure { Log.w(TAG, "Mappa vettoriale non disponibile su questo dispositivo", it) }
            .isSuccess
    }

    private const val TAG = "TripMap"
}

private const val LIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
private const val SOURCE_ID = "partimo-points"
private const val CIRCLE_LAYER_ID = "partimo-points-circles"
private const val LABEL_LAYER_ID = "partimo-points-labels"
private const val PROPERTY_KEY = "key"
private const val PROPERTY_KIND = "kind"
private const val PROPERTY_NAME = "name"
private const val PROPERTY_FAVORITE = "favorite"
private const val PROPERTY_SELECTED = "selected"

/** Font disponibile negli stili di OpenFreeMap per le etichette dei punti. */
private const val LABEL_FONT = "Noto Sans Regular"
private const val INITIAL_ZOOM = 13.0
private const val SINGLE_POINT_ZOOM = 15.0
private const val MAX_ZOOM = 19.0
private const val LABEL_MIN_ZOOM = 12.5f

/** Sotto questa ampiezza (in gradi, circa 200 m) i punti si mostrano come uno solo. */
private const val MIN_BOUNDS_SPAN = 0.002
private const val CAMERA_ANIMATION_MS = 600

@Composable
private fun MapLibreTripMap(content: MapContent, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val darkMap = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val labelColor = if (darkMap) Color.White else Color(0xFF1F2933)
    val haloColor = if (darkMap) Color(0xFF1F2933) else Color.White
    val currentContent by rememberUpdatedState(content)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    // Inquadratura (latitudine, longitudine, zoom) e ultima richiesta di "inquadra tutto" già eseguita:
    // sopravvivono alla rotazione e al ritorno dalla scheda di un luogo, così la mappa resta dov'era.
    var savedCamera by rememberSaveable { mutableStateOf<DoubleArray?>(null) }
    var handledFitRequest by rememberSaveable { mutableIntStateOf(0) }

    val mapView = remember {
        MapLibre.getInstance(context)
        val camera = savedCamera?.let { (latitude, longitude, zoom) -> CameraPosition.Builder().target(LatLng(latitude, longitude)).zoom(zoom).build() }
            ?: CameraPosition.Builder().target(content.center.toLatLng()).zoom(INITIAL_ZOOM).build()
        val options = MapLibreMapOptions.createFromAttributes(context)
            .camera(camera)
            .maxZoomPreference(MAX_ZOOM)
            .attributionEnabled(false)
            .logoEnabled(false)
            .compassEnabled(false)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            // TextureView: la mappa segue le animazioni e il ritaglio di Compose come le altre viste.
            .textureMode(true)
        MapView(context, options).apply { onCreate(null) }
    }

    // La MapView segue il ciclo di vita della schermata e viene distrutta quando esce dalla composizione.
    DisposableEffect(lifecycle, mapView) {
        var lastEvent: Lifecycle.Event? = null
        val observer = LifecycleEventObserver { _, event ->
            lastEvent = event
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            when (lastEvent) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onPause()
                    mapView.onStop()
                }
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_PAUSE -> mapView.onStop()
                else -> Unit
            }
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { mapLibreMap ->
            mapLibreMap.addOnCameraIdleListener {
                val position = mapLibreMap.cameraPosition
                position.target?.let { target -> savedCamera = doubleArrayOf(target.latitude, target.longitude, position.zoom) }
            }
            mapLibreMap.addOnMapClickListener { tap ->
                val point = mapLibreMap.projection.toScreenLocation(tap)
                val radius = with(density) { 22.dp.toPx() }
                val hits = mapLibreMap.queryRenderedFeatures(
                    RectF(point.x - radius, point.y - radius, point.x + radius, point.y + radius),
                    CIRCLE_LAYER_ID,
                )
                currentContent.onPointSelected(hits.firstOrNull()?.getStringProperty(PROPERTY_KEY))
                true
            }
            val styleBuilder = Style.Builder()
                .fromUri(if (darkMap) DARK_STYLE_URL else LIGHT_STYLE_URL)
                .withSource(GeoJsonSource(SOURCE_ID))
                .withLayers(circleLayer(), labelLayer(labelColor, haloColor))
            mapLibreMap.setStyle(styleBuilder) { loaded -> style = loaded }
            map = mapLibreMap
        }
    }

    // Punti, preferiti e selezione: la sorgente GeoJSON si aggiorna a ogni cambiamento.
    LaunchedEffect(style, content.points, content.favoriteKeys, content.selectedKey) {
        style?.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(featureCollection(content))
    }

    // A fine caricamento e con "Inquadra tutto" la mappa mostra tutti i punti visibili (una volta per richiesta).
    LaunchedEffect(map, content.fitRequest) {
        val mapLibreMap = map ?: return@LaunchedEffect
        if (content.fitRequest > handledFitRequest) {
            handledFitRequest = content.fitRequest
            val padding = with(density) { 48.dp.roundToPx() }
            mapView.post {
                if (mapView.isAttachedToWindow) fitCamera(mapLibreMap, currentContent.points, currentContent.center, padding)
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        // Attribuzione richiesta da OpenFreeMap, OpenMapTiles e OpenStreetMap, sempre visibile.
        Text(
            text = stringResource(R.string.map_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                .clickable { currentContent.onOpenLink(OSM_COPYRIGHT_URL) }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

/** Il punto selezionato va per ultimo, così è disegnato sopra gli altri. */
private fun featureCollection(content: MapContent): FeatureCollection = FeatureCollection.fromFeatures(
    content.points.sortedBy { it.key == content.selectedKey }.map { point ->
        Feature.fromGeometry(Point.fromLngLat(point.location.longitude, point.location.latitude)).apply {
            addStringProperty(PROPERTY_KEY, point.key)
            addStringProperty(PROPERTY_KIND, point.kind.name)
            addStringProperty(PROPERTY_NAME, point.item.name)
            addBooleanProperty(PROPERTY_FAVORITE, point.key in content.favoriteKeys)
            addBooleanProperty(PROPERTY_SELECTED, point.key == content.selectedKey)
        }
    },
)

private fun circleLayer(): CircleLayer = CircleLayer(CIRCLE_LAYER_ID, SOURCE_ID).withProperties(
    PropertyFactory.circleColor(
        Expression.match(
            Expression.get(PROPERTY_KIND),
            Expression.color(Color.Gray.toArgb()),
            *FavoriteKind.entries.map { Expression.stop(it.name, Expression.color(it.pinColor().toArgb())) }.toTypedArray(),
        ),
    ),
    PropertyFactory.circleRadius(
        Expression.switchCase(
            Expression.eq(Expression.get(PROPERTY_SELECTED), true), Expression.literal(11f),
            Expression.eq(Expression.get(PROPERTY_FAVORITE), true), Expression.literal(8.5f),
            Expression.literal(7f),
        ),
    ),
    PropertyFactory.circleStrokeColor(
        Expression.switchCase(
            Expression.eq(Expression.get(PROPERTY_FAVORITE), true), Expression.color(FavoriteStroke.toArgb()),
            Expression.color(Color.White.toArgb()),
        ),
    ),
    PropertyFactory.circleStrokeWidth(
        Expression.switchCase(
            Expression.eq(Expression.get(PROPERTY_SELECTED), true), Expression.literal(3f),
            Expression.literal(2f),
        ),
    ),
)

/** Nomi sotto i punti, dallo zoom di quartiere in su; quelli che si sovrapporrebbero vengono omessi. */
private fun labelLayer(textColor: Color, haloColor: Color): SymbolLayer = SymbolLayer(LABEL_LAYER_ID, SOURCE_ID)
    .withProperties(
        PropertyFactory.textField(Expression.get(PROPERTY_NAME)),
        PropertyFactory.textFont(arrayOf(LABEL_FONT)),
        PropertyFactory.textSize(12f),
        PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
        PropertyFactory.textOffset(arrayOf(0f, 1f)),
        PropertyFactory.textMaxWidth(9f),
        PropertyFactory.textColor(textColor.toArgb()),
        PropertyFactory.textHaloColor(haloColor.toArgb()),
        PropertyFactory.textHaloWidth(1.5f),
    )
    .apply { minZoom = LABEL_MIN_ZOOM }

private fun fitCamera(map: MapLibreMap, points: List<MapPoint>, center: GeoPoint, paddingPx: Int) {
    val locations = points.map { it.location.toLatLng() }
    val update = when (locations.size) {
        0 -> CameraUpdateFactory.newLatLngZoom(center.toLatLng(), INITIAL_ZOOM)
        1 -> CameraUpdateFactory.newLatLngZoom(locations.single(), SINGLE_POINT_ZOOM)
        else -> {
            val bounds = LatLngBounds.Builder().includes(locations).build()
            if (bounds.latitudeSpan < MIN_BOUNDS_SPAN && bounds.longitudeSpan < MIN_BOUNDS_SPAN) {
                CameraUpdateFactory.newLatLngZoom(bounds.center, SINGLE_POINT_ZOOM)
            } else {
                CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)
            }
        }
    }
    map.easeCamera(update, CAMERA_ANIMATION_MS)
}

/**
 * Schema dei punti, senza mappa di sfondo: posizioni relative (proiezione equirettangolare),
 * colori dei tipi e tocco sui punti come sulla mappa vera.
 */
@Composable
private fun SchematicTripMap(content: MapContent, modifier: Modifier) {
    val currentContent by rememberUpdatedState(content)
    val background = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val outline = MaterialTheme.colorScheme.surface
    Box(modifier = modifier.background(background)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag(SCHEMATIC_MAP_TAG)
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val projection = SchematicProjection.of(currentContent, size.width.toFloat(), size.height.toFloat(), 32.dp.toPx())
                        val nearest = currentContent.points
                            .map { it to projection.offsetOf(it.location) }
                            .minByOrNull { (_, offset) -> hypot(offset.x - tap.x, offset.y - tap.y) }
                            ?.takeIf { (_, offset) -> hypot(offset.x - tap.x, offset.y - tap.y) <= 24.dp.toPx() }
                        currentContent.onPointSelected(nearest?.first?.key)
                    }
                },
        ) {
            val step = 48.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
                y += step
            }
            val projection = SchematicProjection.of(content, size.width, size.height, 32.dp.toPx())
            content.points.sortedBy { it.key == content.selectedKey }.forEach { point ->
                val offset = projection.offsetOf(point.location)
                val selected = point.key == content.selectedKey
                val favorite = point.key in content.favoriteKeys
                val radius = (if (selected) 11f else if (favorite) 8.5f else 7f).dp.toPx()
                drawCircle(point.kind.pinColor(), radius, offset)
                drawCircle(
                    color = if (favorite) FavoriteStroke else outline,
                    radius = radius,
                    center = offset,
                    style = Stroke(width = (if (selected) 3 else 2).dp.toPx()),
                )
            }
        }
        Text(
            text = stringResource(R.string.map_schematic_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // A destra resta lo spazio del pulsante "Inquadra tutto".
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 72.dp, bottom = 8.dp),
        )
    }
}

/** Proiezione equirettangolare dei punti (o del centro, se non ce ne sono) nell'area disponibile. */
private class SchematicProjection(
    private val minLatitude: Double,
    private val minLongitude: Double,
    private val longitudeScale: Double,
    private val scale: Double,
    private val offsetX: Double,
    private val offsetY: Double,
    private val height: Float,
) {
    fun offsetOf(point: GeoPoint): Offset = Offset(
        x = (offsetX + (point.longitude - minLongitude) * longitudeScale * scale).toFloat(),
        y = (height - offsetY - (point.latitude - minLatitude) * scale).toFloat(),
    )

    companion object {
        fun of(content: MapContent, width: Float, height: Float, padding: Float): SchematicProjection {
            val locations = content.points.map { it.location }.ifEmpty { listOf(content.center) }
            val minLatitude = locations.minOf { it.latitude }
            val maxLatitude = locations.maxOf { it.latitude }
            val minLongitude = locations.minOf { it.longitude }
            val maxLongitude = locations.maxOf { it.longitude }
            val longitudeScale = cos(Math.toRadians((minLatitude + maxLatitude) / 2))
            val spanX = max((maxLongitude - minLongitude) * longitudeScale, MIN_BOUNDS_SPAN)
            val spanY = max(maxLatitude - minLatitude, MIN_BOUNDS_SPAN)
            val usableWidth = max(width - 2 * padding, 1f)
            val usableHeight = max(height - 2 * padding, 1f)
            val scale = min(usableWidth / spanX, usableHeight / spanY)
            return SchematicProjection(
                minLatitude = minLatitude,
                minLongitude = minLongitude,
                longitudeScale = longitudeScale,
                scale = scale,
                offsetX = padding + (usableWidth - (maxLongitude - minLongitude) * longitudeScale * scale) / 2,
                offsetY = padding + (usableHeight - (maxLatitude - minLatitude) * scale) / 2,
                height = height,
            )
        }
    }
}
