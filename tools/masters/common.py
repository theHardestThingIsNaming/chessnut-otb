"""Shared paths and tables for the master-game tools.

Tools live in data-cache/masters/tools and work files in data-cache/masters (git-ignored, kept
across sessions): <folder>-lines.tsv, -cand.tsv, -pgn.tsv from MasterPickTest, prep sheets, notes and out/*.rows.
python-chess is installed into data-cache/pylib: python3 -m pip install --target data-cache/pylib chess
"""
import os, sys

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..'))
DATA = os.path.join(REPO, 'data-cache', 'masters')
GAMES = os.path.join(REPO, 'core', 'src', 'main', 'resources', 'master-games.tsv')
STOCKFISH = os.environ.get('STOCKFISH', '/opt/homebrew/bin/stockfish')
sys.path.insert(0, os.path.join(REPO, 'data-cache', 'pylib'))

# Each opening's work-folder name: its catalog name, your side, and its PGN Mentor folder.
OPENINGS = {
    'english': ('English Opening', 'w'), 'ruy': ('Ruy Lopez', 'w'), 'italian': ('Italian Game', 'w'),
    'scotch': ('Scotch Game', 'w'), 'london': ('London System', 'w'), 'vienna': ('Vienna Game', 'w'),
    'bishop': ("Bishop's Opening", 'w'), 'sicilian': ('Sicilian Defense', 'b'), 'french': ('French Defense', 'b'),
    'kid': ("King's Indian Defense", 'b'), 'pirc': ('Pirc Defense', 'b'), 'slav': ('Slav Defense', 'b'),
    'nimzo': ('Nimzo-Indian Defense', 'b'),
}


def data(name):
    return os.path.join(DATA, name)


# The White repertoire against 1.e4 (white-chapters.tsv: key, chapter): each chapter is the opening "Repertoire: <chapter>".
_wc = os.path.join(DATA, 'white-chapters.tsv')
if os.path.exists(_wc):
    for _l in open(_wc):
        _k, _c = _l.rstrip('\n').split('\t')
        OPENINGS[_k] = ('Repertoire: ' + _c, 'w')
