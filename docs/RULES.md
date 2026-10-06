# Rules

## Classic chess

Standard FIDE rules: pieces move as usual, castling requires an unmoved king and rook, empty
squares between them and no attacked square on the king's path; pawns may capture en passant
immediately after an enemy pawn's double step; a pawn reaching the last rank promotes to a
queen, rook, bishop or knight of the player's choice.

The game ends by checkmate, stalemate (draw), insufficient material (draw), fifty moves by each
side without a capture or pawn move (draw), threefold repetition of a position (draw),
resignation or agreement.

## Quantum chess

Quantum chess is classic chess played on a superposition of boards. Think of it as many
parallel universes, each holding an ordinary chess position. At the start there is one
universe. Each universe has a probability, and a piece "half on e4, half on e2" simply means
that it stands on e4 in half of the universes and on e2 in the other half.

### Moves

On your turn you may do one of three things.

**Normal move.** Pick a piece and a destination. The move is carried out in every universe
where it is legal by classic rules. In universes where it is impossible (the piece is not
there, the path is blocked, your king would be left in check), nothing happens: that universe
simply passes the turn. A move is allowed as long as it is legal in at least one universe.

**Split.** Pick a piece and two destinations (or one destination and "stay"). Every universe
where both options are legal splits into two equally likely universes, one per option. A
universe where only one option is legal follows that option; where neither is legal nothing
happens. After a split the piece is in superposition.

**Observe.** Pick any square whose content is uncertain. The universe "collapses": one of the
possible contents is drawn at random according to its probability, and every universe that
disagrees with the result disappears. Observing costs your turn.

### Check, capture and the end of the game

Check is respected inside each universe: a move that would leave your king in check in some
universe is not carried out there. Because of this, a move can leave your king in check in
the universes where it could not be played, and your opponent may capture the king there.

A king that was captured in some universes still exists in the others. You lose only when
your king is gone from **every** universe. You also lose when you have no legal move in any
universe while being in check in at least one of them; having no legal move anywhere without
check is a draw, like stalemate.

Draws also happen after fifty moves by each side without a capture or pawn move in any
universe, by threefold repetition of the whole superposition, by agreement or when one side
resigns (which is a loss for that side).

### Reading the board

A piece drawn with full strength is certain. A translucent piece with a percentage is present
with that probability; a square can show several candidates. The side panel lists every
possibility for the square under the cursor, including the chance that it is empty. Identical
universes are merged automatically, so the number of universes shown is the number of truly
different positions.
