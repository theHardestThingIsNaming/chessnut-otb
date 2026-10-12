package app.chessnut.otb

import app.chessnut.otb.openings.MiddlegamePlans
import app.chessnut.otb.repertoire.OpeningTree
import java.io.File
import kotlin.test.Test

/**
 * Not a check: a tool. Lists an opening's drilled lines and the middlegame plans that start from each.
 * Run: DUMP_OPENING="Italian Game|w" DUMP_OUT=/tmp/lines.tsv ./gradlew :core:test --tests '*LineDumpTest*' --rerun-tasks
 * Writes `number \t line (SAN) \t plans`; a line with no plan has an empty last column.
 */
class LineDumpTest {
    @Test
    fun dump() {
        val spec = System.getenv("DUMP_OPENING") ?: return
        val (name, side) = spec.split('|')
        val white = side == "w"
        val plans = MiddlegamePlans.of(name, white)
        val out = OpeningTree.lines(name, white).mapIndexed { i, line ->
            val mine = plans.filter { line.size >= it.before.size && line.take(it.before.size) == it.before }.map { it.name }
            "${i + 1}\t${line.joinToString(" ")}\t${mine.joinToString(", ")}"
        }
        File(System.getenv("DUMP_OUT")).writeText(out.joinToString("\n") + "\n")
    }
}
