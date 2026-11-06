# Times Tables

Android app for drilling multiplication tables, and the divisions they make, in the car
on the way to school: two numbers, a big keypad, a chime for right and an "uh-oh" for
wrong so the driver can follow along by ear, and a log of every session.

- **Setup:** multiplication, division or both; the tables and times ranges (7 × 1–12,
  2–10 × 2–10…), a top score, a session length, an idle stop, a time limit per answer,
  a wait and a points penalty after a wrong answer. The last settings are remembered.
- **Session:** questions come from a shuffled deck, so every question in the range is
  asked once before any repeats. Division turns each fact round (21 ÷ 7 or 21 ÷ 3), so
  every answer is whole; with both, each fact comes both ways, never one right after
  the other. Pause, stop, and the screen stays on. Leaving the app pauses. Reaching the
  top score ends with a congratulations screen and a fanfare.
- **History:** every session with each answer and how long it took, and the facts
  missed most often.

Sounds play on the media stream, so on the car's Bluetooth they come from the speakers.

## Layout

- `domain/` – plain Kotlin, no Android: the drill rules (`Drill`, an immutable value
  moved on by `answer`, `tick`, `pause`…), the question deck, the session log format
  and the sounds, synthesized as samples. All unit tested.
- `app/` – Compose UI, the ViewModel and its 100 ms ticker, `SoundPlayer`
  (`AudioTrack`) and `SessionStore`, which appends one JSON line per session to
  `files/sessions.jsonl`.

`./gradlew :domain:test :app:testDebugUnitTest` runs the tests. Pushing to `master`
builds a signed release for Obtainium, as in the other apps.
