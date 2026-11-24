package dev.workshopvideo;

import android.graphics.Bitmap;
import dev.workshopvideo.core.Particles;
import org.json.JSONArray;
import org.json.JSONObject;

/** Wallpaper Engine particle JSON and a scene's instance overrides → {@link Particles.Spec}; stand-ins for built-in particle textures. */
final class SceneParticles {
    private SceneParticles(){}

    static float[] vec(Object value,float... fallback){
        float[] v=WallpaperContent.vector(value,fallback);if(v.length>=fallback.length)return v;
        float[] out=fallback.clone();if(v.length==1)for(int i=0;i<out.length;i++)out[i]=v[0];else System.arraycopy(v,0,out,0,v.length);return out;
    }
    static float num(Object value,float fallback){float[] v=WallpaperContent.vector(value,fallback);return v.length>0&&Float.isFinite(v[0])?v[0]:fallback;}
    /** Rotation and angular values may be scalars or vectors; WE scenes use the z component. */
    private static float z(Object value,float fallback){float[] v=WallpaperContent.vector(value,fallback);return v.length>=3?v[2]:v.length>0?v[0]:fallback;}

    static Particles.Spec spec(JSONObject system,JSONObject override,boolean additive,float[][] frames){
        Particles.Spec s=new Particles.Spec();JSONObject o=override==null?new JSONObject():override;
        float count=num(o.opt("count"),1),rate=num(o.opt("rate"),1),speed=num(o.opt("speed"),1),size=num(o.opt("size"),1),lifetime=num(o.opt("lifetime"),1),alpha=num(o.opt("alpha"),1);
        float[] tint=vec(o.opt("colorn"),1,1,1);
        s.maxCount=Math.max(1,Math.min(2000,Math.round(system.optInt("maxcount",32)*count)));s.startTime=num(system.opt("starttime"),0);s.additive=additive;
        JSONArray emitters=system.optJSONArray("emitter");
        if(emitters!=null)for(int i=0;i<emitters.length();i++){
            JSONObject e=emitters.optJSONObject(i);if(e==null)continue;Particles.Emitter m=new Particles.Emitter();m.sphere=e.optString("name").equals("sphererandom");
            m.rate=num(e.opt("rate"),5)*rate;m.speedMin=num(e.opt("speedmin"),0)*speed;m.speedMax=num(e.opt("speedmax"),0)*speed;m.instantaneous=Math.round(num(e.opt("instantaneous"),0)*count);
            m.origin=vec(e.opt("origin"),0,0,0);m.directions=vec(e.opt("directions"),1,1,0);
            m.distanceMin=vec(e.opt("distancemin"),0,0,0);m.distanceMax=vec(e.opt("distancemax"),m.sphere?0:256,m.sphere?0:256,0);
            s.emitters.add(m);
        }
        JSONArray init=system.optJSONArray("initializer");
        if(init!=null)for(int i=0;i<init.length();i++){
            JSONObject v=init.optJSONObject(i);if(v==null)continue;
            switch(v.optString("name")){
                case "lifetimerandom"->{s.lifeMin=num(v.opt("min"),1)*lifetime;s.lifeMax=num(v.opt("max"),1)*lifetime;}
                case "sizerandom"->{s.sizeMin=num(v.opt("min"),0)*size;s.sizeMax=num(v.opt("max"),20)*size;}
                case "alpharandom"->{s.alphaMin=num(v.opt("min"),.05f);s.alphaMax=num(v.opt("max"),1);}
                case "colorrandom"->{float[] lo=vec(v.opt("min"),255,255,255),hi=v.has("max")?vec(v.opt("max"),255,255,255):lo;for(int k=0;k<3;k++){s.colorMin[k]=lo[k]/255f*tint[k];s.colorMax[k]=hi[k]/255f*tint[k];}}
                case "velocityrandom"->{s.velocityMin=mul(vec(v.opt("min"),-32,-32,0),speed);s.velocityMax=mul(vec(v.opt("max"),32,32,0),speed);}
                case "turbulentvelocityrandom"->{s.turbulentVelocity=true;s.turbulentSpeedMin=num(v.opt("speedmin"),100)*speed;s.turbulentSpeedMax=num(v.opt("speedmax"),250)*speed;s.turbulentScale=num(v.opt("scale"),1);s.turbulentForward=vec(v.opt("forward"),0,1,0);}
                case "rotationrandom"->{s.rotationMin=z(v.opt("min"),0);s.rotationMax=z(v.opt("max"),(float)(Math.PI*2));}
                case "angularvelocityrandom"->{s.angularMin=z(v.opt("min"),-5);s.angularMax=z(v.opt("max"),5);}
                default->{}
            }
        }
        s.alphaMin*=alpha;s.alphaMax*=alpha;
        JSONArray ops=system.optJSONArray("operator");
        if(ops!=null)for(int i=0;i<ops.length();i++){
            JSONObject v=ops.optJSONObject(i);if(v==null)continue;
            switch(v.optString("name")){
                case "movement"->{s.movement=true;s.gravity=vec(v.opt("gravity"),0,0,0);s.drag=num(v.opt("drag"),0);}
                case "alphafade"->{s.alphaFade=true;s.fadeIn=num(v.opt("fadeintime"),.5f);s.fadeOut=num(v.opt("fadeouttime"),.5f);}
                case "sizechange"->{s.sizeChange=true;s.sizeChangeStart=num(v.opt("starttime"),0);s.sizeChangeEnd=num(v.opt("endtime"),1);s.sizeStartValue=num(v.opt("startvalue"),1);s.sizeEndValue=num(v.opt("endvalue"),0);}
                case "oscillatealpha"->{s.oscillateAlpha=true;s.alphaFrequencyMin=num(v.opt("frequencymin"),0);s.alphaFrequencyMax=num(v.opt("frequencymax"),10);s.alphaScaleMin=num(v.opt("scalemin"),0);s.alphaScaleMax=num(v.opt("scalemax"),1);}
                case "oscillatesize"->{s.oscillateSize=true;s.sizeFrequencyMin=num(v.opt("frequencymin"),0);s.sizeFrequencyMax=num(v.opt("frequencymax"),10);s.sizeScaleMin=num(v.opt("scalemin"),.8f);s.sizeScaleMax=num(v.opt("scalemax"),1.2f);}
                case "oscillateposition"->{s.oscillatePosition=true;s.positionFrequencyMin=num(v.opt("frequencymin"),0);s.positionFrequencyMax=num(v.opt("frequencymax"),10);s.positionScaleMin=num(v.opt("scalemin"),0);s.positionScaleMax=num(v.opt("scalemax"),10);s.positionMask=vec(v.opt("mask"),1,1,0);}
                case "turbulence"->{s.turbulence=true;s.turbulenceScale=num(v.opt("scale"),.005f);s.turbulenceSpeedMin=num(v.opt("speedmin"),500)*speed;s.turbulenceSpeedMax=num(v.opt("speedmax"),1000)*speed;s.turbulenceTimescale=num(v.opt("timescale"),1);s.turbulenceMask=vec(v.opt("mask"),1,1,0);}
                case "controlpointattract"->{s.attract=true;s.attractScale=num(v.opt("scale"),1);s.attractThreshold=num(v.opt("threshold"),100);s.attractPoint=vec(v.opt("origin"),0,0,0);}
                case "angularmovement"->{s.angularMovement=true;s.angularForce=z(v.opt("force"),0);s.angularDrag=num(v.opt("drag"),0);}
                default->{}
            }
        }
        JSONArray renderers=system.optJSONArray("renderer");JSONObject renderer=renderers==null?null:renderers.optJSONObject(0);
        if(renderer!=null&&renderer.optString("name").equals("rope"))s.rope=true;
        JSONArray points=system.optJSONArray("controlpoint");if(points!=null)for(int i=0;i<points.length();i++){JSONObject p=points.optJSONObject(i);if(p!=null&&p.optInt("id",-1)==0&&(p.optInt("flags",0)&1)!=0)s.followCursor=true;}
        if(renderer!=null&&renderer.optString("name").equals("spritetrail")){s.trail=true;s.trailLength=num(renderer.opt("length"),.05f);s.trailMaxLength=num(renderer.opt("maxlength"),10);}
        if(frames!=null&&frames.length>0)s.frames=frames;s.sequence="sequence".equals(system.optString("animationmode"));s.sequenceMultiplier=num(system.opt("sequencemultiplier"),1);
        return s;
    }
    private static float[] mul(float[] v,float k){float[] out=v.clone();for(int i=0;i<out.length;i++)out[i]*=k;return out;}

