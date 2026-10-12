package app.chessnut.otb.games

import app.chessnut.otb.analysis.AnalysisFormat
import app.chessnut.otb.analysis.GameAnalysis
import app.chessnut.otb.chess.StoredGameReconstructor
import java.io.File
import java.util.Properties

/**
 * One directory per game under [root], named by [SavedGame.id]:
 * `game.properties`, `placements.txt` (board frames as received), and
 * `analysis.txt` once Stockfish has run. Files are written to a temporary
 * name and renamed so a crash never leaves half a game.
 */
class GameStore(private val root: File) {
    init {
        root.mkdirs()
        foldersRoot.mkdirs()
        val legacy = File(root, LEGACY_ARCHIVE)
        if (legacy.isDirectory) {
            val target = folderDir(LEGACY_ARCHIVE)
            if (!target.exists()) legacy.renameTo(target)
        }
    }

    @Synchronized
    fun contains(id: String): Boolean = File(dir(id), GAME).exists()

    /** Saves [game] unless a game with the same id exists. Returns true when it was new. */
    @Synchronized
    fun addIfNew(game: SavedGame): Boolean {
        if (contains(game.id)) return false
        save(game)
        return true
    }

    @Synchronized
    fun save(game: SavedGame) {
        val dir = dir(game.id).apply { mkdirs() }
        if (game.placements.isNotEmpty()) writeAtomically(File(dir, PLACEMENTS), game.placements.joinToString("\n"))
        val props = Properties().apply {
            setProperty("id", game.id)
            setProperty("createdAt", game.createdAt.toString())
            setProperty("source", game.source.name)
            setProperty("moves", game.sanMoves.joinToString(" "))
            setProperty("complete", game.isComplete.toString())
            game.stopReason?.let { setProperty("stopReason", it) }
            setProperty("white", game.white)
            setProperty("black", game.black)
            setProperty("event", game.event)
            setProperty("date", game.date)
            setProperty("result", game.result)
            setProperty("label", game.label)
            setProperty("practise", game.practise)
        }
        val text = java.io.StringWriter().also { props.store(it, null) }.toString()
        writeAtomically(File(dir, GAME), text)
    }

    @Synchronized
    fun load(id: String): SavedGame? {
        val dir = dir(id)
        val file = File(dir, GAME)
        if (!file.exists()) return null
        val props = Properties().apply { file.reader().use { load(it) } }
        val placementsFile = File(dir, PLACEMENTS)
        val placements = if (placementsFile.exists()) placementsFile.readLines().filter { it.isNotBlank() } else emptyList()
        val storedMoves = props.getProperty("moves").split(' ').filter { it.isNotBlank() }
        val game = SavedGame(
            id = props.getProperty("id"),
            createdAt = props.getProperty("createdAt").toLong(),
            source = GameSource.valueOf(props.getProperty("source")),
            sanMoves = storedMoves,
            isComplete = props.getProperty("complete").toBoolean(),
            stopReason = props.getProperty("stopReason"),
            white = props.getProperty("white", ""),
            black = props.getProperty("black", ""),
            event = props.getProperty("event", ""),
            date = props.getProperty("date", ""),
            result = props.getProperty("result", "*"),
            placements = placements,
            label = props.getProperty("label", ""),
            practise = props.getProperty("practise", ""),
        )
        if (storedMoves.isEmpty() && placements.isNotEmpty()) {
            val rebuilt = StoredGameReconstructor.reconstruct(placements)
            if (rebuilt.sanMoves.isNotEmpty()) {
                val updated = game.copy(
                    sanMoves = rebuilt.sanMoves,
                    isComplete = rebuilt.isComplete,
                    stopReason = rebuilt.stopReason,
                )
                save(updated)
                return updated.copy(folder = folderOf(id))
            }
        }
        return game.copy(folder = folderOf(id))
    }

    /** Games in the main list, outside any folder. */
    @Synchronized
    fun unfiled(): List<SavedGame> = gamesIn(root)

    /** Games in the folder [name]. */
    @Synchronized
    fun inFolder(name: String): List<SavedGame> = gamesIn(folderDir(name))

    /** Newest first. Games in the main list and in named folders. */
    @Synchronized
    fun all(): List<SavedGame> =
        (gamesIn(root) + folders().flatMap { gamesIn(folderDir(it)) })
            .sortedByDescending { it.createdAt }

