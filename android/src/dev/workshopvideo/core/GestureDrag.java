package dev.workshopvideo.core;
/** Relative gesture displacement: a stationary press is never an instruction to move the image. */
public final class GestureDrag {
 private boolean down,moved;private float startX,startY,baseX,baseY,targetX,targetY,x,y;
 public void touch(float px,float py,int action){
  px=EffectMotion.clamp(px,0,1);py=EffectMotion.clamp(py,0,1);
  if(action==0){down=true;moved=false;startX=px;startY=py;baseX=x;baseY=y;targetX=x;targetY=y;}
  else if(action==2&&down){float dx=px-startX,dy=py-startY;if(!moved&&dx*dx+dy*dy<.000036f)return;moved=true;targetX=EffectMotion.clamp(baseX+dx*2,-1,1);targetY=EffectMotion.clamp(baseY+dy*2,-1,1);}
  else if(action==1||action==3){down=false;moved=false;targetX=targetY=0;}
 }
 public void step(float dt){x=EffectMotion.approach(x,targetX,dt,down?.09f:.2f);y=EffectMotion.approach(y,targetY,dt,down?.09f:.2f);if(!down&&Math.abs(x)<.0001f)x=0;if(!down&&Math.abs(y)<.0001f)y=0;}
 public float x(){return x;}public float y(){return y;}
 public void reset(){down=moved=false;baseX=baseY=targetX=targetY=x=y=0;}
}
