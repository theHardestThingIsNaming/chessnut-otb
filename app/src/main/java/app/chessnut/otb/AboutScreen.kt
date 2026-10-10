package app.chessnut.otb

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.clubAppBarColors
import app.chessnut.otb.ui.clubPanel

private val links = listOf(
    "Source code (GPL-3.0)" to BuildInfo.SOURCE_URL,
    "Stockfish (GPL-3.0)" to "https://github.com/official-stockfish/Stockfish",
    "cburnett pieces (GPL-2.0-or-later)" to "https://github.com/lichess-org/lila/tree/master/public/piece/cburnett",
    "chesslib (Apache-2.0)" to "https://github.com/bhlangonijr/chesslib",
    "Privacy policy" to BuildInfo.PRIVACY_URL,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Palette.Text,
        topBar = {
            TopAppBar(
                title = { Text("About") },
                colors = clubAppBarColors(),
                navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = Palette.Gold) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Club games", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
            Text(
                BuildInfo.versionName(context),
                color = Palette.Gold,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "Plays, trains and reviews games on a Chessnut Air, with Stockfish 18 on this phone. Games and analysis are kept on the device. Only Lichess features send anything: online games, club game uploads (which can be turned off in Settings), opening win rates and study imports.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
            Box(Modifier.fillMaxWidth().clubPanel().padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Licence", style = MaterialTheme.typography.titleSmall, color = Palette.Gold)
                    Text(
                        "This app is free software under the GNU General Public License version 3. It bundles the unmodified official Stockfish 18 Android build, whose source is linked below.",
                        color = Palette.TextMuted,
                    )
                    Text(
                        "Accuracy follows the formulas Lichess publishes. Numbers are close to Lichess, not identical, because the depth and engine build differ.",
                        color = Palette.TextMuted,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().clubPanel().padding(8.dp)) {
                Column {
                    links.filter { it.second.isNotBlank() }.forEach { (label, url) ->
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                            Text(label, color = Palette.Gold)
                        }
                    }
                }
            }
            Text("Chessnut is a trademark of its owner. This app is not affiliated with Chessnut.", color = Palette.TextMuted)
        }
    }
}
