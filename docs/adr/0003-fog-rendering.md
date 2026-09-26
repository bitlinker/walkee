# ADR 0003. Рендер тумана через тайловый API MapKit

**Статус:** Accepted (2026-09-26)

## Контекст

MapKit 4.x предоставляет для пользовательских слоёв единственный официальный путь —
`Map.addTileLayer(layerId, LayerOptions, CreateTileDataSource)`:

- `TileDataSourceBuilder`: `setTileFormat(PNG | JPG | VECTOR2 | VECTOR3 | GEO_JSON)`,
  `setProjection`, `setZoomRanges(List<ZoomRange>)` (zMin включительно, zMax исключительно),
  `setTileProvider(WeakReference<TileProvider>)`, `setTileUrlProvider`;
- `TileProvider.load(TileId, Version, features, etag): RawTile` — `@WorkerThread`, вызывается
  конкурентно;
- `RawTile(version, features, etag, useCache, state, rawData)`;
- обновление: `layer.dataSourceLayer().clear()` — «clears all cached tiles and starts new
  requests for tiles that are displayed». `Layer.invalidate(version)` из 3.x удалён.

Известные проблемы: SIGSEGV после нескольких минут работы с PNG-провайдером
(mapkit-android-demo #149, вероятно из-за `WeakReference` на провайдер), OOM на GeoJSON-тайлах
с тысячами объектов (#153), отсутствие официальных примеров (#419).

Отвергнутые альтернативы: `PolygonMapObject` «туман с дырками» (нужна polygon union тысяч
ячеек, плохая тесселяция больших полигонов); своя `View` поверх карты (отстаёт от жестов на
кадр); GeoJSON-тайлы (недокументированные стили, OOM) — оставлены как эксперимент.

## Решение

1. Слой тумана — `addTileLayer` с `TileFormat.PNG`, `Projections.getWgs84Mercator()`,
   `ZoomRange(0, MAX)`; `LayerOptions`: `transparent = true`, `cacheable = false`,
   `tileAppearingAnimationDuration = 0`, `nightModeAvailable = false`,
   `overzoomMode = ENABLED`.
2. `TileProvider` реализует `MapFogLayerRenderer`; сильная ссылка на провайдер хранится в
   рендерере на всё время жизни слоя.
3. Рендер тайла `(x, y, z)` при `Zs = 20`, `Zk = 12`, `Zc = 18`:
   - `z ≥ Zc` — тайл внутри одной ячейки отображения: сплошной туман или прозрачный;
   - `Zk ≤ z < Zc` — ячейка отображения = `2^(8 − (Zc − z))` px; биты берутся из одного чанка,
     свёртываются 4×4 → z18 по правилу порога и рисуются прямоугольниками в ARGB-буфер;
   - `z < Zk` — тайл покрывает несколько чанков; используются агрегаты/mip:
     `alpha = fogAlpha · (1 − covered / total)` на пиксель или блок.
   Стиль (жёсткие пиксели vs мягкий туман) — параметр рендерера.
4. Инвалидация: `MapStorage.changes` → троттлинг (не чаще ~1 раз/с, только если изменённые
   чанки пересекают viewport) → `dataSourceLayer().clear()` на UI-потоке.
5. Кодирование: на старте `Bitmap.compress(PNG)`; при необходимости — собственный кодер
   индексированного 1-битного PNG через `java.util.zip.Deflater` (~0.2 мс на тайл).

## Проверенные детали API (MapKit 4.45.0-lite, по `classes.jar`)

- `Map.addCameraListener/removeCameraListener(WeakReference<CameraListener>)` — все слушатели
  карты передаются через `WeakReference`; сильные ссылки держит наш код.
- `TileProvider.load(TileId, Version, Map<String,String>, String): RawTile`.
- `RawTile(Version, Map<String,String> features, String etag, UseCache, State, byte[])`;
  `RawTile.State = { OK, NOT_MODIFIED, ERROR }`, `RawTile.UseCache = { YES, NO }`.
- `TileFormat` живёт в `com.yandex.mapkit.layers`; `ZoomRange`, `TileId`, `Version`, `RawTile` —
  в `com.yandex.mapkit`.
- `UserLocationLayer` (входит в lite): `setVisible`, `setHeadingModeActive`.

## Спайк (обязателен до основной реализации)

1. Слой с провайдером-заглушкой, 10 минут зума/скролла — воспроизводится ли #149.
2. Стоимость и визуальные артефакты `clear()`; проверить `Version`/`versionSupport` как
   альтернативу точечной инвалидации.
3. Что приходит в `features`, какого размера просят тайлы на hi-DPI.
4. Сверка собственной реализации EPSG:3395 с `map.projection().worldToXY` — узнать единицы
   `XYPoint`.

## Последствия

- Весь рендер тумана изолирован в `MapFogLayerRenderer`; смена формата (PNG → GeoJSON/vector)
  не затрагивает хранилище.
- Полная перезагрузка видимых тайлов на каждое обновление — плата за простоту; при ~20
  видимых тайлах и стоимости тайла в единицы мс приемлемо.
