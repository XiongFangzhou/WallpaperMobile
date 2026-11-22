$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$videoProjectRoot = $PSScriptRoot
$env:TEMP = Join-Path $videoProjectRoot '.cache/tmp'
$env:TMP = $env:TEMP
$env:PYTHONPYCACHEPREFIX = Join-Path $videoProjectRoot '.cache/pycache'
$env:PYTHONIOENCODING = 'utf-8'
$env:ANDROID_HOME = Join-Path $videoProjectRoot '.tools/android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $videoProjectRoot '.cache/android-user'
$env:ANDROID_EMULATOR_HOME = Join-Path $videoProjectRoot '.cache/android-emulator'
$env:ANDROID_AVD_HOME = Join-Path $videoProjectRoot '.cache/avd'
$env:ANDROID_SDK_HOME = Join-Path $videoProjectRoot '.cache/android-home'
$env:GRADLE_USER_HOME = Join-Path $videoProjectRoot '.cache/gradle'
$videoJdkCandidates = @($env:WVB_JDK, $env:JAVA_HOME)
$videoLocalJdk = Join-Path $videoProjectRoot '.tools/jdk-path.txt'
if (Test-Path -LiteralPath $videoLocalJdk) {
    $videoConfiguredJdk = (Get-Content -LiteralPath $videoLocalJdk -Raw).Trim()
    if ($videoConfiguredJdk -and ![IO.Path]::IsPathRooted($videoConfiguredJdk)) {
        $videoConfiguredJdk = Join-Path $videoProjectRoot $videoConfiguredJdk
    }
    $videoJdkCandidates += $videoConfiguredJdk
}
$videoJdkCandidates += Join-Path $videoProjectRoot '.tools/jdk'
$videoCompiler = Get-Command javac.exe -ErrorAction SilentlyContinue
if ($videoCompiler) { $videoJdkCandidates += Split-Path -Parent (Split-Path -Parent $videoCompiler.Source) }
$videoResolvedJdk = $videoJdkCandidates | Where-Object {
    $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin/javac.exe'))
} | Select-Object -First 1
if (!$videoResolvedJdk) { throw 'Install JDK 21 and set JAVA_HOME or WVB_JDK, or configure .tools/jdk-path.txt.' }
$env:WVB_JDK = $videoResolvedJdk
$env:JAVA_HOME = $videoResolvedJdk
$env:ANDROID_ADB_SERVER_PORT = '5041'
$env:ADB_SERVER_SOCKET = 'tcp:127.0.0.1:5041'
New-Item -ItemType Directory -Path $env:TEMP,$env:ANDROID_USER_HOME,$env:ANDROID_SDK_HOME -Force | Out-Null
