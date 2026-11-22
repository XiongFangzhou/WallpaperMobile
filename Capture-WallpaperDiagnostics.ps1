param(
    [ValidateSet('Start','Collect')][string]$Mode = 'Start',
    [string]$Serial = ''
)
. "$PSScriptRoot/Enter-Project.ps1"
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$diagAdb = Join-Path $PSScriptRoot '.tools/android-sdk/platform-tools/adb.exe'
$diagRoot = Join-Path $PSScriptRoot 'dist/diagnostics'
$diagStateFile = Join-Path $diagRoot 'session.json'
New-Item -ItemType Directory -Path $diagRoot -Force | Out-Null
$diagDevices = @(& $diagAdb -P 5041 devices | Where-Object { $_ -match '^([^\s]+)\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($LASTEXITCODE -ne 0) { throw 'Cannot contact adb.' }
if ($Serial) {
    if ($diagDevices -notcontains $Serial) { throw 'Selected device is not connected or USB debugging has not been authorized.' }
    $diagSerial = $Serial
} elseif ($diagDevices.Count -eq 1) {
    $diagSerial = $diagDevices[0]
} else {
    throw 'Connect one phone and authorize USB debugging, or pass -Serial to select one connected device.'
}
function Read-DiagAdb([string[]]$Arguments) {
    $diagResult = @(& $diagAdb -P 5041 -s $diagSerial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw ('adb failed: ' + ($diagResult -join "`n")) }
    return $diagResult
}
function Save-DiagSnapshot([string]$Directory, [string]$Prefix) {
    Read-DiagAdb @('shell','dumpsys','wallpaper') | Set-Content -LiteralPath (Join-Path $Directory "$Prefix-wallpaper.txt") -Encoding UTF8
    Read-DiagAdb @('shell','dumpsys','package','dev.workshopvideo') | Set-Content -LiteralPath (Join-Path $Directory "$Prefix-package.txt") -Encoding UTF8
    $diagRoutes = @()
    foreach ($diagAction in @('android.service.wallpaper.CHANGE_LIVE_WALLPAPER','android.service.wallpaper.LIVE_WALLPAPER_CHOOSER')) {
        $diagRoutes += "Action: $diagAction"
        $diagRoutes += Read-DiagAdb @('shell','cmd','package','resolve-activity','--brief','-a',$diagAction)
        $diagRoutes += Read-DiagAdb @('shell','cmd','package','query-activities','--brief','-a',$diagAction)
    }
    Read-DiagAdb @('shell','dumpsys','activity','dev.workshopvideo/.WallpaperSetupActivity') | Set-Content -LiteralPath (Join-Path $Directory "$Prefix-app-state.txt") -Encoding UTF8
    Read-DiagAdb @('shell','dumpsys','activity','service','dev.workshopvideo/.VideoWallpaperService') | Set-Content -LiteralPath (Join-Path $Directory "$Prefix-video-service.txt") -Encoding UTF8
    $diagManufacturer = (Read-DiagAdb @('shell','getprop','ro.product.manufacturer')) -join ''
    if ($diagManufacturer -match '(?i)xiaomi|redmi|poco') { Read-DiagAdb @('shell','cmd','appops','get','dev.workshopvideo','10045') | Set-Content -LiteralPath (Join-Path $Directory "$Prefix-wallpaper-permission.txt") -Encoding UTF8 }
    $diagRoutes | Set-Content -LiteralPath (Join-Path $Directory "$Prefix-routes.txt") -Encoding UTF8
}
if ($Mode -eq 'Start') {
    $diagDir = Join-Path $diagRoot (Get-Date -Format 'yyyyMMdd-HHmmss')
    New-Item -ItemType Directory -Path $diagDir -Force | Out-Null
    $diagDeviceTime = (Read-DiagAdb @('shell',"date '+%m-%d %H:%M:%S.000'")) -join ''
    if ($diagDeviceTime -notmatch '^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.000$') { throw 'Cannot read device time for log capture.' }
    @{serial=$diagSerial;directory=$diagDir;deviceTime=$diagDeviceTime} | ConvertTo-Json | Set-Content -LiteralPath $diagStateFile -Encoding UTF8
    Read-DiagAdb @('shell','getprop','ro.build.fingerprint') | Set-Content -LiteralPath (Join-Path $diagDir 'firmware.txt') -Encoding UTF8
    Save-DiagSnapshot $diagDir 'before'
    Write-Host 'Capture marker saved. Reproduce wallpaper setup on the phone and return to WallpaperMobile.'
    Write-Host 'Then run: ./Capture-WallpaperDiagnostics.ps1 -Mode Collect'
    Write-Host "Output directory: $diagDir"
} else {
    if (-not (Test-Path -LiteralPath $diagStateFile)) { throw 'Run -Mode Start before reproducing the problem.' }
    $diagState = Get-Content -LiteralPath $diagStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($diagState.serial -ne $diagSerial) { throw 'The capture marker belongs to a different phone.' }
    $diagDir = [System.IO.Path]::GetFullPath($diagState.directory)
    $diagBoundary = [System.IO.Path]::GetFullPath($diagRoot).TrimEnd('\') + '\'
    if (-not $diagDir.StartsWith($diagBoundary,[System.StringComparison]::OrdinalIgnoreCase)) { throw 'Diagnostic path is outside this E: project.' }
    # Only keep wallpaper-related lines and relevant system errors; do not clear phone logs.
    Read-DiagAdb @('logcat','-b','main','-b','system','-b','crash','-v','threadtime','-T',$diagState.deviceTime,'-d') |
        Where-Object { $_ -match '(?i)wallpaper|workshopvideo|livepicker|permission denial|securityexception|fatal exception|unable to start service|unable to bind service' } |
        Set-Content -LiteralPath (Join-Path $diagDir 'wallpaper-logcat.txt') -Encoding UTF8
    Save-DiagSnapshot $diagDir 'after'
    Write-Host "Diagnostics saved: $diagDir"
    Write-Host 'No installation, wallpaper change, data clearing, or automatic upload was performed by this script.'
}
