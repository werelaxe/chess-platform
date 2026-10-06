import { Link } from "react-router";

/** Player-facing rules; the text mirrors docs/RULES.md. */
export function RulesPage() {
  return (
    <div className="page page--prose prose">
      <div className="page__header reveal">
        <div className="page__eyebrow">How to play</div>
        <h1 className="page__title">Rules</h1>
        <p className="page__lede">Two variants share one board: classic chess, and quantum chess played on a superposition of boards.</p>
      </div>

      <div className="reveal" style={{ "--i": 1 } as React.CSSProperties}>
        <h2 id="classic">Classic chess</h2>
        <p>
          Standard FIDE rules: pieces move as usual, castling requires an unmoved king and rook, empty squares between them
          and no attacked square on the king's path; pawns may capture en passant immediately after an enemy pawn's double
          step; a pawn reaching the last rank promotes to a queen, rook, bishop or knight of the player's choice.
        </p>
        <p>
          The game ends by checkmate, stalemate (draw), insufficient material (draw), fifty moves by each side without a
          capture or pawn move (draw), threefold repetition of a position (draw), resignation or agreement.
        </p>
      </div>

      <div className="reveal" style={{ "--i": 2 } as React.CSSProperties}>
        <h2 id="quantum">Quantum chess</h2>
        <p className="lead">
          Quantum chess is classic chess played on a superposition of boards. Think of it as many parallel universes, each
          holding an ordinary chess position. At the start there is one universe. Each universe has a probability, and a
          piece "half on e4, half on e2" simply means that it stands on e4 in half of the universes and on e2 in the other
          half.
        </p>

        <h3>Moves</h3>
        <p>On your turn you may do one of three things.</p>
        <p>
          <strong>Normal move.</strong> Pick a piece and a destination. The move is carried out in every universe where it
          is legal by classic rules. In universes where it is impossible (the piece is not there, the path is blocked, your
          king would be left in check), nothing happens: that universe simply passes the turn. A move is allowed as long as
          it is legal in at least one universe.
        </p>
        <p>
          <strong>Split.</strong> Pick a piece and two destinations (or one destination and "stay"). Every universe where
          both options are legal splits into two equally likely universes, one per option. A universe where only one option
          is legal follows that option; where neither is legal nothing happens. After a split the piece is in superposition.
        </p>
        <p>
          <strong>Observe.</strong> Pick any square whose content is uncertain. The universe "collapses": one of the
          possible contents is drawn at random according to its probability, and every universe that disagrees with the
          result disappears. Observing costs your turn.
        </p>

        <h3>Check, capture and the end of the game</h3>
        <p>
          Check is respected inside each universe: a move that would leave your king in check in some universe is not
          carried out there. Because of this, a move can leave your king in check in the universes where it could not be
          played, and your opponent may capture the king there.
        </p>
        <p>
          A king that was captured in some universes still exists in the others. You lose only when your king is gone from{" "}
          <strong>every</strong> universe. You also lose when you have no legal move in any universe while being in check
          in at least one of them; having no legal move anywhere without check is a draw, like stalemate.
        </p>
        <p>
          Draws also happen after fifty moves by each side without a capture or pawn move in any universe, by threefold
          repetition of the whole superposition, by agreement or when one side resigns (which is a loss for that side).
        </p>

        <h3>Reading the board</h3>
        <p>
          A piece drawn with full strength is certain. A translucent piece with a percentage is present with that
          probability; a square can show several candidates. The side panel lists every possibility for the square under
          the cursor, including the chance that it is empty. Identical universes are merged automatically, so the number of
          universes shown is the number of truly different positions.
        </p>
        <div className="legend">
          <img src="/pieces/Chess_nlt45.svg" alt="" style={{ width: 48, height: 48 }} />
          <p>A piece at full opacity is on that square in every universe.</p>
          <img src="/pieces/Chess_nlt45.svg" alt="" style={{ width: 48, height: 48, opacity: 0.7 }} />
          <p>
            A translucent piece with a percentage badge and an amber ring is there only with that probability; the ring
            fills proportionally.
          </p>
          <span
            aria-hidden="true"
            style={{ width: 48, height: 48, borderRadius: 6, border: "2px dashed var(--quantum)", display: "block" }}
          />
          <p>In Observe mode, squares whose content is uncertain are outlined; click one to measure it.</p>
        </div>
      </div>

      <p className="muted reveal" style={{ "--i": 3 } as React.CSSProperties}>
        Ready? <Link to="/">Find a game in the lobby.</Link>
      </p>
    </div>
  );
}
