"""Generate original geometric layers for testing motion; no external artwork."""
import json,math,pathlib,struct,zlib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
def png(w,h,pixel):
 data=bytearray()
 for y in range(h):
  data.append(0)
  for x in range(w):data.extend(pixel(x,y))
 def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(data,9))+chunk(b'IEND',b'')
def background(x,y):
 glow=max(0,1-math.hypot((x-130)/220,(y-220)/400));star=((x*97+y*37)%1543==0)
 return (min(255,int(8+15*glow)+(120 if star else 0)),min(255,int(20+40*glow)+(120 if star else 0)),min(255,int(42+62*glow)+(120 if star else 0)),255)
def mountain(x,y):
 edge=500+65*math.sin(x/90)+25*math.sin(x/37)
 return (24,69,82,255) if y>edge else (0,0,0,0)
def foreground(x,y):
 ring=abs(math.hypot(x-265,y-290)-43)<2
 if ring:return (169,239,213,210)
 if (x-80)**2+(y-175)**2<22**2:return (140,190,255,220)
 return (12,33,44,255) if y>675+25*math.sin(x/63) else (0,0,0,0)
manifest={'format':'WallpaperMobileScene/1','width':360,'height':800,'effects':{'gyro':True,'touch':True,'pan':True,'ripple':True,'particles':True,'clock':True,'zoom':1.15,'strength':.35,'smooth':.2,'fps':30},'layers':[{'file':'background.png','width':360,'height':800,'depth':0},{'file':'mountains.png','width':360,'height':800,'depth':.65},{'file':'foreground.png','width':360,'height':800,'depth':1.5}]}
asset=ROOT/'android/assets/demo-scene.zip';asset.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(asset,'w',zipfile.ZIP_DEFLATED) as zip:
 zip.writestr('wallpaper-mobile.json',json.dumps(manifest))
 for name,fn in [('background.png',background),('mountains.png',mountain),('foreground.png',foreground)]:zip.writestr(name,png(360,800,fn))
dist=ROOT/'dist/samples';dist.mkdir(parents=True,exist_ok=True);(dist/'WallpaperMobile-分层互动示例.zip').write_bytes(asset.read_bytes());print('Original 3-layer interactive sample:',asset,asset.stat().st_size)
