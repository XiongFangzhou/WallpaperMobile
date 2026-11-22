package dev.workshopvideo.core;
import static dev.workshopvideo.core.CoreChecks.*;

final class WallpaperChecks {
    static boolean close(float a,float b){return Math.abs(a-b)<.00001f;}
    static void run() throws Exception {
        var wide=WallpaperGeometry.layout(1920,1080,1440,3200,false,false,.5f);
        check(close(wide.scaleX(),1)&&close(wide.scaleY(),1),"crop fills display");
        check(wide.uSize()<1&&close(wide.vSize(),1),"landscape cropped horizontally on phone");
        check(close(wide.u0(),(1-wide.uSize())/2),"crop centered without launcher offset");
        var left=WallpaperGeometry.layout(1920,1080,1440,3200,false,true,0);
        var right=WallpaperGeometry.layout(1920,1080,1440,3200,false,true,1);
        check(close(left.u0(),0)&&close(right.u0()+right.uSize(),1),"launcher pans from left edge to right edge");
        var fit=WallpaperGeometry.layout(1920,1080,1440,3200,true,true,0);
        check(close(fit.scaleX(),1)&&fit.scaleY()<1&&close(fit.uSize(),1)&&close(fit.vSize(),1),"fit preserves full image with letterboxing");
        var tall=WallpaperGeometry.layout(1080,3840,1440,3200,false,true,1);
        check(close(tall.uSize(),1)&&tall.vSize()<1&&close(tall.v0(),(1-tall.vSize())/2),"tall wallpaper vertical center crop");
        var same=WallpaperGeometry.layout(900,2000,1440,3200,false,true,1);
        check(close(same.u0(),0)&&close(same.v0(),0)&&close(same.uSize(),1)&&close(same.vSize(),1),"matching aspect no crop or invented pan");
        check(close(WallpaperGeometry.layout(1920,1080,1440,3200,false,true,Float.NaN).u0(),wide.u0()),"invalid launcher offset centers crop");
        check(close(WallpaperGeometry.layout(1920,1080,1440,3200,false,true,-5).u0(),left.u0()),"negative offset clamped");
        check(close(WallpaperGeometry.layout(1920,1080,1440,3200,false,true,5).u0(),right.u0()),"large offset clamped");
        rejects(()->WallpaperGeometry.layout(0,1080,1440,3200,false,false,0),"zero source width");
        rejects(()->WallpaperGeometry.layout(1920,1080,1440,0,false,false,0),"zero destination height");
        var movingLeft=WallpaperGeometry.moving(1920,1080,1080,2400,false,true,0,1,1.5f,0,0);
        var movingCenter=WallpaperGeometry.moving(1920,1080,1080,2400,false,true,.5f,1,1.5f,0,0);
        var movingRight=WallpaperGeometry.moving(1920,1080,1080,2400,false,true,1,1,1.5f,0,0);
        check(close(movingLeft.u0(),0)&&close(movingRight.u0()+movingRight.uSize(),1),"zoomed video paging reaches both edges without counting scroll twice");
        check(close(movingCenter.u0(),(1-movingCenter.uSize())/2),"middle page retains centered video crop");
        WallpaperGeometry.Layout lockLeft=WallpaperGeometry.moving(1920,1080,1080,2400,false,false,0,1,1.5f,0,0),lockRight=WallpaperGeometry.moving(1920,1080,1080,2400,false,false,1,1,1.5f,0,0);
        check(close(lockLeft.u0(),lockRight.u0()),"locked screen crop ignores launcher page offsets");
        WallpaperGeometry.Layout tallLeft=WallpaperGeometry.moving(1080,2400,1080,2400,false,true,0,1,1.5f,0,0),tallRight=WallpaperGeometry.moving(1080,2400,1080,2400,false,true,1,1,1.5f,0,0);
        check(tallRight.u0()>tallLeft.u0(),"portrait video gains page movement only from actual zoom overscan");
        var halfPage=WallpaperGeometry.moving(1920,1080,1080,2400,false,true,0,.5f,1.5f,0,0);
        check(close(halfPage.u0(),0),"legacy half-strength setting cannot truncate first-page endpoint");
        for(int count:new int[]{2,3,5,8}){
            check(WallpaperPaging.pages(1f/(count-1))==count,"launcher reports dynamic page count "+count);
            float previous=-1,travel=1-movingLeft.uSize();
            for(int page=0;page<count;page++){
                float offset=WallpaperPaging.atPage(page,count);var crop=WallpaperGeometry.moving(1920,1080,1080,2400,false,true,offset,.5f,1.5f,0,0);
                check(close(crop.u0(),travel*page/(count-1)),"crop reaches proportional segment for "+count+" pages, page "+page);
                if(page>0)check(close(crop.u0()-previous,travel/(count-1)),"per-page distance uses total page count");previous=crop.u0();
            }
            check(close(previous+movingLeft.uSize(),1),"last page exposes complete source right edge");
        }
        check(WallpaperPaging.pages(0)==0&&WallpaperPaging.pages(Float.NaN)==0&&WallpaperPaging.atPage(0,1)==.5f,"launchers without page-step metadata remain safe");
        java.util.Random random=new java.util.Random(20261122);
        for(int i=0;i<1000;i++){
            int vw=1+random.nextInt(8192),vh=1+random.nextInt(8192),sw=1+random.nextInt(4096),sh=1+random.nextInt(4096);
            for(boolean complete:new boolean[]{false,true}){
                var layout=WallpaperGeometry.layout(vw,vh,sw,sh,complete,true,random.nextFloat());
                if(layout.u0()<0||layout.v0()<0||layout.u0()+layout.uSize()>1.00001||layout.v0()+layout.vSize()>1.00001)throw new AssertionError("crop exceeds source");
                double visibleAspect=(vw*(double)layout.uSize())/(vh*(double)layout.vSize()),drawnAspect=(sw*(double)layout.scaleX())/(sh*(double)layout.scaleY());
                if(Math.abs(visibleAspect/drawnAspect-1)>.00001)throw new AssertionError("wallpaper stretched");
            }
        }
        check(true,"2000 randomized portrait/landscape crop and fit layouts preserve aspect and stay within source");
    }
}
