# Walkee — notes for coding agents

Android city-exploration game: Yandex MapKit map with a fog of war revealed by walking.

## Read first
- `docs/adr/` — all architecture decisions. **0004** (stack & code rules) and **0005** (layering,
  packages) are binding; 0001–0003 explain the grid, storage and rendering design.

## Hard rules (summary of ADR 0004/0005)
- Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`); AGP 9 built-in Kotlin — do
  not apply `kotlin.android` in modules.
- Modules: `:fog-core` (pure JVM, no Android imports) and `:app`.
- Single Activity; MapKit `MapView` beneath, Compose (Material 3, yellow accent) on top;
  Navigation 3 via `Router` → `MainNavDisplay`.
- Coroutines/Flow everywhere; wrap platform callbacks at the boundary (`callbackFlow`).
- DI: Hilt. Every screen: own package under `ui/screens/<name>/` with `State`, `Action`,
  `Reducer` (pure `reduce(state, action)`, split **per field, not per action**: it only
  assembles `state.copy(field = reduceField(state.field, action), …)`; see ADR 0005),
  `ViewModel : ReduxViewModel` exposing only
  `state` + `dispatch`, and a stateless `Screen(state, dispatch)` composable.
- Navigation and side effects happen in `ViewModel.onAction`, results come back as actions.
- ViewModels and renderers talk to data only through use cases in `domain/usecase/`.
- Data: `MapStorage` (fog-core) ← `MapRepository`; `LocationRepository`; `SettingsRepository`
  (DataStore). Room only when a real need appears.
- Map code: `ui/map/MapRenderer` (camera, layers, placemarks) and `MapFogLayerRenderer` (tiles).
- Tracking runs in a foreground service (`tracking/TrackingService`, type `location`) built like a
  screen: `TrackingServiceController : ReduxController` + State/Action/Reducer, rendered by
  `TrackingNotification`. Auto-start on walking/running via Activity Recognition (ADR 0006).
- Tests: JUnit 5 + coroutines-test + Turbine; keep algorithmic code in `:fog-core` or in pure
  classes so it is testable on the JVM.
- Docs in Russian; code, comments, identifiers and commit messages in English.
- Ask the user before changing the stack or adding a major dependency.

## Build
- `./gradlew :fog-core:test :app:testDebugUnitTest` — fast checks.
- `./gradlew :app:assembleDebug` — needs `MAPKIT_API_KEY` in `local.properties`.
