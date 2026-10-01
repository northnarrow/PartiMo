package com.partimo.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.partimo.app.R
import com.partimo.domain.common.DataError
import com.partimo.domain.common.QueryIssue
import com.partimo.domain.model.place.TravelTheme
import com.partimo.domain.model.poi.PoiCategory
import com.partimo.domain.model.poi.PoiTag
import com.partimo.domain.model.poi.RecommendationReason
import com.partimo.domain.model.poi.Season
import com.partimo.domain.model.transit.TransitMode
import com.partimo.domain.model.weather.WeatherCondition

// Traduzione dei concetti di dominio in testi localizzati: il dominio non conosce le risorse Android.

@Composable
fun errorMessage(error: DataError): String = when (error) {
    is DataError.Server -> stringResource(R.string.error_server, error.httpCode)
    is DataError.Client -> stringResource(R.string.error_client, error.httpCode)
    else -> stringResource(error.messageRes())
}

@StringRes
fun DataError.messageRes(): Int = when (this) {
    DataError.NoConnection -> R.string.error_no_connection
    DataError.Timeout -> R.string.error_timeout
    DataError.Unauthorized -> R.string.error_unauthorized
    DataError.RateLimited -> R.string.error_rate_limited
    is DataError.Server -> R.string.error_server
    is DataError.Client -> R.string.error_client
    DataError.InvalidResponse -> R.string.error_invalid_response
    is DataError.InvalidQuery -> issue.messageRes()
    is DataError.Unknown -> R.string.error_unknown
}

@StringRes
private fun QueryIssue.messageRes(): Int = when (this) {
    QueryIssue.INVALID_AIRPORT_CODE -> R.string.error_query_airport
    QueryIssue.SAME_ORIGIN_AND_DESTINATION -> R.string.error_query_same_place
    QueryIssue.DATE_IN_THE_PAST -> R.string.error_query_past_date
    QueryIssue.RETURN_BEFORE_DEPARTURE -> R.string.error_query_return_before_departure
    QueryIssue.INVALID_TRAVELLER_COUNT -> R.string.error_query_travellers
    QueryIssue.INVALID_STAY_DATES -> R.string.error_query_stay_dates
    QueryIssue.STAY_TOO_LONG -> R.string.error_query_stay_too_long
    QueryIssue.QUERY_TOO_SHORT -> R.string.error_query_too_short
    QueryIssue.NO_AIRPORT_NEARBY -> R.string.error_query_no_airport
    QueryIssue.TEXT_TOO_LONG -> R.string.error_query_text_too_long
}

@StringRes
fun Season.labelRes(): Int = when (this) {
    Season.WINTER -> R.string.season_winter
    Season.SPRING -> R.string.season_spring
    Season.SUMMER -> R.string.season_summer
    Season.AUTUMN -> R.string.season_autumn
}

fun Season.emoji(): String = when (this) {
    Season.WINTER -> "❄️"
    Season.SPRING -> "🌸"
    Season.SUMMER -> "☀️"
    Season.AUTUMN -> "🍂"
}

@StringRes
fun WeatherCondition.labelRes(): Int = when (this) {
    WeatherCondition.CLEAR -> R.string.weather_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherCondition.OVERCAST -> R.string.weather_overcast
    WeatherCondition.FOG -> R.string.weather_fog
    WeatherCondition.DRIZZLE -> R.string.weather_drizzle
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.SNOW -> R.string.weather_snow
    WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    WeatherCondition.UNKNOWN -> R.string.weather_unknown
}

fun WeatherCondition.emoji(): String = when (this) {
    WeatherCondition.CLEAR -> "☀️"
    WeatherCondition.PARTLY_CLOUDY -> "⛅"
    WeatherCondition.OVERCAST -> "☁️"
    WeatherCondition.FOG -> "🌫️"
    WeatherCondition.DRIZZLE -> "🌦️"
    WeatherCondition.RAIN -> "🌧️"
    WeatherCondition.SNOW -> "🌨️"
    WeatherCondition.THUNDERSTORM -> "⛈️"
    WeatherCondition.UNKNOWN -> "🌡️"
}

@StringRes
fun PoiCategory.labelRes(): Int = when (this) {
    PoiCategory.MUSEUM -> R.string.category_museum
    PoiCategory.MONUMENT -> R.string.category_monument
    PoiCategory.RELIGIOUS_SITE -> R.string.category_religious_site
    PoiCategory.VIEWPOINT -> R.string.category_viewpoint
    PoiCategory.PARK -> R.string.category_park
    PoiCategory.BEACH -> R.string.category_beach
    PoiCategory.MARKET -> R.string.category_market
    PoiCategory.SEASONAL_EVENT -> R.string.category_seasonal_event
    PoiCategory.SKI_AREA -> R.string.category_ski_area
    PoiCategory.NEIGHBORHOOD -> R.string.category_neighborhood
    PoiCategory.ATTRACTION -> R.string.category_attraction
    PoiCategory.SHOPPING -> R.string.category_shopping
    PoiCategory.OTHER -> R.string.category_other
}

