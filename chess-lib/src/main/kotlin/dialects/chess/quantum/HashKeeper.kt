package dialects.chess.quantum

typealias HashType = Int;


class HashKeeper {
    private val hashes = mutableMapOf<HashType, Int>()
    private val sizes = mutableMapOf<Int, Int>()

    fun add(hash: HashType) {
        if (hash !in hashes) {
            hashes[hash] = 0
        }
        val oldSize = hashes[hash]!!
        hashes.putIfNotZero(hash, oldSize + 1)
        sizes.putIfNotZero(oldSize, sizes.getOrDefault(oldSize, 0) - 1)
        sizes.putIfNotZero(oldSize + 1, sizes.getOrDefault(oldSize + 1, 0) + 1)
    }

    fun remove(hash: HashType) {
        val oldSize = hashes[hash]!!
        hashes.putIfNotZero(hash, oldSize - 1)
        sizes.putIfNotZero(oldSize, sizes.getOrDefault(oldSize, 0) - 1)
        sizes.putIfNotZero(oldSize - 1, sizes.getOrDefault(oldSize - 1, 0) + 1)
    }

    fun change(oldHash: HashType, newHash: HashType) {
        remove(oldHash)
        add(newHash)
    }

    fun isCollapsable(): Boolean {
        if (sizes.size != 1) {
            return false
        }

        if (sizes.keys.single() == 1) {
            return false
        }

        return true
    }

    private fun <K, V> MutableMap<K, V>.putIfNotZero(key: K, value: V) {
        if (value == 0) {
            remove(key)
        } else if (key != 0) {
            this[key] = value
        }
    }
}
