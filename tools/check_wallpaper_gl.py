import ctypes as C,os,re,pathlib,json
root=pathlib.Path.cwd();native=root/'.tools/android-sdk/emulator/lib64/gles_angle'
search=os.add_dll_directory(str(native));egl=C.WinDLL(str(native/'libEGL.dll'));gl=C.WinDLL(str(native/'libGLESv2.dll'))
I=C.c_int;P=C.c_void_p;U=C.c_uint

def api(lib,name,restype,args):
 f=getattr(lib,name);f.restype=restype;f.argtypes=args;return f
getDisplay=api(egl,'eglGetDisplay',P,[P]);initialize=api(egl,'eglInitialize',U,[P,C.POINTER(I),C.POINTER(I)]);choose=api(egl,'eglChooseConfig',U,[P,C.POINTER(I),C.POINTER(P),I,C.POINTER(I)]);bind=api(egl,'eglBindAPI',U,[U]);createContext=api(egl,'eglCreateContext',P,[P,P,P,C.POINTER(I)]);createSurface=api(egl,'eglCreatePbufferSurface',P,[P,P,C.POINTER(I)]);makeCurrent=api(egl,'eglMakeCurrent',U,[P,P,P,P]);error=api(egl,'eglGetError',I,[])
display=getDisplay(None);major=I();minor=I();assert initialize(display,C.byref(major),C.byref(minor)),hex(error());config=P();count=I();attrs=(I*13)(0x3024,8,0x3023,8,0x3022,8,0x3033,1,0x3040,4,0x3021,8,0x3038);assert choose(display,attrs,C.byref(config),1,C.byref(count)) and count.value
assert bind(0x30a0);context=createContext(display,config,None,(I*3)(0x3098,2,0x3038));surface=createSurface(display,config,(I*5)(0x3057,8,0x3056,8,0x3038));assert makeCurrent(display,surface,surface,context),hex(error())
getString=api(gl,'glGetString',C.c_char_p,[U]);print('GL',getString(0x1f02).decode(),'RENDERER',getString(0x1f01).decode())
shaderNew=api(gl,'glCreateShader',U,[U]);shaderSource=api(gl,'glShaderSource',None,[U,I,C.POINTER(C.c_char_p),C.POINTER(I)]);compileShader=api(gl,'glCompileShader',None,[U]);shaderIV=api(gl,'glGetShaderiv',None,[U,U,C.POINTER(I)]);shaderLog=api(gl,'glGetShaderInfoLog',None,[U,I,C.POINTER(I),P]);newProgram=api(gl,'glCreateProgram',U,[]);attach=api(gl,'glAttachShader',None,[U,U]);link=api(gl,'glLinkProgram',None,[U]);programIV=api(gl,'glGetProgramiv',None,[U,U,C.POINTER(I)]);programLog=api(gl,'glGetProgramInfoLog',None,[U,I,C.POINTER(I),P])
source=(root/'android/src/dev/workshopvideo/WallpaperRenderer.java').read_text(encoding='utf-8')
shaders=[bytes(re.search(r'String '+name+r'="((?:[^"\\]|\\.)*)"',source).group(1),'utf-8').decode('unicode_escape') for name in ['vertex','fragment']]

def probe(codes):
 program=newProgram();logs=[]
 for type,code in zip([0x8b31,0x8b30],codes):
  shader=shaderNew(type);text=C.c_char_p(code.encode());shaderSource(shader,1,C.byref(text),None);compileShader(shader);compiled=I();shaderIV(shader,0x8b81,C.byref(compiled));log=C.create_string_buffer(8192);shaderLog(shader,8192,None,log);logs.append(log.value.decode());attach(program,shader)
 link(program);linked=I();programIV(program,0x8b82,C.byref(linked));log=C.create_string_buffer(8192);programLog(program,8192,None,log);return {'linked':bool(linked.value),'logs':logs+[log.value.decode()],'program':program}
