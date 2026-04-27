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
- отображение времени актуальности статусов и времени последней загрузки.

## Сборка debug APK

```bash
export ANDROID_HOME="$HOME/android-sdk"
./gradlew assembleDebug
```

Готовый APK появляется в:

`app/build/outputs/apk/debug/app-debug.apk`

Package name: `ru.timptr.statuswidget`.
