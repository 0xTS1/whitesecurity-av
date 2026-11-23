package id.whitesecurity.antivirus.update

import kotlinx.coroutines.flow.MutableStateFlow

/** Process-wide update status shared by the worker, receiver, and UI. */
object UpdateState {
    val available = MutableStateFlow<UpdateInfo?>(null)
    val busy = MutableStateFlow(false)

    @Volatile var lastError: String? = null
    @Volatile var appInForeground: Boolean = false
}
