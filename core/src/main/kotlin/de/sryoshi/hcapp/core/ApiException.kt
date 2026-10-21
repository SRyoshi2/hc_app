package de.sryoshi.hcapp.core

/**
 * Fehler der Schnittstelle. [code] ist der maschinenlesbare Wert des Servers
 * (`invalid_credentials`, `pending_approval`, `app_disabled`, `validation_error` …) oder einer der
 * clientseitigen Werte [NETWORK], [NO_API], [BAD_RESPONSE].
 */
class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int = 0,
    val fields: Map<String, List<String>> = emptyMap(),
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Das Token ist ungültig oder abgelaufen – neu anmelden. */
    val tokenUngueltig: Boolean get() = httpStatus == 401 && code in setOf("invalid_token", "not_authenticated")

    companion object {
        const val NETWORK = "network_error"
        const val NO_API = "no_api"
        const val BAD_RESPONSE = "bad_response"
    }
}
