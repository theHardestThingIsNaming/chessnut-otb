"""trim.py <in.tsv> <out.tsv>: cuts each line just before White's first move that loses 0.9 pawns or more against the
engine's best (depth 14), drops lines that end worse than -0.6 or shrink under 12 plies, drops illegal lines and any
line that is a prefix of another or a repeat. Prints what happened to each line."""
import sys; sys.path.insert(0, '/Users/ciaranwhyte/git/chessnutApp/data-cache/pylib')
import chess, chess.engine, re
eng = chess.engine.SimpleEngine.popen_uci('/opt/homebrew/bin/stockfish'); eng.configure({'Threads': 1, 'Hash': 64})
def ev(b): return eng.analyse(b, chess.engine.Limit(depth=14))['score'].white().score(mate_score=3000) / 100
RELAX = lambda c: 'Tal' in c or 'Smith-Morra' in c
kept = []
for n, row in enumerate(open(sys.argv[1]), 1):
    if not row.strip() or row.startswith('#'): continue
    chap, _, moves = row.rstrip('\n').partition('\t')
    b = chess.Board(); sans = []; cut = None
    try:
        for m in re.sub(r'\d+\.', ' ', moves).split():
            white = b.turn
            if white:
                best = ev(b); san = b.san(b.parse_san(m)); b.push_san(m)
                if ev(b) - best <= -(2.2 if RELAX(chap) else 0.9): cut = len(sans); break
                sans.append(san)
            else:
                san = b.san(b.parse_san(m)); b.push_san(m); sans.append(san)
    except Exception as ex:
        print(f'{n} illegal at ply {len(sans) + 1}, cut there [{chap}]'); cut = len(sans)
    if len(sans) % 2 == 1: sans.pop()  # end after Black's reply
    if len(sans) < 12: print(f'{n} drop (only {len(sans)} plies) [{chap}]'); continue
    b = chess.Board()
    for s in sans: b.push_san(s)
    e = ev(b)
    if e < -(1.2 if RELAX(chap) else 0.6): print(f'{n} drop (ends {e:+.2f}) [{chap}]'); continue
    kept.append((chap, sans)); print(f'{n} keep {len(sans)} plies{" (cut)" if cut is not None else ""} end {e:+.2f} [{chap}]')
out = []
for i, (chap, sans) in enumerate(kept):
    if any(c2 == chap and (s2 == sans and j < i or (len(s2) > len(sans) and s2[:len(sans)] == sans)) for j, (c2, s2) in enumerate(kept) if j != i): continue
    out.append(f'{chap}\t{" ".join(sans)}')
open(sys.argv[2], 'w').write('\n'.join(out) + '\n'); print('wrote', len(out), 'of', len(kept))
eng.quit()
