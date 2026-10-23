#!/usr/bin/env bash
#
# Проверка работы «Разрешения, компоненты и IPC»: adb-часть чек-листа.
#
#   scripts/check.sh all        — все проверки
#   scripts/check.sh manifest   — шаг 1: exported у компонентов, список разрешений
#   scripts/check.sh closed     — шаг 2: ресивер и FileProvider недоступны извне
#   scripts/check.sh deeplink   — шаг 4: notes://note/{1,999999,abc,} не роняют приложение
#   scripts/check.sh provider   — шаг 7: content query/insert/delete отклонены
#
# Нужны: запущенный эмулятор (API 34+) с установленным приложением «Заметки»,
# `adb` и `apkanalyzer` из Android SDK ($ANDROID_HOME или стандартный путь).
# Запускать через bash: Git Bash на Windows, `bash scripts/check.sh` на macOS/Linux.
#
# Каждая проверка печатает PASS/FAIL и сырой вывод adb под ним.

set -u

PKG="edu.androidsec.notes"
RECEIVER="$PKG/.reminder.ReminderActionReceiver"
ACTION_DONE="edu.androidsec.notes.action.DONE"
EXTRA_NOTE_ID="edu.androidsec.notes.extra.NOTE_ID"
FILES_URI="content://edu.androidsec.notes.files/exports/1.txt"
TITLES_URI="content://edu.androidsec.notes.provider/notes"
READ_NOTES="edu.androidsec.notes.permission.READ_NOTES"

ALLOWED_EXPORTED="$PKG.MainActivity $PKG.provider.TitlesProvider"
# DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION добавляет сам AGP при targetSdk 33+.
ALLOWED_PERMISSIONS="android.permission.POST_NOTIFICATIONS $PKG.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
FORBIDDEN_PERMISSIONS="android.permission.CAMERA android.permission.READ_MEDIA_IMAGES android.permission.READ_MEDIA_VIDEO android.permission.READ_MEDIA_VISUAL_USER_SELECTED android.permission.READ_EXTERNAL_STORAGE android.permission.WRITE_EXTERNAL_STORAGE android.permission.MANAGE_EXTERNAL_STORAGE android.permission.ACCESS_MEDIA_LOCATION"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$SCRIPT_DIR/out"
mkdir -p "$OUT_DIR"

PASS_COUNT=0
FAIL_COUNT=0

# ---------- инструменты ----------

find_sdk() {
    local candidates=(
        "${ANDROID_HOME:-}"
        "${ANDROID_SDK_ROOT:-}"
        "$HOME/Library/Android/sdk"
        "$HOME/Android/Sdk"
        "${LOCALAPPDATA:-}/Android/Sdk"
        "$HOME/AppData/Local/Android/Sdk"
    )
    local c
    for c in "${candidates[@]}"; do
        [ -n "$c" ] && [ -d "$c/platform-tools" ] && { echo "$c"; return 0; }
    done
    return 1
}

tool() {
    # tool <path-without-extension> [.bat-name]  → печатает запускаемый путь или пусто
    local base="$1"
    if [ -x "$base" ]; then echo "$base"; return 0; fi
    if [ -f "$base.exe" ]; then echo "$base.exe"; return 0; fi
    if [ -f "$base.bat" ]; then echo "$base.bat"; return 0; fi
    return 1
}

SDK="$(find_sdk)" || { echo "Android SDK не найден: задайте ANDROID_HOME"; exit 2; }
ADB="$(tool "$SDK/platform-tools/adb")" || { echo "adb не найден в $SDK/platform-tools"; exit 2; }
APKANALYZER="$(tool "$SDK/cmdline-tools/latest/bin/apkanalyzer" || tool "$SDK/tools/bin/apkanalyzer" || true)"

# apkanalyzer — Java-инструмент; если JAVA_HOME не задан, берём JDK из Android Studio.
if [ -z "${JAVA_HOME:-}" ]; then
    for j in "/c/Program Files/Android/Android Studio/jbr" "/Applications/Android Studio.app/Contents/jbr/Contents/Home" "$HOME/android-studio/jbr" "/opt/android-studio/jbr"; do
        [ -d "$j" ] && { export JAVA_HOME="$j"; break; }
    done
fi

adb() { "$ADB" "$@" | tr -d '\r'; }

