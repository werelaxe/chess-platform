package core

import kotlinx.serialization.Serializable


@Serializable
data class Coordinate (
        val nums: List<Int>
) {
    fun single() = nums.single()

    fun x() = nums[0]
    fun y() = nums[1]

    operator fun plus(other: Coordinate): Coordinate {
        if (nums.size != other.nums.size) {
            throw Exception("Can not sum coordinates with different sizes")
        }

        return of(nums.size) { index -> nums[index] + other.nums[index] }
    }

    operator fun div(other: Int) = Coordinate(nums.map { it / other })
    operator fun times(other: Int) = Coordinate(nums.map { it * other })

    companion object {
        fun of(size: Int, init: (Int) -> Int) = Coordinate(List(size, init))
        fun of(vararg elements: Int) = of(elements.size) { elements[it] }
    }
}

