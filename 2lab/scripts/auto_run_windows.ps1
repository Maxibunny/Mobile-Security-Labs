# СХЕМА СЦЕНАРИЯ ДЛЯ ЗАЩИТЫ: self-test -> Gradle -> ADB/AVD -> install ->
# SDK apkanalyzer -> scripts/check.sh all -> AndroidTest -> восстановление APK -> запуск UI.
# Каждая команда имеет timeout, свой stdout/stderr в logs/auto-... и итог в summary.txt.
# Это вспомогательная автоматизация, а НЕ часть Android IPC / разрешений.
#requires -Version 5.1
<#
  Лабораторная #2. Один запуск для Windows 10/11:
  START_LAB2.cmd -> этот скрипт.

  Не требует прав администратора. НЕ выполняет Wipe Data, adb uninstall или очистку
  пользовательских данных. Каждый вызов внешней программы ограничен тайм-аутом.
  Команды и их РЕАЛЬНЫЙ вывод сохраняются в logs/auto-YYYYMMDD-HHMMSS/.

  Скрипт преподавателя scripts/check.sh НЕ изменён.
#>
[CmdletBinding()]
param(
    [switch] $NoBuild,
    [switch] $SkipSecurityChecks,
    [switch] $SkipInstrumentedTests,
    [switch] $SelfTest
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$LogDir = Join-Path $Root "logs\auto-$stamp"
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
$MainLog = Join-Path $LogDir 'summary.txt'
$script:RunNumber = 0
$script:Warnings = New-Object System.Collections.Generic.List[string]
$script:Failures = New-Object System.Collections.Generic.List[string]
$script:Device = $null
$script:Sdk = $null
$script:Adb = $null
$script:Emulator = $null
$script:AvdName = $null

function Log([string] $Message, [string] $Level = 'INFO') {
    $line = '[{0}] [{1}] {2}' -f (Get-Date -Format 'HH:mm:ss'), $Level, $Message
    Write-Host $line
    Add-Content -LiteralPath $MainLog -Value $line -Encoding UTF8
}
function Warn([string] $Message) {
    $script:Warnings.Add($Message)
    Log $Message 'WARN'
}
function Fail([string] $Message) {
    $script:Failures.Add($Message)
    Log $Message 'ERROR'
}
function Q([string] $Text) {
    # quoting for native command line; args passed here contain no embedded double quote
    return '"' + $Text.Replace('"', '\"') + '"'
}
function Invoke-Tool {
    param(
        [string] $Name,
        [string] $File,
        [string] $Arguments = '',
        [int] $Timeout = 20,
        [switch] $ShowOutput
    )
    $script:RunNumber++
    $prefix = '{0:000}-{1}' -f $script:RunNumber, ($Name -replace '[^a-zA-Z0-9_-]', '_')
    $stdout = Join-Path $LogDir ($prefix + '.stdout.txt')
    $stderr = Join-Path $LogDir ($prefix + '.stderr.txt')
    Log ('RUN ' + $Name + ': ' + $File + ' ' + $Arguments)
    $timedOut = $false
    $exitCode = -1
    $out = ''
    $err = ''
    $process = $null
    $outTask = $null
    $errTask = $null
    try {
        # ИСПРАВЛЕНИЕ v3: Start-Process -PassThru иногда возвращал $null в ExitCode
        # даже после успешного выполнения Gradle (BUILD SUCCESSFUL). Используем
        # собственный System.Diagnostics.Process и читаем ExitCode ПОСЛЕ WaitForExit.
        # Оба потока читаем асинхронно одновременно: иначе процесс с большим
        # количеством логов может заблокироваться, когда заполнится pipe-буфер.
        $startInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startInfo.FileName = $File
        $startInfo.Arguments = $Arguments
        $startInfo.WorkingDirectory = $Root
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $process = New-Object System.Diagnostics.Process
        $process.StartInfo = $startInfo
        if (-not $process.Start()) { throw "Не удалось запустить: $File" }
        $outTask = $process.StandardOutput.ReadToEndAsync()
        $errTask = $process.StandardError.ReadToEndAsync()
        $started = Get-Date
        $lastUpdate = $started
        while ($true) {
            if ($process.WaitForExit(2000)) {
                $process.Refresh()
                # ExitCode у Process всегда целое число ПОСЛЕ завершения.
                $exitCode = [int]$process.ExitCode
                break
            }
            $seconds = [int]((Get-Date) - $started).TotalSeconds
            if ($seconds -ge $Timeout) {
                $timedOut = $true
                # Только дерево созданного нами процесса. Без сброса AVD/данных.
                try { & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null } catch {}
                try { $process.WaitForExit(3000) | Out-Null } catch {}
                break
            }
            if (((Get-Date) - $lastUpdate).TotalSeconds -ge 10) {
                Log ("${Name}: работаю ${seconds}с / максимум ${Timeout}с")
                $lastUpdate = Get-Date
            }
        }
        # После остановки дочерних процессов wait может не завершиться: не ждём
        # бесконечно, а сохраняем всё, что успели прочитать.
        if ($null -ne $outTask -and $outTask.Wait(5000)) { $out = [string]$outTask.Result }
        if ($null -ne $errTask -and $errTask.Wait(5000)) { $err = [string]$errTask.Result }
    } catch {
        $exitCode = -1
        $err += ($_.Exception.Message + "`r`n")
    } finally {
        if ($null -ne $process) { $process.Dispose() }
        # UTF-8 без BOM: важен для сравнения adb и вывода скрипта преподавателя.
        $utf8 = [System.Text.UTF8Encoding]::new($false)
        [IO.File]::WriteAllText($stdout, $out, $utf8)
        [IO.File]::WriteAllText($stderr, $err, $utf8)
    }
    if ($timedOut) { Log "${Name}: timeout ${Timeout}s; процесс остановлен" 'WARN' }
    elseif ($exitCode -ne 0) { Log "${Name}: exit code $exitCode" 'WARN' }
    else { Log "${Name}: OK (exit code 0)" }
    if ($ShowOutput -or $exitCode -ne 0 -or $timedOut) {
        $lines = @(($out + "`n" + $err) -split "`r?`n" | Where-Object { $_ -ne '' })
        $limit = if ($ShowOutput) { 50 } else { 18 }
        foreach ($line in ($lines | Select-Object -Last $limit)) { Write-Host ('    ' + $line) }
        if ($lines.Count -gt $limit) { Write-Host "    ... полный вывод: $stdout / $stderr" }
    }
    return [pscustomobject]@{
        Ok = (-not $timedOut -and $exitCode -eq 0)
        ExitCode = $exitCode
        TimedOut = $timedOut
        Output = $out
        ErrorText = $err
        OutPath = $stdout
        ErrPath = $stderr
    }
}
function Find-Sdk {
    $candidates = New-Object System.Collections.Generic.List[string]
    foreach ($v in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($v -and $v.Trim()) { $candidates.Add($v.Trim()) }
    }
    $local = Join-Path $Root 'local.properties'
    if (Test-Path -LiteralPath $local) {
        foreach ($line in (Get-Content -LiteralPath $local)) {
            if ($line -match '^\s*sdk\.dir\s*=\s*(.+)$') {
                $candidates.Add($Matches[1].Trim().Replace('\:', ':').Replace('\\', '\'))
            }
        }
    }
    if ($env:LOCALAPPDATA) { $candidates.Add((Join-Path $env:LOCALAPPDATA 'Android\Sdk')) }
    if ($env:USERPROFILE) { $candidates.Add((Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk')) }
    $adbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($adbCommand) { $candidates.Add((Split-Path (Split-Path $adbCommand.Source -Parent) -Parent)) }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'platform-tools\adb.exe'))) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
}
function Find-Emulator([string] $SdkPath) {
    $candidate = Join-Path $SdkPath 'emulator\emulator.exe'
    if (Test-Path $candidate) { return $candidate }
    return $null
}
function Check-Boot([string] $Serial) {
    $a = Invoke-Tool 'boot_property' $script:Adb ((Q '-s') + ' ' + (Q $Serial) + ' shell getprop sys.boot_completed') 12
    if (-not $a.Ok -or $a.Output.Trim() -ne '1') { return $false }
    $b = Invoke-Tool 'package_manager' $script:Adb ('-s ' + $Serial + ' shell pm path android') 14
    return ($b.Ok -and $b.Output -match 'package:')
}
function Find-OnlineDevice {
    $result = Invoke-Tool 'list_devices' $script:Adb 'devices' 12
    if (-not $result.Ok) { return $null }
    $onlineRows = @(($result.Output -split "`r?`n") | Where-Object { $_ -match '^emulator-\d+\s+device\s*$' })
    if ($onlineRows.Count -gt 0) { return (($onlineRows[0] -split '\s+')[0]) }
    return $null
}
function Find-GitBash {
    $locations = @(
        "$env:ProgramFiles\Git\bin\bash.exe",
        "$env:ProgramFiles\Git\usr\bin\bash.exe",
        "${env:ProgramFiles(x86)}\Git\bin\bash.exe"
    )
    foreach ($path in $locations) { if ($path -and (Test-Path -LiteralPath $path)) { return $path } }
    $command = Get-Command bash.exe -ErrorAction SilentlyContinue
    if ($command -and $command.Source -match '(?i)\\git\\') { return $command.Source }
    return $null
}
function Check-Installed([string] $Pkg) {
    $r = Invoke-Tool ('installed_' + $Pkg.Split('.')[-1]) $script:Adb `
        ('-s ' + $script:Device + ' shell pm path ' + $Pkg) 12
    return ($r.Ok -and $r.Output -match 'package:')
}
function Install-Apk([string] $Apk, [string] $Pkg) {
    $relative = $Apk.Replace($Root + '\', '')
    if (-not (Test-Path -LiteralPath $Apk)) { Fail "APK не найден: $relative"; return $false }
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        Log "Устанавливаю $Pkg (попытка $attempt/2, без потокового режима)"
        $r = Invoke-Tool ("install_" + $Pkg.Split('.')[-1] + '_' + $attempt) $script:Adb `
            ('-s ' + $script:Device + ' install --no-streaming -r ' + (Q $Apk)) 120 -ShowOutput
        if ($r.Ok -and $r.Output -match '(?m)^Success\s*$') { return $true }
        if (($r.Output + $r.ErrorText) -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
            Fail "Подпись $Pkg отличается от уже установленной. Автоматически НЕ удаляю приложение и его данные. См. docs/AUTO_WINDOWS.md."
            return $false
        }
        if ($attempt -eq 1) {
            Warn "Установка не подтвердилась; перезапускаю ADB и повторяю один раз."
            Invoke-Tool 'adb_recover_kill' $script:Adb 'kill-server' 12 | Out-Null
            Invoke-Tool 'adb_recover_start' $script:Adb 'start-server' 20 | Out-Null
            if (-not (Check-Boot $script:Device)) {
                Fail 'Эмулятор перестал отвечать на getprop/pm. Автозапуск остановлен, чтобы не зависать бесконечно. См. docs/AUTO_WINDOWS.md.'
                return $false
            }
        }
    }
    Fail "Не удалось установить $Pkg. Полный вывод сохранён в $LogDir"
    return $false
}
function Start-ColdAvd {
    if (-not $script:Emulator) { return $false }
    $result = Invoke-Tool 'avd_list' $script:Emulator '-list-avds' 15
    if (-not $result.Ok) { return $false }
    $avds = @($result.Output -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -notmatch '^INFO|^WARNING' })
    if ($avds.Count -eq 0) {
        Warn 'Нет созданного виртуального телефона (AVD). Один раз создайте Pixel 6 API 36 в Device Manager.'
        return $false
    }
    if (-not $script:AvdName -or $script:AvdName -notin $avds) { $script:AvdName = $avds[0] }
    if ($avds.Count -gt 1) { Warn "Несколько AVD; будет запущен: $($script:AvdName)" }
    Log "Запускаю Android Emulator: $($script:AvdName) (Cold Boot, без удаления данных)"
    try {
        # Эмулятор работает независимо от нашего скрипта; не ждём окончания процесса.
        Start-Process -FilePath $script:Emulator -ArgumentList @('-avd', $script:AvdName, '-no-snapshot-load') -WorkingDirectory $Root | Out-Null
        return $true
    } catch {
        Warn ('Не удалось открыть эмулятор: ' + $_.Exception.Message)
        return $false
    }
}
function Wait-For-Device([int] $MaxSeconds) {
    $end = (Get-Date).AddSeconds($MaxSeconds)
    while ((Get-Date) -lt $end) {
        $candidate = Find-OnlineDevice
        if ($candidate -and (Check-Boot $candidate)) { return $candidate }
        Log 'Ожидаю полную загрузку Android (до 5 минут)...'
        Start-Sleep -Seconds 8
    }
    return $null
}
function Fix-Unresponsive-Emulator([string] $Serial) {
    Warn "Эмулятор $Serial подключён, но Android не отвечает. Пробую восстановить ADB."
    Invoke-Tool 'restart_adb_kill' $script:Adb 'kill-server' 10 | Out-Null
    Invoke-Tool 'restart_adb_start' $script:Adb 'start-server' 18 | Out-Null
    if (Check-Boot $Serial) { return $true }
    # Стараемся определить имя именно зависшего AVD до его выключения.
    $avdInfo = Invoke-Tool 'emulator_avd_name' $script:Adb ('-s ' + $Serial + ' emu avd name') 8
    if ($avdInfo.Ok) {
        $names = @($avdInfo.Output -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -notmatch '^(OK|INFO|WARNING)' })
        if ($names.Count -gt 0) { $script:AvdName = $names[0] }
    }
    # Только мягкая команда остановки конкретного эмулятора. Не затрагиваем другие
    # устройства, не очищаем данные, не убиваем посторонние процессы Windows.
    $k = Invoke-Tool 'emulator_shutdown' $script:Adb ('-s ' + $Serial + ' emu kill') 12
    if (-not $k.Ok) {
        Warn 'Эмулятор не ответил даже на emu kill. Требуется закрыть его в Device Manager и повторить запуск.'
        return $false
    }
    Start-Sleep -Seconds 6
    if (-not (Start-ColdAvd)) { return $false }
    $script:Device = Wait-For-Device 300
    return [bool]$script:Device
}

