"""check.py <file.tsv>: rows are `chapter<TAB>SAN moves from move 1`. Checks legality, then Stockfish (depth 14):
prints per line the eval (pawns, White's view) at the end and the biggest drop after one of White's moves."""
import sys; sys.path.insert(0, '/Users/ciaranwhyte/git/chessnutApp/data-cache/pylib')
import chess, chess.engine, re
eng = chess.engine.SimpleEngine.popen_uci('/opt/homebrew/bin/stockfish'); eng.configure({'Threads': 1, 'Hash': 64})
def ev(b): return eng.analyse(b, chess.engine.Limit(depth=14))['score'].white().score(mate_score=3000) / 100
seen = set()
for n, row in enumerate(open(sys.argv[1]), 1):
    if not row.strip() or row.startswith('#'): continue
    chap, _, moves = row.rstrip('\n').partition('\t')
    b = chess.Board(); prev = 0.2; worst = 0.0; i = 0
    try:
        for m in re.sub(r'\d+\.', ' ', moves).split():
            white = b.turn; b.push_san(m); i += 1
            if white:
                b.pop(); best = ev(b); b.push_san(m)
                e = ev(b); worst = min(worst, e - best); prev = e
            else:
                prev = ev(b)
    except Exception as ex:
        print(f'{n} ILLEGAL at ply {i + 1}: {ex}  [{chap}]'); continue
    key = moves.strip()
    flag = ' DUPLICATE' if key in seen else ''; seen.add(key)
    warn = ' <-- CHECK' if prev < -0.3 or worst < -0.4 else ''
    print(f'{n} {chap}: {i} plies, end {prev:+.2f}, worst White loss vs best {worst:+.2f}{flag}{warn}')
eng.quit()