# ---------- вывод ----------

section() { printf '\n=== %s ===\n' "$1"; }

report() {
    # report PASS|FAIL "<что проверяли>" "<сырой вывод>"
    local status="$1" what="$2" raw="${3:-}"
    printf '%s  %s\n' "$status" "$what"
    if [ -n "$raw" ]; then
        printf '%s\n' "$raw" | sed 's/^/    | /'
    fi
    if [ "$status" = "PASS" ]; then PASS_COUNT=$((PASS_COUNT + 1)); else FAIL_COUNT=$((FAIL_COUNT + 1)); fi
}

require_device() {
    local devices
    devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
    if [ -z "$devices" ]; then
        echo "Нет подключённого устройства/эмулятора (adb devices)"; exit 2
    fi
    if ! adb shell pm path "$PKG" >/dev/null 2>&1 || [ -z "$(adb shell pm path "$PKG")" ]; then
        echo "Пакет $PKG не установлен на устройстве"; exit 2
    fi
}

# ---------- манифест ----------

MANIFEST_FLAT=""   # по одному XML-элементу на строку

load_manifest() {
    [ -n "$MANIFEST_FLAT" ] && return 0
    if [ -z "$APKANALYZER" ]; then
        echo "apkanalyzer не найден: установите «Android SDK Command-line Tools» в SDK Manager"; exit 2
    fi
    local apk_path
    apk_path="$(adb shell pm path "$PKG" | sed -n 's/^package://p' | head -1)"
    "$ADB" pull "$apk_path" "$OUT_DIR/notes.apk" || { echo "Не удалось скачать APK"; exit 2; }
    "$APKANALYZER" manifest print "$OUT_DIR/notes.apk" | tr -d '\r' > "$OUT_DIR/manifest.xml"
    # Каждый элемент — на отдельной строке: <tag attr="..." attr="...">
    MANIFEST_FLAT="$(tr '\n' ' ' < "$OUT_DIR/manifest.xml" | sed 's/</\n</g' | sed 's/[[:space:]]\+/ /g')"
}

attr() {
    # attr "<элемент>" android:name  → значение атрибута или пусто
    printf '%s\n' "$1" | sed -n "s/.* $2=\"\([^\"]*\)\".*/\1/p"
}

full_name() {
    # имя компонента с пакетом
    local n="$1"
    case "$n" in
        .*) echo "$PKG$n" ;;
        *.*) echo "$n" ;;
        *) echo "$PKG.$n" ;;
    esac
}

permission_protection() {
    # Уровень защиты разрешения в системе, например "signature|privileged|development".
    adb shell dumpsys package permission "$1" 2>/dev/null | grep -m1 -oE 'prot=[A-Za-z|]+' | cut -d= -f2
}

is_signature_level() {
    # signature, signature|privileged, signatureOrSystem — да; normal, dangerous, пусто — нет.
    case "$1" in
        signature*) return 0 ;;
        *) return 1 ;;
    esac
}

components() {
    # печатает строки: type|name|exported|permission
    printf '%s\n' "$MANIFEST_FLAT" | grep -E '^<(activity|activity-alias|service|receiver|provider) ' | while IFS= read -r el; do
        local type name exported perm
        type="$(printf '%s' "$el" | sed -n 's/^<\([a-z-]*\) .*/\1/p')"
        name="$(full_name "$(attr "$el" android:name)")"
        exported="$(attr "$el" android:exported)"
        perm="$(attr "$el" android:permission)"
        printf '%s|%s|%s|%s\n' "$type" "$name" "${exported:-<нет>}" "${perm:--}"
    done
}

