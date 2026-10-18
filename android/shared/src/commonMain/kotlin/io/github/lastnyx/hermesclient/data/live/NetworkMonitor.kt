package io.github.lastnyx.hermesclient.data.live

import kotlinx.coroutines.flow.StateFlow

/** True only while the device has a default network. */
interface NetworkMonitor {
    val online: StateFlow<Boolean>
}
