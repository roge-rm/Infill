package com.rm.infill.sim

/** A save that can't be read: not a city, from a newer version, or cut short. */
class SaveError(message: String) : Exception(message)

/**
 * A city as bytes and back. The file is a short header, then everything the
 * city needs to carry on exactly as it would have: the date, money, weather,
 * random numbers, history, map layers (run-length packed) and buildings in
 * the order they were built. What follows from those (power, road reach, the
 * map's copy of each building) is worked out again on loading.
 */
object SaveGame {
    const val VERSION = 36
    private const val MAGIC = 0x494E464C // "INFL"

    fun write(city: City): ByteArray {
        val w = SaveWriter()
        w.int(MAGIC)
        w.int(VERSION)
        city.writeTo(w)
        return w.bytes()
    }

    fun read(bytes: ByteArray): City {
        val r = SaveReader(bytes)
        try {
            if (r.int() != MAGIC) throw SaveError("not a saved city")
            val version = r.int()
            if (version > VERSION) throw SaveError("saved by a newer version ($version)")
            return City.readFrom(r, version)
        } catch (e: IndexOutOfBoundsException) {
            throw SaveError("the file is cut short")
        }
    }

    /** Just the name, date and population, for listing saves without loading them. */
    fun summary(bytes: ByteArray): SaveSummary? = try {
        val r = SaveReader(bytes)
        if (r.int() != MAGIC || r.int() > VERSION) null
        else City.readSummary(r)
    } catch (e: Exception) {
        null
    }
}

class SaveSummary(val name: String, val year: Int, val month: Int, val population: Int, val funds: Long)

internal class SaveWriter {
    private var buf = ByteArray(1 shl 16)
    private var size = 0

    private fun room(n: Int) {
        if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
    }

    fun byte(v: Int) {
        room(1)
        buf[size++] = v.toByte()
    }

    fun int(v: Int) {
        room(4)
        for (k in 3 downTo 0) buf[size++] = (v shr (k * 8)).toByte()
    }

    fun long(v: Long) {
        int((v ushr 32).toInt())
        int(v.toInt())
    }

    fun bool(v: Boolean) = byte(if (v) 1 else 0)

    /** A number from 0 up, in as few bytes as it needs. */
    fun count(v: Int) {
        var x = v
        while (x >= 0x80) {
            byte((x and 0x7f) or 0x80)
            x = x ushr 7
        }
        byte(x)
    }

    fun string(s: String) {
        val b = s.encodeToByteArray()
        count(b.size)
        room(b.size)
        b.copyInto(buf, size)
        size += b.size
    }

    /** A layer as runs of the same value. */
    fun layer(a: ByteArray) {
        count(a.size)
        var i = 0
        while (i < a.size) {
            var run = 1
            while (i + run < a.size && a[i + run] == a[i]) run++
            count(run)
            byte(a[i].toInt())
            i += run
        }
    }

    /** A layer of small numbers as runs of the same value. */
    fun shorts(a: ShortArray) {
        count(a.size)
        var i = 0
        while (i < a.size) {
            var run = 1
            while (i + run < a.size && a[i + run] == a[i]) run++
            count(run)
            count(a[i].toInt() and 0xffff)
            i += run
        }
    }

    fun bytes(): ByteArray = buf.copyOf(size)
}

internal class SaveReader(private val buf: ByteArray) {
    private var at = 0

    fun byte(): Int = buf[at++].toInt() and 0xff

    fun int(): Int {
        var v = 0
        repeat(4) { v = (v shl 8) or byte() }
        return v
    }

    fun long(): Long = (int().toLong() shl 32) or (int().toLong() and 0xffffffffL)

    fun bool() = byte() != 0

    fun count(): Int {
        var v = 0
        var shift = 0
        while (true) {
            val b = byte()
            v = v or ((b and 0x7f) shl shift)
            if (b < 0x80) return v
            shift += 7
            if (shift > 28) throw SaveError("a number is too long")
        }
    }

    fun shorts(a: ShortArray) {
        if (count() != a.size) throw SaveError("a layer is the wrong size")
        var i = 0
        while (i < a.size) {
            val run = count()
            val v = count().toShort()
            if (run <= 0 || i + run > a.size) throw SaveError("a layer runs off the end")
            a.fill(v, i, i + run)
            i += run
        }
    }

    fun string(): String {
        val n = count()
        val s = buf.decodeToString(at, at + n)
        at += n
        return s
    }

    fun layer(into: ByteArray) {
        val n = count()
        if (n != into.size) throw SaveError("a map layer is the wrong size")
        var i = 0
        while (i < n) {
            val run = count()
            val v = byte().toByte()
            if (i + run > n) throw SaveError("a map layer runs over")
            into.fill(v, i, i + run)
            i += run
        }
    }
}
