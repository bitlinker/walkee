# Walkee

Игра для исследования города на Android: карта Яндекс MapKit, закрытая «туманом войны», который
рассеивается там, где вы прошли. Прогресс, ачивки — впереди.

- Архитектурные решения: [`docs/adr/`](docs/adr/README.md).
- Модули: `:fog-core` (проекция EPSG:3395, тайловая арифметика, хранилище тумана — чистый JVM),
  `:app` (Android, Compose, Hilt, Navigation 3).

## Сборка

1. Получите ключ MapKit и положите его в `local.properties` (файл не коммитится):
   ```
   MAPKIT_API_KEY=ваш-ключ
   ```
2. `./gradlew :app:assembleDebug`
3. Тесты: `./gradlew :fog-core:test :app:testDebugUnitTest`

Требования: JDK 17+, Android SDK с платформой API 37.
