package com.partimo.domain.usecase

import com.partimo.domain.common.DataError
import com.partimo.domain.common.DataResult
import com.partimo.domain.model.Destination
import com.partimo.domain.model.guide.CountryInfo
import com.partimo.domain.model.guide.EURO
import com.partimo.domain.model.guide.ExchangeRate
import com.partimo.domain.model.guide.GuideSection
import com.partimo.domain.model.guide.TravelGuide
import com.partimo.domain.repository.CountryInfoRepository
import com.partimo.domain.repository.ExchangeRateRepository
import com.partimo.domain.repository.TravelGuideRepository
import java.util.Locale

/** Informazioni pratiche sul paese della meta. */
class GetCountryInfoUseCase(private val repository: CountryInfoRepository) {
    suspend operator fun invoke(countryCode: String): DataResult<CountryInfo> = repository.countryInfo(countryCode.uppercase(Locale.ROOT))
}

/** Cambio aggiornato tra l'euro (o [from]) e la valuta del paese. */
class GetExchangeRateUseCase(private val repository: ExchangeRateRepository) {

    suspend operator fun invoke(to: String, from: String = EURO, forceRefresh: Boolean = false): DataResult<ExchangeRate> =
        when (val result = repository.latestRates(from, forceRefresh)) {
            is DataResult.Failure -> result
            is DataResult.Success -> {
                val rate = result.data.rate(from, to)
                if (rate == null || rate.signum() <= 0) {
                    DataResult.Failure(DataError.InvalidResponse)
                } else {
                    DataResult.Success(ExchangeRate(from, to, rate, result.data.updatedAt), result.origin)
                }
            }
        }
}

/**
 * Guida della città con i soli capitoli utili a chi parte: come arrivare e muoversi, cosa sapere,
 * dove mangiare, sicurezza e connessioni. Luoghi da vedere e alloggi hanno già le loro sezioni.
 */
class GetTravelGuideUseCase(
    private val repository: TravelGuideRepository,
    private val maxParagraphsPerSection: Int = DEFAULT_MAX_PARAGRAPHS,
) {

    suspend operator fun invoke(destination: Destination, forceRefresh: Boolean = false): DataResult<TravelGuide?> =
        when (val result = repository.guide(destination, forceRefresh)) {
            is DataResult.Failure -> result
            is DataResult.Success -> DataResult.Success(result.data?.let(::usefulParts), result.origin)
        }

    private fun usefulParts(guide: TravelGuide): TravelGuide = guide.copy(
        sections = guide.sections
            .mapNotNull { section -> USEFUL_TOPICS.indexOfFirst { topic -> topic.matches(section.title) }.takeIf { it >= 0 }?.let { it to section } }
            .sortedBy { it.first }
            .distinctBy { it.first }
            .map { (_, section) -> section.trimmed() }
            .filterNot { it.isEmpty },
    )

    private fun GuideSection.trimmed(): GuideSection = copy(
        paragraphs = paragraphs.filter { it.isNotBlank() }.take(maxParagraphsPerSection),
        subsections = subsections.map { it.trimmed() }.filterNot { it.isEmpty },
    )

    /** Argomenti nell'ordine in cui servono, con i titoli di Wikivoyage in italiano e in inglese. */
    private class Topic(vararg val prefixes: String) {
        fun matches(title: String): Boolean {
            val normalized = title.lowercase(Locale.ROOT).trim()
            return prefixes.any { normalized.startsWith(it) }
        }
    }

    private companion object {
        const val DEFAULT_MAX_PARAGRAPHS = 12

        val USEFUL_TOPICS = listOf(
            Topic("da sapere", "understand"),
            Topic("come arrivare", "get in"),
            Topic("come spostarsi", "get around"),
            Topic("eventi e feste", "events"),
            Topic("dove mangiare", "eat"),
            Topic("come divertirsi", "drink"),
            Topic("acquisti", "buy"),
            Topic("sicurezza", "stay safe"),
            Topic("come restare in contatto", "connect"),
        )
    }
}
