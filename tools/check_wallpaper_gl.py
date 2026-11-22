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
 link(program);linked=I();programIV(program,0x8b82,C.byref(linked));log=C.create_string_buffer(8192);programLog(program,8192,None,log);return {'linked':bool(linked.value),'logs':logs+[log.value.decode()]}
video=probe(shaders)
print('VIDEO',json.dumps(video));assert video['linked'],video
extra=[bytes(re.search(r'String '+name+r'="((?:[^"\\]|\\.)*)"',source).group(1),'utf-8').decode('unicode_escape') for name in ['vert','frag']]
overlay=probe(extra);print('OVERLAY',json.dumps(overlay));assert overlay['linked'],overlay
print('PASS: production video and overlay shaders compile/link in desktop ANGLE; not a phone GPU test')
