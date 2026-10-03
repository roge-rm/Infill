package com.rm.infill.sim

/**
 * The parts names are made from, in one language. [after] is said after a
 * first part and [before] before a place, each as [afterPattern] and
 * [beforePattern] put them together, with %1$s and %2$s for the two words.
 */
class NameParts(
    val first: List<String>,
    val endings: List<String>,
    val after: List<String>,
    val before: List<String>,
    val places: List<String>,
    val afterPattern: String = "%1\$s %2\$s",
    val beforePattern: String = "%1\$s %2\$s",
) {
    companion object {
        /** The English parts, as the app's strings have them too. */
        val ENGLISH = NameParts(
            first = listOf(
                "Ash", "Amber", "Beacon", "Birch", "Black", "Bridge", "Brook", "Cedar", "Clear", "Cold", "Copper",
                "Deer", "Dover", "East", "Elm", "Fair", "Finch", "Fox", "Glen", "Grand", "Green", "Harbour",
                "Hazel", "High", "Holly", "Iron", "Kings", "Lake", "Lark", "Long", "Maple", "Marsh", "Mill",
                "North", "Oak", "Pine", "Queens", "Red", "River", "Rock", "Rose", "Salt", "Silver", "South",
                "Spring", "Stone", "Sun", "Thorn", "Timber", "West", "White", "Willow", "Wolf", "Wood", "Wren",
            ),
            endings = listOf(
                "ford", "field", "ton", "ville", "wood", "brook", "dale", "burg", "port", "bury", "vale", "ridge",
                "haven", "mouth", "stead", "worth", "ham", "wick", "by", "gate", "view", "mere", "cliff", "hurst",
            ),
            after = listOf("Falls", "Springs", "Mills", "Crossing", "Landing", "Junction", "Hill", "Bay", "Creek", "Rapids"),
            before = listOf("Port", "Fort", "Mount", "New", "Glen", "Lake"),
            places = listOf(
                "Elgin", "Albert", "Hope", "Carleton", "Arthur", "Stanley", "Russell", "Lorne", "Durham", "Clare",
                "Perth", "Selkirk", "Warren", "Hamilton", "Grenville", "Leslie", "Morris", "Dufferin", "Garry", "Ellis",
            ),
        )
    }
}

/**
 * Names for new towns, put together from parts that sound like a town of
 * around 1900: a first part and an ending run together ("Ashford",
 * "Maplewood"), or now and then two words ("Cedar Falls", "Port Elgin").
 * The same number always makes the same name.
 */
object TownNames {
    /** The parts in the language being shown. The app sets them from its strings. */
    var parts = NameParts.ENGLISH

    fun make(number: Long): String {
        val rng = Rng(number * 31 + 7)
        for (attempt in 0 until 20) {
            val p = parts
            val name = when (rng.nextInt(10)) {
                0, 1 -> p.afterPattern.join(p.first.pick(rng), p.after.pick(rng))
                2 -> p.beforePattern.join(p.before.pick(rng), p.places.pick(rng))
                else -> {
                    val first = p.first.pick(rng)
                    val end = p.endings.pick(rng)
                    // A doubled letter at the join reads badly: "Willowworth", "Wooddale".
                    if (first.last().lowercaseChar() == end.first()) continue
                    first + end
                }
            }
            if (reads(name)) return name
        }
        return parts.first.first() + parts.endings.first()
    }

    /** Leaves out the awkward ones: a part said twice ("Millmills") or three of a letter in a row. */
    private fun reads(name: String): Boolean {
        val words = name.lowercase().split(' ')
        if (words.size == 2 && (words[0].startsWith(words[1].take(4)) || words[1].startsWith(words[0].take(4)))) return false
        if (words.size == 1) {
            val w = words[0]
            for (end in parts.endings) if (w.endsWith(end) && w.removeSuffix(end).endsWith(end.take(3))) return false
        }
        for (i in 2 until name.length) if (name[i].lowercaseChar() == name[i - 1].lowercaseChar() && name[i] == name[i - 2]) return false
        return true
    }

    private fun <T> List<T>.pick(rng: Rng) = this[rng.nextInt(size)]

    private fun String.join(a: String, b: String) = replace("%1\$s", a).replace("%2\$s", b)
}
