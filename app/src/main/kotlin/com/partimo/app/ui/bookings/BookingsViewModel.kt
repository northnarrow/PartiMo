package com.partimo.app.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.domain.model.booking.Booking
import com.partimo.domain.usecase.ObserveBookingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** Prenotazioni di un giorno, nella linea del tempo. */
data class BookingDay(val date: LocalDate, val bookings: List<Booking>)

/** Linea del tempo delle prenotazioni: le prossime per giorno, quelle passate a parte. */
data class BookingsUiState(
    val today: LocalDate,
    /** Date del viaggio da cui si è aperta la schermata (dashboard); `null` = tutte le prenotazioni. */
    val tripDates: ClosedRange<LocalDate>? = null,
    val tripName: String? = null,
    val upcoming: List<BookingDay> = emptyList(),
    val past: List<Booking> = emptyList(),
    val showPast: Boolean = false,
    /** `false` finché le prenotazioni salvate non sono state lette. */
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && upcoming.isEmpty() && past.isEmpty()
}

class BookingsViewModel(
    observeBookings: ObserveBookingsUseCase,
    clock: Clock,
    tripDates: ClosedRange<LocalDate>? = null,
    tripName: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingsUiState(today = LocalDate.now(clock), tripDates = tripDates, tripName = tripName))
    val uiState: StateFlow<BookingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeBookings().collect { all ->
                val today = _uiState.value.today
                val shown = tripDates?.let { range -> all.filter { it.overlaps(range.start, range.endInclusive) } } ?: all
                val (past, upcoming) = shown.partition { it.isPast(today) }
                _uiState.update { state ->
                    state.copy(
                        upcoming = upcoming.groupBy { it.startDate }.map { (date, bookings) -> BookingDay(date, bookings) },
                        past = past.reversed(),
                        loaded = true,
                    )
                }
            }
        }
    }

    fun onTogglePast() {
        _uiState.update { it.copy(showPast = !it.showPast) }
    }

    companion object {
        fun factory(container: AppContainer, tripDates: ClosedRange<LocalDate>?, tripName: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { BookingsViewModel(container.observeBookings, container.clock, tripDates, tripName) }
        }
    }
}
