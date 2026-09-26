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
   - `z ≥ Zc` — тайл внутри одной ячейки отображения: сплошной туман или подкраска открытой области;
   - `Zk ≤ z < Zc` — ячейка отображения = `2^(8 − (Zc − z))` px; биты берутся из одного чанка,
     свёртываются 4×4 → z18 по правилу порога и рисуются прямоугольниками в ARGB-буфер;
   - `z < Zk` — тайл покрывает несколько чанков; используются агрегаты/mip.
   Скрытые клетки — туман `#1B1B1F` с непрозрачностью 0.75; открытые — подкраска акцентным
   жёлтым `#FFD600` с непрозрачностью 0.1. Частично открытая клетка на мелких зумах смешивает
   их по площади: `alpha = fogAlpha · h + tintAlpha · (1 − h)`, цвет — среднее, взвешенное по
   вкладу каждого, где `h` — доля скрытых клеток. `h` квантуется до 256 уровней, чтобы тайл
   всегда помещался в палитру PNG. Полностью скрытые и полностью открытые тайлы кодируются
   один раз на стиль.
4. Инвалидация: `MapStorage.changes` → debounce 400 мс → **версионная инвалидация**
   `TileDataSource.invalidate(version)` на UI-потоке (см. «Обновление без мерцания»).
   `dataSourceLayer().clear()` — только запасной путь.
5. Кодирование: собственный кодер палитрового PNG (`IndexedPngEncoder`, colour type 3 + tRNS,
   deflate `BEST_SPEED`) вместо `Bitmap.compress`.

## Обновление без мерцания (проверено на устройстве, 2026-09-26)

`DataSourceLayer.clear()` сразу выбрасывает все тайлы слоя: на доли секунды сквозь пустой слой
видна светлая подложка — белая вспышка на каждом обновлении. Рабочая схема:

- `LayerOptions.versionSupport = true`;
- `layer.dataSourceLayer().setDataSourceListener(WeakReference(listener))` — MapKit сразу
  вызывает `onDataSourceUpdated(BaseDataSource)`; объект приводится к
  `com.yandex.mapkit.layers.TileDataSource` (в документации его нет, есть в `classes.jar`);
- обновление — `tileDataSource.invalidate("<растущий номер>")`: тайлы перезапрашиваются,
  старые остаются на экране до прихода новых;
- каждый тайл отдаётся с etag = хэш стиля + `FogCoverage.fingerprint()` (FNV-1a 64); на
  перезапросе совпавший etag → `RawTile.State.NOT_MODIFIED` без рендера и кодирования.

Тайлы отдаются с `RawTile.UseCache.YES`: MapKit держит их в памяти, и возврат на уже
посещённый зум не требует перезапросов. Актуальность обеспечивают версия и etag, а не сброс
кеша. Постоянный кеш на диске (`LayerOptions.cacheable`) выключен, чтобы после перезапуска не
показывались тайлы прошлой сессии: счётчик версий начинается заново.

## Пропуски тайлов при скролле и отдалении (эксперименты, 2026-09-26)

MapKit запрашивает тайлы только для своего окна и только текущего зума. Там, где ни на одном
зуме ещё нет загруженного тайла, слой тумана на время загрузки (≈ 0.1 с) не рисует ничего, и
видна подложка. Замеры (запись кадров экрана, доля светлых пикселей):

- отдаление на уже посещённый зум — без вспышек (благодаря `UseCache.YES`);
- отдаление на новый зум — ~3 кадра со светлыми прямоугольниками по краям; в центре, где был
  прежний экран, overzoom подставляет растянутые тайлы высокого зума;
- приближение — без вспышек (overzoom из родительских тайлов работает).

Что пробовали:

- `OverzoomMode.WITH_PREFETCH` — заметного эффекта нет, оставлен `ENABLED`;
- **overscan**: `MapView` больше экрана на 64–80 dp с каждой стороны, `MapWindow.focusRect` и
  `Logo.setPadding` — на видимую часть. Убирает кромки при обычном скролле, но GPU рисует
  ~1.4–1.5× пикселей и отдаление всё равно мелькает. **Отложен**; вернуться, если кромки при
  скролле станут проблемой.

- **ночная подложка** (`Map.isNightModeEnabled = true`) при тёмном тумане — вспышки темнее, но
  по-прежнему заметны (карта ночью не однотонная), а сама карта хуже читается. **Отказались.**

- **светлый туман в цвет подложки** (`#EBE7E1` — заливка городских кварталов дневной карты,
  непрозрачность 0.9) — пропуск тайла почти незаметен, но тёмный туман значительно красивее.
  **Отказались**; к нему можно вернуться, если вспышки станут проблемой.

**Принято: тёмный туман на дневной карте, вспышки при отдалении на новый зум допустимы.**

Запасной вариант, если понадобится: второй «страховочный» слой тайлов низкого зума под
основным с разделением прозрачности (размывает края открытых клеток на крупных зумах).

## Что туман не перекрывает (проверено на устройстве, 2026-09-26)

- **3D-здания** рисуются поверх любых тайловых слоёв и торчат из тумана на z ≥ 16.
  `Map.set2DMode(true)` делает их плоскими — они уходят под туман. Включено всегда.
- **Подписи, иконки POI, номера домов, остановки** рисуются отдельным проходом поверх всего:
  и тайлового слоя, и `MapObject` (полигон с `zIndex = 1e6` тоже оказался под подписями).
  API для z-порядка тайлового слоя нет (`Layer` — только `remove/dataSourceLayer/isValid`).
  Скрыть их можно только глобально стилем карты. Решение: `Map.setMapStyle` везде, в том числе
  в открытых областях, прячет POI и номера домов
  (`{"tags":{"any":["poi","address"]},"stylers":{"visibility":"off"}}`), а остальные подписи —
  названия улиц, районов, остановки — рисует полупрозрачными
  (`{"elements":"label","stylers":{"opacity":0.5}}`). Фильтр `"types":"point"` с исключением
  топонимов номера домов не прячет.
- Изредка остаются серые кружки на месте скрытых объектов — источник пока не выяснен.

## Проверенные детали API (MapKit 4.45.0-lite, по `classes.jar`)

- `Map.addCameraListener/removeCameraListener(WeakReference<CameraListener>)` — все слушатели
  карты передаются через `WeakReference`; сильные ссылки держит наш код.
- `TileProvider.load(TileId, Version, Map<String,String>, String): RawTile`.
- `RawTile(Version, Map<String,String> features, String etag, UseCache, State, byte[])`;
  `RawTile.State = { OK, NOT_MODIFIED, ERROR }`, `RawTile.UseCache = { YES, NO }`.
- `TileFormat` живёт в `com.yandex.mapkit.layers`; `ZoomRange`, `TileId`, `Version`, `RawTile` —
  в `com.yandex.mapkit`.
- `UserLocationLayer` (входит в lite): `setVisible`, `setHeadingModeActive`.
- `Map.set2DMode(boolean)`, `Map.setMapStyle(String)`, `Map.setPoiLimit(Integer)`,
  `Map.isNightModeEnabled` — есть в lite.

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
- Каждое обновление перезапрашивает все видимые тайлы, но неизменившиеся отвечают
  `NOT_MODIFIED`; полный рендер тайла на устройстве — ~1.5–2 мс (покрытие + палитровый PNG).
