param([switch]$ShowWindow)
$ErrorActionPreference='Stop'
. (Join-Path (Split-Path -Parent $PSScriptRoot) 'Enter-Project.ps1')
$validationAdb=Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$validationDevices=& $validationAdb -P 5041 devices
if($validationDevices -match '^emulator-5556\s+(device|offline)') {Write-Output 'emulator-5556 is already running or booting. Run tools/check_emulator.py --wait 900 to verify readiness.';exit 0}
$validationEmulator=Join-Path (Get-Location) '.tools/emulator-software/emulator.exe'
if(!(Test-Path -LiteralPath $validationEmulator)){throw 'Run tools/prepare_software_avd.py first.'}
$validationLogs=Join-Path (Get-Location) '.cache/emulator-validation'
New-Item -ItemType Directory -Force -Path $validationLogs | Out-Null
$validationArgs=@('-avd','WESoftware','-port','5556','-accel','off','-no-audio','-no-snapshot','-no-boot-anim','-gpu','swiftshader_indirect','-feature','-Vulkan')
if(!$ShowWindow){$validationArgs+='-no-window'}
$validationProcess=Start-Process -FilePath $validationEmulator -ArgumentList $validationArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $validationLogs 'stdout.log') -RedirectStandardError (Join-Path $validationLogs 'stderr.log')
$validationProcess.Id | Set-Content -Encoding ascii (Join-Path $validationLogs 'launcher.pid')
Write-Output 'Started Android 11/API30 software CPU emulator-5556, adb port 5041. Images, userdata and logs stay in the repository workspace. Cold boot may take several minutes.'
Write-Output 'Verify readiness: python tools/check_emulator.py. Full app tests: python tools/check_emulator.py --run'
