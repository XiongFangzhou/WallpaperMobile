package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;
final class ParticleChecks {
    static void run(){
        Particles.Spec spec=new Particles.Spec();Particles.Emitter e=new Particles.Emitter();e.rate=10;e.distanceMax=new float[]{100,0,0};spec.emitters.add(e);
        spec.maxCount=50;spec.lifeMin=spec.lifeMax=2;spec.sizeMin=spec.sizeMax=10;spec.velocityMin=new float[]{0,50,0};spec.velocityMax=new float[]{0,50,0};spec.movement=true;
        Particles p=new Particles(spec,7);p.advance(1);
        check(p.count()==10,"emitters spawn at their rate: "+p.count());
        p.advance(1.55);check(p.count()==20,"particles die at the end of their life: "+p.count());
        spec.maxCount=5;Particles capped=new Particles(spec,7);capped.advance(3);check(capped.count()==5,"maxcount caps live particles");
        Particles still=new Particles(spec,7);still.emitting=false;still.advance(2);check(still.count()==0,"a stopped system emits nothing");

        float[] points=new float[8*20],uv=new float[8*20],colors=new float[16*20];
        Particles one=new Particles(single(),1);one.advance(.5);
        int quads=one.vertices(new float[]{500,100,1,1,0},1000,1000,1,points,uv,colors);
        check(quads==1,"one quad per visible particle");
        float cx=(points[0]+points[4])/2,cy=(points[1]+points[5])/2;
        check(Math.abs(cx-.5f)<1e-3&&Math.abs(cy-(1000-(100+25))/1000f)<1e-3,"particles move up in scene space and map to canvas y-down: "+cx+","+cy);
        check(Math.abs(points[4]-points[0]-.02f)<1e-4,"sprite size in canvas units");
        check(Math.abs(colors[3]-.5f)<1e-3&&Math.abs(colors[0]-.5f)<1e-3,"colours are premultiplied by the faded alpha");

        Particles.Spec glow=single();glow.additive=true;Particles g=new Particles(glow,1);g.advance(.5);g.vertices(new float[]{0,0,1,1,0},1000,1000,1,points,uv,colors);
        check(colors[3]==0&&colors[0]>0,"additive particles write zero alpha so ONE, ONE_MINUS_SRC_ALPHA adds them");

        Particles.Spec trail=single();trail.trail=true;trail.trailLength=.2f;trail.trailMaxLength=10;Particles t=new Particles(trail,1);t.advance(.5);t.vertices(new float[]{0,0,1,1,0},1000,1000,1,points,uv,colors);
        Particles.Spec ropeSpec=single();ropeSpec.emitters.get(0).instantaneous=0;ropeSpec.emitters.get(0).rate=30;ropeSpec.rope=true;ropeSpec.followCursor=true;ropeSpec.maxCount=64;ropeSpec.velocityMin=new float[]{0,0,0};ropeSpec.velocityMax=new float[]{0,0,0};ropeSpec.alphaFade=false;
        Particles rope=new Particles(ropeSpec,3);rope.pointer(0,0,false);rope.advance(.5);check(rope.count()==0,"pointer-following systems emit nothing until the finger is down");
        for(int f=0;f<15;f++){rope.pointer(f*10,0,true);rope.advance(1/30.0);}
        int ropeQuads=rope.vertices(new float[]{0,0,1,1,0},1000,1000,1,points,uv,colors);
        check(rope.count()>=14&&ropeQuads>=rope.count()-2,"a rope joins consecutive particles into one ribbon: "+ropeQuads+" of "+rope.count());
        check(Math.abs(points[1]-points[3])<1e-4&&points[2]>points[0],"the ribbon follows the finger's path");
        t.vertices(new float[]{0,0,1,1,0},1000,1000,1,points,uv,colors);
        float along=(float)Math.hypot(points[2]-points[0],points[3]-points[1])*1000,across=(float)Math.hypot(points[4]-points[2],points[5]-points[3])*1000;
        check(along>across,"sprite trails stretch along the velocity: "+along+" vs "+across);
    }
    /** One particle rising at 50 px/s, 20 px wide, half faded at mid-life. */
    private static Particles.Spec single(){
        Particles.Spec s=new Particles.Spec();Particles.Emitter e=new Particles.Emitter();e.instantaneous=1;s.emitters.add(e);
        s.lifeMin=s.lifeMax=1;s.sizeMin=s.sizeMax=20;s.velocityMin=new float[]{0,50,0};s.velocityMax=new float[]{0,50,0};s.movement=true;
        s.alphaFade=true;s.fadeIn=0;s.fadeOut=1;return s;
    }
}
