# OmaApp — One Shell. Every Device.

[![CI](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml/badge.svg)](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml) · [English](README.md) · [Русский](README_RU.md)

![Баннер проекта OmaApp](assets/hero.svg)

OmaApp — Android-лаунчер и прямой компаньон Omarchy на Qt Quick. Он напрямую подключается к одному плагину Omarchy и сохраняет локальные данные лаунчера и кэш оформления для автономной работы. Проверка на физическом Pixel/GrapheneOS ещё предстоит.

## Текущее состояние

- Android-приложение использует Qt 6.10+, C++ и QML. Оно получает список приложений Android, запускает их, использует системные иконки, поддерживает поиск, dock закреплённых приложений, папки, сетку из 3–5 колонок, свайпы между Apps/Devices/Style/Settings, выбор HOME и сканирование QR по запросу.
- Сопряжение выполняется напрямую по HTTPS с одним плагином Omarchy. При первом сопряжении нужно подтвердить компьютер в OmaApp и телефон в плагине; приложение закрепляет сертификат компьютера из QR-кода и хранит токен в Android Keystore.
- После QR-доверия приложение в открытом состоянии обнаруживает тот же плагин через DNS-SD. Новый локальный IP сохраняется только после совпадения закреплённого отпечатка и успешного token-authenticated TLS-запроса; multicast не содержит pairing secret или токен.
- Плагин читает цвета Omarchy, `shell.toml`, обои, градиенты рамок Hyprland, скругление, rounding power, интервалы, анимации и Bezier-кривые. Изменения темы и рабочих пространств доставляются событиями; медленный foreground-refresh раз в 30 секунд покрывает системные показатели и пропущенные события. Последняя тема остаётся доступной офлайн.
- Буфер обмена, передача файлов, MPRIS, показатели ПК, переключение рабочих пространств и пользовательские команды защищены независимыми разрешениями на каждое устройство. Буфер работает по нажатию в открытом приложении; Android использует системный выбор файлов, лимит — 100 МиБ; произвольные shell-команды не принимаются.
- На домашнем экране есть drag-and-drop приложений между страницами и Dock, динамические Android shortcuts, настройки жестов и локальный backup/restore. Добавлены локальный каталог/редактор OWL, исходный компилятор и runtime OWL, а также Canvas-рендерер для ограниченного набора автономных виджетов. Список приложений, исходники виджетов и раскладка доступны без компьютера.
- Виброотклик Home можно включить отдельно: он использует системный `View.performHapticFeedback` при запуске приложений и редактировании домашнего экрана. По умолчанию он отключён, настройка сохраняется локально и в backup, а итоговое поведение зависит от настроек Android.
- Интерфейс на русском и английском работает офлайн; язык выбирается по Android или в Settings. Список приложений обновляется при системных событиях установки и удаления пакета.
- Android-виджеты используют системный выбор и настоящий `AppWidgetHostView` внутри того же APK. OmaApp сохраняет версионированные координаты и размер на отдельной сетке каждой страницы, привязывает перемещение к шагу 24 dp, переносит виджеты между страницами и запрещает их перекрытие. Удержание ручки у края экрана открывает соседнюю страницу для перетаскивания; последнее изменение раскладки можно отменить, а перед удалением требуется подтверждение. Доступны кнопки позиционирования и нативная ручка изменения размера в углу; остальные нажатия RemoteViews остаются активны. Переносимый backup восстанавливает placeholder и требует новой системной привязки Android. Иконки приложений и OWL пока остаются в отдельных областях Home. CI на OpenGL/Vulkan проверяет системный выбор, нажатия RemoteViews, миграцию схемы, управление сеткой, изменение размера, восстановление процесса, placeholder backup и привязку с новым ID.
- Сейчас поддерживается автономное подмножество OWL: layout, текст и базовые элементы управления, Canvas-фигуры, пути, преобразования, clipping и градиенты. Виджеты с shell-командами, сетью, assets, фоновыми триггерами, обработчиками нажатий, секретами или sampled state показывают ошибку в редакторе и не исполняются.
- Настольная версия — только предпросмотр. Список приложений на ней не подменяется фиктивными данными.
- Сетевой слушатель запускается при запросе QR-кода или наличии сохранённых устройств. Python-помощник и генератор QR входят в состав одного плагина; отдельная программа или системная служба не устанавливаются.
- OmaApp не устанавливает Arch Linux ARM или PRoot и не включает Quickshell executable или Linux/Wayland-модули в APK.
- Текущая итерация улучшает DNS-SD-переподключение при смене адреса на активных IPv4-интерфейсах, обновляет mDNS memberships при смене интерфейсов, использует обновления Android NSD на API 34+ и Android 13 T extension 7+ и сохраняет старый путь для предыдущих версий. Она также проверяет масштаб системного текста, повторные переключения языка и QML binding loops; при тайм-ауте готовности Home CI сохраняет диагностику Android и Qt. Текущий статус сборок — в [GitHub Actions](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml). APK из CI предназначен для тестов; подписанный релиз этой итерацией не заявляется.
- В Android-приложение не включён Quickshell: приложение использует Qt Quick, а плагин Omarchy исполняется внутри Quickshell.

