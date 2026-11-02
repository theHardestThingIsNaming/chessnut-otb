"""mkwork.py: from hits-*.jsonl, makes per-chapter work folders in data-cache/masters: wr<N>-lines.tsv, -pgn.tsv, -work.tsv and white-chapters.tsv."""
import sys, json, glob, collections, re, hashlib
sys.path.insert(0, '/Users/ciaranwhyte/git/chessnutApp/data-cache/pylib')
R = '/Users/ciaranwhyte/git/chessnutApp'; M = R + '/data-cache/masters'
rows = [l.rstrip('\n').split('\t') for l in open(R + '/core/src/main/resources/white-repertoire.tsv') if l.strip() and not l.startswith('#')]
chapters = list(dict.fromkeys(r[0] for r in rows))
key = {c: f'wr{i + 1}' for i, c in enumerate(chapters)}
open(M + '/white-chapters.tsv', 'w').write(''.join(f'{key[c]}\t{c}\n' for c in chapters))
skip = re.compile(r'blitz|rapid|bullet|online|titled|simul|blind|chess\.com|esports|armageddon|speed|lichess', re.I)
def elo(h, k):
    try: return int(h.get(k) or 0)
    except ValueError: return 0
best = {}
for path in glob.glob(R + '/data-cache/white/masters/hits-*.jsonl'):
    for l in open(path):
        g = json.loads(l); h = g['h']
        if skip.search(h.get('Event', '')) or skip.search(h.get('Site', '')): continue
        w, b = elo(h, 'WhiteElo'), elo(h, 'BlackElo')
        if min(w, b) < 2200: continue
        for i in g['lines']:
            if g['plies'] - len(rows[i][2].split()) < 14: continue
            score = w + b
            if i not in best or score > best[i][0]: best[i] = (score, g)
by = collections.defaultdict(list)
for i, (chap, _, line) in enumerate(rows): by[chap].append((i, line))
for chap, ls in by.items():
    k = key[chap]
    with open(f'{M}/{k}-lines.tsv', 'w') as lf, open(f'{M}/{k}-pgn.tsv', 'w') as pf, open(f'{M}/{k}-work.tsv', 'w') as wf:
        for n, (i, line) in enumerate(ls, 1):
            lf.write(f'{n}\t0\t0\t{line}\n')
            if i in best:
                _, g = best[i]; h = g['h']
                gid = hashlib.md5((h.get('White', '') + h.get('Black', '') + h.get('Date', '') + g['moves'][:60]).encode()).hexdigest()[:7]
                head = ''.join(f'[{t} "{h.get(t, "?")}"]\n' for t in ('Event', 'Site', 'Date', 'Round', 'White', 'Black', 'Result', 'WhiteElo', 'BlackElo'))
                pf.write(f'PGN\t{gid}\t' + (head + '\n' + g['moves'] + ' 1-0\n').replace('\n', '\\n') + '\n')
                wf.write(f'{n}\t{n}\t{gid}\t{h.get("White")} - {h.get("Black")}\t{h.get("WhiteElo")}/{h.get("BlackElo")}\t{h.get("Date", "")[:4]}\t{h.get("Event")}\t{g["plies"]}\n')
            else:
                wf.write(f'{n}\t{n}\tSKIP\tno master game reaches this line\n')
print({c: sum(1 for i, _ in by[c] if i in best) for c in chapters})
