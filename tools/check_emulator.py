"""Readiness and full-Android checks. Refuses every physical-device serial."""
import argparse,subprocess,time,json,pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1];ADB=ROOT/'.tools/android-sdk/platform-tools/adb.exe'
parser=argparse.ArgumentParser();parser.add_argument('--run',action='store_true');parser.add_argument('--wait',type=int,default=30);args=parser.parse_args()
OUT=ROOT/'.cache/emulator-app-validation';OUT.mkdir(parents=True,exist_ok=True)
def adb(*cmd,timeout=60):
 result=subprocess.run([str(ADB),'-P','5041','-s','emulator-5556',*cmd],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
 if result.returncode:raise RuntimeError(result.stdout+result.stderr)
 return result.stdout.strip()
end=time.monotonic()+args.wait
while True:
 try:ready=adb('get-state',timeout=10)=='device' and adb('shell','getprop','sys.boot_completed',timeout=10)=='1'
 except Exception:ready=False
 if ready:break
 if time.monotonic()>=end:raise SystemExit('Emulator not boot-complete. Start tools/start_android_validation.ps1 and retry --wait 900. No phone was accessed.')
 time.sleep(2)
hardware=adb('shell','getprop','ro.hardware');assert hardware in ('ranchu','goldfish'),'Refusing a non-emulator hardware target'
info={'serial':'emulator-5556','release':adb('shell','getprop','ro.build.version.release'),'api':adb('shell','getprop','ro.build.version.sdk'),'hardware':hardware,'boot_completed':True}
print('READY: '+json.dumps(info),flush=True)
(OUT/'environment.json').write_text(json.dumps(info,indent=2),encoding='utf-8')
if args.run:
 for apk,flags in [(ROOT/'dist/WallpaperMobile.apk',[]),(ROOT/'android/build/device-checks/WallpaperMobile-checks.apk',['-t'])]:
  if not apk.exists():raise RuntimeError('Missing '+str(apk))
  print(adb('install','--no-incremental','-r',*flags,str(apk),timeout=180),flush=True)
 print('Running production app, real TextureView/Canvas and video decoder checks in emulator only...',flush=True)
 result=adb('shell','am','instrument','-w','dev.workshopvideo.test/dev.workshopvideo.EmulatorChecks',timeout=1200)
 (OUT/'instrumentation.txt').write_text(result,encoding='utf-8');print(result,flush=True)
 if 'INSTRUMENTATION_RESULT: result=PASS' not in result:raise RuntimeError('Instrumentation did not finish with PASS; old artifact results are not accepted')
 adb('root',timeout=30)
 adb('wait-for-device',timeout=30)
 adb('pull','/data/media/0/Android/data/dev.workshopvideo/files/validation',str(OUT),timeout=90)
 report=json.loads((OUT/'validation/results.json').read_text(encoding='utf-8'))
 if report.get('result')!='PASS':raise RuntimeError('Full Android app validation failed: '+report.get('error','unknown'))
 print('PASS: '+str(report['checks'])+' full Android checks; screenshots and result in '+str(OUT),flush=True)

 print('Running real WallpaperService / WindowManager offset checks...',flush=True)
 result=adb('shell','am','instrument','--no-hidden-api-checks','-w','dev.workshopvideo.test/dev.workshopvideo.WallpaperServiceChecks',timeout=900)
 (OUT/'service-instrumentation.txt').write_text(result,encoding='utf-8');print(result,flush=True)
 if 'INSTRUMENTATION_RESULT: result=PASS' not in result:raise RuntimeError('Service instrumentation did not finish with PASS; old artifact results are not accepted')
 adb('pull','/data/media/0/Android/data/dev.workshopvideo/files/service-validation',str(OUT),timeout=90)
 service=json.loads((OUT/'service-validation/results.json').read_text(encoding='utf-8'))
 if service.get('result')!='PASS':raise RuntimeError('WallpaperService framework validation failed: '+service.get('error','unknown'))
 print('PASS: '+str(service['checks'])+' bound WallpaperService/WindowManager checks',flush=True)
