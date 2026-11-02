"""validate.py <rows file>: the checks MasterGamesTest makes, without Gradle, plus: the line is a drilled line in the
drilled order, and the game isn't in master-games.tsv already (set REPLACING=1 to allow that)."""
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))
import os, sys
from common import OPENINGS, GAMES, data
import chess

rows = [l.rstrip('\n').split('\t') for l in open(sys.argv[1]) if l.strip()]
g = next(r for r in rows if r[0] == 'GAME')
gid, side, line, start, end, moves = g[1], g[3], g[4].split(), int(g[10]), int(g[11]), g[12].split()
b = chess.Board(); fens = [b.fen()]
for m in moves:
    b.push_san(m); fens.append(b.fen())
assert moves[:len(line)] == line and start == len(line), 'the game must start with its line'
folder = next(k for k, v in OPENINGS.items() if v[0] == g[2])
drilled = [l.rstrip('\n').split('\t')[3].split() for l in open(data(f'{folder}-lines.tsv'))]
assert line in drilled, 'the line is not one of the drilled lines, in their move order'
taken = [r.split('\t') for r in open(GAMES) if r.startswith('GAME\t')]
if not os.environ.get('REPLACING'):
    assert not any(r[1] == gid for r in taken), f'{gid} is already in master-games.tsv'
    assert not any(r[2] == g[2] and r[4] == g[4] for r in taken), 'that line already has a master game'
assert start < end <= len(moves), 'end must be after the line and within the game'
notes = {int(r[2]): r for r in rows if r[0] == 'NOTE'}
white = side == 'w'
for ply in range(start, len(moves)):
    assert ply in notes, f'no note at ply {ply}'
    extra = notes[ply][4] if len(notes[ply]) > 4 else ''
    if extra.startswith('good:'):
        for u in filter(None, extra[5:].split(',')):
            assert chess.Move.from_uci(u) in chess.Board(fens[ply]).legal_moves, f'good move {u} at {ply}'
    if extra.startswith('threat:'):
        bb = chess.Board(fens[ply + 1]); bb.push(chess.Move.null())
        assert chess.Move.from_uci(extra[7:]) in bb.legal_moves, f'threat {extra} at {ply}'
for r in rows:
    if r[0] == 'REVIEW':
        ply = int(r[2])
        for a in filter(None, (r[5] if len(r) > 5 else '').split(',')):
            sq = chess.parse_square(a.split(':')[0][:2])
            assert chess.Board(fens[ply + 1]).piece_at(sq) or chess.Board(fens[ply]).piece_at(sq), f'arrow {a} at {ply} starts on an empty square'
assert any(r[0] == 'OUTRO' for r in rows), 'no outro'
print(gid, 'ok:', len(moves), 'plies, guess', start, '-', end, ',', sum(1 for p in range(start, end) if (p % 2 == 0) == white), 'of your moves')
