#!/usr/bin/env python3
"""Validate that each QML qsTr source has a complete Russian translation."""

from __future__ import annotations

import ast
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TS_FILE = ROOT / "app/i18n/omaapp_ru.ts"
QML_FILES = sorted((ROOT / "app/qml").glob("*.qml"))
CPP_FILES = sorted((ROOT / "app/native").glob("*.cpp"))
ANDROID_JAVA_FILES = sorted((ROOT / "app/android/src/app/omaapp").glob("*.java"))
QS_TR = re.compile(r"qsTr\s*\(\s*(\"(?:\\.|[^\"\\])*\")")
TR = re.compile(r"\btr\s*\(\s*(\"(?:\\.|[^\"\\])*\")")
NATIVE_STRINGS = re.compile(
    r"NativeStrings\.(?:get|format)\s*\(\s*(\"(?:\\.|[^\"\\])*\")"
)
TRANSLATE_NOOP = re.compile(
    r"QT_TRANSLATE_NOOP\s*\(\s*(\"(?:\\.|[^\"\\])*\")\s*,\s*(\"(?:\\.|[^\"\\])*\")"
)
PLACEHOLDER = re.compile(r"%[1-9][0-9]*")
UNCHANGED_ALLOWED = {
    "%1 / %2",
    "%1°",
    "ANDROID",
    "OMA / APP",
    "OmaApp",
    'widget "My widget" { ... }',
}


def source_messages() -> dict[str, set[str]]:
    contexts: dict[str, set[str]] = {}
    for qml_file in QML_FILES:
        messages = {
            ast.literal_eval(raw)
            for raw in QS_TR.findall(qml_file.read_text(encoding="utf-8"))
        }
        if messages:
            contexts[qml_file.stem] = messages
    for cpp_file in CPP_FILES:
        messages = {
            ast.literal_eval(raw)
            for raw in TR.findall(cpp_file.read_text(encoding="utf-8"))
        }
        if messages:
            contexts[cpp_file.stem] = messages
        for raw_context, raw_source in TRANSLATE_NOOP.findall(
            cpp_file.read_text(encoding="utf-8")
        ):
            context = ast.literal_eval(raw_context)
            contexts.setdefault(context, set()).add(ast.literal_eval(raw_source))
    for java_file in ANDROID_JAVA_FILES:
        messages = {
            ast.literal_eval(raw)
            for raw in NATIVE_STRINGS.findall(java_file.read_text(encoding="utf-8"))
        }
        if messages:
            contexts.setdefault("Main", set()).update(messages)
    return contexts


def catalog_messages(path: Path = TS_FILE) -> dict[str, dict[str, str]]:
    root = ET.parse(path).getroot()
    if root.attrib.get("language") != "ru_RU":
        raise ValueError("Russian catalog must declare language ru_RU")
    result: dict[str, dict[str, str]] = {}
    for context in root.findall("context"):
        name = context.findtext("name", default="")
        messages: dict[str, str] = {}
        for item in context.findall("message"):
            source = item.findtext("source", default="")
            translation = item.findtext("translation", default="")
            if source in messages:
                raise ValueError(f"Duplicate source string in context {name}: {source}")
            translation_type = (
                item.find("translation").get("type")
                if item.find("translation") is not None else None
            )
            if translation_type in {"unfinished", "obsolete", "vanished"}:
                raise ValueError(f"{translation_type.title()} {name} translation: {source}")
            messages[source] = translation
        result[name] = messages
    return result


def validate(ts_file: Path = TS_FILE) -> list[str]:
    errors: list[str] = []
    try:
        sources = source_messages()
        catalog = catalog_messages(ts_file)
    except (OSError, ET.ParseError, ValueError) as error:
        return [str(error)]

    for context, source_set in sources.items():
        translations = catalog.get(context, {})
        for source in sorted(source_set - translations.keys()):
            errors.append(f"Missing {context} translation: {source}")
        for source in sorted(source_set & translations.keys()):
            translation = translations[source]
            if not translation.strip():
                errors.append(f"Empty {context} translation: {source}")
                continue
            if Counter(PLACEHOLDER.findall(source)) != Counter(PLACEHOLDER.findall(translation)):
                errors.append(f"Placeholder mismatch in {context}: {source} -> {translation}")
            if source == translation and source not in UNCHANGED_ALLOWED:
                errors.append(f"Untranslated English string in {context}: {source}")

    for context, messages in catalog.items():
        for source in sorted(messages.keys() - sources.get(context, set())):
            errors.append(f"Stale {context} catalog entry: {source}")
    return errors


if __name__ == "__main__":
    problems = validate()
    if problems:
        print("\n".join(problems), file=sys.stderr)
        raise SystemExit(1)
    count = sum(map(len, source_messages().values()))
    print(f"Validated {count} Russian UI strings")
