#!/usr/bin/env bash
# Фактические adb-результаты лабораторной. Не подменяет stdout/stderr тестов.
# Сначала записываем дату/устройство, затем без изменений запускаем скрипт преподавателя.
# pipefail возвращает ошибку, если упал check.sh, даже при успешной записи через tee.
set -o pipefail
set -u
cd "$(dirname "$0")/.."
mkdir -p docs/evidence

# Поиск adb повторяет распространённые каталоги Android SDK в Windows/macOS/Linux.
ADB=""
if command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
else
    for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/AppData/Local/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        [ -n "$sdk" ] || continue
        if [ -x "$sdk/platform-tools/adb" ]; then ADB="$sdk/platform-tools/adb"; break; fi
        if [ -f "$sdk/platform-tools/adb.exe" ]; then ADB="$sdk/platform-tools/adb.exe"; break; fi
    done
fi

{
    printf 'Timestamp: %s\n' "$(date -Iseconds)"
    printf 'Devices:\n'
    if [ -n "$ADB" ]; then "$ADB" devices; else printf 'adb not found: set ANDROID_HOME\n'; fi
} | tee docs/evidence/check-all.txt
bash scripts/check.sh all 2>&1 | tee -a docs/evidence/check-all.txt