fun PoiCategory.emoji(): String = when (this) {
    PoiCategory.MUSEUM -> "🖼️"
    PoiCategory.MONUMENT -> "🏛️"
    PoiCategory.RELIGIOUS_SITE -> "⛪"
    PoiCategory.VIEWPOINT -> "🌄"
    PoiCategory.PARK -> "🌳"
    PoiCategory.BEACH -> "🏖️"
    PoiCategory.MARKET -> "🧺"
    PoiCategory.SEASONAL_EVENT -> "🎪"
    PoiCategory.SKI_AREA -> "⛷️"
    PoiCategory.NEIGHBORHOOD -> "🏘️"
    PoiCategory.ATTRACTION -> "📍"
    PoiCategory.SHOPPING -> "🛍️"
    PoiCategory.OTHER -> "📍"
}

@StringRes
fun PoiTag.labelRes(): Int = when (this) {
    PoiTag.INSTAGRAMMABLE -> R.string.tag_instagrammable
    PoiTag.PANORAMIC -> R.string.tag_panoramic
    PoiTag.SUNSET_SPOT -> R.string.tag_sunset
    PoiTag.HIDDEN_GEM -> R.string.tag_hidden_gem
    PoiTag.SEASONAL_HIGHLIGHT -> R.string.tag_seasonal
}

fun PoiTag.emoji(): String = when (this) {
    PoiTag.INSTAGRAMMABLE -> "📸"
    PoiTag.PANORAMIC -> "🌄"
    PoiTag.SUNSET_SPOT -> "🌅"
    PoiTag.HIDDEN_GEM -> "💎"
    PoiTag.SEASONAL_HIGHLIGHT -> "🗓️"
}

@StringRes
fun RecommendationReason.labelRes(): Int = when (this) {
    RecommendationReason.IN_SEASON -> R.string.reason_in_season
    RecommendationReason.GREAT_WEATHER_OUTDOOR -> R.string.reason_great_weather
    RecommendationReason.INDOOR_ALTERNATIVE -> R.string.reason_indoor
    RecommendationReason.SNOW_ATMOSPHERE -> R.string.reason_snow
    RecommendationReason.WEATHER_RISK -> R.string.reason_weather_risk
    RecommendationReason.PHOTO_SPOT -> R.string.reason_photo_spot
}

fun TransitMode.emoji(): String = when (this) {
    TransitMode.WALK -> "🚶"
    TransitMode.BUS -> "🚌"
    TransitMode.TRAM -> "🚋"
    TransitMode.METRO -> "🚇"
    TransitMode.TRAIN -> "🚆"
    TransitMode.FERRY -> "⛴️"
    TransitMode.CABLE_CAR -> "🚡"
    TransitMode.OTHER -> "🚏"
}

@StringRes
fun TravelTheme.labelRes(): Int = when (this) {
    TravelTheme.CHRISTMAS_MARKETS -> R.string.theme_christmas_markets
    TravelTheme.NORTHERN_LIGHTS -> R.string.theme_northern_lights
    TravelTheme.WINTER_SPORTS -> R.string.theme_winter_sports
    TravelTheme.BEACH -> R.string.theme_beach
    TravelTheme.FOLIAGE -> R.string.theme_foliage
    TravelTheme.BLOSSOM -> R.string.theme_blossom
    TravelTheme.FESTIVAL -> R.string.theme_festival
    TravelTheme.WARM_ESCAPE -> R.string.theme_warm_escape
    TravelTheme.CULTURE -> R.string.theme_culture
    TravelTheme.FOOD -> R.string.theme_food
    TravelTheme.NATURE -> R.string.theme_nature
    TravelTheme.NIGHTLIFE -> R.string.theme_nightlife
}

fun TravelTheme.emoji(): String = when (this) {
    TravelTheme.CHRISTMAS_MARKETS -> "🎄"
    TravelTheme.NORTHERN_LIGHTS -> "🌌"
    TravelTheme.WINTER_SPORTS -> "⛷️"
    TravelTheme.BEACH -> "🏖️"
    TravelTheme.FOLIAGE -> "🍁"
    TravelTheme.BLOSSOM -> "🌸"
    TravelTheme.FESTIVAL -> "🎉"
    TravelTheme.WARM_ESCAPE -> "🌴"
    TravelTheme.CULTURE -> "🏛️"
    TravelTheme.FOOD -> "🍜"
    TravelTheme.NATURE -> "🏔️"
    TravelTheme.NIGHTLIFE -> "🌃"
}

/** Bandiera emoji dal codice paese ISO (es. "IT" → 🇮🇹); globo se il codice non è valido. */
fun flagEmoji(countryCode: String): String {
    val code = countryCode.uppercase()
    if (code.length != 2 || !code.all { it in 'A'..'Z' }) return "🌍"
    return code.map { char -> String(Character.toChars(REGIONAL_INDICATOR_A + (char - 'A'))) }.joinToString("")
}

private const val REGIONAL_INDICATOR_A = 0x1F1E6
