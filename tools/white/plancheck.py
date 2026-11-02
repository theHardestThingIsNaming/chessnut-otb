"""plancheck.py <plans.tsv>: rows `chapter<TAB>plan name<TAB>before (SAN)<TAB>moves (SAN, Black's first)<TAB>summary<TAB>notes joined by ' ¦ '`.
Checks: legal; `before` has an odd number of plies and starts at least one line of that chapter in white-repertoire.tsv;
one note per move; White's moves (Stockfish depth 14) lose at most 0.6 pawns against the best move. Prints the lines
of each chapter that no plan covers."""
import sys; sys.path.insert(0, '/Users/ciaranwhyte/git/chessnutApp/data-cache/pylib')
import chess, chess.engine, collections
R = '/Users/ciaranwhyte/git/chessnutApp'
lines = collections.defaultdict(list)
for l in open(R + '/core/src/main/resources/white-repertoire.tsv'):
    if l.strip() and not l.startswith('#'):
        c, _, m = l.rstrip('\n').split('\t'); lines[c].append(m.split())
eng = chess.engine.SimpleEngine.popen_uci('/opt/homebrew/bin/stockfish'); eng.configure({'Threads': 1, 'Hash': 64})
def ev(b): return eng.analyse(b, chess.engine.Limit(depth=14))['score'].white().score(mate_score=3000) / 100
covered = collections.defaultdict(set)
for n, row in enumerate(open(sys.argv[1]), 1):
    if not row.strip() or row.startswith('#'): continue
    chap, name, before, moves, summary, notes = row.rstrip('\n').split('\t')
    before, moves, notes = before.split(), moves.split(), notes.split(' ¦ ')
    errs = []
    if len(before) % 2 == 0: errs.append('before must end on a White move (odd plies)')
    hit = [i for i, l in enumerate(lines[chap]) if l[:len(before)] == before]
    if not hit: errs.append('before starts no line of this chapter')
    covered[chap].update(hit)
    if len(notes) != len(moves): errs.append(f'{len(moves)} moves but {len(notes)} notes')
    b = chess.Board(); worst = 0.0; end = None
    try:
        for m in before: b.push_san(m)
        for i, m in enumerate(moves):
            if i % 2 == 1:
                b.pop() if False else None
            white = b.turn
            if white:
                best = ev(b); b.push_san(m); worst = min(worst, ev(b) - best)
            else:
                b.push_san(m)
        end = ev(b)
    except Exception as ex:
        errs.append(f'illegal move {m}: {ex}')
    if worst < -0.6: errs.append(f'White loses {worst:+.2f} on one move')
    print(f'{n} {chap} / {name}: covers {len(hit)} lines, {len(moves)} moves, end {end if end is None else format(end, "+.2f")}' + (' ERR ' + '; '.join(errs) if errs else ''))
for c, ls in lines.items():
    if c in {r.split('\t')[0] for r in open(sys.argv[1]) if r.strip()}:
        miss = [i for i in range(len(ls)) if i not in covered[c]]
        if miss: print(f'UNCOVERED in {c}: lines {miss}')
eng.quit()
