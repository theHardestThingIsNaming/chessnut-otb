package app.chessnut.otb.board

import android.content.Context
import app.chessnut.otb.protocol.FakeChessnutBoard
import com.bleforemulator.EmulatorDetector

object BoardLinks {
    fun options(context: Context): List<LinkOption> {
        val bridge = LinkOption("Mac Bluetooth (BLEForEmulator)", needsPermissions = false) { BridgeGattLink(it) }
        val native = LinkOption("Phone Bluetooth", needsPermissions = true) { AndroidGattLink(context, it) }
        val simulated = LinkOption("Simulated board", needsPermissions = false) { FakeChessnutBoard() }
        return if (EmulatorDetector.isEmulator()) listOf(bridge, simulated, native) else listOf(native, simulated)
    }
}