PR #17 остаётся Draft. Последний удалённый CI #311 на `4685588` прошёл Desktop и arm64, но оба emulator smoke завершились ошибкой; последующие исправления требуют нового полного прогона всех четырёх jobs на одном HEAD. Локально проходят 183 Python-тестов, проверка манифеста плагина, синтаксис shell, компиляция Python и whitespace. В этой среде нет CMake, Qt lint toolchain, Android SDK, эмулятора или подключённого телефона для полной локальной сборки. Реальное сопряжение Android с Omarchy и поведение на Pixel 10 / GrapheneOS не проверены. Скриншоты Settings ниже сняты в CI run #201.

### Скриншоты эмулятора

Экран Settings на русском снят в разрешении 412 × 892 в обоих графических backend во время CI run #201.

![Settings на русском, OpenGL](docs/screenshots/ci-201-opengl-settings-ru.png)

![Settings на русском, Vulkan](docs/screenshots/ci-201-vulkan-settings-ru.png)

![Home, OpenGL](docs/screenshots/ci-201-opengl-home.png)

![Home, Vulkan](docs/screenshots/ci-201-vulkan-home.png)

## Сборка настольного предпросмотра

Нужны CMake 3.24+, Ninja, Qt 6.10+ с Qt Quick Controls 2, Qt ShaderTools и Qt Test, а также C++17.

    cmake -S . -B build/desktop -GNinja -DCMAKE_BUILD_TYPE=Debug
    cmake --build build/desktop
    ctest --test-dir build/desktop --output-on-failure

Запустите созданный executable `omaapp`, чтобы открыть оболочку и GPU-предпросмотр скруглений.

## Сборка Android APK

Android-цель использует Qt for Android arm64, JDK 17, Android SDK 36 и NDK 27.2.12479018. GitHub Actions устанавливает этот toolchain и собирает APK.

Для локальной сборки настройте CMake с Qt Android arm64 и путями к Android SDK/NDK, затем соберите цель apk:

    qt-cmake -S . -B build/android -GNinja -DANDROID_SDK_ROOT=/path/to/Android/Sdk -DANDROID_NDK_ROOT=/path/to/Android/Sdk/ndk/27.2.12479018
    cmake --build build/android --target apk

Инструкция установки и проверки на Pixel 10 с GrapheneOS находится в [docs/GRAPHENEOS.md](docs/GRAPHENEOS.md). Текущий commit ещё не установлен на физический телефон.

## Установка плагина Omarchy

В клоне репозитория на компьютере с Omarchy выполните:

    ./omarchy-plugin/install.sh

Установщик проверяет Python 3.11+, OpenSSL и Omarchy, затем проверяет манифест и копирует один плагин в пользовательский каталог Omarchy. Вспомогательный процесс принадлежит этому плагину; отдельный сервер или системная служба не устанавливаются. Инструкции по сопряжению — в [руководстве плагина](omarchy-plugin/README_RU.md) и [описании протокола](docs/PAIRING.md).

## План и ограничения

См. [архитектуру](docs/ARCHITECTURE.md), [инструкцию установки](docs/INSTALLATION.md), [сопряжение и синхронизацию](docs/PAIRING.md), [статус GrapheneOS](docs/GRAPHENEOS.md), [совместимость](docs/compatibility.md), [модель угроз](docs/threat-model.md) и [план реализации](tasks/plan.md). Первый тестовый релиз настроен как GitHub Pre-release и требует секретов подписи, принадлежащих владельцу репозитория; без них workflow не публикует APK.

OmaApp — независимый проект, не являющийся официальным продуктом Omarchy.

## Special Mention / Специальное упоминание

OmaApp builds upon the original Omalauncher project created by Simon Schubert. His work provided the original foundation for this project, and we gratefully acknowledge his contribution to the open-source community.

Исходный проект: [SimonSchubert/omalauncher](https://github.com/SimonSchubert/omalauncher)
Автор исходного проекта: [Simon Schubert](https://github.com/SimonSchubert)
