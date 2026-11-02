"""survey.py: for each White-repertoire line, the master games (PGN Mentor) that reach the line's end position
(same position, any move order) with White winning. Writes survey.tsv: line no, chapter, games, best game ids."""
import sys; sys.path.insert(0, '/Users/ciaranwhyte/git/chessnutApp/data-cache/pylib')
import chess, chess.pgn, glob, os, collections
R = '/Users/ciaranwhyte/git/chessnutApp'
rows = [l.rstrip('\n').split('\t') for l in open(R + '/core/src/main/resources/white-repertoire.tsv') if l.strip() and not l.startswith('#')]
def key(b): return ' '.join(b.epd().split(' ')[:4])
ends = collections.defaultdict(list)
for i, (chap, _, line) in enumerate(rows):
    b = chess.Board()
    for m in line.split(): b.push_san(m)
    ends[(key(b), len(line.split()))].append(i)
folders = {'french': 'French', 'sicilian': 'Sicilian', 'pirc': 'Pirc'}
hits = collections.defaultdict(list)
maxply = max(n for _, n in ends)
for folder in folders:
    for path in sorted(glob.glob(f'{R}/data-cache/pgnmentor/{folder}/*.pgn')):
        with open(path, errors='replace') as f:
            while True:
                g = chess.pgn.read_game(f)
                if g is None: break
                b = g.board(); n = 0
                for mv in g.mainline_moves():
                    b.push(mv); n += 1
                    if n > maxply: break
                    for i in ends.get((key(b), n), []):
                        h = g.headers
                        hits[i].append((h.get('Result'), h.get('White'), h.get('Black'), h.get('Date', '')[:4], h.get('WhiteElo', ''), h.get('BlackElo', ''), os.path.basename(path)))
with open('survey.tsv', 'w') as out:
    for i, (chap, _, line) in enumerate(rows):
        hs = hits.get(i, [])
        wins = [h for h in hs if h[0] == '1-0']
        out.write(f'{i}\t{chap}\t{len(hs)}\t{len(wins)}\t{line}\n')
print('done')
