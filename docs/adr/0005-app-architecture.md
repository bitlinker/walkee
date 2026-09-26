# ADR 0005. Архитектура приложения

**Статус:** Accepted (2026-09-26)

## Слои и правила зависимостей

```
 ui (Compose screens, MapView host)          ─┐
   └─ ViewModel (redux: State/Action/reduce) ─┤  видят только use case'ы и Router
 map renderers (MapRenderer, MapFogLayerRenderer) ─┘
        │
 domain: use cases  ── единственная точка входа в данные для ui/renderers
        │
 data: MapRepository, LocationRepository, SettingsRepository (DataStore)
        │
 fog core: MapStorage, projection (EPSG:3395), grid math   ── чистый Kotlin, без Android
```

Стрелки только вниз. `ui`/renderers не импортируют `data` и `fog core` напрямую.

## Экран

Каждый экран — набор из:

- `XxxState` — иммутабельный data class;
- `XxxAction` — sealed interface всех событий экрана (включая навигационные намерения);
- `reduce(state, action): XxxState` — чистая функция;
- `XxxViewModel` — держит `MutableStateFlow<XxxState>`, наружу отдаёт `state: StateFlow` и
  `dispatch(action)`. Побочные эффекты (вызовы use case'ов, навигация через `Router`)
  выполняются в `ViewModel` в ответ на `Action` и приводят к новым `Action`'ам с результатом;
- `XxxScreen(state, dispatch)` — composable без собственной логики.

## Навигация

- `Router` — singleton в DI; хранит стек `StateFlow<List<NavKey>>`, методы `push/pop/replace`.
- `ViewModel` экрана в ответ на навигационный `Action` вызывает `Router`.
- `MainNavDisplay` (главный composable) подписан на стек и рендерит его через
  `NavDisplay` Navigation 3; системный «назад» → `Router.pop()`.

## Карта

- `MapViewModel` — состояние карты (камера, режим слежения, видимость слоёв) как view state;
  команды — `MapAction`. Отходит от строгого redux там, где нужен прямой доступ к рендеру.
- `MapRenderer` — владеет `MapView`/`Map`, применяет view state к камере, плейсмаркам и слоям.
- `MapFogLayerRenderer` — `TileProvider` + управление слоем тумана (ADR 0003).
- Рендереры получают данные через use case'ы (`GetFogTileUseCase`, `ObserveFogChangesUseCase`
  и т. п.), не через репозитории.

## Модули и пакеты

Базовый пакет — `me.bitlinker.walkee`. Два Gradle-модуля:

- **`:fog-core`** — чистый Kotlin/JVM без Android-зависимостей: проекция EPSG:3395, тайловая
  арифметика, `MapStorage`. Быстрые unit-тесты без эмулятора.
- **`:app`** — всё остальное (Android).

```
:fog-core   me.bitlinker.walkee.fog/
  geo/                   Epsg3395, TileMath, CellId, TileKey (quadkey)
  storage/               MapStorage, Chunk, ChunkCodec, MipLevels, AggregateIndex, ChunkStore (disk)

:app        me.bitlinker.walkee/
  WalkeeApp.kt, MainActivity.kt
  di/                    Hilt/Dagger modules
  domain/usecase/        use case'ы (по одному классу на операцию)
  data/map/              MapRepository
  data/location/         LocationRepository (Fused Location Provider)
  data/settings/         SettingsRepository (DataStore)
  ui/navigation/         Router, NavKeys, MainNavDisplay
  ui/screens/<screen>/   пакет на экран: <Screen>Screen.kt, <Screen>State.kt,
                         <Screen>Action.kt, <Screen>Reducer.kt, <Screen>ViewModel.kt
  ui/map/                MapViewModel, MapState, MapAction, MapRenderer, MapFogLayerRenderer
  ui/theme/              Material 3, жёлтый акцент
```
