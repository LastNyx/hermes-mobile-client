package io.github.lastnyx.hermesclient.data.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

class CustomTabBrowserLauncher(private val context: Context) : BrowserLauncher {
    override fun open(url: String) {
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}