    @Synchronized
    fun folders(): List<String> =
        (foldersRoot.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .map { it.name }
            .sorted()

    @Synchronized
    fun createFolder(name: String): String? {
        val clean = cleanFolder(name) ?: return null
        folderDir(clean).mkdirs()
        return clean
    }

    @Synchronized
    fun renameFolder(from: String, to: String): Boolean {
        val clean = cleanFolder(to) ?: return false
        if (clean == from) return true
        val source = folderDir(from)
        val target = folderDir(clean)
        if (!source.isDirectory || target.exists()) return false
        return source.renameTo(target)
    }

    @Synchronized
    fun deleteFolder(name: String) {
        val folder = folderDir(name)
        if (!folder.isDirectory) return
        gamesIn(folder).forEach { move(from = File(folder, it.id), to = File(root, it.id)) }
        folder.deleteRecursively()
    }

    @Synchronized
    fun moveToFolder(id: String, folder: String) {
        val clean = cleanFolder(folder) ?: return
        folderDir(clean).mkdirs()
        move(from = dir(id), to = File(folderDir(clean), id))
    }

    @Synchronized
    fun moveToGames(id: String) = move(from = dir(id), to = File(root, id))

    /** Old Archive chip. Kept so earlier builds still compile against the store. */
    @Synchronized
    fun archive(id: String) = moveToFolder(id, LEGACY_ARCHIVE)

    @Synchronized
    fun unarchive(id: String) = moveToGames(id)

    @Synchronized
    fun delete(id: String) {
        dir(id).deleteRecursively()
    }

    @Synchronized
    fun saveAnalysis(id: String, analysis: GameAnalysis) {
        writeAtomically(File(dir(id), ANALYSIS), AnalysisFormat.write(analysis))
    }

    @Synchronized
    fun loadAnalysis(id: String): GameAnalysis? {
        val file = File(dir(id), ANALYSIS)
        return if (file.exists()) runCatching { AnalysisFormat.read(file.readText()) }.getOrNull() else null
    }

    @Synchronized
    fun clearAnalysis(id: String) {
        File(dir(id), ANALYSIS).delete()
    }

    private fun gamesIn(folder: File): List<SavedGame> =
        (folder.listFiles() ?: emptyArray())
            .filter { it.isDirectory && it.name.matches(ID) }
            .mapNotNull { runCatching { load(it.name) }.getOrNull() }

    private fun folderOf(id: String): String {
        val parent = locate(id)?.parentFile ?: return ""
        return if (parent == root) "" else parent.name
    }

    private fun locate(id: String): File? {
        val live = File(root, id)
        if (live.isDirectory) return live
        return foldersRoot.walkTopDown().maxDepth(2).firstOrNull { it.isDirectory && it.name == id && it.parentFile != root }
    }

    private fun cleanFolder(name: String): String? {
        val clean = name.trim().replace(Regex("""[\\/]+"""), " ").replace(Regex("""\s+"""), " ")
        if (clean.isEmpty()) return null
        if (clean.length > 40) return null
        // "." and ".." would point back at the folders or the games themselves.
        if (clean.all { it == '.' }) return null
        return clean
    }

    private fun move(from: File, to: File) {
        if (!from.isDirectory) return
        // Moving onto itself would delete the game below before the rename.
        if (from.canonicalFile == to.canonicalFile) return
        to.parentFile.mkdirs()
        if (to.exists()) to.deleteRecursively()
        if (from.renameTo(to)) return
        from.copyRecursively(to)
        from.deleteRecursively()
    }

    private fun dir(id: String): File {
        require(id.matches(ID)) { "Bad game id $id" }
        return locate(id) ?: File(root, id)
    }

    private fun folderDir(name: String): File = File(foldersRoot, name)

    private val foldersRoot: File get() = File(root, FOLDERS)

    private fun writeAtomically(target: File, text: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Could not write ${target.path}" }
        }
    }

    private companion object {
        const val GAME = "game.properties"
        const val PLACEMENTS = "placements.txt"
        const val ANALYSIS = "analysis.txt"
        const val FOLDERS = "folders"
        const val LEGACY_ARCHIVE = "Archive"
        val ID = Regex("[0-9a-f]{16,64}")
    }
}
