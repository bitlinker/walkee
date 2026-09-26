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
- `reduce(state, action): XxxState` — чистая функция, разбитая **по полям** (см. ниже);
- `XxxViewModel` — держит `MutableStateFlow<XxxState>`, наружу отдаёт `state: StateFlow` и
  `dispatch(action)`. Побочные эффекты (вызовы use case'ов, навигация через `Router`)
  выполняются в `ViewModel` в ответ на `Action` и приводят к новым `Action`'ам с результатом;
- `XxxScreen(state, dispatch)` — composable без собственной логики.

### Редьюсер: по полям, а не по экшенам (2026-09-26)

Обязательно для всех редьюсеров, включая `reduceMap`. Редьюсер не разбирает экшены — он
собирает новое состояние из редьюсеров полей:

```kotlin
fun reduceHome(state: HomeState, action: HomeAction): HomeState = state.copy(
    hasLocationPermission = reduceHasLocationPermission(state.hasLocationPermission, action),
    permissionRequestPending = reducePermissionRequestPending(state.permissionRequestPending, action, ...),
)

private fun reduceHasLocationPermission(granted: Boolean, action: HomeAction): Boolean = when (action) {
    is HomeAction.PermissionResult -> action.granted
    is HomeAction.PermissionChanged -> action.granted
    else -> granted
}
```

- На каждое поле `State` — своя чистая функция `reduceПоле(значение, action)`, в `when` которой
  перечислены **все** экшены, меняющие это поле; остальные — `else -> значение`. Всё, что может
  изменить поле, видно в одном месте.
- Запрещено `when (action)` на верхнем уровне с `state.copy(a = …, b = …)` в ветках: один экшен,
  меняющий несколько полей, размазывает логику каждого поля по веткам.
- Если новое значение поля зависит от других полей, они передаются в его редьюсер отдельными
  параметрами из **прежнего** состояния (`reduceDisplayZoom(zoom, action, cellShape = state.cellShape)`),
  а не из частично собранного нового.
- Производные значения (полностью вычисляемые из других полей) в `State` не хранятся — это
  свойства `State` (`SettingsState.displayCellMetres` из `displayZoom`).

## Сервис трекинга

Foreground-сервис (ADR 0006) устроен так же, как экран. `TrackingService` — тонкий хост, как
`MainActivity`. Состояние, экшены и редьюсер (`TrackingServiceState`, `TrackingServiceAction`,
`reduceTrackingService`, по полям) — как у экрана. `TrackingServiceController` — аналог view
model: наружу только `state` и `dispatch`, с данными — только через use case'ы.
`TrackingNotification` — stateless-рендер состояния в уведомление, аналог `Screen`.

Контроллер наследует `ReduxController` — тот же контракт, что `ReduxViewModel`, но без
`ViewModel`. Его `scope` живёт, пока хост не вызовет `clear()` в `onDestroy`.

Broadcast-ресиверы (`ActivityTransitionReceiver`, `AutoStartRestoreReceiver`) — тоже точки входа
верхнего уровня: они только вызывают use case'ы.

Домен поднимает сервис через порт `TrackingForeground` (интерфейс в `domain`, реализация в
`tracking/`, связка в `di/TrackingModule`), так что стрелки зависимостей по-прежнему идут вниз.

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
  tracking/              TrackingService (+ Controller/State/Action/Reducer/Notification), ресиверы
                         activity transitions и BOOT_COMPLETED (ADR 0006)
  di/                    Hilt/Dagger modules
  domain/usecase/        use case'ы (по одному классу на операцию)
  data/map/              MapRepository
  data/location/         LocationRepository (Fused Location Provider, Activity Recognition)
  data/settings/         SettingsRepository (DataStore)
  ui/navigation/         Router, NavKeys, MainNavDisplay
  ui/screens/<screen>/   пакет на экран: <Screen>Screen.kt, <Screen>State.kt,
                         <Screen>Action.kt, <Screen>Reducer.kt, <Screen>ViewModel.kt
  ui/map/                MapViewModel, MapState, MapAction, MapReducer, MapRenderer, MapFogLayerRenderer
  ui/theme/              Material 3, жёлтый акцент
```
