# Timptr Status Widget

Android-приложение и home-screen виджеты для отображения текущих статусов из:

`https://check.timptr.ru/api/statuses/current`

## Что есть

- основной экран приложения со списком индикаторов;
- два варианта виджета: обычный и компактный;
- цветные индикаторы `green` / `yellow` / `red`;
- ручное обновление с экрана приложения и с кнопки виджета;
- фоновое обновление примерно раз в минуту через `AlarmManager`;
- кеш последнего успешного ответа, который показывается при ошибке сети;
- отображение времени актуальности статусов и времени последней загрузки;
- Pebble companion: Android отправляет статусы на часы через PebbleKit AppMessage;
- Pebble PBW watchface в консольном стиле.

## Сборка debug APK

```bash
export ANDROID_HOME="$HOME/android-sdk"
./gradlew assembleDebug
```

Готовый APK появляется в:

`app/build/outputs/apk/debug/check-widget-debug-build-4.apk`

Версия, добавленная в git:

`dist/check-widget-debug-build-4.apk`

Package name: `ru.timptr.statuswidget`.

## Pebble watchface

UUID Android companion и PBW: `62391359-3e79-487e-b011-c5583372b08f`.

Сборка PBW:

```bash
cd watchapp
pebble build
```

Готовый PBW:

`dist/timptr-check-watchface-build-4.pbw`

Watchface показывает крупное время по центру, батарею часов цветом, `BT` цветом и центрированные статусы `[V]`, `[!]`, `[X]` в порядке Android/API.

## Алгоритм связи Pebble

1. Watchface при старте и при восстановлении BT отправляет AppMessage `request=refresh`.
2. Android receiver подтверждает входящее сообщение через ACK и сразу отправляет кешированные статусы.
3. Android параллельно обновляет API и пушит свежие статусы с новым transaction id.
4. Android слушает `RECEIVE_ACK` / `RECEIVE_NACK` и показывает состояние на главном экране.
5. Если часы подключились заново, Android запускает watchface и принудительно отправляет последние данные.
6. Если watchface не получил данные, он повторяет запрос раз в 30 секунд и показывает `PHONE?`; без BT показывает `NO BT`.

На главном экране Android есть блок Pebble со статусом подключения, последней отправкой, ACK, запросом часов и кнопкой `Pebble: открыть и отправить`.
