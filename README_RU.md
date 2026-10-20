# OmaApp — One Shell. Every Device.

[![CI](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml/badge.svg)](https://github.com/pluseight8/omarchy-app-mobile/actions/workflows/ci.yml) · [English](README.md) · [Русский](README_RU.md)

![Баннер проекта OmaApp](assets/hero.svg)

OmaApp — задуманный Android-компаньон для Omarchy. Сейчас репозиторий находится на этапе прототипа Qt Quick.

## Текущее состояние

- Исходники Android-приложения используют Qt 6.11, C++ и QML.
- На Android приложение получает список доступных launcher-активностей через небольшой Java-мост и запускает выбранное приложение. Манифест позволяет выбрать OmaApp как домашний экран Android.
- Настольная версия — только предпросмотр. Список приложений на ней не подменяется фиктивными данными.
- Компонент Omarchy — один status-widget по манифесту. Пока он показывает «не сопряжено».
- Сопряжение, синхронизация, удалённое управление, перенос функций Omalauncher и готовый APK пока недоступны.
- В этот прототип не включён Quickshell executable или библиотека.

В текущем локальном окружении есть настольные инструменты Qt 6.11.2 и `qmllint`, но нет CMake, Qt ShaderTools, JDK, Android SDK и Android NDK. Сборка Android настроена в GitHub Actions; заявлять о доступном APK можно после успешной CI-сборки.

## Сборка настольного предпросмотра

Нужны CMake 3.24+, Ninja, Qt 6.11 с Qt Quick Controls 2, Qt ShaderTools и Qt Test, а также C++17.

    cmake -S . -B build/desktop -GNinja -DCMAKE_BUILD_TYPE=Debug
    cmake --build build/desktop
    ctest --test-dir build/desktop --output-on-failure

Запустите созданный executable `omaapp`, чтобы открыть оболочку и GPU-предпросмотр скруглений.

## Сборка Android APK

Android-цель использует Qt for Android arm64, JDK 21, Android SDK 36 и NDK 27.2.12479018. GitHub Actions устанавливает этот toolchain и собирает APK.

Для локальной сборки настройте CMake с Qt Android arm64 и путями к Android SDK/NDK, затем соберите цель apk:

    qt-cmake -S . -B build/android -GNinja -DANDROID_SDK_ROOT=/path/to/Android/Sdk -DANDROID_NDK_ROOT=/path/to/Android/Sdk/ndk/27.2.12479018
    cmake --build build/android --target apk

Прототип ещё не установлен на GrapheneOS или устройство Pixel. Первый APK пока не опубликован в Releases.

## Установка status-widget для Omarchy

В клоне репозитория на компьютере с Omarchy выполните:

    ./omarchy-plugin/install.sh

Установщик проверяет манифест и копирует один плагин в пользовательский каталог Omarchy. Отдельный сервер или системная служба не устанавливаются.

## План и ограничения

См. [архитектуру](docs/ARCHITECTURE.md), [инструкцию установки](docs/INSTALLATION.md), [статус сопряжения](docs/PAIRING.md), [статус GrapheneOS](docs/GRAPHENEOS.md), [совместимость](docs/compatibility.md), [модель угроз](docs/threat-model.md) и [план реализации](tasks/plan.md). Удалённые функции останутся недоступны, пока не появятся протокол сопряжения, авторизация устройств, отзыв ключей и проверки безопасности.

OmaApp — независимый проект, не являющийся официальным продуктом Omarchy.

## Special Mention / Специальное упоминание

OmaApp builds upon the original Omalauncher project created by Simon Schubert. His work provided the original foundation for this project, and we gratefully acknowledge his contribution to the open-source community.

Исходный проект: [SimonSchubert/omalauncher](https://github.com/SimonSchubert/omalauncher)
Автор исходного проекта: [Simon Schubert](https://github.com/SimonSchubert)
