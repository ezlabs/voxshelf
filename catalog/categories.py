"""Map LibriVox's raw genres onto Voxshelf's browse categories, format flags and eras.

Edit this file to change how books are sorted into categories.
"""

CATEGORIES = {
    "Adventure": ["Action & Adventure Fiction", "Action & Adventure", "Nautical & Marine Fiction", "Westerns",
                  "Exploration", "Travel Fiction", "Sports Fiction"],
    "Mystery & Crime": ["Detective Fiction", "Crime & Mystery Fiction", "True Crime",
                        "Suspense, Espionage, Political & Thrillers"],
    "Science Fiction & Fantasy": ["Science Fiction", "Fantasy Fiction", "Fantastic Fiction"],
    "Horror & Gothic": ["Horror & Supernatural Fiction", "Gothic Fiction"],
    "Romance": ["Romance", "Erotica"],
    "Classic & Literary Fiction": ["General Fiction", "Literary Fiction", "Historical Fiction", "Humorous Fiction",
                                   "Satire", "Culture & Heritage Fiction", "Epistolary Fiction", "Family Life",
                                   "Fictional Biographies & Memoirs", "War & Military Fiction",
                                   "Classics (Greek & Latin Antiquity)", "Nature & Animal Fiction",
                                   "Literary Collections", "Single Author Collections", "Short Stories"],
    "Children & Family": ["Children's Fiction", "Children's Non-fiction", "Family", "School", "Animals",
                          "Historical", "Action & Adventure"],
    "Myths, Legends & Fairy Tales": ["Myths, Legends & Fairy Tales", "Sagas", "Epics"],
    "Poetry": ["Poetry", "Multi-version (Weekly and Fortnightly poetry)", "Lyric", "Sonnets", "Ballads",
               "Elegies & Odes", "Free Verse", "Narratives", "Single author", "Anthologies"],
    "Plays & Drama": ["Plays", "Drama", "Comedy", "Tragedy", "Dramatic Readings"],
    "History & Biography": ["History", "Biography & Autobiography", "Biography", "Memoirs", "War & Military",
                            "Letters", "Antiquity", "Middle Ages/Middle History", "Early Modern", "Modern (19th C)",
                            "Modern (20th C)", "Asian Antiquity", "Christianity - Biographies"],
    "Religion & Philosophy": ["Religion", "Christianity - Other", "Christianity - Commentary", "Christian Fiction",
                              "Religious Fiction", "Bibles", "King James Version", "American Standard Version",
                              "Young's Literal Translation", "World English Bible", "Douay-Rheims Version",
                              "Weymouth New Testament", "Other religions", "Philosophy", "Atheism & Agnosticism"],
    "Science & Nature": ["Science", "Nature", "Animals & Nature", "Life Sciences", "Earth Sciences",
                         "Astronomy, Physics & Mechanics", "Chemistry", "Mathematics", "Medical",
                         "Technology & Engineering", "Psychology", "Travel & Geography",
                         "Social Science (Culture & Anthropology)", "Political Science"],
    "Practical & Self-Help": ["Self-Help", "Cooking", "Health & Fitness", "Business & Economics", "House & Home",
                              "Crafts & Hobbies", "Gardening", "Education", "Language learning", "Reference",
                              "Family & Relationships", "Sports & Recreation", "Games", "Law", "Transportation",
                              "Writing & Linguistics", "Art, Design & Architecture", "Music", "Performing Arts",
                              "Arts", "Literary Criticism", "Essays", "Essays & Short Works", "Short non-fiction",
                              "Periodicals & Magazines", "*Non-fiction"],
}

FALLBACK = "Everything else"

SHORT_WORKS = {"Short Stories", "Short works", "Essays & Short Works", "Short non-fiction"}
COLLECTIONS = {"Anthologies", "Literary Collections", "Single Author Collections",
               "Multi-version (Weekly and Fortnightly poetry)", "Periodicals & Magazines"}
MATURE = {"Erotica"}
ERAS = {"Published before 1800": "Before 1800", "Published 1800 -1900": "1800s", "Published 1900 onward": "1900s onward"}

_genre_to_categories = {}
for category, genres in CATEGORIES.items():
    for genre in genres:
        _genre_to_categories.setdefault(genre, []).append(category)


def classify(genres, copyright_year):
    categories = []
    for g in genres:
        for c in _genre_to_categories.get(g, []):
            if c not in categories:
                categories.append(c)
    if not categories:
        categories.append(FALLBACK)
    era = next((ERAS[g] for g in genres if g in ERAS), None)
    if era is None and str(copyright_year or "").isdigit():
        year = int(copyright_year)
        era = "Before 1800" if year < 1800 else "1800s" if year < 1900 else "1900s onward"
    return {
        "categories": "|".join(categories),
        "short": 1 if SHORT_WORKS & set(genres) else 0,
        "collection": 1 if COLLECTIONS & set(genres) else 0,
        "mature": 1 if MATURE & set(genres) else 0,
        "era": era,
    }
