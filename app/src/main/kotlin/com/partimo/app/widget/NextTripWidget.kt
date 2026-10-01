package com.partimo.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.partimo.app.MainActivity
import com.partimo.app.PartiMoApp
import com.partimo.app.R
import com.partimo.app.navigation.DashboardDestination
import com.partimo.app.notifications.DealNotifier
import com.partimo.app.ui.common.Formatters
import com.partimo.domain.model.saved.SavedTrip
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Cosa mostra il widget: il prossimo viaggio salvato, con le date e i giorni che mancano. */
data class NextTripInfo(val trip: SavedTrip, val departure: LocalDate, val returnDate: LocalDate, val daysLeft: Int) {

    companion object {
        /** Il viaggio salvato che parte per primo tra quelli non ancora passati; `null` se non ce ne sono. */
        fun from(trips: List<SavedTrip>, today: LocalDate): NextTripInfo? = trips
            .filterNot { it.period.isOver(today) }
            .map { trip ->
                val departure = trip.departureDate(today)
                NextTripInfo(trip, departure, trip.returnDate(today), ChronoUnit.DAYS.between(today, departure).toInt())
            }
            .minWithOrNull(compareBy<NextTripInfo> { it.departure }.thenBy { it.trip.destination.name })
    }
}

/** "oggi", "domani" o "tra 12 giorni". */
fun countdownText(context: Context, daysLeft: Int): String = when {
    daysLeft <= 0 -> context.getString(R.string.widget_today)
    daysLeft == 1 -> context.getString(R.string.widget_tomorrow)
    else -> context.getString(R.string.widget_in_days, daysLeft)
}

/**
 * Widget "Prossimo viaggio" per la schermata Home: meta, conto alla rovescia e date del primo viaggio
 * salvato; il tocco apre il viaggio (o PartiMo, se non ci sono viaggi salvati).
 */
class NextTripWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as? PartiMoApp)?.container
        val info = container?.let { NextTripInfo.from(it.observeSavedTrips().first(), LocalDate.now(it.clock)) }
        provideContent { GlanceTheme { NextTripContent(context, info) } }
    }
}

@Composable
private fun NextTripContent(context: Context, info: NextTripInfo?) {
    val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    info?.let { open.putExtra(DealNotifier.EXTRA_DASHBOARD_ROUTE, DashboardDestination.from(it.trip.destination, it.trip.period).toJson()) }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(actionStartActivity(open)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "✈️ " + context.getString(R.string.widget_label),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        if (info == null) {
            Text(text = context.getString(R.string.widget_empty), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), maxLines = 2)
        } else {
            Text(
                text = info.trip.destination.name,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = countdownText(context, info.daysLeft) + " · " + Formatters.dateRange(info.departure, info.returnDate),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                maxLines = 1,
            )
        }
    }
}

class NextTripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextTripWidget()
}
