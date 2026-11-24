package dev.workshopvideo.core;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Wallpaper Engine particle systems: box/sphere emitters, the common initializers and operators, and sprite or
 * sprite-trail rendering. Simulation runs in the system's local space (pixels, y up); {@link #vertices} maps it
 * into the canvas for drawing.
 */
public final class Particles {
    /** One emitter. Box: each axis within ±distanceMax, outside the ±distanceMin box. Sphere: distance between min and max. */
    public static final class Emitter {
        public boolean sphere;public float rate=10,speedMin,speedMax;public int instantaneous;
        public float[] origin={0,0,0},distanceMin={0,0,0},distanceMax={0,0,0},directions={1,1,0};
    }
    /** Parameters after the scene's instance overrides were applied. */
    public static final class Spec {
        public int maxCount=32;public float startTime;public final List<Emitter> emitters=new ArrayList<>();
        public float lifeMin=1,lifeMax=1,sizeMin=0,sizeMax=20,alphaMin=1,alphaMax=1;public float[] colorMin={1,1,1},colorMax={1,1,1};
        public float[] velocityMin={0,0,0},velocityMax={0,0,0};
        public boolean turbulentVelocity;public float turbulentSpeedMin=100,turbulentSpeedMax=250,turbulentScale=1;public float[] turbulentForward={0,1,0};
        public float rotationMin,rotationMax,angularMin,angularMax;
        public boolean movement;public float[] gravity={0,0,0};public float drag;
        public boolean alphaFade;public float fadeIn=.5f,fadeOut=.5f;
        public boolean sizeChange;public float sizeChangeStart=0,sizeChangeEnd=1,sizeStartValue=1,sizeEndValue=0;
        public boolean oscillateAlpha;public float alphaFrequencyMin=0,alphaFrequencyMax=10,alphaScaleMin=0,alphaScaleMax=1;
        public boolean oscillateSize;public float sizeFrequencyMin=0,sizeFrequencyMax=10,sizeScaleMin=.8f,sizeScaleMax=1.2f;
        public boolean oscillatePosition;public float positionFrequencyMin=0,positionFrequencyMax=10,positionScaleMin=0,positionScaleMax=10;public float[] positionMask={1,1,0};
        public boolean turbulence;public float turbulenceScale=.005f,turbulenceSpeedMin=500,turbulenceSpeedMax=1000,turbulenceTimescale=1;public float[] turbulenceMask={1,1,0};
        public boolean attract;public float[] attractPoint={0,0,0};public float attractScale=1,attractThreshold=100;
        public boolean angularMovement;public float angularForce,angularDrag;
        public boolean trail;public float trailLength=.05f,trailMaxLength=10;
        public boolean additive;
        /** Control point 0 follows the pointer: particles are emitted at the finger, only while it is down. */
        public boolean followCursor;
        /** Rope renderer: consecutive particles are joined into one ribbon instead of separate sprites. */
        public boolean rope;
        /** Sprite-sheet frames of the texture (u0,v0,u1,v1 each); particles step through them over their life when sequence is set. */
        public float[][] frames={{0,0,1,1}};public boolean sequence;public float sequenceMultiplier=1;
    }

    public final Spec spec;
    private final SplittableRandom random;
    private final int capacity;private int count;private double emitted;private float time;
    private final float[] x,y,vx,vy,age,life,size,red,green,blue,alpha,rotation,spin,phase,frequency,scale,frequency2,scale2,turbulence,born;
    /** Pointer in local space and whether it is down, for systems that follow it. */
    private float pointerX,pointerY;private boolean pointerDown;
    public void pointer(float x,float y,boolean down){pointerX=x;pointerY=y;pointerDown=down;}
    /** Emitter position in the system's parent space and whether new particles may be emitted (scripts can stop a system). */
    public boolean emitting=true;

    public Particles(Spec spec,long seed){
        this.spec=spec;random=new SplittableRandom(seed);capacity=Math.max(1,Math.min(2000,spec.maxCount));
        x=new float[capacity];y=new float[capacity];vx=new float[capacity];vy=new float[capacity];age=new float[capacity];life=new float[capacity];size=new float[capacity];
        red=new float[capacity];green=new float[capacity];blue=new float[capacity];alpha=new float[capacity];rotation=new float[capacity];spin=new float[capacity];
        phase=new float[capacity];frequency=new float[capacity];scale=new float[capacity];frequency2=new float[capacity];scale2=new float[capacity];turbulence=new float[capacity];born=new float[capacity];
        for(Emitter e:spec.emitters)for(int i=0;i<e.instantaneous;i++)spawn(e);
        if(spec.startTime>0)advance(Math.min(30,spec.startTime));
    }
    public int count(){return count;}

