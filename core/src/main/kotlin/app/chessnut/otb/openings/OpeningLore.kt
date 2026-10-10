package app.chessnut.otb.openings

/**
 * Short spoken histories for the trainer's openings. Keys are book names; a
 * variation without its own entry falls back to its family, the part before ':'.
 */
object OpeningLore {
    /** The story for [name], or its family's. */
    fun of(name: String): String? = LORE[name] ?: LORE[name.substringBefore(':').trim()]

    /** Only an entry written for exactly [name]. Used mid-game, so a family is not repeated. */
    fun exact(name: String): String? = LORE[name]

    val names: Set<String> get() = LORE.keys

    private val LORE = mapOf(
        "Italian Game" to
            "The Italian Game is one of the oldest openings on record. Italian masters such as Polerio and Greco analysed it around four hundred years ago. The bishop on c4 aims straight at f7, the weakest square in Black's camp.",
        "Ruy Lopez" to
            "The Ruy Lopez is named after Ruy López de Segura, a Spanish priest who analysed it in his book of 1561. It is also called the Spanish Game, and it has been a favourite of world champions ever since.",
        "Scotch Game" to
            "The Scotch Game takes its name from a correspondence match between Edinburgh and London in the 1820s. It had faded from top play until Garry Kasparov revived it in the 1990s.",
        "Vienna Game" to
            "The Vienna Game was a favourite of the Viennese masters of the nineteenth century. White develops the queen's knight first, keeping the f-pawn free to advance.",
        "Sicilian Defense" to
            "The Sicilian was already described by Giulio Polerio in 1594. Today it is the most popular reply to e4 among strong players. Bobby Fischer and Garry Kasparov both built their careers on it.",
        "French Defense" to
            "The French Defense is named after a correspondence match between London and Paris in the 1830s, in which the Paris players used it. Black builds a solid pawn chain and waits to strike at White's centre.",
        "Caro-Kann Defense" to
            "The Caro-Kann is named after Horatio Caro and Marcus Kann, who analysed it in the 1880s. It has a reputation for rock solid structure, and Anatoly Karpov trusted it at the very top.",
        "Slav Defense" to
            "The Slav is named after the Slavic masters who developed it. It was a key battleground in the world championship matches between Alekhine and Euwe in the 1930s.",
        "Nimzo-Indian Defense" to
            "The Nimzo-Indian is named after Aron Nimzowitsch, a leader of the hypermodern school in the 1920s. Black pins the knight on c3 and fights for the e4 square without occupying the centre.",
        "King's Indian Defense" to
            "In the King's Indian, Black lets White build a big centre, then attacks it. Bobby Fischer and Garry Kasparov both played it in their sharpest games.",
        "English Opening" to
            "The English Opening is named after Howard Staunton, the leading English player of his day, who used it in his 1843 match against Saint-Amant.",
        "London System" to
            "The London System takes its name from the London tournament of 1922, where several masters, including Capablanca, played it. It has had a big revival in recent years, with Magnus Carlsen among its fans.",
        "King's Gambit" to
            "The King's Gambit was the opening of the romantic era. The famous Immortal Game, Anderssen against Kieseritzky in London in 1851, began with it. After Spassky beat him with it in 1960, Bobby Fischer published his famous article, A Bust to the King's Gambit.",
        "King's Gambit Accepted" to
            "Black has taken the pawn. White gives up material for quick development and an open f-file aimed at f7.",
        "King's Gambit Declined: Falkbeer Countergambit" to
            "The Falkbeer Countergambit is named after Ernst Falkbeer, an Austrian master of the 1850s. Instead of taking, Black strikes back in the centre at once.",
        "Queen's Gambit" to
            "The Queen's Gambit appears in the Göttingen manuscript from around 1500, one of the oldest chess texts. It is not a true gambit, since White can usually win the pawn back.",
        "Queen's Gambit Accepted" to
            "Black has taken on c4. The pawn is hard to keep, so Black uses the time to free their game instead.",
        "Queen's Gambit Declined" to
            "The Queen's Gambit Declined is a classical defence of great solidity. In the 1927 world championship between Capablanca and Alekhine, almost every game was a Queen's Gambit Declined.",
        "Queen's Gambit Declined: Albin Countergambit" to
            "The Albin Countergambit is named after Adolf Albin. It is home to the Lasker Trap, where Black wins by promoting a pawn to a knight.",
        "Danish Gambit" to
            "In the Danish Gambit White can give away two pawns. In return White's two bishops sweep across the open board towards the black king.",
        "Scotch Game: Göring Gambit" to
            "The Göring Gambit is named after the German master Carl Göring. White offers a pawn on c3 rather than recapturing on d4.",
        "Italian Game: Evans Gambit" to
            "The Evans Gambit was invented by Captain William Evans, a Welsh sea captain, in the 1820s. Garry Kasparov famously used it to beat Viswanathan Anand in 1995.",
        "Italian Game: Two Knights Defense, Fried Liver Attack" to
            "The Fried Liver Attack appears in Italian analysis from the late 1500s. White sacrifices a knight on f7 to drag the black king into the open.",
        "Italian Game: Two Knights Defense, Traxler Counterattack" to
            "The Traxler is named after Karel Traxler, a Czech priest and problem composer, who played it in 1890. Black ignores the threat on f7 and counterattacks the white king.",
        "Petrov's Defense: Stafford Gambit" to
            "The Stafford Gambit was an obscure sideline until the streamer Eric Rosen made it famous online, thanks to its many traps.",
        "Four Knights Game: Halloween Gambit" to
            "The Halloween Gambit gets its name from the shock it gives Black. White simply gives up a knight on e5 for a big centre.",
        "Ruy Lopez: Marshall Attack" to
            "Frank Marshall is said to have saved this attack for years to use against Capablanca. When he finally sprang it in New York in 1918, Capablanca defended accurately and won.",
        "Sicilian Defense: Smith-Morra Gambit" to
            "The Smith-Morra Gambit is named after Pierre Morra of France and Ken Smith of Texas, who championed it in the mid twentieth century. White gives a pawn for fast development against the Sicilian.",
        "Englund Gambit" to
            "The Englund Gambit is named after the Swedish player Fritz Englund. It is objectively dubious, but full of traps for the unwary.",
        "Benko Gambit" to
            "The Benko Gambit is named after Pal Benko, who popularised it in the 1960s and 70s. Black gives a queenside pawn for long term pressure on the a and b files.",
        "Indian Defense: Budapest Gambit" to
            "The Budapest Gambit came from Hungarian masters. It made its name in 1918, when Milan Vidmar used it to beat Akiba Rubinstein.",
        "Bongcloud Attack" to
            "The Bongcloud began as an internet joke, a deliberately terrible king walk. Then in 2021 Magnus Carlsen and Hikaru Nakamura played it against each other in a top online event, and agreed a draw by walking their kings back and forth.",
        "The Cow" to
            "The Cow was made famous in 2023 by Anna Cramling, who wanted an opening that was short, easy to remember and still playable. The streamer Tyler1 then played it in PogChamps, and in 2024 grandmaster Platon Galperin even played it against Cramling herself in a classical game at the Reykjavik Open.",
        "Kilkenny Gambit" to
            "The Kilkenny Gambit is the nickname grandmaster Simon Williams gave this line after a game in Ireland. Its official name is the Gibbins-Weidenhagen Gambit. White throws in g4 on move two to drag Black's knight away from the centre.",
        "Irish Gambit" to
            "The Irish Gambit, also called the Chicago Gambit, gives a whole knight for a single pawn, hoping to win it back with a big centre. It is unsound, but it has caught plenty of players in fast games.",
        "King's Pawn Game: Wayward Queen Attack" to
            "The Wayward Queen brings the queen out on move two, chasing the Scholar's Mate on f7. It is a beginners' favourite, but Hikaru Nakamura has played it against top grandmasters in online blitz.",
        "Italian Game: Jerome Gambit" to
            "The Jerome Gambit is named after Alonzo Wheeler Jerome, an American amateur who published it in the 1870s. White gives up two pieces to drag the black king into the open. It is unsound, and great fun.",
        "Caro-Kann Defense: Alien Gambit" to
            "The Alien Gambit throws a knight onto f7 against the solid Caro-Kann, a defence that is famous for never allowing anything like it.",
        "Grob Opening" to
            "The Grob is named after Henri Grob, a Swiss master who played it for decades, often by correspondence. It weakens White's kingside, but it is easy to underestimate, which is half its charm.",
        "Barnes Opening: Hammerschlag" to
            "The Hammerschlag, also called the Fried Fox, walks the white king out on move two. Strong players play it online purely to show they can win anyway.",
        "Sodium Attack" to
            "The Sodium Attack is a chemistry joke. The move is knight to a3, written N a 3, and N a is the chemical symbol for sodium.",
        "Modern Defense: Bishop Attack, Monkey's Bum" to
            "The Monkey's Bum has one of the silliest names in chess. White's queen and bishop both aim at f7 against the Modern Defense, and Black must respond with care.",
        "Sicilian Defense: McDonnell Attack, Toilet Variation" to
            "Legend says the Toilet Variation was dreamt up in a toilet, which gave it its name. White gives a pawn against the Sicilian for quick development.",
        "Formation: Hippopotamus Attack" to
            "The Hippopotamus moves every pawn one step and hides behind them, like a hippo half hidden in the water. It looks passive, but it is surprisingly hard to break down.",
        "Ware Opening: Crab Variation" to
            "In the Crab, both rook pawns step forward like a pair of claws. It is purely a joke, and gives Black a free hand in the centre.",
        "Blackmar-Diemer Gambit" to
            "The Blackmar-Diemer is named after Armand Blackmar of New Orleans, and Emil Josef Diemer, who devoted much of his life to it. White gives a pawn for an open f-file and a fast attack.",
    )
}