video=probe(shaders)
print('VIDEO',json.dumps(video));assert video['linked'],video
extra=[bytes(re.search(r'String '+name+r'="((?:[^"\\]|\\.)*)"',source).group(1),'utf-8').decode('unicode_escape') for name in ['vert','frag']]
overlay=probe(extra);print('OVERLAY',json.dumps(overlay));assert overlay['linked'],overlay
canvas_fragment=bytes(re.search(r'if\(canvasMode\)fragment="((?:[^"\\]|\\.)*)"',source).group(1),'utf-8').decode('unicode_escape')
canvas_program=probe([shaders[0],canvas_fragment]);assert canvas_program['linked'],canvas_program
F=C.c_float
use=api(gl,'glUseProgram',None,[U]);uniform=api(gl,'glGetUniformLocation',I,[U,C.c_char_p]);u1=api(gl,'glUniform1f',None,[I,F]);u2=api(gl,'glUniform2f',None,[I,F,F]);u4=api(gl,'glUniform4f',None,[I,F,F,F,F]);ui=api(gl,'glUniform1i',None,[I,I]);um=api(gl,'glUniformMatrix4fv',None,[I,I,U,P]);textureNew=api(gl,'glGenTextures',None,[I,P]);textureBind=api(gl,'glBindTexture',None,[U,U]);parameter=api(gl,'glTexParameteri',None,[U,U,I]);textureImage=api(gl,'glTexImage2D',None,[U,I,I,I,I,I,U,U,P]);attribute=api(gl,'glGetAttribLocation',I,[U,C.c_char_p]);pointer=api(gl,'glVertexAttribPointer',None,[U,I,U,U,I,P]);enable=api(gl,'glEnableVertexAttribArray',None,[U]);viewport=api(gl,'glViewport',None,[I,I,I,I]);draw=api(gl,'glDrawArrays',None,[U,I,I]);pixels=api(gl,'glReadPixels',None,[I,I,I,I,U,U,P]);glError=api(gl,'glGetError',U,[])
program=canvas_program['program'];use(program)
def location(name):return uniform(program,name.encode())
ui(location('video'),0);u1(location('angle'),0);u1(location('aspect'),1);u1(location('flip'),0);u4(location('crop'),0,0,1,1);u2(location('scale'),3,1)
identity=(F*16)(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1);um(location('transform'),1,0,identity)
texture=U();textureNew(1,C.byref(texture));textureBind(0x0de1,texture.value)
for key,value in [(0x2801,0x2601),(0x2800,0x2601),(0x2802,0x812f),(0x2803,0x812f)]:parameter(0x0de1,key,value)
gradient=bytes(channel for y in range(64) for x in range(192) for channel in (x,255-x,y*4,255));data=C.create_string_buffer(gradient);textureImage(0x0de1,0,0x1908,192,64,0,0x1908,0x1401,data)
quad=(F*16)(-1,-1,0,0,1,-1,1,0,-1,1,0,1,1,1,1,1)
for name,offset in [('aPosition',0),('aUv',8)]:
 index=attribute(program,name.encode());enable(index);pointer(index,2,0x1406,0,16,C.cast(C.byref(quad,offset),P))
viewport(0,0,8,8);frame=(C.c_ubyte*(8*8*4))()
for offset,expected in [(0,36),(.5,100),(1,164)]:
 u2(location('position'),(1-2*offset)*2,0);draw(5,0,4);pixels(0,0,8,8,0x1908,0x1401,frame)
 assert abs(frame[(4*8+4)*4]-expected)<=2,('cached scene page',offset,list(frame[(4*8+4)*4:(4*8+4)*4+4]))
 assert frame[(0*8+4)*4+2]>220 and frame[(7*8+4)*4+2]<30,'cached texture vertical orientation'
assert glError()==0
print('PASS: production shaders compile/link; cached scene texture draws first/middle/last pages with correct orientation in desktop ANGLE; not phone frame-rate validation')
