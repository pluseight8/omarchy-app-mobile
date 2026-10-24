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
- Эмулятор CI проверяет настоящий Android `AppWidgetHost`, привязку через системное подтверждение и интерактивный `AppWidgetHostView` поверх QtActivity. Это proof of concept; штатные Android-виджеты пока нельзя добавлять в сохраняемую раскладку Home.
- Сейчас поддерживается автономное подмножество OWL: layout, текст и базовые элементы управления, Canvas-фигуры, пути, преобразования, clipping и градиенты. Виджеты с shell-командами, сетью, assets, фоновыми триггерами, обработчиками нажатий, секретами или sampled state показывают ошибку в редакторе и не исполняются.
- Настольная версия — только предпросмотр. Список приложений на ней не подменяется фиктивными данными.
- Сетевой слушатель запускается при запросе QR-кода или наличии сохранённых устройств. Python-помощник и генератор QR входят в состав одного плагина; отдельная программа или системная служба не устанавливаются.
- OmaApp не устанавливает Arch Linux ARM или PRoot и не включает Quickshell executable или Linux/Wayland-модули в APK.
- Текущая итерация добавляет локализацию, автоматическое обновление списка приложений, тест AppWidgetHost, безопасное DNS-SD-переподключение, настраиваемый виброотклик и доработку русского OWL-интерфейса и навигации для узких экранов. Текущий статус сборок — в [GitHub Actions](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml). APK из CI предназначен для тестов; подписанный релиз этой итерацией не заявляется.
- В Android-приложение не включён Quickshell: приложение использует Qt Quick, а плагин Omarchy исполняется внутри Quickshell.

Локальный набор проходит 112 Python-тестов и проверку 291 русской строки; изменённые OWL-компоненты проходят `qmllint`. Статус текущих проверок PR для desktop, arm64, OpenGL и Vulkan доступен в [GitHub Actions](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml). Предыдущие успешные прогоны проверяли переходы лаунчера, локализацию, OWL и backup, сопряжение и переподключение, разрешения и их отзыв; скриншоты Settings ниже сняты в run #201. В предыдущей работе плагин устанавливался и перезапускался в настоящей сессии Omarchy 4.0.4 / Quickshell 0.3.1, но сквозное Android DNS-SD-переподключение к работающему компьютеру Omarchy не проверено. Полная сборка локально недоступна: отсутствуют CMake, Qt ShaderTools и Android/JDK toolchain. Pixel 10 / GrapheneOS не проверялся.

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
