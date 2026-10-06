import { BRAND_NAME } from "../brand";

export function AboutPage() {
  return (
    <div className="page page--prose prose">
      <div className="page__header reveal">
        <div className="page__eyebrow">Credits</div>
        <h1 className="page__title">About {BRAND_NAME}</h1>
        <p className="page__lede">
          A small platform for classic and quantum chess. The rules engine is one shared library that runs both in the
          browser, so your moves are validated instantly, and on the server, which stays the authority.
        </p>
      </div>
      <div className="reveal" style={{ "--i": 1 } as React.CSSProperties}>
        <h2>Credits</h2>
        <div className="credits">
          <div className="credits__item">
            <div className="credits__pieces" aria-hidden="true">
              <img src="/pieces/Chess_klt45.svg" alt="" />
              <img src="/pieces/Chess_qdt45.svg" alt="" />
              <img src="/pieces/Chess_ndt45.svg" alt="" />
              <img src="/pieces/Chess_rlt45.svg" alt="" />
            </div>
            <p>
              Chess piece artwork by{" "}
              <a href="https://en.wikipedia.org/wiki/User:Cburnett" target="_blank" rel="noreferrer">
                Colin M.L. Burnett
              </a>
              , licensed under{" "}
              <a href="https://creativecommons.org/licenses/by-sa/3.0/" target="_blank" rel="noreferrer">
                CC BY-SA 3.0
              </a>
              , from{" "}
              <a href="https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces" target="_blank" rel="noreferrer">
                Wikimedia Commons
              </a>
              .
            </p>
          </div>
          <div className="credits__item">
            <div style={{ fontFamily: "var(--font-display)", fontSize: "1.8rem", textAlign: "center" }} aria-hidden="true">
              Aa
            </div>
            <p>
              Typefaces: Fraunces by Undercase Type, Instrument Sans by Rodrigo Fuenzalida and Jordan Egstad, and IBM Plex
              Mono by IBM, all under the SIL Open Font License.
            </p>
          </div>
        </div>
        <h2>Open source</h2>
        <p>
          The rules library is written in Kotlin Multiplatform; the server runs on Ktor with PostgreSQL; this client is
          React and TypeScript. Quantum chess here follows the rules described on the <a href="/rules">rules page</a>:
          universes are merged whenever they are identical, so the universe count is always the number of truly different
          positions.
        </p>
      </div>
    </div>
  );
}
