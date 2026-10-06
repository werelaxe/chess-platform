import { Suspense, lazy } from "react";
import { Route, Routes } from "react-router";
import { Layout } from "./components/Layout";
import { RequireAuth } from "./components/RequireAuth";
import { Loading } from "./components/ui";
import { AboutPage } from "./pages/About";
import { HomePage } from "./pages/Home";
import { LoginPage } from "./pages/Login";
import { MyGamesPage } from "./pages/MyGames";
import { NotFoundPage } from "./pages/NotFound";
import { RegisterPage } from "./pages/Register";
import { RulesPage } from "./pages/Rules";

// The game page pulls in the rules engine, which is by far the largest module; load it on demand.
const GamePage = lazy(() => import("./pages/Game").then((module) => ({ default: module.GamePage })));

function GameRoute() {
  return (
    <Suspense
      fallback={
        <div className="page">
          <Loading label="Loading game" />
        </div>
      }
    >
      <GamePage />
    </Suspense>
  );
}

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="rules" element={<RulesPage />} />
        <Route path="about" element={<AboutPage />} />
        <Route path="games/:id" element={<GameRoute />} />
        <Route element={<RequireAuth />}>
          <Route path="games/mine" element={<MyGamesPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
