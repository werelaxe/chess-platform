package core

data class Result (
        val isOver: Boolean,
        val winners: Set<Int>?,
        val losers: Set<Int>?
) {
    init {
        winners?.let { winners ->
            losers?.let { losers ->
                if (winners.intersect(losers).isNotEmpty()) {
                    throw Exception("Winner and losers can not intersect")
                }
            }
        }
    }
}
