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
     *
     * Each group lists the basic names for the thing first and then the generic words people
     * actually file lists under, at least five per icon (`ListIconInferenceTest` holds that
     * floor). One-line-per-key is load-bearing: `list-icon-inference-parity.test.ts` reads
     * this file with a regex that wants a whole `"key" to listOf(...)` on a single line.
     *
     * A word earns its place only if it rarely pairs with another icon's word in the same
     * title, because two different icons in one title means no icon at all. That is why
     * these are NOT here, though each is a plausible list name: `video` (a camera, or
     * "Video Games"), `auto` ("Auto Repair" is tools), `project(s)` ("Home Projects" is a
     * house), `plans` ("Travel Plans" is a trip), `rent` (a home, or a bill), `date` (a
     * heart, or a calendar), `list` ("Reading List" is books), `ice` ("Ice Cream"),
     * `homework`, `firewood` and `carpentry` (each is proof the matcher is whole-word).
     */
    private val keywordsByIconKey: Map<String, List<String>> = mapOf(
        "work" to listOf("work", "job", "jobs", "office", "career", "business", "meeting", "meetings", "clients", "client", "workplace", "colleagues", "coworkers", "interview", "interviews", "employer", "freelance", "professional", "sales", "boss", "presentation", "presentations"),
        "cart" to listOf("groceries", "grocery", "shopping", "shop", "supermarket", "market", "errand", "errands", "shops", "store", "stores", "buy", "purchase", "purchases", "wishlist", "deals", "coupons", "retail"),
        "mall" to listOf("clothes", "clothing", "fashion", "outfits", "wardrobe", "shoes", "dresses", "accessories", "jewelry", "jewellery", "style", "apparel", "sneakers"),
        "fitness" to listOf("gym", "fitness", "workout", "workouts", "exercise", "exercises", "weights", "training", "lifting", "yoga", "pilates", "crossfit", "stretching", "hiit", "reps", "bodybuilding", "calisthenics", "sports"),
        "run" to listOf("running", "jog", "jogging", "cardio", "marathon", "runner", "runners", "jogger", "5k", "10k", "triathlon", "treadmill", "sprinting"),
        "book" to listOf("book", "books", "reading", "library", "novel", "novels", "literature", "fiction", "nonfiction", "textbook", "textbooks", "bookshelf", "ebook", "ebooks", "magazines", "manga", "comics"),
        "school" to listOf("school", "class", "classes", "study", "studies", "college", "university", "exam", "exams", "course", "courses", "lecture", "lectures", "assignments", "assignment", "student", "students", "teacher", "teachers", "semester", "learning", "education", "lessons", "lesson", "tutorial", "tutorials", "thesis", "research", "tuition", "syllabus", "flashcards", "quiz", "quizzes", "degree"),
        "flight" to listOf("travel", "trip", "trips", "flight", "flights", "vacation", "holiday", "holidays", "packing", "airport", "airline", "passport", "itinerary", "hotel", "hotels", "luggage", "getaway", "abroad", "vacations"),
        "train" to listOf("train", "trains", "commute", "subway", "railway", "metro", "tram", "transit", "commuting", "bus", "buses"),
        "boat" to listOf("boat", "boats", "sailing", "cruise", "ship", "ships", "yacht", "ferry", "kayak", "canoe", "fishing", "marina", "sailboat"),
        "car" to listOf("car", "cars", "garage", "driving", "vehicle", "vehicles", "automotive", "mechanic", "tires", "tyres", "parking", "fuel", "petrol", "truck", "trucks", "driver"),
        "home" to listOf("home", "house", "household", "apartment", "chores", "cleaning", "laundry", "housework", "housekeeping", "condo", "dishes", "decor", "furniture", "bedroom", "bathroom"),
        "food" to listOf("food", "meal", "meals", "recipe", "recipes", "cooking", "dinner", "lunch", "breakfast", "kitchen", "baking", "bakery", "snacks", "snack", "brunch", "restaurant", "restaurants", "takeout", "menu", "mealprep", "cookbook"),
        "drink" to listOf("drink", "drinks", "bar", "cocktails", "wine", "beer", "cocktail", "coffee", "tea", "juice", "beverage", "beverages", "whiskey", "pub", "brewery", "liquor", "smoothies"),
        "money" to listOf("money", "budget", "budgets", "finance", "finances", "expenses", "bills", "savings", "cash", "income", "salary", "payment", "payments", "spending", "investing", "investment", "investments", "crypto", "debt", "loan", "loans", "wallet", "payday", "paycheck"),
        "bank" to listOf("bank", "banking", "tax", "taxes", "mortgage", "invoices", "invoice", "insurance", "accounting", "accountant", "irs", "pension", "retirement", "audit"),
        "health" to listOf("health", "medical", "doctor", "doctors", "medicine", "pharmacy", "dentist", "therapy", "meds", "medication", "medications", "prescription", "prescriptions", "hospital", "clinic", "checkup", "vitamins", "symptoms", "surgery", "nurse", "dental", "physio", "vaccine", "vaccines"),
        "code" to listOf("code", "coding", "dev", "development", "programming", "bugs", "engineering", "backlog", "software", "developer", "developers", "devops", "git", "github", "api", "repo", "repos", "bug", "frontend", "backend", "deploy", "deployment", "javascript", "python", "refactor"),
        "computer" to listOf("computer", "laptop", "desktop", "tech", "pc", "computers", "laptops", "technology", "gadgets", "gadget", "electronics", "devices", "wifi", "internet", "network", "router", "printer", "keyboard", "monitor"),
        "idea" to listOf("idea", "ideas", "brainstorm", "inspiration", "thoughts", "thought", "brainstorming", "innovation", "concepts", "concept", "someday", "maybe", "inspo"),
        "music" to listOf("music", "song", "songs", "playlist", "playlists", "guitar", "piano", "band", "concert", "concerts", "album", "albums", "lyrics", "drums", "violin", "singing", "karaoke", "spotify", "vinyl", "choir", "instruments", "bands"),
        "headphones" to listOf("podcast", "podcasts", "audiobooks", "audio", "audiobook", "listening", "radio", "headphones", "headphone", "earbuds", "episodes", "episode", "airpods"),
        "game" to listOf("game", "games", "gaming", "videogames", "videogame", "xbox", "playstation", "nintendo", "steam", "boardgames", "boardgame", "puzzle", "puzzles", "chess", "esports", "console", "consoles", "arcade", "ps5"),
        "camera" to listOf("photo", "photos", "photography", "camera", "pictures", "picture", "photographer", "photoshoot", "lens", "gallery", "filming", "selfies"),
        "palette" to listOf("art", "drawing", "painting", "design", "designs", "sketches", "artwork", "artist", "illustration", "illustrations", "sketch", "paint", "crafts", "craft", "crochet", "knitting", "sewing", "pottery", "ceramics", "calligraphy", "doodles", "creative", "creativity"),
        "pets" to listOf("pet", "pets", "dog", "dogs", "cat", "cats", "puppy", "puppies", "kitten", "kittens", "vet", "hamster", "hamsters", "rabbit", "rabbits", "parrot", "bird", "birds", "aquarium", "litter", "grooming"),
        "child" to listOf("baby", "babies", "toddler", "nursery", "infant", "newborn", "diapers", "diaper", "stroller", "crib", "preschool", "daycare", "babysitter", "nanny", "toys", "toy", "pregnancy", "maternity"),
        "family" to listOf("family", "families", "kids", "children", "parents", "kid", "child", "mom", "dad", "mother", "father", "grandparents", "grandma", "grandpa", "siblings", "sister", "brother", "relatives", "cousins", "husband", "wife", "spouse", "parenting"),
        "gift" to listOf("gift", "gifts", "presents", "christmas", "present", "xmas", "santa", "surprise", "surprises", "wrapping", "registry"),
        "cake" to listOf("birthday", "birthdays", "anniversary", "cake", "cakes", "party", "parties", "celebration", "celebrations", "bday", "anniversaries", "cupcakes", "dessert", "desserts"),
        "tools" to listOf("tools", "tool", "repair", "repairs", "diy", "maintenance", "hardware", "renovation", "fix", "fixes", "toolbox", "handyman", "plumbing", "plumber", "electrician", "woodworking", "remodel", "remodeling", "construction", "wrench", "drill", "upgrades"),
        "eco" to listOf("garden", "gardening", "plants", "plant", "yard", "allotment", "lawn", "flowers", "flower", "herbs", "seeds", "greenhouse", "compost", "trees", "nature", "environment", "recycling", "recycle", "sustainability"),
        "star" to listOf("favorites", "favourites", "favorite", "favourite", "starred", "favs", "fav", "best", "highlights", "special", "vip"),
        "heart" to listOf("love", "wellbeing", "selfcare", "romance", "dating", "valentine", "valentines", "wedding", "weddings", "relationship", "relationships", "gratitude", "mindfulness", "meditation", "wellness", "kindness", "charity", "volunteering", "donation", "donations", "couples"),
        "chat" to listOf("chat", "messages", "calls", "followups", "message", "texts", "email", "emails", "reply", "replies", "callbacks", "contacts", "phone", "calling", "whatsapp", "slack", "conversation", "conversations", "dms", "followup", "friends", "friend", "social", "catchup"),
        "document" to listOf("document", "documents", "docs", "notes", "note", "paperwork", "forms", "admin", "files", "file", "papers", "letters", "letter", "contracts", "contract", "legal", "reports", "report", "resume", "cv", "templates", "template", "records", "filing", "scans"),
        "edit" to listOf("writing", "journal", "blog", "drafts", "diary", "write", "essay", "essays", "article", "articles", "story", "stories", "poem", "poems", "poetry", "blogging", "newsletter", "manuscript", "editing", "draft", "journaling", "journals", "memoir", "screenplay"),
        "inventory" to listOf("inventory", "storage", "archive", "supplies", "stock", "warehouse", "boxes", "pantry", "stockpile", "shelves", "declutter", "decluttering", "organizing", "organize"),
        "snow" to listOf("snow", "ski", "skiing", "winter", "snowboard", "snowboarding", "sledding", "snowy", "frost", "blizzard", "skates", "skating", "snowman"),
        // "firewood" is deliberately absent. It is the word that proves the matcher is
        // not a substring matcher, and a table entry for it would retire that evidence.
        "fire" to listOf("fire", "bonfire", "campfire", "fireplace", "flame", "flames", "bbq", "barbecue", "grill", "grilling", "fireworks", "wildfire", "streak", "streaks", "trending"),
        "drop" to listOf("water", "hydration", "hydrate", "swimming", "swim", "pool", "pools", "shower", "bath", "faucet", "leak", "leaks", "irrigation", "ocean", "lake", "river", "sea"),
        "umbrella" to listOf("rain", "monsoon", "rainy", "raining", "storm", "storms", "weather", "forecast", "umbrella", "umbrellas", "drizzle", "flood", "floods", "hurricane", "typhoon", "thunderstorm"),
        "sun" to listOf("summer", "morning", "beach", "sunny", "sunshine", "sunrise", "sunset", "sunscreen", "outdoors", "outdoor", "picnic", "daytime", "heatwave", "seaside", "coast", "sunbathing"),
        "schedule" to listOf("routine", "routines", "reminders", "deadlines", "timers", "schedule", "schedules", "timetable", "habits", "habit", "timeline", "daily", "weekly", "monthly", "reminder", "deadline", "timer", "shifts", "rota", "alarm", "alarms", "clock"),
        "calendar" to listOf("calendar", "events", "event", "agenda", "planner", "appointments", "appointment", "reservations", "reservation", "rsvp", "meetups", "meetup", "calendars"),
        "flag" to listOf("goal", "goals", "priorities", "milestones", "targets", "priority", "milestone", "target", "objectives", "objective", "okr", "okrs", "important", "urgent", "resolutions", "resolution", "mission", "achievements", "focus"),
        "check" to listOf("todo", "todos", "task", "tasks", "checklist", "checklists", "action", "actions", "pending", "todolist", "outstanding", "unfinished"),
        "key" to listOf("password", "passwords", "keys", "security", "accounts", "logins", "key", "login", "account", "credentials", "passcode", "passcodes", "privacy", "vault", "secrets", "secret", "lock", "locks", "2fa", "encryption", "authentication"),
        "city" to listOf("city", "neighbourhood", "neighborhood", "moving", "cities", "town", "downtown", "urban", "relocation", "relocating", "relocate", "movers", "neighbors", "neighbours", "community", "suburbs", "skyline"),
        "bookmark" to listOf("bookmarks", "saved", "links", "bookmark", "link", "urls", "url", "websites", "website", "resources", "resource", "references", "reference", "clippings", "readlater", "bookmarked", "tabs"),
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
