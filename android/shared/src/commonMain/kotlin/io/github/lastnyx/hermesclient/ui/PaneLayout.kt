package io.github.lastnyx.hermesclient.ui

data class PanePlan(
    val showList: Boolean,
    val listFixedWidth: Boolean,
    val showChat: Boolean,
    val showPlaceholder: Boolean,
)

fun paneLayout(wide: Boolean, chatOpen: Boolean): PanePlan =
    if (wide) PanePlan(showList = true, listFixedWidth = true, showChat = chatOpen, showPlaceholder = !chatOpen)
    else PanePlan(showList = !chatOpen, listFixedWidth = false, showChat = chatOpen, showPlaceholder = false)
