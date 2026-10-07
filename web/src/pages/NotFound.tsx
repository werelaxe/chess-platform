import { Link } from "react-router";

export function NotFoundPage() {
  return (
    <div className="page page--narrow" style={{ textAlign: "center" }}>
      <div className="page__eyebrow">404</div>
      <h1 className="page__title">Nothing on this square</h1>
      <p className="page__lede" style={{ margin: "12px auto 24px" }}>
        The page you asked for does not exist.
      </p>
      <Link to="/" className="btn btn--primary">
        Back to the lobby
      </Link>
    </div>
  );
}
