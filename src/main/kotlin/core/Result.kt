package core

data class Result (
        val isOver: Boolean,
        val winners: Set<Player>?,
        val losers: Set<Player?>
) {
    init {
        winners?.intersect(losers)?.isNotEmpty()?.let {
            if (it) throw Exception("Winner and losers can not intersect")
        }
    }
}
