package core

import kotlinx.serialization.Serializable


@Serializable
data class Coordinate (
        val nums: List<Int>
) {
    fun single() = nums.single()

    operator fun plus(other: Coordinate): Coordinate {
        if (nums.size != other.nums.size) {
            throw Exception("Can not sum coordinates with different sizes")
        }

        return of(nums.size) { index -> nums[index] + other.nums[index] }
    }

    operator fun div(other: Int) = of(nums.size) { index -> nums[index] / other }

    companion object {
        fun of(size: Int, init: (Int) -> Int) = Coordinate(List(size, init))
        fun of(vararg elements: Int) = of(elements.size) { elements[it] }
    }
}