# Проверка apkanalyzer для НЕИЗМЕНЁННОГО scripts/check.sh.
# Преподавательский скрипт ищет SDK/cmdline-tools/latest/bin/apkanalyzer.bat.
# При отсутствии инструмента скачиваем только официальный архив Android SDK
# Command-Line Tools for Windows (с SHA-256 официального сайта разработчиков).
# Никогда не перезаписываем существующую установку latest; не меняем лицензии.
function Ensure-ApkAnalyzer {
    $targetRoot = Join-Path $script:Sdk 'cmdline-tools\latest'
    $binary = Join-Path $targetRoot 'bin\apkanalyzer.bat'
    if (Test-Path -LiteralPath $binary) {
        Log 'apkanalyzer найден: cmdline-tools/latest/bin/apkanalyzer.bat'
        return $true
    }
    if (Test-Path -LiteralPath $targetRoot) {
        Warn 'В SDK есть cmdline-tools/latest, но apkanalyzer.bat отсутствует. Не перезаписываю повреждённую или неполную SDK-установку. Откройте Android Studio -> Tools -> SDK Manager -> SDK Tools -> Android SDK Command-line Tools (latest).'
        return $false
    }

    $license = Join-Path $script:Sdk 'licenses\android-sdk-license'
    if (-not (Test-Path -LiteralPath $license)) {
        Warn 'В SDK не найдено подтверждение Android SDK License. Не принимаю лицензию автоматически. Установите Android SDK Command-line Tools (latest) через SDK Manager с принятием условий Google.'
        return $false
    }

    # Никакого Wipe Data или удаления установленного SDK; скачиваем в TEMP.
    # URL и хеш для версии 15859902: https://developer.android.com/studio
    $url = 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip'
    $expected = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    $tempRoot = Join-Path $env:TEMP ('Lab2CmdTools-' + [guid]::NewGuid().ToString('N'))
    $zipFile = Join-Path $tempRoot 'cmdline-tools.zip'
    $unpacked = Join-Path $tempRoot 'expanded'
    Log 'apkanalyzer отсутствует. Попытка установить официальные Command-line Tools (около 156 МБ, нужен интернет).'
    try {
        New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $url -OutFile $zipFile -UseBasicParsing -TimeoutSec 300 -ErrorAction Stop
        $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $zipFile).Hash.ToLowerInvariant()
        if ($actual -ne $expected) { throw 'SHA-256 скачанного SDK-архива не совпал с официальным значением. Установка остановлена.' }
        Log 'SHA-256 архива SDK подтверждён; распаковываю.'
        Expand-Archive -LiteralPath $zipFile -DestinationPath $unpacked -Force
        $source = Join-Path $unpacked 'cmdline-tools'
        if (-not (Test-Path (Join-Path $source 'bin\apkanalyzer.bat')) -or
            -not (Test-Path (Join-Path $source 'lib'))) {
            throw 'В скачанном архиве отсутствует apkanalyzer.bat либо каталог lib.'
        }
        $parent = Join-Path $script:Sdk 'cmdline-tools'
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
        if (Test-Path -LiteralPath $targetRoot) {
            # Возможна параллельная установка через Android Studio.
            if (Test-Path -LiteralPath $binary) { return $true }
            throw 'Папка cmdline-tools/latest появилась во время загрузки. Ничего не перезаписано.'
        }
        Move-Item -LiteralPath $source -Destination $targetRoot -ErrorAction Stop
        if (-not (Test-Path -LiteralPath $binary)) { throw 'После установки apkanalyzer.bat не найден.' }
        Log 'Android SDK Command-line Tools установлены, apkanalyzer доступен.'
        return $true
    } catch {
        Warn ('Автоустановка Command-line Tools не удалась: ' + $_.Exception.Message)
        Warn 'Можно установить их через Android Studio -> Tools -> SDK Manager -> SDK Tools -> Android SDK Command-line Tools (latest), затем нажать CHECK_LAB2.cmd.'
        return $false
    } finally {
        if (Test-Path -LiteralPath $tempRoot) {
            try { Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction Stop } catch { Warn 'Не удалось удалить временные файлы SDK: ' + $tempRoot }
        }
    }
}

