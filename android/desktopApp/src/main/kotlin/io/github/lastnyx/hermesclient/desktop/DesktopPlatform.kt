package io.github.lastnyx.hermesclient.desktop

import io.github.lastnyx.hermesclient.data.settings.DraftStore
import io.github.lastnyx.hermesclient.data.settings.SessionViewStore
import io.github.lastnyx.hermesclient.ui.AlwaysOnline
import io.github.lastnyx.hermesclient.ui.DesktopChatPlatform
import io.github.lastnyx.hermesclient.ui.HostPlatform

/** What SessionsHost takes on the desktop; the chat screens are wired into the window by the next ticket. */
fun desktopPlatform(files: DesktopFiles): HostPlatform =
    HostPlatform(AlwaysOnline, SessionViewStore(files.sessionView), DraftStore(files.drafts), DesktopChatPlatform())
