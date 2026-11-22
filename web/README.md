# UNO Chess Web

A full-parity React + TypeScript + Vite web port of **UNO Chess** (matching the Android Jetpack Compose app in gameplay, visual design, procedural Web Audio SFX, and Firebase Firestore online multiplayer cross-play).

## Quick Start

```bash
cd web
npm install
npm run dev
```

Open `http://localhost:3000`.

## Production Build

```bash
cd web
npm run build
npm run preview
```

## Online Multiplayer (Web ↔ Android Cross-Play)

1. Copy `web/.env.example` to `web/.env`.
2. Fill in your Firebase Web App credentials from Firebase Console (`unochess-online` project).
3. Restart the dev server or rebuild — Web and Android players share the exact same Firestore `/lobbies/{code}` protocol and can join each other's 6-character room codes.
