package de.sryoshi.hcapp.core

/** Version der API, die dieser Client spricht, und die des Clients selbst. */
object ClientVersion {
    const val API = 1
    const val CLIENT = 1
}

sealed interface Compatibility {
    data object Ok : Compatibility

    /** Die Instanz ist älter als dieser Client: ihre API kennt die Funktionen nicht. */
    data class InstanceTooOld(val serverApi: Int) : Compatibility

    /** Die Instanz verlangt einen neueren Client. */
    data class ClientTooOld(val required: Int) : Compatibility

    companion object {
        fun pruefen(meta: Meta): Compatibility = when {
            meta.minClientVersion > ClientVersion.CLIENT -> ClientTooOld(meta.minClientVersion)
            meta.apiVersion < ClientVersion.API -> InstanceTooOld(meta.apiVersion)
            // Eine neuere API-Version ist kein Fehler, solange der Server den Client noch zulässt:
            // `min_client_version` ist genau dafür da.
            else -> Ok
        }
    }
}