    private float between(float a,float b){return a==b?a:a+(b-a)*(float)random.nextDouble();}
    private void spawn(Emitter e){
        if(count>=capacity)return;int i=count++;float px,py,dx,dy;
        if(e.sphere){
            double angle=random.nextDouble()*Math.PI*2;float distance=between(e.distanceMin[0],e.distanceMax[0]);
            dx=(float)Math.cos(angle)*e.directions[0];dy=(float)Math.sin(angle)*e.directions[1];float length=(float)Math.hypot(dx,dy);if(length>0){dx/=length;dy/=length;}
            px=dx*distance;py=dy*distance;
        }else{
            px=0;py=0;
            for(int tries=0;tries<8;tries++){
                px=between(-e.distanceMax[0],e.distanceMax[0]);py=between(-e.distanceMax[1],e.distanceMax[1]);
                if(Math.abs(px)>=e.distanceMin[0]||Math.abs(py)>=e.distanceMin[1])break;
            }
            float length=(float)Math.hypot(px,py);dx=length>0?px/length*e.directions[0]:0;dy=length>0?py/length*e.directions[1]:0;
        }
        if(spec.followCursor){px+=pointerX;py+=pointerY;}born[i]=time;
        x[i]=e.origin[0]+px;y[i]=e.origin[1]+py;float speed=between(e.speedMin,e.speedMax);vx[i]=dx*speed;vy[i]=dy*speed;
        Spec s=spec;
        vx[i]+=between(s.velocityMin[0],s.velocityMax[0]);vy[i]+=between(s.velocityMin[1],s.velocityMax[1]);
        if(s.turbulentVelocity){
            double base=Math.atan2(s.turbulentForward[1],s.turbulentForward[0]),spread=Math.PI*.5*Math.max(0,Math.min(2,s.turbulentScale)),angle=base+(random.nextDouble()*2-1)*spread;
            float speed2=between(s.turbulentSpeedMin,s.turbulentSpeedMax);vx[i]+=(float)Math.cos(angle)*speed2;vy[i]+=(float)Math.sin(angle)*speed2;
        }
        age[i]=0;life[i]=Math.max(.01f,between(s.lifeMin,s.lifeMax));size[i]=between(s.sizeMin,s.sizeMax);
        float t=(float)random.nextDouble();red[i]=s.colorMin[0]+(s.colorMax[0]-s.colorMin[0])*t;green[i]=s.colorMin[1]+(s.colorMax[1]-s.colorMin[1])*t;blue[i]=s.colorMin[2]+(s.colorMax[2]-s.colorMin[2])*t;
        alpha[i]=between(s.alphaMin,s.alphaMax);rotation[i]=between(s.rotationMin,s.rotationMax);spin[i]=between(s.angularMin,s.angularMax);
        phase[i]=(float)(random.nextDouble()*Math.PI*2);turbulence[i]=between(s.turbulenceSpeedMin,s.turbulenceSpeedMax);
        frequency[i]=s.oscillateAlpha?between(s.alphaFrequencyMin,s.alphaFrequencyMax):between(s.positionFrequencyMin,s.positionFrequencyMax);
        scale[i]=s.oscillateAlpha?between(s.alphaScaleMin,s.alphaScaleMax):between(s.positionScaleMin,s.positionScaleMax);
        frequency2[i]=between(s.sizeFrequencyMin,s.sizeFrequencyMax);scale2[i]=between(s.sizeScaleMin,s.sizeScaleMax);
    }
    private void remove(int i){
        int last=--count;if(i==last)return;
        x[i]=x[last];y[i]=y[last];vx[i]=vx[last];vy[i]=vy[last];age[i]=age[last];life[i]=life[last];size[i]=size[last];red[i]=red[last];green[i]=green[last];blue[i]=blue[last];
        alpha[i]=alpha[last];rotation[i]=rotation[last];spin[i]=spin[last];phase[i]=phase[last];frequency[i]=frequency[last];scale[i]=scale[last];frequency2[i]=frequency2[last];scale2[i]=scale2[last];turbulence[i]=turbulence[last];born[i]=born[last];
    }

