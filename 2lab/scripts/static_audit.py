#!/usr/bin/env python3
"""Быстрая статическая инспекция источников без Android SDK.

Не проверяет merged manifest, Kotlin-компиляцию, разрешения ОС или runtime.
Для фактического результата использовать ТОЛЬКО bash scripts/check.sh all на устройстве.
"""
from pathlib import Path
import re
import sys
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / 'notes/src/main/AndroidManifest.xml'
MAIN = ROOT / 'notes/src/main'
JAVA = MAIN / 'java/edu/androidsec/notes'
AND = '{http://schemas.android.com/apk/res/android}'
issues = []

def expect(condition, message):
    print(('PASS' if condition else 'FAIL') + ': ' + message)
    if not condition:
        issues.append(message)

def text(relative):
    return (JAVA / relative).read_text(encoding='utf-8')

for xml in MAIN.rglob('*.xml'):
    try:
        ET.parse(xml)
    except ET.ParseError as exc:
        issues.append(f'{xml}: XML error {exc}')
# Конфигурация должна быть согласованной: простое понижение AGP с новыми AndroidX
# приводит к 28 ошибкам AAR Metadata (лог пользователя). Это НЕ build-проверка.
versions = tomllib.loads((ROOT/'gradle/libs.versions.toml').read_text(encoding='utf-8'))['versions']
expect(versions['agp'] == '8.13.0' and versions['composeBom'] == '2025.10.00' and versions['navigationCompose'] == '2.9.5', 'AGP 8.13 / Compose 1.9 / Navigation 2.9 family')
expect(versions['kotlin'] == '2.2.21' and versions['ksp'] == '2.2.21-2.0.4', 'Kotlin/KSP pair')
expect('gradle-8.13-bin.zip' in (ROOT/'gradle/wrapper/gradle-wrapper.properties').read_text(), 'Gradle 8.13 wrapper')
expect(not (ROOT/'gradle/gradle-daemon-jvm.properties').exists(), 'no forced Java 25 daemon')
for module in ('notes','client'):
    gradle = (ROOT/module/'build.gradle.kts').read_text()
    expect('alias(libs.plugins.kotlin.android)' in gradle and 'compileSdk = 36' in gradle and 'targetSdk = 36' in gradle, f'{module}: Kotlin Android plugin, API 36')

manifest = ET.parse(MANIFEST).getroot()
app = manifest.find('application')
permissions = {x.get(AND+'name') for x in manifest.findall('uses-permission')}
expect(permissions == {'android.permission.POST_NOTIFICATIONS'}, 'minimum uses-permission')
permission = next((x for x in manifest.findall('permission') if x.get(AND+'name') == 'edu.androidsec.notes.permission.READ_NOTES'), None)
expect(permission is not None and permission.get(AND+'protectionLevel') == 'signature', 'signature permission declared')
comps = {x.get(AND+'name'):x for x in app if x.tag in ('activity','receiver','provider')}
for name, exported in [('.MainActivity','true'),('.reminder.ReminderActionReceiver','false'),('androidx.core.content.FileProvider','false'),('.provider.TitlesProvider','true')]:
    expect(name in comps and comps[name].get(AND+'exported') == exported, f'component {name} exported={exported}')
expect(comps['.provider.TitlesProvider'].get(AND+'permission') == 'edu.androidsec.notes.permission.READ_NOTES', 'provider permission for all operations')
expect(comps['androidx.core.content.FileProvider'].get(AND+'grantUriPermissions') == 'true', 'FileProvider grants URI')
expected = ET.parse(MAIN/'res/xml/file_paths.xml').getroot()
expect(len(list(expected)) == 1 and expected[0].tag == 'cache-path' and expected[0].get('path') == 'exports/', 'one FileProvider directory')
links = text('deeplink/NoteDeepLink.kt')
expect('toLongOrNull()' in links and 'IncomingNote.Invalid' in links and 'uri.encodedPath' in links, 'strict deep link parsing')
notFound = text('ui/note/NoteViewModel.kt')
expect('notFound = true' in notFound and 'if (_uiState.value.isLoading || _uiState.value.notFound) return' in notFound, 'not-found state prevents edits')
rem = text('reminder/ReminderScheduler.kt')
expect(rem.count('PendingIntent.FLAG_IMMUTABLE') >= 2 and 'PendingIntent.getBroadcast' in rem and 'PendingIntent.getActivity' in rem, 'immutable explicit PendingIntents')
recv = text('reminder/ReminderActionReceiver.kt')
expect('DONE received for note $noteId' in recv and 'NotificationManagerCompat.from(context).cancel' in recv, 'DONE receiver')
expect('PickVisualMedia' in text('photo/PhotoPicker.kt'), 'photo picker')
expect('shouldShowRequestPermissionRationale' in text('reminder/ReminderPermission.kt'), 'permission rationale')
share = text('share/ShareNote.kt')
expect('FileProvider.getUriForFile' in share and 'FLAG_GRANT_READ_URI_PERMISSION' in share and 'ClipData.newUri' in share, 'scoped file share')
prov = text('provider/TitlesProvider.kt')
expect('MatrixCursor' in prov and 'titlesBlocking()' in prov and prov.count('UnsupportedOperationException') >= 3, 'read-only titles provider')

if issues:
    print(f'Static audit FAIL: {len(issues)} issue(s) — NOT runtime results')
    sys.exit(1)
print('Static audit PASS: source shape only; run scripts/check.sh all on a device!')
