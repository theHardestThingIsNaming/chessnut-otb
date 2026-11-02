"""autonotes.py <folder> <line> <prep sheet>: a draft notes file, so only the moments that need insight are written by hand.

Every ply from the line's end to the game's end gets a plain, factual note made from the move and the engine's facts
(capture, check, castling, development, threats, the engine's preference when a move gives ground). Plies that
deserve real insight start with `TODO:` (turning points, captures with check, the first guess, the last moves): rewrite
those, and the intro, outro and reviews (all TODO). build.py refuses a file that still has TODO in it.
Writes data-cache/masters/<folder>-<line>.notes.
"""
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))
import re, sys, io
from common import OPENINGS, data
import chess, chess.pgn

folder, line_no, sheet = sys.argv[1], int(sys.argv[2]), sys.argv[3]
name, side = OPENINGS[folder]
white_side = side == 'w'
prep = [l.rstrip('\n').split('\t') for l in open(sheet)]
g = next(p for p in prep if p[0] == 'GAME')
gid, start, moves = g[2], int(g[8]), g[9].split()
line = next(l.rstrip('\n').split('\t')[3] for l in open(data(f'{folder}-lines.tsv')) if int(l.split('\t')[0]) == line_no)
pgn = next(l.split('\t', 2)[2].replace('\\n', '\n') for l in open(data(f'{folder}-pgn.tsv')) if l.split('\t')[1] == gid)
head = chess.pgn.read_game(io.StringIO(pgn)).headers

evals, tops, threats = {}, {}, {}
for p in prep:
    if p[0] == 'PV':
        m = re.search(r'\(eval (mate )?(-?\d+)', p[2])
        v = int(m.group(2)); evals[int(p[1])] = (10000 if v > 0 else -10000) if m.group(1) else v
    elif p[0] == 'YOU':
        tops[int(p[1])] = [re.match(r'(.+)=(\w+)\((-?\d+)\)', o).groups() for o in p[4].split()]
    elif p[0] == 'THEM':
        t = re.match(r'threat (.+)=(\w+)\((-?\d+)\)', p[4])
        if t: threats[int(p[1])] = (t.group(1), int(t.group(3)))
NAMES = {'P': 'pawn', 'N': 'knight', 'B': 'bishop', 'R': 'rook', 'Q': 'queen', 'K': 'king'}
b = chess.Board()
for m in moves[:start]: b.push_san(m)
prev = None
n = len(moves)
swing_plies = []
rows = []
for ply in range(start, n):
    san = moves[ply]; mv = b.parse_san(san)
    who = 'White' if b.turn else 'Black'
    pc = NAMES[b.piece_at(mv.from_square).symbol().upper()]
    cap = b.piece_at(mv.to_square)
    if b.is_en_passant(mv): cap = chess.Piece(chess.PAWN, not b.turn)
    sq = chess.square_name(mv.to_square)
    ev = evals.get(ply)
    before = evals.get(ply - 1)
    yours = (ply % 2 == 0) == white_side
    mover_sign = 1 if b.turn == chess.WHITE else -1
    loss = (before - ev) * mover_sign if before is not None and ev is not None else 0
    gain = -loss
    if b.is_castling(mv):
        text = f"{who} castles {'short' if chess.square_file(mv.to_square) > 4 else 'long'}, putting the king to safety and bringing a rook towards the centre."
    elif cap:
        text = f"{who}'s {pc} takes the {NAMES[cap.symbol().upper()]} on {sq}."
    elif mv.promotion:
        text = f"{who} promotes the pawn on {sq}."
    elif pc in ('knight', 'bishop') and chess.square_rank(mv.from_square) == (0 if b.turn else 7):
        text = f"{who} develops the {pc} to {sq}."
    elif pc == 'pawn':
        text = f"{who} plays the pawn to {sq}."
    else:
        text = f"{who}'s {pc} goes to {sq}."
    if b.gives_check(mv):
        text = text.rstrip('.') + ', with check.'
    if ply in threats and not yours and threats[ply][1] >= 100:
        text += f" It threatens {threats[ply][0]}."
    extra = ''
    if yours and ply in tops:
        best = tops[ply][0]
        if best[1] != mv.uci() and loss >= 60:
            extra = f" The engine prefers {best[0]}."
    if loss >= 150:
        text += f" A mistake: it gives away about {loss/100:.1f} of a pawn." + extra
    elif loss >= 60:
        text += " A slight inaccuracy." + extra
    elif ev is not None and abs(ev) >= 9000:
        text += " A forced mate follows."
    needs = (abs(gain) >= 100 or (cap and b.gives_check(mv)) or ply == start or ply >= n - 2
             or (ev is not None and before is not None and (abs(ev) >= 250) != (abs(before) >= 250)))
    if needs: swing_plies.append(ply)
    rows.append((ply, ('TODO: ' if needs else '') + text))
    b.push(mv)
fin = [p for p in swing_plies if p < n - 2 and p > start]
end = min(n, max(start + 8, (fin[-1] + 3) if fin else n)) if fin else n
out = [f"# {head.get('White')} ({head.get('WhiteElo', '?')}) - {head.get('Black')} ({head.get('BlackElo', '?')}), {head.get('Event')}, {head.get('Date', '')[:4]}, {head.get('Result')}",
       f"id {gid}", f"opening {name}|{side}", f"line {line}", f"end {end}   # TODO: adjust: just after the decisive moment, 10 to 25 of your moves",
       "intro TODO: event, year, who and ratings, what was at stake, how it relates to your line. End: Play your line, then find <surname>'s moves.",
       *[f"note {p} {t}" for p, t in rows],
       "review TODO|TODO|TODO|uci:G   # 3 or 4 of these, at the turning points",
       "outro TODO: how it ended, the lesson for the line"]
open(data(f'{folder}-{line_no}.notes'), 'w').write('\n'.join(out) + '\n')
print(f'{folder}-{line_no}.notes: {len(rows)} plies, {sum(1 for _, t in rows if t.startswith("TODO"))} need writing, end {end}')
