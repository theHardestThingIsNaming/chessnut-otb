"""at.py <prep sheet or master game id> <ply> [extra SAN moves...] [--multi N] [--depth D]: the board and Stockfish's best lines (in SAN,
White's view) after the game's first <ply> moves, plus any extra moves. For checking claims before writing them."""
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))
import sys, logging
from common import STOCKFISH
import chess, chess.engine
logging.getLogger('chess.engine').setLevel(logging.ERROR)

args = sys.argv[1:]
opt = lambda name, default: int(args[args.index(name) + 1]) if name in args else default
multi, depth = opt('--multi', 4), opt('--depth', 20)
rest = [a for i, a in enumerate(args) if a not in ('--multi', '--depth') and (i == 0 or args[i - 1] not in ('--multi', '--depth'))]
sheet, ply, extra = rest[0], int(rest[1]), rest[2:]
if sheet.endswith('.txt'):
    moves = next(l.split('\t')[9].split() for l in open(sheet) if l.startswith('GAME'))
else:
    # a game id from the shipped master-games.tsv
    from common import GAMES
    moves = next(l.split('\t')[12].split() for l in open(GAMES) if l.startswith('GAME\t') and l.split('\t')[1] == sheet)
b = chess.Board()
for m in moves[:ply] + extra: b.push_san(m)
print(b, '\n', b.fen())
eng = chess.engine.SimpleEngine.popen_uci(STOCKFISH); eng.configure({'Threads': 1})
for info in eng.analyse(b, chess.engine.Limit(depth=depth), multipv=multi):
    print(info['score'].pov(chess.WHITE), b.variation_san(info['pv'][:10]))
eng.quit()