check_manifest() {
    section "manifest (шаг 1)"
    load_manifest
    local comps
    comps="$(components)"
    echo "Компоненты (тип|имя|exported|permission):"
    printf '%s\n' "$comps" | sed 's/^/    | /'

    # 1. у каждого компонента явно указан exported
    local missing
    missing="$(printf '%s\n' "$comps" | awk -F'|' '$3=="<нет>" {print $2}')"
    if [ -z "$missing" ]; then
        report PASS "у каждого компонента явно задан android:exported"
    else
        report FAIL "без android:exported:" "$missing"
    fi

    # 2. экспортированы только разрешённые. Компонент, закрытый разрешением уровня signature
    #    (например, ресивер библиотеки под android.permission.DUMP), — не дыра: снаружи его
    #    вызовет только приложение с той же подписью или система. Такие компоненты проходят
    #    с пометкой NOTE; остальные лишние — FAIL.
    local exported extra notes e perm prot
    exported="$(printf '%s\n' "$comps" | awk -F'|' '$3=="true" {print $2}')"
    extra=""
    notes=""
    for e in $exported; do
        case " $ALLOWED_EXPORTED " in
            *" $e "*) continue ;;
        esac
        perm="$(printf '%s\n' "$comps" | awk -F'|' -v n="$e" '$2==n {print $4; exit}')"
        prot=""
        [ -n "$perm" ] && [ "$perm" != "-" ] && prot="$(permission_protection "$perm")"
        if is_signature_level "$prot"; then
            notes="${notes}NOTE: $e экспортирован, но закрыт $perm ($prot)"$'\n'
        else
            extra="$extra$e${perm:+ [permission: $perm${prot:+, $prot}]}"$'\n'
        fi
    done
    if [ -z "$extra" ]; then
        report PASS "экспортированы только: $ALLOWED_EXPORTED${notes:+ и компоненты под signature-разрешением}" "$exported${notes:+$'\n'${notes%$'\n'}}"
    else
        report FAIL "лишние экспортированные компоненты без signature-разрешения" "$extra"
    fi

    # 3. обязательные компоненты объявлены с нужным exported
    local want
    for want in "receiver|$PKG.reminder.ReminderActionReceiver|false" \
                "provider|androidx.core.content.FileProvider|false" \
                "provider|$PKG.provider.TitlesProvider|true"; do
        local wtype wname wexp line
        wtype="${want%%|*}"; wname="$(printf '%s' "$want" | cut -d'|' -f2)"; wexp="${want##*|}"
        line="$(printf '%s\n' "$comps" | awk -F'|' -v t="$wtype" -v n="$wname" '$1==t && $2==n')"
        if [ -z "$line" ]; then
            report FAIL "$wtype $wname не объявлен в манифесте"
        elif [ "$(printf '%s' "$line" | cut -d'|' -f3)" != "$wexp" ]; then
            report FAIL "$wname: ожидался exported=$wexp" "$line"
        else
            report PASS "$wname объявлен, exported=$wexp" "$line"
        fi
    done

    # 4. провайдер заголовков защищён signature-разрешением
    local tp perm_el level
    tp="$(printf '%s\n' "$comps" | awk -F'|' -v n="$PKG.provider.TitlesProvider" '$2==n')"
    perm_el="$(printf '%s\n' "$MANIFEST_FLAT" | grep -E '^<permission ' | grep "android:name=\"$READ_NOTES\"" | head -1)"
    level="$(attr "$perm_el" android:protectionLevel)"
    if [ -z "$perm_el" ]; then
        report FAIL "<permission $READ_NOTES> не объявлено"
    elif [ "$level" != "signature" ] && [ "$level" != "0x2" ] && [ "$level" != "0x00000002" ]; then
        report FAIL "$READ_NOTES: protectionLevel должен быть signature" "$perm_el"
    else
        report PASS "$READ_NOTES объявлено с protectionLevel=signature" "$perm_el"
    fi
    # Допустимы два варианта: android:permission, либо readPermission + writePermission (оба).
    local tp_el rp wp
    tp_el="$(printf '%s\n' "$MANIFEST_FLAT" | grep -E '^<provider ' | grep -F "$PKG.provider.TitlesProvider" | head -1)"
    rp="$(attr "$tp_el" android:readPermission)"
    wp="$(attr "$tp_el" android:writePermission)"
    if [ -n "$tp" ] && [ "$(printf '%s' "$tp" | cut -d'|' -f4)" = "$READ_NOTES" ]; then
        report PASS "TitlesProvider защищён android:permission=$READ_NOTES"
    elif [ -n "$tp" ] && [ "$rp" = "$READ_NOTES" ] && [ "$wp" = "$READ_NOTES" ]; then
        report PASS "TitlesProvider защищён readPermission и writePermission=$READ_NOTES"
    elif [ -n "$tp" ] && [ "$rp" = "$READ_NOTES" ]; then
        report FAIL "TitlesProvider: только readPermission — запись (insert/delete) остаётся открытой" "$tp_el"
    else
        report FAIL "TitlesProvider должен иметь android:permission=$READ_NOTES" "$tp"
    fi

    # 5. разрешения
    local perms bad p
    perms="$(printf '%s\n' "$MANIFEST_FLAT" | grep -E '^<uses-permission' | while IFS= read -r el; do attr "$el" android:name; done)"
    echo "Запрошенные разрешения:"
    printf '%s\n' "${perms:-<нет>}" | sed 's/^/    | /'
    bad=""
    for p in $perms; do
        case " $ALLOWED_PERMISSIONS " in
            *" $p "*) ;;
            *) bad="$bad$p"$'\n' ;;
        esac
    done
    if [ -z "$bad" ]; then
        report PASS "лишних разрешений нет (допустимы: $ALLOWED_PERMISSIONS)"
    else
        local forbidden_hit=""
        for p in $bad; do
            case " $FORBIDDEN_PERMISSIONS " in *" $p "*) forbidden_hit="$forbidden_hit$p "; esac
        done
        report FAIL "разрешения вне списка допустимых${forbidden_hit:+ (в т.ч. запрещённые: $forbidden_hit)}" "$bad"
    fi
    if printf '%s\n' "$perms" | grep -qx 'android.permission.POST_NOTIFICATIONS'; then
        report PASS "POST_NOTIFICATIONS запрошено (нужно для напоминания)"
    else
        report FAIL "POST_NOTIFICATIONS не запрошено: напоминание на API 33+ не покажется"
    fi
}

