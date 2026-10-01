package com.partimo.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.partimo.app.di.AppContainer
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.Destination
import com.partimo.domain.model.TravelPeriod
import com.partimo.domain.model.Travellers
import com.partimo.domain.model.budget.BudgetSummary
import com.partimo.domain.model.budget.Expense
import com.partimo.domain.model.budget.ExpenseCategory
import com.partimo.domain.model.budget.TripBudget
import com.partimo.domain.model.guide.EURO
import com.partimo.domain.usecase.EditTripBudgetUseCase
import com.partimo.domain.usecase.GetCountryInfoUseCase
import com.partimo.domain.usecase.ObserveTravellersUseCase
import com.partimo.domain.usecase.ObserveTripBudgetUseCase
import com.partimo.domain.usecase.SummarizeBudgetUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

/** Spesa in scrittura nella finestra "Aggiungi spesa". */
data class ExpenseDraft(
    val amountText: String = "",
    val currency: String,
    val category: ExpenseCategory = ExpenseCategory.FOOD,
    val date: LocalDate,
    val note: String = "",
) {
    /** Importo scritto, con la virgola o il punto come separatore; `null` se non è un importo valido. */
    val amount: BigDecimal? get() = parseAmount(amountText)
}

data class BudgetUiState(
    val destination: Destination,
    val period: TravelPeriod,
    val from: LocalDate,
    val to: LocalDate,
    /** Valuta dei totali e del budget. */
    val homeCurrency: String = EURO,
    /** Valuta del paese, se diversa dall'euro: si può scegliere per le spese. */
    val localCurrency: String? = null,
    /** `null` finché il budget salvato non è stato letto. */
    val budget: TripBudget? = null,
    val summary: BudgetSummary? = null,
    /** Spesa in scrittura; `null` con la finestra chiusa. */
    val draft: ExpenseDraft? = null,
    /** Budget in scrittura; `null` con la finestra chiusa. */
    val limitText: String? = null,
    /** Chi parte: con più persone si mostra anche la spesa a testa. */
    val travellers: Travellers = Travellers.SOLO,
) {
    /** Spesa a testa nella valuta dei totali; `null` per chi viaggia da solo o senza spese. */
    val perPerson: BigDecimal?
        get() = summary?.total?.takeIf { travellers.total > 1 && it.signum() > 0 }
            ?.divide(travellers.total.toBigDecimal(), 2, RoundingMode.HALF_UP)

    /** Valute proposte per una spesa: l'euro e, se diversa, quella del paese. */
    val currencies: List<String> get() = listOfNotNull(homeCurrency, localCurrency?.takeIf { it != homeCurrency })

    /** Giorni del viaggio, per scegliere quando è stata fatta una spesa. */
    val days: List<LocalDate> get() = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

    /** Spese dalla più recente (a parità di giorno, l'ultima registrata in cima). */
    val expenses: List<Expense>
        get() = budget?.expenses.orEmpty().withIndex()
            .sortedWith(compareByDescending<IndexedValue<Expense>> { it.value.date }.thenByDescending { it.index })
            .map { it.value }
}

/** Importo scritto dall'utente ("12,50" o "12.50"); `null` se non è positivo. */
fun parseAmount(text: String): BigDecimal? = text.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

/**
 * ViewModel del budget del viaggio: spese nella valuta in cui si pagano, totali in euro con i cambi
 * del giorno e tetto di spesa. Tutto resta sul telefono.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(
    observeTripBudget: ObserveTripBudgetUseCase,
    private val editTripBudget: EditTripBudgetUseCase,
    private val summarizeBudget: SummarizeBudgetUseCase,
    private val getCountryInfo: GetCountryInfoUseCase,
    private val clock: Clock,
    destination: Destination,
    period: TravelPeriod,
    from: LocalDate,
    to: LocalDate,
    observeTravellers: ObserveTravellersUseCase? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState(destination, period, from, to))
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        observeTravellers?.let { observe ->
            viewModelScope.launch { observe().collect { travellers -> _uiState.update { it.copy(travellers = travellers) } } }
        }
        viewModelScope.launch {
            val country = (getCountryInfo(destination.countryCode) as? DataResult.Success)?.data
            _uiState.update { it.copy(localCurrency = country?.currencyCode?.takeIf { code -> code != it.homeCurrency }) }
        }
        viewModelScope.launch {
            observeTripBudget(destination, period)
                .mapLatest { budget -> budget to summarizeBudget(budget, _uiState.value.homeCurrency) }
                .collect { (budget, summary) -> _uiState.update { it.copy(budget = budget, summary = summary) } }
        }
    }

    /** Apre la finestra per una nuova spesa: valuta del paese se c'è, giorno di oggi se si è in viaggio. */
    fun onAddExpense() {
        _uiState.update { state ->
            val today = LocalDate.now(clock.withZone(state.destination.timeZone))
            val day = today.takeIf { !it.isBefore(state.from) && !it.isAfter(state.to) } ?: state.from
            state.copy(draft = ExpenseDraft(currency = state.localCurrency ?: state.homeCurrency, date = day))
        }
    }

    fun onDraftChanged(draft: ExpenseDraft) {
        // Solo cifre e un separatore decimale: è un campo numerico.
        if (draft.amountText.length > MAX_AMOUNT_LENGTH || !draft.amountText.all { it.isDigit() || it == ',' || it == '.' }) return
        _uiState.update { it.copy(draft = draft.copy(note = draft.note.take(EditTripBudgetUseCase.MAX_NOTE_LENGTH))) }
    }

    fun onDismissDraft() {
        _uiState.update { it.copy(draft = null) }
    }

    fun onSaveDraft() {
        val state = _uiState.value
        val draft = state.draft ?: return
        val amount = draft.amount ?: return
        _uiState.update { it.copy(draft = null) }
        viewModelScope.launch {
            editTripBudget.addExpense(state.destination, state.period, amount, draft.currency, draft.category, draft.date, draft.note)
        }
    }

    fun onRemoveExpense(expense: Expense) {
        val state = _uiState.value
        viewModelScope.launch { editTripBudget.removeExpense(state.destination, state.period, expense.id) }
    }

    fun onEditLimit() {
        _uiState.update { state -> state.copy(limitText = state.budget?.limit?.toPlainString()?.replace('.', ',').orEmpty()) }
    }

    fun onLimitChanged(text: String) {
        if (text.length > MAX_AMOUNT_LENGTH || !text.all { it.isDigit() || it == ',' || it == '.' }) return
        _uiState.update { it.copy(limitText = text) }
    }

    fun onDismissLimit() {
        _uiState.update { it.copy(limitText = null) }
    }

    /** Salva il budget; un campo vuoto lo toglie. */
    fun onSaveLimit() {
        val state = _uiState.value
        val text = state.limitText ?: return
        val limit = parseAmount(text)
        if (limit == null && text.isNotBlank()) return
        _uiState.update { it.copy(limitText = null) }
        viewModelScope.launch { editTripBudget.setLimit(state.destination, state.period, limit) }
    }

    companion object {
        private const val MAX_AMOUNT_LENGTH = 10

        fun factory(
            container: AppContainer,
            destination: Destination,
            period: TravelPeriod,
            from: LocalDate,
            to: LocalDate,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BudgetViewModel(
                    observeTripBudget = container.observeTripBudget,
                    editTripBudget = container.editTripBudget,
                    summarizeBudget = container.summarizeBudget,
                    getCountryInfo = container.getCountryInfo,
                    clock = container.clock,
                    destination = destination,
                    period = period,
                    from = from,
                    to = to,
                    observeTravellers = container.observeTravellers,
                )
            }
        }
    }
}
