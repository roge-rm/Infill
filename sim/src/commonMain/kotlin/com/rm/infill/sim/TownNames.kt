package com.rm.infill.sim


/**
 * Names for new towns, put together from parts that sound like a town of
 * around 1900: a first part and an ending run together ("Ashford",
 * "Maplewood"), or now and then two words ("Cedar Falls", "Port Elgin").
 * The same number always makes the same name.
 */
object TownNames {
    private val FIRST = listOf(
        "Ash", "Amber", "Beacon", "Birch", "Black", "Bridge", "Brook", "Cedar", "Clear", "Cold", "Copper",
        "Deer", "Dover", "East", "Elm", "Fair", "Finch", "Fox", "Glen", "Grand", "Green", "Harbour", "Hazel",
        "High", "Holly", "Iron", "Kings", "Lake", "Lark", "Long", "Maple", "Marsh", "Mill", "North", "Oak",
        "Pine", "Queens", "Red", "River", "Rock", "Rose", "Salt", "Silver", "South", "Spring", "Stone",
        "Sun", "Thorn", "Timber", "West", "White", "Willow", "Wolf", "Wood", "Wren",
    )

    private val ENDINGS = listOf(
        "ford", "field", "ton", "ville", "wood", "brook", "dale", "burg", "port", "bury", "vale", "ridge",
        "haven", "mouth", "stead", "worth", "ham", "wick", "by", "gate", "view", "mere", "cliff", "hurst",
    )

    /** Words that stand after a first part: "Cedar Falls". */
    private val AFTER = listOf("Falls", "Springs", "Mills", "Crossing", "Landing", "Junction", "Hill", "Bay", "Creek", "Rapids")

    /** Words that stand before a place name: "Port Elgin". */
    private val BEFORE = listOf("Port", "Fort", "Mount", "New", "Glen", "Lake")
    private val PLACES = listOf(
        "Elgin", "Albert", "Hope", "Carleton", "Arthur", "Stanley", "Russell", "Lorne", "Durham", "Clare",
        "Perth", "Selkirk", "Warren", "Hamilton", "Grenville", "Leslie", "Morris", "Dufferin", "Garry", "Ellis",
    )

    fun make(number: Long): String {
        val rng = Rng(number * 31 + 7)
        for (attempt in 0 until 20) {
            val name = when (rng.nextInt(10)) {
                0, 1 -> "${FIRST.pick(rng)} ${AFTER.pick(rng)}"
                2 -> "${BEFORE.pick(rng)} ${PLACES.pick(rng)}"
                else -> {
                    val first = FIRST.pick(rng)
                    val end = ENDINGS.pick(rng)
                    // A doubled letter at the join reads badly: "Willowworth", "Wooddale".
                    if (first.last().lowercaseChar() == end.first()) continue
                    first + end
                }
            }
            if (reads(name)) return name
        }
        return "Ashford"
    }

    /** Leaves out the awkward ones: a part said twice ("Millmills") or three of a letter in a row. */
    private fun reads(name: String): Boolean {
        val words = name.lowercase().split(' ')
        if (words.size == 2 && (words[0].startsWith(words[1].take(4)) || words[1].startsWith(words[0].take(4)))) return false
        if (words.size == 1) {
            val w = words[0]
            for (end in ENDINGS) if (w.endsWith(end) && w.removeSuffix(end).endsWith(end.take(3))) return false
        }
        for (i in 2 until name.length) if (name[i].lowercaseChar() == name[i - 1].lowercaseChar() && name[i] == name[i - 2]) return false
        return true
    }

    private fun <T> List<T>.pick(rng: Rng) = this[rng.nextInt(size)]
}