# ---------- закрытые компоненты ----------

check_closed() {
    section "closed (шаг 2)"
    load_manifest
    local raw log

    # Ресивер: должен быть объявлен с exported=false; broadcast от shell система молча отбрасывает
    # (на Android 14+ отказ в logcat не пишется), поэтому признак — отсутствие реакции ресивера.
    # Шлём два broadcast: явный на компонент из манифеста и неявный в пакет — второй дошёл бы до
    # ресивера, зарегистрированного в коде с RECEIVER_EXPORTED. Реакция — любая строка в logcat
    # с тегом ReminderActionReceiver (её пишет ресивер, строка фиксирована в заготовке).
    local rline raw2
    rline="$(components | grep "^receiver|$PKG.reminder.ReminderActionReceiver|" || true)"
    if [ -z "$rline" ]; then
        report FAIL "ReminderActionReceiver не объявлен в манифесте: проверять нечего"
    elif [ "$(printf '%s' "$rline" | cut -d'|' -f3)" != "false" ]; then
        report FAIL "ReminderActionReceiver должен быть exported=false" "$rline"
    else
        adb logcat -c
        raw="$(adb shell am broadcast -n "$RECEIVER" -a "$ACTION_DONE" --el "$EXTRA_NOTE_ID" 1 2>&1)"
        raw2="$(adb shell am broadcast -a "$ACTION_DONE" -p "$PKG" --el "$EXTRA_NOTE_ID" 1 2>&1)"
        sleep 2
        log="$(adb logcat -d 2>/dev/null | grep -E '[0-9]+ +[0-9]+ [VDIWEF] ReminderActionReceiver *:|DONE received' | tail -5)"
        if [ -n "$log" ]; then
            report FAIL "am broadcast: ресивер ОТРЕАГИРОВАЛ на посторонний интент" "$raw"$'\n'"$raw2"$'\n'"$log"
        else
            report PASS "am broadcast (явный и в пакет): ресивер не вызывался" "$raw"$'\n'"$raw2"$'\n'"<logcat: строк ReminderActionReceiver нет>"
        fi
    fi

    # FileProvider: чтение URI от shell отклоняется.
    raw="$(adb shell content read --uri "$FILES_URI" 2>&1)"
    if printf '%s' "$raw" | grep -qiE 'Permission Denial|SecurityException|not exported'; then
        report PASS "content read на FileProvider отклонён" "$raw"
    elif printf '%s' "$raw" | grep -qiE 'Could not find provider|Unknown URI|No content provider'; then
        report FAIL "FileProvider с authority edu.androidsec.notes.files не найден" "$raw"
    else
        report FAIL "content read на FileProvider НЕ отклонён" "${raw:-<пустой вывод: файл прочитан>}"
    fi
}