    /** Approximation of a built-in WE particle texture by name, or null when there is no reasonable stand-in. */
    static Bitmap builtIn(String name){
        String n=name.toLowerCase(java.util.Locale.ROOT);
        if(n.startsWith("particle/halo")||n.contains("chromaticdot")||n.startsWith("particle/light")||n.contains("glow"))return radial(64,2.2f);
        if(n.startsWith("particle/fog")||n.contains("smoke")||n.contains("cloud"))return fog(128);
        return null;
    }
    /** Soft white disc whose alpha falls off as (1-r)^power. */
    static Bitmap radial(int size,float power){
        int[] pixels=new int[size*size];float c=(size-1)/2f;
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){float r=(float)Math.hypot(x-c,y-c)/c;int a=Math.round(255*(float)Math.pow(Math.max(0,1-r),power));pixels[y*size+x]=(a<<24|0xffffff);}
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
    }
    /** Soft cloudy blob: a radial falloff modulated by a few octaves of smooth noise. */
    static Bitmap fog(int size){
        int[] pixels=new int[size*size];float c=(size-1)/2f;java.util.SplittableRandom random=new java.util.SplittableRandom(11);
        float[][] grid=new float[9][9];for(float[] row:grid)for(int i=0;i<row.length;i++)row[i]=(float)random.nextDouble();
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            float r=(float)Math.hypot(x-c,y-c)/c,falloff=(float)Math.pow(Math.max(0,1-r),1.6f);
            float n=0,amp=.6f,scale=1;for(int o=0;o<3;o++){n+=amp*smooth(grid,x/(float)size*4*scale,y/(float)size*4*scale);amp*=.5f;scale*=2;}
            int a=Math.round(255*Math.max(0,Math.min(1,falloff*(.55f+n*.8f))));pixels[y*size+x]=(a<<24|0xffffff);
        }
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
    }
    private static float smooth(float[][] g,float x,float y){
        int x0=(int)Math.floor(x)&7,y0=(int)Math.floor(y)&7;float fx=x-(float)Math.floor(x),fy=y-(float)Math.floor(y);fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
        float a=g[y0][x0]+(g[y0][x0+1]-g[y0][x0])*fx,b=g[y0+1][x0]+(g[y0+1][x0+1]-g[y0+1][x0])*fx;return a+(b-a)*fy;
    }
}
