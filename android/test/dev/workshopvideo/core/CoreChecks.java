package dev.workshopvideo.core;

import in.dragonbra.javasteam.types.*;
import java.util.*;
import java.io.IOException;

/** Executable invariants for search semantics and untrusted download manifests. */
public final class CoreChecks {
    static int checks=0;
    static void check(boolean value,String name) { checks++; if(!value)throw new AssertionError(name); }
    interface Checked { void run() throws Exception; }
    static void rejects(Checked f,String name) throws Exception {
        checks++;try{f.run();throw new AssertionError("accepted "+name);}catch(IllegalArgumentException|IOException expected){}
    }
    public static void main(String[] args) throws Exception {
        WorkshopQuery q=new WorkshopQuery();
        q.text="orbital home";q.exact=true;q.excluded.add("Portrait");
        var request=q.request();
        check(request.getAppid()==431960 && request.getCreatorAppid()==431960,"app scope");
        check(request.getSearchText().equals("\"orbital home\""),"WE exact match");
        check(request.getDays()==365&&request.getQueryType()==3,"WE trend_year default");
        check(request.getRequiredtagsCount()==0,"no forced wallpaper or video tags");
        check(request.getExcludedtagsList().equals(List.of("Scene","Web","Application","Portrait")),"WE unchecked type exclusions");
        q.required.add("Nature");check(q.request().getMatchAllTags()&&q.request().getRequiredtagsList().contains("Nature"),"required intersection");
        WorkshopQuery allTypes=new WorkshopQuery();allTypes.setType("");check(allTypes.request().getExcludedtagsCount()==0,"all types remove exclusions");
        allTypes.setType("Scene");check(allTypes.type().equals("Scene")&&!allTypes.request().getExcludedtagsList().contains("Scene"),"scene type selection");
        q.sort=11;check(q.request().getQueryType()==12,"PublishedFile relevance enum mapping");
        q.sort=10;check(q.request().getQueryType()==11,"PublishedFile votes enum mapping");
        q.sort=12;check(q.request().getQueryType()==9,"PublishedFile subscriptions enum mapping");
        q.sort=19;rejects(q::request,"unsupported recently-updated sort");q.sort=3;
        q.from="2026-11-01";q.to="2026-11-21";
        request=q.request();
        check(Integer.toUnsignedLong(request.getDateRangeCreated().getTimestampEnd())-
              Integer.toUnsignedLong(request.getDateRangeCreated().getTimestampStart())==21*86400-1,"inclusive date range");
        q.to="2026-10-31";rejects(q::request,"backwards date range");q.to="2026-11-21";
        q.excluded.add("Nature");rejects(q::request,"contradictory tags");q.excluded.remove("Nature");
        q.titleOnly=true;check(q.acceptsTitle("Orbital Home 4K") && !q.acceptsTitle("Other title"),"title-only filtering");
        WorkshopQuery clone=q.copy();clone.required.add("Game");check(!q.required.contains("Game"),"independent filter snapshot");
        rejects(()->WorkshopDownload.normalize("../video.mp4"),"parent path");
        rejects(()->WorkshopDownload.normalize("C:\\video.mp4"),"absolute windows path");
        rejects(()->WorkshopDownload.normalize("/video.mp4"),"absolute unix path");
        check(WorkshopDownload.normalize("sub\\video.mp4").equals("sub/video.mp4"),"cross-platform manifest path");
        FileData file=new FileData();file.setFileName("video.mp4");file.setTotalSize(8);
        file.getChunks().add(new ChunkData(new byte[20],0,0,32,4));
        file.getChunks().add(new ChunkData(new byte[20],0,4,32,4));
        WorkshopDownload.validate(file,100);check(true,"contiguous manifest");
        rejects(()->WorkshopDownload.validate(file,7),"oversize file");
        file.getChunks().get(1).setOffset(3);rejects(()->WorkshopDownload.validate(file,100),"overlap");
        file.getChunks().get(1).setOffset(5);rejects(()->WorkshopDownload.validate(file,100),"gap");
        file.getChunks().get(1).setOffset(4);file.setLinkTarget("/private/data");
        rejects(()->WorkshopDownload.validate(file,100),"manifest link");
        CdnChecks.run();
        VideoFilesChecks.run();
        PlayerControlsChecks.run();
        FilterSettingsChecks.run();
        PreviewChecks.run();
        WallpaperChecks.run();
        LibraryChecks.run();
        WeFilterChecks.run();
        WallpaperApplyChecks.run();
        WallpaperScopeChecks.run();
        ContentChecks.run();
        GestureDragChecks.run();
        LauncherPagingChecks.run();
        WorkshopAssetChecks.run();
        WorkshopRangeChecks.run();
        DownloadTaskChecks.run();
        FrameSelectionChecks.run();
        PaginationChecks.run();
        QueryCacheChecks.run();
        RatingChecks.run();
        CommentChecks.run();
        PuppetChecks.run();
        ParticleChecks.run();
        PointerChecks.run();
        FallbackChecks.run();
        BudgetChecks.run();
        PropertyChecks.run();
        DesktopBehaviorChecks.run();
        MotionPagingChecks.run();
        AdaptivePagingChecks.run();
        FramePacingChecks.run();
        check(RenderFailure.transientSurface("无法连接壁纸显示区域，EGL=0x3003"),"window allocation failure is transient, not media incompatibility");
        check(RenderFailure.transientSurface("EGL=0x300d"),"surface loss is retryable");
        check(!RenderFailure.transientSurface("视频编码不受支持"),"decoder incompatibility is not masked by surface recovery");
        System.out.println("PASS: "+checks+" core invariants");
        if(args.length>0 && args[0].equals("--network")) {
            if(args.length>1 && args[1].equals("--debug"))
                in.dragonbra.javasteam.util.log.LogManager.addListener(new in.dragonbra.javasteam.util.log.DefaultLogListener());
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            java.util.concurrent.CountDownLatch disconnected=new java.util.concurrent.CountDownLatch(1);
            try(SteamSession session=new SteamSession(message->{System.out.println(message);if(message.contains("暂时断开"))disconnected.countDown();})) {
                session.anonymous();
                WorkshopQuery search=new WorkshopQuery();search.text="orbital home";search.sort=11;search.exact=true;
                var response=session.query(search);
                System.out.println("LIVE_QUERY_TOTAL="+response.getTotal()+" RETURNED="+response.getPublishedfiledetailsCount());
                if(response.getPublishedfiledetailsCount()==0)throw new AssertionError("empty live query");
                check(response.getPublishedfiledetailsList().stream().anyMatch(i->i.getPublishedfileid()==3254571754L),"known workshop result");
                for(var item:response.getPublishedfiledetailsList())
                    System.out.println(Long.toUnsignedString(item.getPublishedfileid())+" "+item.getTitle());
                var detail=session.details(3254571754L);
                System.out.println("LIVE_MANIFEST_UNSIGNED="+Long.toUnsignedString(detail.getHcontentFile()));
                var pics=session.client.getHandler(in.dragonbra.javasteam.steam.handlers.steamapps.SteamApps.class)
                    .picsGetProductInfo(new in.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest(431960),null,false)
                    .toFuture().get(35,java.util.concurrent.TimeUnit.SECONDS);
                for(var part:pics.getResults()) {
                    var info=part.getApps().get(431960);
                    if(info!=null) {
                        String workshopDepot=info.getKeyValues().get("depots").get("workshopdepot").asString();
                        System.out.println("WORKSHOP_DEPOT="+workshopDepot);
                        check("431960".equals(workshopDepot),"workshop depot metadata");
                    }
                }
                if(java.util.Arrays.asList(args).contains("--idle")) {
                    Thread.sleep(35000);
                    check(session.isAuthenticated(),"WebSocket survives idle beyond HTTP read timeout");
                    check(session.query(search).getPublishedfiledetailsCount()>0,"query after idle");
                    System.out.println("PASS: 35-second idle connection reuse");
                }
                session.client.disconnect();
                check(disconnected.await(5,java.util.concurrent.TimeUnit.SECONDS),"disconnect callback");
                check(!session.isAuthenticated(),"disconnected session state");
                java.util.concurrent.CompletableFuture<?>[] restorations=new java.util.concurrent.CompletableFuture<?>[2];
                for(int i=0;i<restorations.length;i++)restorations[i]=java.util.concurrent.CompletableFuture.runAsync(()->{
                    try{session.ensureAuthenticated();}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}
                });
                java.util.concurrent.CompletableFuture.allOf(restorations).get(150,java.util.concurrent.TimeUnit.SECONDS);
                var restored=session.query(search);
                check(session.isAuthenticated()&&restored.getPublishedfiledetailsCount()>0,"concurrent automatic session recovery and query");
                System.out.println("PASS: live public query, forced disconnect and concurrent automatic recovery");
            }
        }
    }
}
