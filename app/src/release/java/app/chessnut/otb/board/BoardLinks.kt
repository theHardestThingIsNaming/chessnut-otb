package app.chessnut.otb.board

import android.content.Context

object BoardLinks {
    fun options(context: Context): List<LinkOption> =
        listOf(LinkOption("Phone Bluetooth", needsPermissions = true) { AndroidGattLink(context, it) })
}
