. "$PSScriptRoot/Enter-Project.ps1"
if (-not (Test-Path -LiteralPath './.tools/r8.jar')) {
    python ./tools/prepare_android.py
    if ($LASTEXITCODE -ne 0) { throw 'Android tools preparation failed.' }
}
python ./tools/prepare_dependencies.py
if ($LASTEXITCODE -ne 0) { throw 'Runtime dependency preparation failed.' }
python ./tools/check_core.py
if ($LASTEXITCODE -ne 0) { throw 'Core checks failed.' }
python ./tools/build_android.py
if ($LASTEXITCODE -ne 0) { throw 'APK build failed.' }
