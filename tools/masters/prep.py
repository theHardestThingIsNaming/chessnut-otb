"""prep.py <folder> <line number> <game id> [--depth D]: the fact sheet for writing a master game's notes.

Writes data-cache/masters/<folder>-<line>-prep.txt, in the format build.py reads:
  GAME line id white black year event result start moves
  then for every ply from the line's end to the game's end:
  BOARD ply (the board before the move), PV ply (the move, the eval after it from White's view, the engine's line),
  YOU ply san uci top3 (your side's moves: the engine's three best, scores for the side to move)
  THEM ply san uci threat X=uci(value) (the opponent's: what it threatens if you passed)
  and at the end, SWINGS: the plies where the eval moved by a pawn or more.
BOARD blocks are only written with --boards (use at.py to see a position).
Runs Stockfish with one thread, so several can run side by side. About 15 seconds a game at depth 16.
"""
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))
import sys, io, logging
from common import OPENINGS, STOCKFISH, data
import chess, chess.engine, chess.pgn
logging.getLogger('chess.engine').setLevel(logging.ERROR)

args = sys.argv[1:]
depth = int(args[args.index('--depth') + 1]) if '--depth' in args else 16
folder, line_no, gid = args[0], int(args[1]), args[2]
side_w = OPENINGS[folder][1] == 'w'
line = next(l.rstrip('\n').split('\t')[3].split() for l in open(data(f'{folder}-lines.tsv')) if int(l.split('\t')[0]) == line_no)
pgn = next(l.split('\t', 2)[2].replace('\\n', '\n') for l in open(data(f'{folder}-pgn.tsv')) if l.split('\t')[1] == gid)
game = chess.pgn.read_game(io.StringIO(pgn))
head = game.headers
moves = list(game.mainline_moves())
b = chess.Board(); sans = []
for m in moves:
    sans.append(b.san(m)); b.push(m)
lb = chess.Board()
for m in line: lb.push_san(m)
key = lambda bd: bd.board_fen() + (' w' if bd.turn else ' b')
b = chess.Board(); start = None
for i, m in enumerate(moves):
    b.push(m)
    if key(b) == key(lb):
        start = i + 1; break
assert start is not None, 'the game never reaches the line'
eng = chess.engine.SimpleEngine.popen_uci(STOCKFISH); eng.configure({'Threads': 1, 'Hash': 64})
lim = chess.engine.Limit(depth=depth)
cp = lambda s: s.score(mate_score=10000)
out = [f"GAME\t{line_no}\t{gid}\t{head.get('White')}\t{head.get('Black')}\t{head.get('Date', '')[:4]}\t{head.get('Event')}\t{head.get('Result')}\t{start}\t{' '.join(sans)}"]
b = chess.Board()
for m in moves[:start]: b.push(m)
evals = []
for ply in range(start, len(moves)):
    m = moves[ply]; san = b.san(m)
    if '--boards' in args:
        out.append(f'BOARD\t{ply}')
        out += ['    ' + r.replace(' ', '') for r in str(b).splitlines()]
    yours = (ply % 2 == 0) == side_w
    if yours:
        infos = eng.analyse(b, lim, multipv=3)
        tops = ' '.join(f"{b.san(i['pv'][0])}={i['pv'][0].uci()}({cp(i['score'].relative)})" for i in infos)
        out.append(f'YOU\t{ply}\t{san}\t{m.uci()}\t{tops}')
    b.push(m)
    info = eng.analyse(b, lim)
    ev = info['score'].white()
    ev_s = f'mate {ev.mate()}' if ev.is_mate() else str(ev.score())
    pv = b.variation_san(info['pv'][:6]) if info.get('pv') else ''
    out.append(f"PV\t{ply}\t{san} (eval {ev_s}, White's view) then {pv}")
    evals.append((ply, san, cp(ev)))
    if not yours:
        threat = '-'
        if not b.is_check() and not b.is_game_over():
            nb = b.copy(); nb.push(chess.Move.null())
            t = eng.analyse(nb, lim)
            if t.get('pv'):
                tu = t['pv'][0]
                threat = f"{nb.san(tu)}={tu.uci()}({cp(t['score'].relative) + cp(info['score'].relative)})"
        out.append(f'THEM\t{ply}\t{san}\t{m.uci()}\tthreat {threat}')
eng.quit()
sw, prev = [], None
for ply, san, e in evals:
    if prev is not None and abs(e - prev) >= 100:
        sw.append(f'{ply}:{san} {prev}->{e}')
    prev = e
out.append('SWINGS\t' + ', '.join(sw))
open(data(f'{folder}-{line_no}-prep.txt'), 'w').write('\n'.join(out) + '\n')
print(f'{folder} line {line_no} {gid}: start {start}, {len(moves)} plies, swings: ' + ', '.join(sw))
