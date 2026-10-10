package app.chessnut.otb

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chessnut.otb.analysis.AccuracyCalculator
import app.chessnut.otb.analysis.ChessLines
import app.chessnut.otb.board.BoardLinkSection
import app.chessnut.otb.board.BoardViewModel
import app.chessnut.otb.games.GameSource
import app.chessnut.otb.games.SavedGame
import app.chessnut.otb.ui.BoardMark
import app.chessnut.otb.ui.BookMark
import app.chessnut.otb.ui.ChessBoard
import app.chessnut.otb.ui.ClockMark
import app.chessnut.otb.ui.Palette
import app.chessnut.otb.ui.PawnMark
import app.chessnut.otb.ui.ResultChip
import app.chessnut.otb.ui.clubAppBarColors
import app.chessnut.otb.ui.clubChipColors
import app.chessnut.otb.ui.clubOutlinedButtonColors
import app.chessnut.otb.ui.clubPanel
import app.chessnut.otb.ui.clubTextButtonColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    app: OtbApp,
    board: BoardViewModel,
    onOpen: (String) -> Unit,
    onBoard: () -> Unit,
    onBot: () -> Unit,
    onOnline: () -> Unit,
    onTrainer: () -> Unit,
    onAbout: () -> Unit,
    onImport: (String) -> String?,
    onMistakes: () -> Unit = {},
) {
    val games by app.games.collectAsStateWithLifecycle()
    val progress by app.analysisProgress.collectAsStateWithLifecycle()
    val version by app.analysisVersion.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var sounds by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf("") }
    var browsing by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<SavedGame?>(null) }
    var renaming by remember { mutableStateOf<SavedGame?>(null) }
    var moving by remember { mutableStateOf<SavedGame?>(null) }

    val folders = games.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
    val visible = if (browsing) games.filter { it.folder == folder } else games.filter { it.folder.isBlank() }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = Palette.Text,
        topBar = {
            TopAppBar(
                title = {},
                colors = clubAppBarColors(),
                actions = {
                    TextButton(onClick = { menu = true }, colors = clubTextButtonColors()) { Text("More", color = Palette.Gold) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Learn from mistakes") }, onClick = { menu = false; onMistakes() })
                        DropdownMenuItem(text = { Text("Paste a PGN") }, onClick = { menu = false; pasting = true })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; sounds = true })
                        DropdownMenuItem(text = { Text("My name") }, onClick = { menu = false; naming = true })
                        DropdownMenuItem(text = { Text("About") }, onClick = { menu = false; onAbout() })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BoardLinkSection(board, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), compact = true)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBoard,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold, contentColor = Palette.OnGold),
                ) {
                    BoardMark(Palette.OnGold)
                    Text("Play over the board", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onBot, modifier = Modifier.fillMaxWidth(), colors = clubOutlinedButtonColors()) {
                    PawnMark(Palette.Gold)
                    Text("Play a bot", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onOnline, modifier = Modifier.fillMaxWidth(), colors = clubOutlinedButtonColors()) {
                    ClockMark(Palette.Gold)
                    Text("Play online", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onTrainer, modifier = Modifier.fillMaxWidth(), colors = clubOutlinedButtonColors()) {
                    BookMark(Palette.Gold)
                    Text("Opening trainer", modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (browsing) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { browsing = false; folder = "" }, colors = clubTextButtonColors()) { Text("Recent", color = Palette.Gold) }
                    LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(folders) { name ->
                            FilterChip(
                                selected = folder == name,
                                onClick = { folder = name },
                                colors = clubChipColors(),
                                label = { Text(name, color = if (folder == name) Palette.OnGold else Palette.Text) },
                            )
                        }
                    }
                    TextButton(onClick = { newFolder = true }, colors = clubTextButtonColors()) { Text("Folder", color = Palette.Gold) }
                }
                if (folder.isNotBlank()) {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { renameFolder = folder }, colors = clubTextButtonColors()) { Text("Rename folder", color = Palette.Gold) }
                        TextButton(onClick = { app.deleteFolder(folder); folder = ""; browsing = folders.size <= 1 }, colors = clubTextButtonColors()) { Text("Remove folder", color = Palette.Error) }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Recent", color = Palette.Text, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = {
                        browsing = true
                        if (folder.isBlank()) folder = folders.firstOrNull().orEmpty()
                    }, colors = clubTextButtonColors()) { Text("Folders", color = Palette.Gold) }
                }
            }
            if (!browsing && visible.isEmpty()) {
                Text(
                    "No games yet. Games you play or import show up here.",
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else if (visible.isEmpty()) {
                Text(
                    if (folder.isBlank()) "No folders yet." else "$folder is empty.",
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { game ->
                        GameRow(
                            app = app,
                            game = game,
                            progress = progress[game.id],
                            version = version,
                            onClick = { onOpen(game.id) },
                            onRename = { renaming = game },
                            onMove = { moving = game },
                            onDelete = { pendingDelete = game },
                        )
                    }
                    if (!browsing) {
                        item {
                            Text(
                                "Only your last 5 games stay here. Move a game into a folder to keep it.",
                                color = Palette.TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (!browsing && "Earlier" in folders) {
                        item {
                            TextButton(
                                onClick = { browsing = true; folder = "Earlier" },
                                colors = clubTextButtonColors(),
                            ) { Text("Earlier", color = Palette.Gold) }
                        }
                    }
                }
            }
        }
    }

    if (pasting) {
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { pasting = false },
            title = { Text("Paste a PGN") },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, minLines = 6, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = Palette.Error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    error = onImport(text)
                    if (error == null) pasting = false
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text("Cancel") } },
        )
    }
    if (sounds) SoundDialog(app, onDismiss = { sounds = false })
    if (naming) {
        var name by remember { mutableStateOf(app.myName) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Your name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Games where you are Black open from Black's side.", color = Palette.Text)
                    OutlinedTextField(name, { name = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    app.myName = name.trim()
                    naming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
    if (newFolder) NameDialog("New folder", "", "Create") { name ->
        newFolder = false
        if (name != null) app.createFolder(name)?.let { folder = it }
    }
    renameFolder?.let { current ->
        NameDialog("Rename folder", current, "Rename") { name ->
            renameFolder = null
            if (name != null) {
                app.renameFolder(current, name)
                folder = name.trim()
            }
        }
    }
    renaming?.let { game ->
        NameDialog("Rename game", game.label.ifBlank { game.title }, "Save") { name ->
            renaming = null
            if (name != null) app.renameGame(game.id, name)
        }
    }
    moving?.let { game ->
        MoveDialog(game, folders, onDismiss = { moving = null }) { target ->
            moving = null
            app.moveGame(game.id, target)
            folder = target
        }
    }
    pendingDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this game?") },
            text = { Text("“${game.title}” is removed from this phone. Games on the board are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    app.deleteGame(game.id)
                    pendingDelete = null
                }) { Text("Delete", color = Palette.Error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SoundDialog(app: OtbApp, onDismiss: () -> Unit) {
    var phone by remember { mutableStateOf(app.phoneSounds) }
    var board by remember { mutableStateOf(app.boardSounds) }
    var upload by remember { mutableStateOf(app.autoUpload) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The phone says each move and beeps. The board only buzzes. Lights stay on.", color = Palette.Text)
                SoundSwitch("Phone", phone) {
                    phone = it
                    app.phoneSounds = it
                }
                SoundSwitch("Board", board) {
                    board = it
                    app.boardSounds = it
                }
                Text(
                    "Club games from the board go to your Lichess account as soon as they are saved. Imported games are public on Lichess.",
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                SoundSwitch("Upload club games to Lichess", upload) {
                    upload = it
                    app.autoUpload = it
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SoundSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.Text)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun GameRow(
    app: OtbApp,
    game: SavedGame,
    progress: Float?,
    version: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val accuracy = remember(game.id, version, progress == null) {
        app.store.loadAnalysis(game.id)?.let { analysis ->
            val report = AccuracyCalculator.report(game.sanMoves, ChessLines.replay(game.sanMoves).uci, analysis)
            report.whiteAccuracy to report.blackAccuracy
        }
    }
    val fen = remember(game.id, game.sanMoves.size, game.placements.size) {
        game.placements.lastOrNull()
            ?: runCatching { ChessLines.replay(game.sanMoves).fens.last() }.getOrDefault(START_FEN)
    }
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().clubPanel()) {
        Row(
            Modifier.clickable(onClick = onClick).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChessBoard(fen = fen, modifier = Modifier.size(68.dp), coordinates = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                val details = listOfNotNull(
                    game.date.ifBlank { null },
                    "${(game.sanMoves.size + 1) / 2} moves",
                    when (game.source) {
                        GameSource.Board -> "board"
                        GameSource.Pgn -> "PGN"
                        GameSource.Play -> "play"
                    },
                ).joinToString("  ·  ")
                Text(details, color = Palette.Text, style = MaterialTheme.typography.bodyMedium)
                when {
                    progress != null -> LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = Palette.Gold,
                        trackColor = Palette.Outline,
                    )
                    accuracy != null -> Text(
                        "Accuracy  ${fmt(accuracy.first)}  ·  ${fmt(accuracy.second)}",
                        color = Palette.Gold,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    !game.isComplete -> Text("Recording incomplete", color = Palette.Error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                ResultChip(game.result)
                Box {
                    TextButton(onClick = { menu = true }) { Text("⋮", color = Palette.Gold, fontSize = 22.sp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Move to folder") }, onClick = { menu = false; onMove() })
                        DropdownMenuItem(text = { Text("Delete", color = Palette.Error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onDone(name) }, colors = clubTextButtonColors()) { Text(confirm, color = Palette.Gold) } },
        dismissButton = { TextButton(onClick = { onDone(null) }, colors = clubTextButtonColors()) { Text("Cancel", color = Palette.Gold) } },
    )
}

@Composable
private fun MoveDialog(game: SavedGame, folders: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    var creating by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move “${game.title}”") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (game.folder.isNotBlank()) {
                    TextButton(onClick = { onMove("") }, colors = clubTextButtonColors()) { Text("Games", color = Palette.Text) }
                }
                folders.filter { it != game.folder }.forEach { name ->
                    TextButton(onClick = { onMove(name) }, colors = clubTextButtonColors()) { Text(name, color = Palette.Text) }
                }
                OutlinedTextField(creating, { creating = it }, label = { Text("New folder") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (creating.isNotBlank()) onMove(creating) },
                enabled = creating.isNotBlank(),
                colors = clubTextButtonColors(),
            ) { Text("Create and move", color = Palette.Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, colors = clubTextButtonColors()) { Text("Cancel", color = Palette.Gold) } },
    )
}

private const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"

private fun fmt(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "–"
