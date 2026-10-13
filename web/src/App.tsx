import { Suspense, lazy } from "react";
import { useTranslation } from "react-i18next";
import { Route, Routes } from "react-router";
import { Layout } from "./components/Layout";
import { RequireAuth } from "./components/RequireAuth";
import { Loading } from "./components/ui";
import { AboutPage } from "./pages/About";
import { HomePage } from "./pages/Home";
import { InvitationsPage } from "./pages/Invitations";
import { LoginPage } from "./pages/Login";
import { MyGamesPage } from "./pages/MyGames";
import { NotFoundPage } from "./pages/NotFound";
import { PlayerProfilePage } from "./pages/PlayerProfile";
import { PlayersPage } from "./pages/Players";
import { RegisterPage } from "./pages/Register";
import { RulesPage } from "./pages/Rules";
import { SettingsPage } from "./pages/Settings";

// The game page pulls in the rules engine, which is by far the largest module; load it on demand.
const GamePage = lazy(() => import("./pages/Game").then((module) => ({ default: module.GamePage })));

function GameRoute() {
  const { t } = useTranslation();
  return (
    <Suspense
      fallback={
        <div className="page">
          <Loading label={t("game.loading")} />
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
        <Route path="players" element={<PlayersPage />} />
        <Route path="players/:username" element={<PlayerProfilePage />} />
        <Route element={<RequireAuth />}>
          <Route path="games/mine" element={<MyGamesPage />} />
          <Route path="invitations" element={<InvitationsPage />} />
          <Route path="settings" element={<SettingsPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
