# Запуск: powershell -ExecutionPolicy Bypass -File .\scripts\check-windows.ps1
# Ничего не устанавливает, не раскрывает API key и не меняет конфигурацию.
$root = Split-Path -Parent $PSScriptRoot
Write-Host "Make It So — локальная проверка перед сборкой" -ForegroundColor Cyan
$required = @("settings.gradle.kts", "gradlew.bat", "gradle\wrapper\gradle-wrapper.jar", "gradle\wrapper\gradle-wrapper.properties", "gradle\libs.versions.toml", "app\build.gradle.kts", "firestore.rules", "app\google-services.json")
foreach($f in $required) {
  $path = Join-Path $root $f
  if(Test-Path $path) { Write-Host "OK: $f" -ForegroundColor Green }
  else { Write-Host "НЕТ: $f" -ForegroundColor Yellow }
}
if(Get-Command java -ErrorAction SilentlyContinue) { Write-Host "Java обнаружена:"; java -version }
else { Write-Host "Java отсутствует в PATH. В Android Studio можно использовать Embedded JDK." -ForegroundColor Yellow }
if (Test-Path (Join-Path $root "app\google-services.json")) {
 try {
  $json=Get-Content (Join-Path $root "app\google-services.json") -Raw | ConvertFrom-Json
  $packages=@($json.client | ForEach-Object { $_.client_info.android_client_info.package_name })
  if($packages -contains "com.google.firebase.example.makeitso") { Write-Host "OK: package name в Firebase JSON" -ForegroundColor Green }
  else { Write-Host "ERROR: package name не совпадает с com.google.firebase.example.makeitso" -ForegroundColor Red }
 } catch { Write-Host "ERROR: неверный JSON" -ForegroundColor Red }
}
Write-Host "Далее: из папки v2 запусти .\gradlew.bat signingReport и .\gradlew.bat assembleDebug"
