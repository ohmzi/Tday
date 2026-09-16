package com.ohmz.tday.shared.listicon

/**
 * Guesses a list's glyph from its name, for lists whose owner never picked one.
 *
 * ENGLISH ONLY, and that is a decision rather than an oversight. The app ships ten
 * locales, and the i18n parity gate compares KEY SETS — it would green-light nine
 * locales of untranslated English keywords without a murmur, so the limitation has
 * to be stated here or it is stated nowhere. [RecurrencePriorityGrammar] made the
 * same call for the same class of problem in this same module and wrote it in the
 * same place. The adoption path if ten locales are ever wanted: a `listIcons`
 * namespace in `tday-web/messages/<locale>.json`, exported into commonMain the way
 * `SummaryStringBundlesGenerated.kt` already is. A nine-locale table filled with
 * English words would be worse than one that admits it is English.
 *
 * WHOLE WORDS, NEVER SUBSTRINGS. A substring matcher reads "Carpentry" as `car`,
 * "Firewood" as `fire` and "Scarlett" as `car` — a confidently wrong glyph, which
 * the brief rates worse than the generic one. So the title is cut into words at
 * every non-alphanumeric boundary and each whole word is looked up. Plurals are
 * enumerated in the table rather than stemmed: a stemmer is another thing that can
 * be wrong, and this table is small enough to just say "recipe" and "recipes".
 *
 * UNSURE RETURNS null, AND null IS NOT `inbox`. The caller keeps whatever default
 * it already had. This matters more on Android than it looks: `tdayListIconResForKey`
 * paints the inbox drawable for null, blank AND unknown alike, so a matcher that
 * answered "inbox" for "I don't know" would have destroyed the only distinction the
 * rule depends on. Two DIFFERENT keys matching is also unsure — "Work Travel" gets
 * nothing rather than a coin toss.
 *
 * Deliberately NOT in the table:
 *  - `inbox`, the default, for the reason above.
 *  - `soccer`/`baseball`/`basketball`/`football`/`tennis`, which all draw the same
 *    bare Lucide circle on every client. Inferring "Soccer" → a circle spends the
 *    user's attention on a glyph that tells them nothing.
 *  - `list`, `circle`, `square`, `triangle`, `smile`, `alert` — shapes with no
 *    subject, so no word can evidence them.
 */
object ListIconInference {