# ---------- deep link ----------

check_deeplink() {
    section "deeplink (шаг 4)"
    local id uri raw crash
    for id in "1" "999999" "abc" ""; do
        uri="notes://note/$id"
        adb shell am force-stop "$PKG" >/dev/null 2>&1
        adb logcat -c
        raw="$(adb shell am start -W -a android.intent.action.VIEW -d "$uri" 2>&1)"
        sleep 3
        crash="$(adb logcat -d 2>/dev/null | grep -E 'FATAL EXCEPTION|AndroidRuntime' | head -8)"
        if printf '%s' "$raw" | grep -qi 'unable to resolve' && { [ "$id" = "abc" ] || [ -z "$id" ]; }; then
            # Фильтр строже (например, pathAdvancedPattern только из цифр): некорректная ссылка
            # не открывает ничего — это допустимо и даже лучше, чем обрабатывать её в коде.
            report PASS "$uri: ссылка не разрешается ни в одну activity — для некорректного id это допустимо" "$raw"
        elif printf '%s' "$raw" | grep -qiE 'Error|unable to resolve|not started'; then
            report FAIL "$uri: activity не открылась (нет intent-filter?)" "$raw"
        elif [ -n "$crash" ]; then
            report FAIL "$uri: приложение упало" "$raw"$'\n'"$crash"$'\n'"$(adb logcat -d | grep -A12 'FATAL EXCEPTION' | head -14)"
        elif ! printf '%s' "$raw" | grep -q "$PKG"; then
            report FAIL "$uri: интент ушёл не в наше приложение" "$raw"
        else
            report PASS "$uri: открыто приложением, падения нет" "$raw"
        fi
    done
    echo "Что показано на экране (сообщение об ошибке для abc и пустого id) — проверяется вручную."
}

# ---------- провайдер заголовков ----------

check_provider() {
    section "provider (шаг 7)"
    local raw
    raw="$(adb shell content query --uri "$TITLES_URI" 2>&1)"
    if printf '%s' "$raw" | grep -qiE 'SecurityException|Permission Denial'; then
        report PASS "content query от shell отклонён (SecurityException)" "$raw"
    elif printf '%s' "$raw" | grep -qiE 'Could not find provider|Unknown URI|No content provider'; then
        report FAIL "провайдер edu.androidsec.notes.provider не найден" "$raw"
    else
        report FAIL "content query от shell НЕ отклонён: заголовки читаются без разрешения" "${raw:-<пустой вывод>}"
    fi

    raw="$(adb shell content insert --uri "$TITLES_URI" --bind title:s:hacked 2>&1)"
    if printf '%s' "$raw" | grep -qiE 'SecurityException|Permission Denial|UnsupportedOperationException'; then
        report PASS "content insert отклонён" "$raw"
    else
        report FAIL "content insert НЕ отклонён" "${raw:-<пустой вывод>}"
    fi

    raw="$(adb shell content delete --uri "$TITLES_URI" 2>&1)"
    if printf '%s' "$raw" | grep -qiE 'SecurityException|Permission Denial|UnsupportedOperationException'; then
        report PASS "content delete отклонён" "$raw"
    else
        report FAIL "content delete НЕ отклонён" "${raw:-<пустой вывод>}"
    fi
    echo "Клиент с той же подписью (видит заголовки) и клиент с другой подписью (SecurityException) — проверяются вручную."
}

# ---------- main ----------

usage() {
    sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 1
}

main() {
    local cmd="${1:-}"
    [ -z "$cmd" ] && usage
    require_device
    echo "SDK: $SDK"
    echo "Устройство: $(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -1), Android $(adb shell getprop ro.build.version.release) (API $(adb shell getprop ro.build.version.sdk))"
    case "$cmd" in
        all) check_manifest; check_closed; check_deeplink; check_provider ;;
        manifest) check_manifest ;;
        closed) check_closed ;;
        deeplink) check_deeplink ;;
        provider) check_provider ;;
        *) usage ;;
    esac
    printf '\nИТОГО: PASS=%d FAIL=%d\n' "$PASS_COUNT" "$FAIL_COUNT"
    [ "$FAIL_COUNT" -eq 0 ]
}

main "$@"
