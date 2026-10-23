"""Sort LibriVox's raw genres into Voxshelf's two-level browse: a shelf (top tab), then subjects.

Every book lands on exactly one shelf, so fiction and nonfiction never mix.
Edit the tables below to change where books go.
"""

SHELVES = ["Fiction", "Nonfiction", "Poetry & Plays", "Kids"]

FICTION = {
    "Classics & Literary": ["General Fiction", "Literary Fiction", "Culture & Heritage Fiction", "Epistolary Fiction",
                            "Family Life", "Fictional Biographies & Memoirs", "Nature & Animal Fiction"],
    "Adventure & Westerns": ["Action & Adventure Fiction", "Nautical & Marine Fiction", "Westerns", "Travel Fiction",
                             "Sports Fiction"],
    "Mystery & Crime": ["Detective Fiction", "Crime & Mystery Fiction", "Suspense, Espionage, Political & Thrillers"],
    "Science Fiction & Fantasy": ["Science Fiction", "Fantasy Fiction", "Fantastic Fiction"],
    "Horror & Gothic": ["Horror & Supernatural Fiction", "Gothic Fiction"],
    "Romance": ["Romance", "Erotica"],
    "Historical Fiction": ["Historical Fiction", "War & Military Fiction"],
    "Humour & Satire": ["Humorous Fiction", "Satire"],
    "Short Stories": ["Short Stories"],
    "Myths & Legends": ["Myths, Legends & Fairy Tales", "Sagas"],
    "Religious Fiction": ["Christian Fiction", "Religious Fiction"],
}

NONFICTION = {
    "History": ["History", "War & Military", "Antiquity", "Middle Ages/Middle History", "Early Modern",
                "Modern (19th C)", "Modern (20th C)", "Asian Antiquity", "True Crime"],
    "Biography & Memoir": ["Biography & Autobiography", "Biography", "Memoirs", "Christianity - Biographies"],
    "Philosophy": ["Philosophy", "Ancient", "Medieval", "Modern", "Contemporary", "Atheism & Agnosticism"],
    "Religion & Spirituality": ["Religion", "Christianity - Other", "Christianity - Commentary", "Bibles",
                                "King James Version", "American Standard Version", "Young's Literal Translation",
                                "World English Bible", "Douay-Rheims Version", "Weymouth New Testament",
                                "Other religions"],
    "Politics & Society": ["Political Science", "Social Science (Culture & Anthropology)", "Law", "Education",
                           "Business & Economics"],
    "Science": ["Science", "Life Sciences", "Earth Sciences", "Astronomy, Physics & Mechanics", "Chemistry",
                "Mathematics", "Medical", "Technology & Engineering", "Psychology"],
    "Nature & Animals": ["Nature", "Animals & Nature"],
    "Travel & Exploration": ["Travel & Geography", "Exploration"],
    "Essays & Letters": ["Essays", "Essays & Short Works", "Short non-fiction", "Letters", "Periodicals & Magazines"],
    "Arts & Criticism": ["Literary Criticism", "Art, Design & Architecture", "Music", "Performing Arts", "Arts",
                         "Writing & Linguistics"],
    "Practical & Self-Help": ["Self-Help", "Cooking", "Health & Fitness", "House & Home", "Crafts & Hobbies",
                              "Gardening", "Language learning", "Reference", "Family & Relationships",
                              "Sports & Recreation", "Games", "Transportation"],
}
GENERAL_NONFICTION = "General Nonfiction"  # tagged nonfiction but with no specific subject

POETRY_PLAYS = {
    "Poetry": ["Poetry", "Lyric", "Sonnets", "Ballads", "Elegies & Odes", "Free Verse", "Narratives", "Single author",
               "Epics"],
    "Poems of the Week": ["Multi-version (Weekly and Fortnightly poetry)"],
    "Plays & Drama": ["Plays", "Drama", "Comedy", "Tragedy"],
}

# LibriVox files these under Children's; several reuse generic names.
KIDS_FICTION = {"Children's Fiction", "Action & Adventure", "Historical", "Family", "School", "General", "Animals"}
KIDS_NONFICTION = {"Children's Non-fiction"}
KIDS_FAIRY_TALES = {"Myths, Legends & Fairy Tales"}

# Genres that don't say fiction or nonfiction; used only when nothing else does.
NEUTRAL = {"Classics (Greek & Latin Antiquity)": "Classics & Literary", "Literary Collections": "Short Stories",
           "Single Author Collections": "Short Stories", "Anthologies": "Short Stories"}

FALLBACK = "Everything else"

SHORT_WORKS = {"Short Stories", "Short works", "Essays & Short Works", "Short non-fiction"}
COLLECTIONS = {"Anthologies", "Literary Collections", "Single Author Collections",
               "Multi-version (Weekly and Fortnightly poetry)", "Periodicals & Magazines"}
MATURE = {"Erotica"}
ERAS = {"Published before 1800": "Before 1800", "Published 1800 -1900": "1800s", "Published 1900 onward": "1900s onward"}


def _matches(table, genres):
    return [subject for subject, names in table.items() if genres & set(names)]


def candidates(genres):
    """Every shelf a book's genres point to, with its subjects there. Merge these across recordings of one book."""
    g = set(genres)
    kids = []
    if g & KIDS_FICTION:
        kids.append("Children's Stories")
    if g & KIDS_NONFICTION:
        kids.append("Children's Nonfiction")
    if kids and g & KIDS_FAIRY_TALES:
        kids.append("Fairy Tales & Fables")
    nonfiction = _matches(NONFICTION, g)
    if not nonfiction and "*Non-fiction" in g:
        nonfiction = [GENERAL_NONFICTION]
    return {
        "Kids": kids,
        "Poetry & Plays": _matches(POETRY_PLAYS, g),
        "Fiction": _matches(FICTION, g),
        "Nonfiction": nonfiction,
        "neutral": list(dict.fromkeys(NEUTRAL[x] for x in genres if x in NEUTRAL)),
    }


def merge(cands_list):
    merged = {}
    for cands in cands_list:
        for shelf, subjects in cands.items():
            bucket = merged.setdefault(shelf, [])
            bucket += [s for s in subjects if s not in bucket]
    return merged


def resolve(cands):
    """Pick one shelf: Kids, then Poetry & Plays, then Fiction (fiction wins over nonfiction tags), then Nonfiction."""
    for shelf in ("Kids", "Poetry & Plays", "Fiction", "Nonfiction"):
        if cands.get(shelf):
            return shelf, cands[shelf]
    if cands.get("neutral"):
        return "Fiction", cands["neutral"]
    return "", [FALLBACK]


def flags(genres):
    g = set(genres)
    return {
        "short": 1 if SHORT_WORKS & g else 0,
        "collection": 1 if COLLECTIONS & g else 0,
        "mature": 1 if MATURE & g else 0,
        "era": next((ERAS[x] for x in genres if x in ERAS), None),
    }
