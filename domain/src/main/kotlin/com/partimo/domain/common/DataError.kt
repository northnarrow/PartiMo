package com.partimo.domain.common

/** Errori di dominio, indipendenti dalla libreria di rete usata nel data layer. */
sealed interface DataError {

    /** Dispositivo offline o host non raggiungibile. */
    data object NoConnection : DataError

    /** Il servizio non ha risposto entro il timeout. */
    data object Timeout : DataError

    /** Chiave API assente, non valida o senza permessi (HTTP 401/403). */
    data object Unauthorized : DataError

    /** Quota del provider esaurita (HTTP 429). */
    data object RateLimited : DataError

    /** Errore lato server (HTTP 5xx). */
    data class Server(val httpCode: Int) : DataError

    /** Richiesta rifiutata dal provider (altri HTTP 4xx). */
    data class Client(val httpCode: Int) : DataError

    /** Risposta non interpretabile (JSON inatteso o dati incoerenti). */
    data object InvalidResponse : DataError

    /** Parametri di ricerca non validi, intercettati dai casi d'uso prima di chiamare le API. */
    data class InvalidQuery(val issue: QueryIssue) : DataError

    /** Errore imprevisto. */
    data class Unknown(val message: String? = null) : DataError
}

/** Motivi di validazione fallita (la presentation li traduce in messaggi localizzati). */
enum class QueryIssue {
    INVALID_AIRPORT_CODE,
    SAME_ORIGIN_AND_DESTINATION,
    DATE_IN_THE_PAST,
    RETURN_BEFORE_DEPARTURE,
    INVALID_TRAVELLER_COUNT,
    INVALID_STAY_DATES,
    STAY_TOO_LONG,
    QUERY_TOO_SHORT,
    NO_AIRPORT_NEARBY,
    TEXT_TOO_LONG,
    INVALID_AMOUNT,

    /** Lingua con una scrittura che il riconoscimento del testo sul telefono non legge (es. il russo). */
    UNSUPPORTED_SCRIPT,
}
