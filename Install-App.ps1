param([string]$Serial)
. "$PSScriptRoot/Enter-Project.ps1"
$videoAdb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
if (-not $Serial) {
    & $videoAdb -P 5041 devices -l
    Write-Output 'Run Install-App.ps1 -Serial <device-serial> to select the phone explicitly.'
    exit 0
}
& $videoAdb -P 5041 -s $Serial install -r './dist/WallpaperMobile.apk'
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
