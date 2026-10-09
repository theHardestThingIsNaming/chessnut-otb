package app.chessnut.otb

import android.content.Context

object BuildInfo {
    val SOURCE_URL: String = BuildConfig.SOURCE_URL
    val PRIVACY_URL: String = BuildConfig.PRIVACY_URL

    fun versionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}