    /** Advances the simulation; large steps are split so fast particles stay smooth. */
    public void advance(double seconds){
        double left=Math.max(0,Math.min(seconds,30));
        while(left>1e-6){float dt=(float)Math.min(left,1/30.0);step(dt);left-=dt;}
    }
    private void step(float dt){
        time+=dt;Spec s=spec;
        for(int i=0;i<count;){
            age[i]+=dt;if(age[i]>=life[i]){remove(i);continue;}
            if(s.turbulence){
                double k=s.turbulenceScale,t=time*s.turbulenceTimescale;
                float nx=(float)(Math.sin(y[i]*k*1.7+t*1.3+phase[i])+Math.sin(x[i]*k*.9-t*.7)),ny=(float)(Math.cos(x[i]*k*1.3-t*1.1+phase[i])+Math.sin(y[i]*k*.8+t*.9));
                vx[i]+=nx*.5f*turbulence[i]*s.turbulenceMask[0]*dt;vy[i]+=ny*.5f*turbulence[i]*s.turbulenceMask[1]*dt;
            }
            if(s.attract){float dx=s.attractPoint[0]-x[i],dy=s.attractPoint[1]-y[i],d=(float)Math.hypot(dx,dy);if(d>1e-3&&d<s.attractThreshold){vx[i]+=dx/d*s.attractScale*dt*100;vy[i]+=dy/d*s.attractScale*dt*100;}}
            if(s.movement){vx[i]+=s.gravity[0]*dt;vy[i]+=s.gravity[1]*dt;float damp=Math.max(0,1-s.drag*dt);vx[i]*=damp;vy[i]*=damp;}
            x[i]+=vx[i]*dt;y[i]+=vy[i]*dt;
            if(s.angularMovement){spin[i]+=s.angularForce*dt;spin[i]*=Math.max(0,1-s.angularDrag*dt);}
            rotation[i]+=spin[i]*dt;i++;
        }
        if(emitting&&(!s.followCursor||pointerDown))for(Emitter e:s.emitters){
            if(e.instantaneous>0||e.rate<=0)continue;
            emitted+=e.rate*dt;while(emitted>=1-1e-6){emitted-=1;if(count>=capacity){emitted=0;break;}spawn(e);}
        }
    }

    /** Current alpha of particle i after fades and oscillation. */
    private float alphaOf(int i){
        Spec s=spec;float f=age[i]/life[i],a=alpha[i];
        if(s.alphaFade){if(s.fadeIn>0&&f<s.fadeIn)a*=f/s.fadeIn;float out=1-s.fadeOut;if(s.fadeOut>0&&f>out)a*=Math.max(0,(1-f)/s.fadeOut);}
        if(s.oscillateAlpha)a*=1-scale[i]*(.5f+.5f*(float)Math.sin(age[i]*frequency[i]+phase[i]));
        return Math.max(0,Math.min(1,a));
    }
    private float sizeOf(int i){
        Spec s=spec;float f=age[i]/life[i],v=size[i];
        if(s.sizeChange){float span=Math.max(1e-4f,s.sizeChangeEnd-s.sizeChangeStart),t=Math.max(0,Math.min(1,(f-s.sizeChangeStart)/span));v*=s.sizeStartValue+(s.sizeEndValue-s.sizeStartValue)*t;}
        if(s.oscillateSize)v*=scale2[i]+(1-scale2[i])*(.5f+.5f*(float)Math.sin(age[i]*frequency2[i]+phase[i]));
        return Math.max(0,v);
    }

