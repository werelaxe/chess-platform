package ru.werelaxe.chess.core

fun board(fen: String): Board = Fen.parse(fen).board

fun sq(name: String): Square = Square.parse(name)

fun mv(uci: String): Move = Move.parseUci(uci)

fun normal(uci: String): GameMove.Normal = mv(uci).let { GameMove.Normal(it.from, it.to, it.promotion) }

fun split(from: String, first: String, second: String, promotion: PieceType? = null): GameMove.Split =
    GameMove.Split(sq(from), sq(first), sq(second), promotion)

fun observe(square: String, piece: Piece?): GameMove.Observe = GameMove.Observe(sq(square), Observation(piece))

fun piece(fenChar: Char): Piece = requireNotNull(Piece.fromFenChar(fenChar))

fun Game.play(vararg ucis: String): Game {
    for (uci in ucis) apply(normal(uci))
    return this
}
