package app.chessnut.otb.board

import app.chessnut.otb.protocol.ByteLog
import app.chessnut.otb.protocol.GattLink

class LinkOption(
    val label: String,
    val needsPermissions: Boolean,
    val create: (ByteLog) -> GattLink,
)
