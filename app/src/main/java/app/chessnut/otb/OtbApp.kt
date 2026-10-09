package app.chessnut.otb

import android.app.Application
import android.content.SharedPreferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.chessnut.otb.analysis.AnalysisWorker
import app.chessnut.otb.games.GameStore
import app.chessnut.otb.games.SavedGame
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class OtbApp : Application() {
    val settings: SharedPreferences by lazy { getSharedPreferences("club_games", MODE_PRIVATE) }

    val store: GameStore by lazy { GameStore(File(filesDir, "games")) }

    private val gamesFlow = MutableStateFlow<List<SavedGame>>(emptyList())
    val games: StateFlow<List<SavedGame>> = gamesFlow.asStateFlow()

    private val progressFlow = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Analysis progress per game id, 0..1, while a job is running. */
    val analysisProgress: StateFlow<Map<String, Float>> = progressFlow.asStateFlow()

    private val analysedFlow = MutableStateFlow(0)
    val analysisVersion: StateFlow<Int> = analysedFlow.asStateFlow()

    var myName: String
        get() = settings.getString(KEY_MY_NAME, "").orEmpty()
        set(value) = settings.edit().putString(KEY_MY_NAME, value).apply()

    override fun onCreate() {
        super.onCreate()
        refreshGames()
    }

    fun refreshGames() {
        gamesFlow.value = store.all()
    }

    fun createFolder(name: String): String? {
        val created = store.createFolder(name)
        refreshGames()
        return created
    }

    fun renameFolder(from: String, to: String) {
        store.renameFolder(from, to)
        refreshGames()
    }

    fun deleteFolder(name: String) {
        store.deleteFolder(name)
        refreshGames()
    }

    fun moveGame(id: String, folder: String) {
        if (folder.isBlank()) store.moveToGames(id) else store.moveToFolder(id, folder)
        refreshGames()
    }

    fun renameGame(id: String, label: String) {
        val game = store.load(id) ?: return
        store.save(game.copy(label = label.trim()))
        refreshGames()
    }

    fun deleteGame(id: String) {
        store.delete(id)
        refreshGames()
    }

    fun analyse(id: String, replace: Boolean = false) {
        if (replace) store.clearAnalysis(id)
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(workDataOf(AnalysisWorker.KEY_GAME_ID to id))
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork("analyse-$id", ExistingWorkPolicy.KEEP, request)
        reportProgress(id, 0f)
    }

    fun reportProgress(id: String, fraction: Float?) {
        progressFlow.update { if (fraction == null) it - id else it + (id to fraction) }
        if (fraction == null) analysedFlow.update { it + 1 }
    }

    private companion object {
        const val KEY_MY_NAME = "my_name"
    }
}
