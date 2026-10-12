package app.chessnut.otb.analysis

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.chessnut.otb.OtbApp
import app.chessnut.otb.engine.StockfishProcess
import app.chessnut.otb.engine.UciEngine

/** Runs Stockfish over one saved game and stores the result next to it. */
class AnalysisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as OtbApp
        val id = inputData.getString(KEY_GAME_ID) ?: return Result.failure()
        // A game deleted before its turn: clear the progress analyse() set, or it shows as analysing for good.
        val game = app.store.load(id) ?: run {
            app.reportProgress(id, null)
            return Result.failure()
        }
        if (app.store.loadAnalysis(id) != null) {
            app.reportProgress(id, null)
            return Result.success()
        }
        val engine = try {
            UciEngine(StockfishProcess.start(applicationContext), threads = THREADS)
        } catch (e: Exception) {
            app.reportProgress(id, null)
            return Result.failure(workDataOf(KEY_ERROR to e.message))
        }
        return try {
            val analysis = GameAnalyzer(engine).analyse(game.sanMoves) { done, total ->
                app.reportProgress(id, done.toFloat() / total.coerceAtLeast(1))
            }
            app.store.saveAnalysis(id, analysis)
            Result.success()
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to e.message))
        } finally {
            engine.close()
            app.reportProgress(id, null)
        }
    }

    companion object {
        const val KEY_GAME_ID = "game_id"
        const val KEY_ERROR = "error"
        private val THREADS = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(1, 4)
    }
}
