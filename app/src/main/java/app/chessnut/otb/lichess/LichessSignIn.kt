package app.chessnut.otb.lichess

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/** Lichess login as a sheet over this app. Closing it returns here, and the token stays. */
object LichessSignIn {
    private val browsers = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.google.android.apps.chrome",
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
    )

    fun warmUp(context: Context) {
        val browser = CustomTabsClient.getPackageName(context, browsers) ?: return
        runCatching { CustomTabsClient.connectAndInitialize(context, browser) }
    }

    fun open(context: Context, url: String) {
        val scheme = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(0xFF16130F.toInt())
            .setSecondaryToolbarColor(0xFF16130F.toInt())
            .setNavigationBarColor(0xFF0C0B09.toInt())
            .build()
        val height = (context.resources.displayMetrics.heightPixels * 0.92f).toInt()
        val tabs = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setUrlBarHidingEnabled(false)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setDefaultColorSchemeParams(scheme)
            .setCloseButtonPosition(CustomTabsIntent.CLOSE_BUTTON_POSITION_END)
            .setInitialActivityHeightPx(height, CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE)
            .setBackgroundInteractionEnabled(false)
            .build()
        val browser = CustomTabsClient.getPackageName(context, browsers)
        if (browser != null) tabs.intent.setPackage(browser)
        try {
            tabs.launchUrl(context, Uri.parse(url))
        } catch (_: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