    /**
     * Writes one quad per particle: points (canvas 0..1, y down), texture coordinates and premultiplied colours, and returns the quad count.
     * transform maps local space to the scene (pixels, y up): {originX, originY, scaleX, scaleY, radians}.
     */
    public int vertices(float[] transform,int canvasWidth,int canvasHeight,float opacity,float[] points,float[] texcoords,float[] colors){
        if(spec.rope)return rope(transform,canvasWidth,canvasHeight,opacity,points,texcoords,colors);
        Spec s=spec;double c=Math.cos(transform[4]),sn=Math.sin(transform[4]);int quads=0;
        for(int i=0;i<count&&quads*8+8<=points.length;i++){
            float a=alphaOf(i)*opacity;if(a<=.002f)continue;float half=sizeOf(i)*.5f;if(half<=0)continue;
            float px=x[i],py=y[i];
            if(s.oscillatePosition){float wave=(float)Math.sin(age[i]*frequency[i]+phase[i])*scale[i];px+=wave*s.positionMask[0];py+=wave*s.positionMask[1];}
            // Axes of the sprite in local space: trails stretch along the velocity, sprites turn with their rotation.
            float ax,ay,bx,by;
            float speed=(float)Math.hypot(vx[i],vy[i]);
            if(s.trail&&speed>1e-3){float length=Math.max(half,Math.min(speed*s.trailLength,s.trailMaxLength*half*2)*.5f);float ux=vx[i]/speed,uy=vy[i]/speed;ax=ux*length;ay=uy*length;bx=-uy*half;by=ux*half;px-=ux*(length-half);py-=uy*(length-half);}
            else{float rc=(float)Math.cos(rotation[i]),rs=(float)Math.sin(rotation[i]);ax=rc*half;ay=rs*half;bx=-rs*half;by=rc*half;}
            float[][] corners={{px-ax-bx,py-ay-by},{px+ax-bx,py+ay-by},{px+ax+bx,py+ay+by},{px-ax+bx,py-ay+by}};
            float[] frame=s.frames[0];
            if(s.frames.length>1){int n=s.frames.length;int k=s.sequence?Math.min(n-1,(int)(age[i]/life[i]*n*s.sequenceMultiplier)%n):(int)(phase[i]/(Math.PI*2)*n)%n;frame=s.frames[k];}
            float[][] uv={{frame[0],frame[3]},{frame[2],frame[3]},{frame[2],frame[1]},{frame[0],frame[1]}};
            for(int k=0;k<4;k++){
                float lx=corners[k][0]*transform[2],ly=corners[k][1]*transform[3];
                double wx=transform[0]+lx*c-ly*sn,wy=transform[1]+lx*sn+ly*c;
                int p=quads*8+k*2;points[p]=(float)(wx/canvasWidth);points[p+1]=(float)((canvasHeight-wy)/canvasHeight);texcoords[p]=uv[k][0];texcoords[p+1]=uv[k][1];
                int q=quads*16+k*4;colors[q]=red[i]*a;colors[q+1]=green[i]*a;colors[q+2]=blue[i]*a;colors[q+3]=s.additive?0:a;
            }
            quads++;
        }
        return quads;
    }
    /** Ribbon through the particles in emission order; a pause in emission (finger lifted) starts a new ribbon. */
    private int rope(float[] transform,int canvasWidth,int canvasHeight,float opacity,float[] points,float[] texcoords,float[] colors){
        Integer[] order=new Integer[count];for(int i=0;i<count;i++)order[i]=i;java.util.Arrays.sort(order,(p,q)->Float.compare(born[p],born[q]));
        double c=Math.cos(transform[4]),sn=Math.sin(transform[4]);float gap=0;for(Emitter e:spec.emitters)gap=Math.max(gap,e.rate>0?3f/e.rate:0);gap=Math.max(gap,.1f);
        int quads=0;
        for(int k=0;k+1<count&&quads*8+8<=points.length;k++){
            int a=order[k],b=order[k+1];if(born[b]-born[a]>gap)continue;
            float dx=x[b]-x[a],dy=y[b]-y[a],length=(float)Math.hypot(dx,dy);if(length<1e-3)continue;
            float nx=-dy/length,ny=dx/length,ha=sizeOf(a)*.5f,hb=sizeOf(b)*.5f,aa=alphaOf(a)*opacity,ab=alphaOf(b)*opacity;if(aa<=.002f&&ab<=.002f)continue;
            float ua=1-age[a]/life[a],ub=1-age[b]/life[b];
            float[][] corners={{x[a]-nx*ha,y[a]-ny*ha},{x[b]-nx*hb,y[b]-ny*hb},{x[b]+nx*hb,y[b]+ny*hb},{x[a]+nx*ha,y[a]+ny*ha}};
            float[][] uv={{ua,1},{ub,1},{ub,0},{ua,0}};int[] owner={a,b,b,a};float[] alphas={aa,ab,ab,aa};
            for(int v=0;v<4;v++){
                float lx=corners[v][0]*transform[2],ly=corners[v][1]*transform[3];double wx=transform[0]+lx*c-ly*sn,wy=transform[1]+lx*sn+ly*c;
                int p=quads*8+v*2;points[p]=(float)(wx/canvasWidth);points[p+1]=(float)((canvasHeight-wy)/canvasHeight);texcoords[p]=uv[v][0];texcoords[p+1]=uv[v][1];
                int q=quads*16+v*4,i=owner[v];float al=alphas[v];colors[q]=red[i]*al;colors[q+1]=green[i]*al;colors[q+2]=blue[i]*al;colors[q+3]=spec.additive?0:al;
            }
            quads++;
        }
        return quads;
    }
    public int capacity(){return capacity;}
}