# Запуск Activity через intent-filter MAIN/LAUNCHER — без жёсткого имени класса.
# Результат am start дополнительно проверяем по тексту: некоторые Android shell
# команды могут вернуть 0, но сообщить Error type 3 внутри stdout.
function Launch-App([string] $Pkg, [string] $Label) {
    $args = '-s ' + $script:Device + ' shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p ' + $Pkg
    $r = Invoke-Tool ('launch_' + $Label) $script:Adb $args 25 -ShowOutput
    if ($r.Ok -and ($r.Output + $r.ErrorText) -notmatch 'Error type|Activity not started, unable to resolve|does not exist|Exception|Error:') {
        return $true
    }
    Warn ('MAIN/LAUNCHER не запустил ' + $Pkg + '; пробую monkey для системного выбора Activity.')
    $fallback = Invoke-Tool ('launch_' + $Label + '_monkey') $script:Adb `
        ('-s ' + $script:Device + ' shell monkey -p ' + $Pkg + ' -c android.intent.category.LAUNCHER 1') 30 -ShowOutput
    return ($fallback.Ok -and $fallback.Output -match 'Events injected: 1')
}

function Print-Finish {
    Log '---------------- РЕЗУЛЬТАТ ----------------'
    if ($script:Failures.Count -eq 0) {
        Log 'Сценарий завершён без критических ошибок. Проверяйте статус тестов выше.'
    } else {
        foreach ($failure in $script:Failures) { Log $failure 'ERROR' }
    }
    if ($script:Warnings.Count -gt 0) {
        foreach ($warning in $script:Warnings) { Log $warning 'WARN' }
    }
    Log "Логи всех команд: $LogDir"
    Log 'Для отправки отчёта приложите logs/auto-.../summary.txt, security-check.stdout.txt и при необходимости остальные файлы.'
}


# Автономная проверка считывания exit code: никаких SDK/эмуляторов не нужно.
# Запуск: SELFTEST_LAB2.cmd. Успех: 0 распознан как OK, 7 — как ошибка.
if ($SelfTest) {
    Log '=== Самопроверка запуска внешних программ ==='
    $test0 = Invoke-Tool 'selftest_success' 'cmd.exe' '/d /s /c "exit /b 0"' 15
    $test7 = Invoke-Tool 'selftest_failure_expected' 'cmd.exe' '/d /s /c "exit /b 7"' 15
    if ($test0.Ok -and $test0.ExitCode -eq 0 -and -not $test7.Ok -and $test7.ExitCode -eq 7) {
        Log 'SELFTEST PASSED: коды завершения 0 и 7 определены правильно.'
        exit 0
    }
    Fail "SELFTEST FAILED: 0 -> $($test0.ExitCode), 7 -> $($test7.ExitCode). См. логи."
    exit 1
}

try {
    Log '=== Лабораторная #2: автоматическая сборка, установка, проверка ==='
    Log "Папка проекта: $Root"
    # Проверяем код завершения заранее: если Windows запрещает запуск, не ждём Gradle 30 секунд.
    $runnerHealth = Invoke-Tool 'runner_healthcheck' 'cmd.exe' '/d /s /c "exit /b 0"' 15
    if (-not $runnerHealth.Ok -or $runnerHealth.ExitCode -ne 0) {
        throw 'Самопроверка запуска внешних команд не прошла. Выполните SELFTEST_LAB2.cmd и отправьте summary.txt.'
    }
    $script:Sdk = Find-Sdk
    if (-not $script:Sdk) { throw 'Android SDK не найден. Установите SDK через Android Studio -> Tools -> SDK Manager; см. docs/AUTO_WINDOWS.md.' }
    $env:ANDROID_HOME = $script:Sdk
    $env:ANDROID_SDK_ROOT = $script:Sdk
    $script:Adb = Join-Path $script:Sdk 'platform-tools\adb.exe'
    $script:Emulator = Find-Emulator $script:Sdk
    Log "Android SDK: $script:Sdk"

    # Gradle 8.13 работает с Java 17/21. Ищем JBR Android Studio, если JAVA_HOME не задан.
    if (-not $env:JAVA_HOME) {
        foreach ($jdk in @(
            "$env:ProgramFiles\Android\Android Studio\jbr",
            "$env:ProgramFiles\Java\jdk-17",
            "$env:ProgramFiles\Eclipse Adoptium\jdk-17"
        )) {
            if ($jdk -and (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
                $env:JAVA_HOME = $jdk
                Log "JAVA_HOME установлен для этого запуска: $jdk"
                break
            }
        }
    }

    $notesApk = Join-Path $Root 'notes\build\outputs\apk\debug\notes-debug.apk'
    $clientApk = Join-Path $Root 'client\build\outputs\apk\debug\client-debug.apk'
    if (-not $NoBuild) {
        $wrapper = Join-Path $Root 'gradlew.bat'
        if (-not (Test-Path -LiteralPath $wrapper)) { throw 'Отсутствует gradlew.bat' }
        $gradleCmd = '/d /s /c ' + ('"' + (Q $wrapper) + ' :notes:assembleDebug :client:assembleDebug --console=plain"')
        $build = Invoke-Tool 'gradle_build' 'cmd.exe' $gradleCmd 900 -ShowOutput
        if (-not $build.Ok) { throw 'Gradle не собрал два APK. Ошибка в logs/auto-.../NNN-gradle_build.*. Не устанавливаю старые APK.' }
        Log 'Оба APK собраны.'
    } else {
        Log 'Сборка пропущена по параметру -NoBuild.'
    }
    if (-not (Test-Path $notesApk) -or -not (Test-Path $clientApk)) {
        throw 'APK не найдены после сборки. Смотрите Gradle-лог и пути build/outputs/apk/debug/.'
    }

    $script:Device = Find-OnlineDevice
    if ($script:Device) {
        Log "Найден запущенный эмулятор: $script:Device"
        if (-not (Check-Boot $script:Device)) {
            if (-not (Fix-Unresponsive-Emulator $script:Device)) {
                throw 'Эмулятор не отвечает; автоматическая установка остановлена. Данные эмулятора не удалялись.'
            }
        }
    } else {
        if (-not (Start-ColdAvd)) { throw 'Эмулятор отсутствует и автоматически запустить его не удалось.' }
        $script:Device = Wait-For-Device 300
        if (-not $script:Device) { throw 'Эмулятор не загрузился за 5 минут. Проверьте Device Manager и logs/.' }
    }
    Log "Android готов: $script:Device"
    # Все последующие adb вызовы из Git Bash адресуются этому же устройству.
    $env:ANDROID_SERIAL = $script:Device

    if (-not (Install-Apk $notesApk 'edu.androidsec.notes')) { throw 'Приложение Notes не установлено.' }
    if (-not (Install-Apk $clientApk 'edu.androidsec.notes.client')) { throw 'Приложение Client не установлено.' }
    if (-not (Check-Installed 'edu.androidsec.notes')) { throw 'Notes не обнаружено после установки.' }
    if (-not (Check-Installed 'edu.androidsec.notes.client')) { throw 'Client не обнаружен после установки.' }
    Log 'Оба приложения установлены и обнаружены Package Manager.'

    if (-not $SkipSecurityChecks) {
        $bash = Find-GitBash
        if ($bash) {
            # Git Bash требует /c/Users/... для Android SDK; перевод пути без внешних команд.
            $sdkUnix = ($script:Sdk -replace '\\', '/')
            if ($sdkUnix -match '^([A-Za-z]):(.*)$') {
                $sdkUnix = '/' + $Matches[1].ToLowerInvariant() + $Matches[2]
            }
            $env:ANDROID_HOME = $sdkUnix
            $env:ANDROID_SDK_ROOT = $sdkUnix
            if (Ensure-ApkAnalyzer) {
                Log 'Запускаю НЕИЗМЕНЁННЫЙ скрипт преподавателя: scripts/check.sh all'
                $check = Invoke-Tool 'security-check' $bash '-c "bash scripts/check.sh all"' 300 -ShowOutput
                $evidence = Join-Path $Root 'docs\evidence\check-all.txt'
                Copy-Item -LiteralPath $check.OutPath -Destination $evidence -Force
                Add-Content -LiteralPath $evidence -Value ("`n=== stderr ===`n" + $check.ErrorText) -Encoding UTF8
                Log "Реальный вывод check.sh сохранён: $evidence"
                if ($check.TimedOut) { Fail 'scripts/check.sh all превысил 5 минут (возможно, ADB или устройство зависло).' }
                elseif (-not $check.Ok) { Fail "scripts/check.sh all: код $($check.ExitCode). Прочтите PASS/FAIL в docs/evidence/check-all.txt." }
                else { Log 'scripts/check.sh all завершился с кодом 0. Все проверки скрипта пройдены.' }
            } else {
                Fail 'scripts/check.sh all пока нельзя выполнить: apkanalyzer отсутствует в SDK. При этом тесты и запуск приложений продолжатся.'
            }
        } else {
            Fail 'Git for Windows не найден: scripts/check.sh all НЕ проверен. Установите Git for Windows и повторите запуск.'
        }
    }

    if (-not $SkipInstrumentedTests) {
        # Инструментальные тесты дополняют, но НЕ заменяют проверку check.sh.
        # На Google Play emulator тесты не должны затрагивать пользовательские заметки.
        $env:ANDROID_HOME = $script:Sdk
        $env:ANDROID_SDK_ROOT = $script:Sdk
        $wrapper = Join-Path $Root 'gradlew.bat'
        $testCmd = '/d /s /c ' + ('"' + (Q $wrapper) + ' :notes:connectedDebugAndroidTest --console=plain"')
        $tests = Invoke-Tool 'instrumented-tests' 'cmd.exe' $testCmd 900 -ShowOutput
        if (-not $tests.Ok) { Fail 'Инструментальные тесты не прошли: см. instrumented-tests.* и notes/build/reports/androidTests/' }
        else { Log 'Инструментальные тесты завершились успешно.' }
    }

    # Инструментальные тесты иногда переустанавливают/удаляют target APK.
    # Не делаем вид, что приложение присутствует, пока не проверили Package Manager.
    if (-not (Check-Installed 'edu.androidsec.notes')) {
        Warn 'После инструментальных тестов Notes отсутствует. Устанавливаю заново (без очистки данных).'
        if (-not (Install-Apk $notesApk 'edu.androidsec.notes')) { Fail 'Notes отсутствует после тестов и не переустановилось.' }
    }
    if (-not (Check-Installed 'edu.androidsec.notes.client')) {
        Warn 'После инструментальных тестов Client отсутствует. Устанавливаю заново (без очистки данных).'
        if (-not (Install-Apk $clientApk 'edu.androidsec.notes.client')) { Fail 'Client отсутствует после тестов и не переустановился.' }
    }
    # Контрольный запуск через MAIN/LAUNCHER, а не предположение о классе Activity.
    $notesLaunched = Launch-App 'edu.androidsec.notes' 'notes'
    $clientLaunched = Launch-App 'edu.androidsec.notes.client' 'client'
    if (-not $notesLaunched -or -not $clientLaunched) {
        Warn 'Одна из Activity не запустилась: см. launch_* в logs/auto-... и фактический AndroidManifest APK.'
    } else { Log 'Приложения запущены. На экране должен появиться клиент со списком заголовков.' }
} catch {
    Fail $_.Exception.Message
} finally {
    Print-Finish
}
if ($script:Failures.Count -gt 0) { exit 1 }
exit 0