    /**
     * The keyword table, grouped by the icon key each group emits.
     *
     * Every key here must exist in the clients' 68-key icon set; `tdayListIconResForKey`
     * and its web/iOS twins fall back SILENTLY, so a typo would ship as an inbox glyph
     * and no error anywhere. `ListIconInferenceKeyParityTest` on Android is what makes
     * that mechanical instead of a promise.
     */
    private val keywordsByIconKey: Map<String, List<String>> = mapOf(
        "work" to listOf("work", "job", "jobs", "office", "career", "business", "meeting", "meetings", "clients", "client"),
        "cart" to listOf("groceries", "grocery", "shopping", "shop", "supermarket", "market", "errand", "errands"),
        "mall" to listOf("clothes", "clothing", "fashion", "outfits", "wardrobe"),
        "fitness" to listOf("gym", "fitness", "workout", "workouts", "exercise", "exercises", "weights", "training"),
        "run" to listOf("running", "jog", "jogging", "cardio", "marathon"),
        "book" to listOf("book", "books", "reading", "library", "novel", "novels"),
        "school" to listOf("school", "class", "classes", "study", "studies", "college", "university", "exam", "exams", "course", "courses", "lecture", "lectures"),
        "flight" to listOf("travel", "trip", "trips", "flight", "flights", "vacation", "holiday", "holidays", "packing"),
        "train" to listOf("train", "trains", "commute", "subway"),
        "boat" to listOf("boat", "boats", "sailing", "cruise"),
        "car" to listOf("car", "cars", "garage", "driving", "vehicle", "vehicles"),
        "home" to listOf("home", "house", "household", "apartment", "chores", "cleaning", "laundry"),
        "food" to listOf("food", "meal", "meals", "recipe", "recipes", "cooking", "dinner", "lunch", "breakfast", "kitchen"),
        "drink" to listOf("drink", "drinks", "bar", "cocktails", "wine", "beer"),
        "money" to listOf("money", "budget", "budgets", "finance", "finances", "expenses", "bills", "savings"),
        "bank" to listOf("bank", "banking", "tax", "taxes", "mortgage", "invoices"),
        "health" to listOf("health", "medical", "doctor", "doctors", "medicine", "pharmacy", "dentist", "therapy"),
        "code" to listOf("code", "coding", "dev", "development", "programming", "bugs", "engineering", "backlog"),
        "computer" to listOf("computer", "laptop", "desktop", "tech"),
        "idea" to listOf("idea", "ideas", "brainstorm", "inspiration"),
        "music" to listOf("music", "song", "songs", "playlist", "playlists", "guitar", "piano", "band"),
        "headphones" to listOf("podcast", "podcasts", "audiobooks", "audio"),
        "game" to listOf("game", "games", "gaming"),
        "camera" to listOf("photo", "photos", "photography", "camera"),
        "palette" to listOf("art", "drawing", "painting", "design", "designs", "sketches"),
        "pets" to listOf("pet", "pets", "dog", "dogs", "cat", "cats"),
        "child" to listOf("baby", "babies", "toddler", "nursery"),
        "family" to listOf("family", "families", "kids", "children", "parents"),
        "gift" to listOf("gift", "gifts", "presents", "christmas"),
        "cake" to listOf("birthday", "birthdays", "anniversary"),
        "tools" to listOf("tools", "tool", "repair", "repairs", "diy", "maintenance", "hardware", "renovation"),
        "eco" to listOf("garden", "gardening", "plants", "plant", "yard", "allotment"),
        "star" to listOf("favorites", "favourites", "favorite", "favourite"),
        "heart" to listOf("love", "wellbeing", "selfcare"),
        "chat" to listOf("chat", "messages", "calls", "followups"),
        "document" to listOf("document", "documents", "docs", "notes", "note", "paperwork", "forms", "admin"),
        "edit" to listOf("writing", "journal", "blog", "drafts", "diary"),
        "inventory" to listOf("inventory", "storage", "archive", "supplies", "stock"),
        "snow" to listOf("snow", "ski", "skiing", "winter"),
        // "firewood" is deliberately absent. It is the word that proves the matcher is
        // not a substring matcher, and a table entry for it would retire that evidence.
        "fire" to listOf("fire", "bonfire"),
        "drop" to listOf("water", "hydration"),
        "umbrella" to listOf("rain", "monsoon"),
        "sun" to listOf("summer", "morning", "beach"),
        "schedule" to listOf("routine", "routines", "reminders", "deadlines", "timers"),
        "calendar" to listOf("calendar", "events", "event", "agenda", "planner"),
        "flag" to listOf("goal", "goals", "priorities", "milestones", "targets"),
        "check" to listOf("todo", "todos", "task", "tasks", "checklist"),
        "key" to listOf("password", "passwords", "keys", "security", "accounts", "logins"),
        "city" to listOf("city", "neighbourhood", "neighborhood", "moving"),
        "bookmark" to listOf("bookmarks", "saved", "links"),
    )

    /** Flattened once: word → the single key it evidences. */
    private val iconKeyByKeyword: Map<String, String> =
        keywordsByIconKey.entries
            .flatMap { (iconKey, words) -> words.map { word -> word to iconKey } }
            .toMap()

    /** Every key this matcher can ever emit — the surface the parity tests assert against. */
    val emittableIconKeys: Set<String> = keywordsByIconKey.keys

    /** Every word the table recognises, for the duplicate-word guard in tests. */
    internal val keywordTable: Map<String, List<String>> = keywordsByIconKey

    /**
     * The inferred icon key for [title], or null when the title evidences nothing or
     * evidences two different glyphs. Never returns the default key.
     *
     * `lowercase()` without a locale on purpose: this is a lookup against an English
     * table, and the locale-sensitive overload turns "I" into a dotless ı under a
     * Turkish default locale, which would make the same title infer differently on two
     * phones holding the same list.
     */
    fun inferIconKey(title: String?): String? {
        if (title.isNullOrBlank()) return null

        var found: String? = null
        for (word in wordsOf(title)) {
            val iconKey = iconKeyByKeyword[word] ?: continue
            if (found == null) {
                found = iconKey
            } else if (found != iconKey) {
                // Two subjects, one glyph slot. "Work Travel" is a briefcase and a
                // plane with equal warrant, and picking the leftmost would only be
                // hiding the coin toss behind reading order.
                return null
            }
        }
        return found
    }

    /**
     * The title cut into lowercase alphanumeric runs.
     *
     * Hand-rolled rather than `Regex`, because the Unicode property classes this
     * would need (`\p{L}`) are the part of the regex surface that differs between
     * the JVM and Native engines this module compiles for, and a splitter that
     * behaves differently on iOS is a matcher that infers differently on iOS.
     * [Char.isLetterOrDigit] is common-stdlib and carries no such caveat.
     */
    private fun wordsOf(title: String): List<String> {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        for (char in title.lowercase()) {
            if (char.isLetterOrDigit()) {
                word.append(char)
            } else if (word.isNotEmpty()) {
                words += word.toString()
                word.clear()
            }
        }
        if (word.isNotEmpty()) words += word.toString()
        return words
    }
}
