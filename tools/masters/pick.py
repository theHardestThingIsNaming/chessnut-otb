"""pick.py <folder>: the worklist of master games for an opening, most played lines first.

Lines are ordered by the phone's club counts (data-cache/masters/phone-stats.tsv, the app's files/opening-stats.tsv)
where it has them, then by the club proxy (<folder>-lines.tsv order). Lines that already have a master game are DONE.
For each other line, the candidates (<folder>-cand.tsv) get a quick Stockfish pass and the first good one is taken:
a classical game your side won, at most 110 plies, where your side reaches a clear advantage (+2) after the line ends
and doesn't let it slip back below +0.5. A line with no good game is SKIP.
Writes <folder>-work.tsv: rank, line, game id, players, ratings, year, event, plies.
"""
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))
import os, re, sys, logging
from common import OPENINGS, GAMES, STOCKFISH, data
import chess, chess.engine
logging.getLogger('chess.engine').setLevel(logging.ERROR)

folder = sys.argv[1]
name, side = OPENINGS[folder]
white = side == 'w'
done = {tuple(r.split('\t')[4].split()) for r in open(GAMES) if r.startswith('GAME\t') and r.split('\t')[2] == name}
stats = {}
if os.path.exists(data('phone-stats.tsv')):
    for l in open(data('phone-stats.tsv')):
        p = l.rstrip('\n').split('\t')
        if len(p) == 5: stats[p[0]] = int(p[1]) + int(p[2]) + int(p[3])
lines = []
for i, l in enumerate(open(data(f'{folder}-lines.tsv'))):
    n, club, masters, sans = l.rstrip('\n').split('\t')
    b = chess.Board(); u = [b.push_san(m).uci() for m in sans.split()]
    lines.append((stats.get('line:' + ','.join(u), -1), i, int(n), sans.split()))
lines.sort(key=lambda r: (-r[0], r[1]))
cands = {}
for l in open(data(f'{folder}-cand.tsv')):
    f = l.rstrip('\n').split('\t')
    cands.setdefault(int(f[0]), []).append(f)
skip_event = re.compile(r'blitz|rapid|bullet|online|titled|simul|blind|chess\.com|esports|play-?in|showdown|meltwater|tour|armageddon|g/\d|speed|lichess', re.I)
win = '1-0' if white else '0-1'
eng = chess.engine.SimpleEngine.popen_uci(STOCKFISH); eng.configure({'Threads': 1, 'Hash': 32})


def good(f):
    if f[8] != win or skip_event.search(f[7]) or int(f[9]) > 110: return False
    moves, reach = f[11].split(), int(f[10])
    b = chess.Board(); clear = None
    for i, m in enumerate(moves):
        b.push_san(m)
        if i + 1 < reach or (i - reach) % 2: continue
        s = eng.analyse(b, chess.engine.Limit(depth=10))['score'].white().score(mate_score=3000)
        s = s if white else -s
        if clear is None and s >= 200: clear = i + 1
        elif clear is not None and s < 50: return False
    return clear is not None


out = []
for rank, (plays, _, n, sans) in enumerate(lines, 1):
    if tuple(sans) in done:
        out.append(f'{rank}\t{n}\tDONE'); continue
    best = next((f for f in cands.get(n, []) if good(f)), None)
    if best:
        out.append('\t'.join([str(rank), str(n), best[1], f'{best[2]} - {best[3]}', f'{best[4]}/{best[5]}', best[6], best[7], best[9]]))
    else:
        out.append(f'{rank}\t{n}\tSKIP\tno good game ({len(cands.get(n, []))} candidates)')
eng.quit()
open(data(f'{folder}-work.tsv'), 'w').write('\n'.join(out) + '\n')
print(folder, sum(1 for o in out if o.split('\t')[2] not in ('DONE', 'SKIP')), 'to do,', sum(1 for o in out if '\tSKIP' in o), 'skipped,', sum(1 for o in out if '\tDONE' in o), 'done')
