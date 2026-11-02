"""enrich.py list <game id> | apply <game id> <notes file> | todo

list:  the generic notes of a game in its guessing stretch, each with the engine's facts, to rewrite with insight.
       Writes data-cache/masters/enrich/<id>.facts and prints it.
check:  validates a notes file for a game without changing anything.
apply: patches NOTE rows of master-games.tsv from <notes file> (lines `ply<TAB>new text`), refusing a text that is
       empty, still generic, or has TODO. The shipped file is edited in place; run MasterGamesTest after.
todo:  games with generic notes left in the guessing stretch, most first.
"""
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))
import re, sys, logging
from common import GAMES, STOCKFISH, data
import chess, chess.engine
logging.getLogger('chess.engine').setLevel(logging.ERROR)

GENERIC = re.compile(r"^(White|Black)(\'s \w+ goes to [a-h][1-8]|\'s \w+ takes the \w+ on [a-h][1-8]|"
                     r" plays the pawn to [a-h][1-8]| develops the \w+ to [a-h][1-8]| castles (short|long), putting the king)")


def load():
    return [l.rstrip('\n').split('\t') for l in open(GAMES)]


def is_generic(note):
    # Generic = starts with the drafted pattern and is not much longer than the draft.
    return bool(GENERIC.match(note)) and len(note) < 140


def todo():
    rows = load()
    games = {r[1]: r for r in rows if r and r[0] == 'GAME'}
    left = {}
    for r in rows:
        if r and r[0] == 'NOTE' and r[1] in games and len(r) > 3:
            g = games[r[1]]
            if int(g[10]) <= int(r[2]) < int(g[11]) and is_generic(r[3]):
                left[r[1]] = left.get(r[1], 0) + 1
    return games, left


if sys.argv[1] == 'todo':
    games, left = todo()
    for k, n in sorted(left.items(), key=lambda kv: -kv[1]):
        g = games[k]
        print(f'{k}\t{n}\t{g[2]}\t{g[5]} - {g[6]} {g[7]}')
    print(sum(left.values()), 'generic notes in', len(left), 'games')
elif sys.argv[1] == 'list':
    gid = sys.argv[2]
    rows = load()
    g = next(r for r in rows if r[0] == 'GAME' and r[1] == gid)
    moves, start, end = g[12].split(), int(g[10]), int(g[11])
    side_w = g[3] == 'w'
    notes = {int(r[2]): r[3] for r in rows if r[0] == 'NOTE' and r[1] == gid}
    eng = chess.engine.SimpleEngine.popen_uci(STOCKFISH); eng.configure({'Threads': 1, 'Hash': 64})
    lim = chess.engine.Limit(depth=13)
    cp = lambda s: s.score(mate_score=10000)
    out = [f'# {gid} {g[5]} - {g[6]}, {g[8]} {g[7]}; {g[2]}; you are {"White" if side_w else "Black"}; guess stretch plies {start}-{end}',
           '# Each: ply | move | now says | FEN before | engine best moves (side to move) | after: eval (White view), line']
    b = chess.Board()
    for m in moves[:start]: b.push_san(m)
    for ply in range(start, end):
        san = moves[ply]
        mv = b.parse_san(san)
        if is_generic(notes.get(ply, '')):
            infos = eng.analyse(b, lim, multipv=3)
            best = ' ; '.join(f"{b.san(i['pv'][0])} {cp(i['score'].relative):+}" for i in infos)
            fen = b.fen()
            after = b.copy(); after.push(mv)
            ai = eng.analyse(after, lim)
            line = after.variation_san(ai['pv'][:5]) if ai.get('pv') else ''
            label = f"{ply // 2 + 1}{'.' if ply % 2 == 0 else '...'} {san}"
            mover = 'you' if (ply % 2 == 0) == side_w else 'opponent'
            out.append(f"{ply} | {label} ({mover}) | {notes[ply]} | {fen} | {best} | {cp(ai['score'].white()):+} {line}")
        b.push(mv)
    eng.quit()
    os_dir = data('enrich'); _os.makedirs(os_dir, exist_ok=True)
    open(f'{os_dir}/{gid}.facts', 'w').write('\n'.join(out) + '\n')
    print('\n'.join(out))
elif sys.argv[1] in ('apply', 'check'):
    gid, path = sys.argv[2], sys.argv[3]
    rows0 = load()
    g0 = next(r for r in rows0 if r[0] == 'GAME' and r[1] == gid)
    valid = {int(r[2]) for r in rows0 if r[0] == 'NOTE' and r[1] == gid}
    new = {}
    for l in open(path):
        if '\t' not in l: continue
        ply, text = l.rstrip('\n').split('\t', 1)
        text = text.strip()
        assert text and 'TODO' not in text, f'bad text at {ply}'
        assert int(ply) in valid, f'no note at ply {ply}'
        assert not GENERIC.match(text) or len(text) >= 140, f'still generic at {ply}: {text[:60]}'
        assert '\t' not in text and '\n' not in text and '—' not in text, f'tab, newline or em-dash at {ply}'
        new[int(ply)] = text
    if sys.argv[1] == 'check':
        print(f'{gid}: {len(new)} notes ok'); sys.exit(0)
    out, done = [], 0
    for line in open(GAMES).read().split('\n'):
        p = line.split('\t')
        if len(p) > 3 and p[0] == 'NOTE' and p[1] == gid and int(p[2]) in new:
            p[3] = new[int(p[2])]; done += 1
            line = '\t'.join(p)
        out.append(line)
    open(GAMES, 'w').write('\n'.join(out))
    print(f'{gid}: {done} of {len(new)} notes replaced')
