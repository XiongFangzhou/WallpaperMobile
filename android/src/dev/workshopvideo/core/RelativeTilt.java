package dev.workshopvideo.core;

/** Screen-local plane normals avoid Euler pitch/roll singularities at upright poses. */
public final class RelativeTilt {
    private final float[] center=new float[9],screen=new float[9],gravity=new float[3];
    private boolean centered,gravityReady;
    private int rotation=-1;
    public void reset(){centered=gravityReady=false;rotation=-1;}
    public float[] matrix(float[] device,int displayRotation){
        if(device.length<9)return null;
        for(int i=0;i<9;i++)if(!Float.isFinite(device[i]))return null;
        int turn=displayRotation&3;
        int x=turn==1||turn==3?1:0,y=1-x;
        int xs=turn==2||turn==3?-1:1,ys=turn==1||turn==2?-1:1;
        for(int row=0;row<3;row++){screen[row*3]=device[row*3+x]*xs;screen[row*3+1]=device[row*3+y]*ys;screen[row*3+2]=device[row*3+2];}
        if(!centered||turn!=rotation){System.arraycopy(screen,0,center,0,9);centered=true;rotation=turn;return new float[]{0,0};}
        // Relative rotation = transpose(reference screen orientation) * current screen orientation.
        float horizontal=0,vertical=0,normal=0;
        for(int row=0;row<3;row++){float z=screen[row*3+2];horizontal+=center[row*3]*z;vertical+=center[row*3+1]*z;normal+=center[row*3+2]*z;}
        return new float[]{bounded((float)Math.atan2(horizontal,normal)),bounded(-(float)Math.atan2(vertical,normal))};
    }
    public float[] acceleration(float[] values,int displayRotation,float seconds){
        if(values.length<3)return null;
        for(int i=0;i<3;i++)if(!Float.isFinite(values[i]))return null;
        int turn=displayRotation&3;
        if(rotation!=turn){reset();rotation=turn;}
        float gain=gravityReady?1-(float)Math.exp(-Math.max(.001f,Math.min(.1f,seconds))/.25f):1;
        for(int i=0;i<3;i++)gravity[i]+=(values[i]-gravity[i])*gain;
        gravityReady=true;
        float length=(float)Math.sqrt(gravity[0]*gravity[0]+gravity[1]*gravity[1]+gravity[2]*gravity[2]);
        if(length<1)return null;
        float x=gravity[0]/length,y=gravity[1]/length,z=gravity[2]/length;
        float sx=turn==1?y:turn==2?-x:turn==3?-y:x;
        float sy=turn==1?-x:turn==2?-y:turn==3?x:y;
        if(!centered){center[0]=sx;center[1]=sy;center[2]=z;centered=true;return new float[]{0,0};}
        return new float[]{bounded(-(sx-center[0])),bounded(sy-center[1])};
    }
    private static float bounded(float angle){float magnitude=Math.max(0,Math.abs(angle)-.008f);return Math.copySign(Math.min(1,magnitude*1.8f),angle);}
}
