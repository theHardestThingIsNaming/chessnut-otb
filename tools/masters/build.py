"""build.py <folder> <notes file> <prep sheet>: master-games.tsv rows from a prep sheet and a notes file (prints them).

Notes file:
  id <game id>            opening <name>|<w/b>            line <the drilled line, SAN, in the drilled order>
  end <ply>               the guessing runs from the line's end up to this ply
  white <name>  black <name>  event <event, place>   (optional; default from the PGN)
  intro <text>
  note <ply> <text>       every ply from the line's end to the game's end
  review <ply>|<title>|<text>|<arrows uci:G,uci:R>
  outro <text>
Good moves: the engine's other moves within 0.3 of a pawn of its best. Threats: captures and checks worth a pawn.
The game's own opening moves are replaced by your line, so you play it in your own order.
"""
import os as _os, sys as _sys
_sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))
import re, sys
from common import data

folder, notes_path, prep_path = sys.argv[1], sys.argv[2], sys.argv[3]
prep = open(prep_path).read().splitlines()
pgns = {l.split('\t')[1]: l.split('\t', 2)[2].replace('\\n', '\n') for l in open(data(f'{folder}-pgn.tsv'))}

meta, notes, reviews = {}, {}, []
for raw in open(notes_path):
    raw = raw.rstrip('\n')
    if not raw.strip() or raw.startswith('#'):
        continue
    key, _, rest = raw.partition(' ')
    if key == 'note':
        ply, _, text = rest.partition(' ')
        notes[int(ply)] = text.strip()
    elif key == 'review':
        reviews.append(rest.split('|'))
    else:
        meta[key] = rest.strip()

assert not any('TODO' in v for v in list(notes.values()) + [meta.get('intro',''), meta.get('outro','')] + ['|'.join(r) for r in reviews]), 'the notes file still has TODO in it'
gid = meta['id']
game = next(l.split('\t') for l in prep if l.startswith('GAME\t') and l.split('\t')[2] == gid)
start = int(game[8])
line = meta['line'].split()
assert len(line) == start, f'line has {len(line)} plies, game reaches it at {start}'
moves = line + game[9].split()[start:]
head = dict(re.findall(r'\[(\w+) "([^"]*)"\]', pgns[gid]))
opening, side = meta['opening'].split('|')
white_side = side == 'w'
end = int(meta['end'])

good, threat = {}, {}
for l in prep:
    p = l.split('\t')
    if p[0] == 'YOU':
        ply, master_uci = int(p[1]), p[3]
        opts = [re.match(r'(.+)=(\w+)\((-?\d+)\)', o).groups() for o in p[4].split()]
        best = max(int(c) for _, _, c in opts)
        good[ply] = [u for _, u, c in opts if u != master_uci and int(c) >= best - 30]
    elif p[0] == 'THEM':
        m = re.match(r'threat (.+)=(\w+)\((-?\d+)\)', p[4])
        if m and int(m.group(3)) >= 100 and any(c in m.group(1) for c in 'x+#'):
            threat[int(p[1])] = m.group(2)


def name(n):
    # PGN Mentor writes "Fischer, Robert James"; show "Robert James Fischer".
    if ',' in n:
        last, first = [s.strip() for s in n.split(',', 1)]
        return f'{first} {last}'.strip()
    return n.strip()


white, black = meta.get('white', name(head['White'])), meta.get('black', name(head['Black']))
rows = ['\t'.join(['GAME', gid, opening, side, ' '.join(line), white, black, head['Date'][:4], meta.get('event', head['Event']),
                   head['Result'], str(start), str(end), ' '.join(moves), meta['intro'], head.get('WhiteElo', ''), head.get('BlackElo', '')])]
missing = [p for p in range(start, len(moves)) if p not in notes]
assert not missing, f'no note at plies {missing}'
for ply in range(start, len(moves)):
    yours = (ply % 2 == 0) == white_side
    # Good moves and threats only in the guessing stretch.
    if ply >= end:
        extra = ''
    else:
        extra = ('good:' + ','.join(good.get(ply, []))) if yours else (f'threat:{threat[ply]}' if ply in threat else '')
    rows.append('\t'.join(['NOTE', gid, str(ply), notes[ply], extra]))
for r in reviews:
    rows.append('\t'.join(['REVIEW', gid] + [s.strip() for s in r] + ([''] if len(r) == 3 else [])))
rows.append('\t'.join(['OUTRO', gid, meta['outro']]))
print('\n'.join(rows))
