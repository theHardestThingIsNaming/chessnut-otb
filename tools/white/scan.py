"""scan.py <folder>: saves the master games (White wins, <= 110 plies) that reach a repertoire line's end position to hits-<folder>.jsonl."""
import sys; sys.path.insert(0, '/Users/ciaranwhyte/git/chessnutApp/data-cache/pylib')
import chess, chess.pgn, glob, os, collections, json, io
R = '/Users/ciaranwhyte/git/chessnutApp'
folder = sys.argv[1]
rows = [l.rstrip('\n').split('\t') for l in open(R + '/core/src/main/resources/white-repertoire.tsv') if l.strip() and not l.startswith('#')]
def key(b): return ' '.join(b.epd().split(' ')[:4])
ends = collections.defaultdict(list)
for i, (chap, _, line) in enumerate(rows):
    b = chess.Board()
    for m in line.split(): b.push_san(m)
    ends[(key(b), len(line.split()))].append(i)
maxply = max(n for _, n in ends)
out = open(f'hits-{folder}.jsonl', 'w')
for path in sorted(glob.glob(f'{R}/data-cache/pgnmentor/{folder}/*.pgn')):
    with open(path, errors='replace') as f:
        while True:
            try: g = chess.pgn.read_game(f)
            except Exception: continue
            if g is None: break
            if g.headers.get('Result') != '1-0': continue
            b = g.board(); n = 0; found = []
            try:
                for mv in g.mainline_moves():
                    b.push(mv); n += 1
                    if n <= maxply: found += ends.get((key(b), n), [])
            except Exception: continue
            if found and n <= 110:
                h = g.headers
                out.write(json.dumps({'lines': found, 'plies': n, 'h': dict(h), 'moves': g.board().variation_san(g.mainline_moves())}) + '\n')
out.close(); print('done', folder)
