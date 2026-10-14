"""facts.py <flags.tsv> [--depth D]: a fact sheet for each flagged move not yet explained, to write the explanation from.

For each pending flag (its key is not in core/src/main/resources/move-insights.tsv): the board before the move, the move,
the engine's best four moves there with scores (side to move), how the played move compares, the engine's line after
it, the note that was showing and the user's question. Needs python-chess (python3 -m pip install --target data-cache/pylib chess)
and Stockfish (STOCKFISH env, default /opt/homebrew/bin/stockfish). Prints to stdout.
"""
import os, sys, logging
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'data-cache', 'pylib'))
import chess, chess.engine
logging.getLogger('chess.engine').setLevel(logging.ERROR)

args = sys.argv[1:]
depth = int(args[args.index('--depth') + 1]) if '--depth' in args else 18
known = set()
for l in open(os.path.join(ROOT, 'core/src/main/resources/move-insights.tsv')):
    if l.strip() and not l.startswith('#'): known.add(l.split('\t')[0])
unesc = lambda s: s.replace('\\n', '\n').replace('\\t', '\t').replace('\\\\', '\\')
flags = []
for l in open(args[0]):
    p = l.rstrip('\n').split('\t')
    if len(p) >= 7 and p[0] not in known: flags.append(p)
eng = chess.engine.SimpleEngine.popen_uci(os.environ.get('STOCKFISH', '/opt/homebrew/bin/stockfish')); eng.configure({'Threads': 4})
lim = chess.engine.Limit(depth=depth)
cp = lambda s: s.score(mate_score=10000)
print(f'{len(flags)} flagged move(s) to explain\n')
for key, moves, source, context, note, question, at in [(p[0], p[1].split(), p[2], unesc(p[3]), unesc(p[4]), unesc(p[5]), p[6]) for p in flags]:
    b = chess.Board()
    sans = []
    for u in moves[:-1]:
        sans.append(b.san(chess.Move.from_uci(u))); b.push_uci(u)
    mv = chess.Move.from_uci(moves[-1])
    san = b.san(mv)
    n = len(sans)
    label = f"{n // 2 + 1}{'.' if n % 2 == 0 else '...'} {san}"
    print('=' * 70)
    print(f'KEY      {key}')
    print(f'WHERE    {source}: {context}')
    print(f'MOVE     {label}')
    print(f'GAME     ' + ' '.join(f'{i // 2 + 1}.{s}' if i % 2 == 0 else s for i, s in enumerate(sans + [san])))
    print(f'NOTE     {note or "-"}')
    print(f'ASKED    {question or "-"}')
    print(b)
    infos = eng.analyse(b, lim, multipv=4)
    played = None
    for i in infos:
        m = i['pv'][0]
        print(f"  engine #{i['multipv']}: {b.san(m):8} {cp(i['score'].relative):+5}   {b.variation_san(i['pv'][:7])}")
        if m == mv: played = cp(i['score'].relative)
    after = b.copy(); after.push(mv)
    ai = eng.analyse(after, lim)
    s = -cp(ai['score'].relative)
    best = cp(infos[0]['score'].relative)
    print(f"  played   : {san:8} {s:+5} (best {best:+}; difference {s - best:+})   {after.variation_san(ai['pv'][:7]) if ai.get('pv') else ''}")
    print()
eng.quit()
