package dev.workshopvideo.core;

import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.networking.steam3.ProtocolTypes;
import in.dragonbra.javasteam.rpc.service.PublishedFile;
import in.dragonbra.javasteam.steam.authentication.*;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback;
import in.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration;
import in.dragonbra.javasteam.types.AsyncJobSingle;
import in.dragonbra.javasteam.steam.discovery.ServerRecord;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.*;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** A private Steam network session, with no dependency on the desktop client. */
public final class SteamSession implements AutoCloseable {
    public interface Events { void status(String message); }
    public interface TokenSink { void save(String username, String refreshToken) throws Exception; }
    private final List<Closeable> subscriptions = new ArrayList<>();
    private volatile CompletableFuture<Void> connected = new CompletableFuture<>();
    private volatile CompletableFuture<Void> loggedOn = new CompletableFuture<>();
    private final Events events;
    private final Thread pump;
    private volatile boolean running=true, authenticated=false, establishing=false;
    public final SteamClient client;
    public final SteamUnifiedMessages unified;
    public final PublishedFile published;
    private final SteamUser user;
    private String savedUsername, savedToken;
    private boolean anonymousSession=false;
    private volatile int cellId=0;
    private String lastHost;
    private final int loginId=0x57450000 | new java.security.SecureRandom().nextInt(65535);
    public static final class LoginRejected extends IllegalStateException {
        public final EResult result;
        LoginRejected(EResult result) { super("Steam 登录被拒绝："+result); this.result=result; }
        public boolean invalidToken() {
            return result==EResult.InvalidPassword || result==EResult.Expired || result==EResult.Revoked;
        }
    }

    public SteamSession(Events events) {
        this.events=events;
        OkHttpClient http = new OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS)
            .pingInterval(25,TimeUnit.SECONDS)
            .protocols(java.util.Collections.singletonList(Protocol.HTTP_1_1)).build();
        SteamConfiguration configuration = SteamConfiguration.create(b -> {
            b.withProtocolTypes(ProtocolTypes.WEB_SOCKET);
            b.withHttpClient(http);
            b.withConnectionFactory((config, protocols) -> new SteamWebSocket(http));
            b.withConnectionTimeout(25000);
        });
        client = new SteamClient(configuration);
        user = client.getHandler(SteamUser.class);
        unified = client.getHandler(SteamUnifiedMessages.class);
        published = unified.createService(PublishedFile.class);
        CallbackManager callbacks = new CallbackManager(client);
        subscriptions.add(callbacks.subscribe(ConnectedCallback.class, c -> connected.complete(null)));
        subscriptions.add(callbacks.subscribe(DisconnectedCallback.class, c -> {
            authenticated=false;
            if(c.isUserInitiated() && establishing)return;
            connected.completeExceptionally(new IllegalStateException("Steam 连接中断，请检查网络后重试。"));
            if(establishing)return;
            loggedOn.completeExceptionally(new IllegalStateException("Steam 连接中断，将在下次操作时恢复。"));
            if (running) events.status("Steam 暂时断开，返回应用或继续操作时自动恢复");
        }));
        subscriptions.add(callbacks.subscribe(LoggedOnCallback.class, c -> {
            if(c.getResult()==EResult.OK) { cellId=c.getCellID(); authenticated=true; loggedOn.complete(null); }
            else loggedOn.completeExceptionally(new LoginRejected(c.getResult()));
        }));
        subscriptions.add(callbacks.subscribe(LoggedOffCallback.class, c -> {
            authenticated=false;loggedOn.completeExceptionally(new LoginRejected(c.getResult()));
            events.status("Steam 会话已退出："+c.getResult());
        }));
        pump = new Thread(() -> {
            while(running) {
                try { callbacks.runWaitCallbacks(250L); }
                catch(Throwable e) { if(!running) return; try { events.status("Steam 回调处理异常，已继续监听"); Thread.sleep(250L); } catch(Throwable ignored) { if(Thread.currentThread().isInterrupted()) return; } }
            }
        }, "steam-callbacks");
        pump.setDaemon(true); pump.start();
    }

    private void connect() throws Exception {
        establishing=true;
        authenticated=false;
        loggedOn=new CompletableFuture<>();
        Exception last=null;
        try {
            String[] bootstrap={"cmp1-hkg1.steamserver.net","cmp1-sgp1.steamserver.net","cmp1-sea1.steamserver.net"};
            // CM discovery can hang while mobile networks block the directory.
            // Start with the last successful node, then try official TLS endpoints.
            java.util.LinkedHashSet<String> candidates=new java.util.LinkedHashSet<>();
            if(lastHost!=null)candidates.add(lastHost);
            java.util.Collections.addAll(candidates,bootstrap);
            List<String> hosts=new ArrayList<>(candidates);
            for(int attempt=0;attempt<hosts.size();attempt++) {
                events.status("正在连接 Steam… ("+(attempt+1)+"/"+hosts.size()+")");
                connected=new CompletableFuture<>();
                try {
                    // Official Steam nodes, independently TLS-verified on 2026-11-21.
                    // A directory outage must not make an otherwise reachable CM unusable.
                    String host=hosts.get(attempt);
                    // 443 works on mobile networks that block Steam's 27018/27019 ports.
                    client.connect(ServerRecord.createWebSocketServer(host+":443"));
                    connected.get(20,TimeUnit.SECONDS);lastHost=host;return;
                } catch(Exception e) {last=e;client.disconnect();}
            }
            throw new IllegalStateException("无法连接 Steam，请检查手机网络是否可访问 Steam 后重试。",last);
        } finally {establishing=false;}
    }

    public synchronized void anonymous() throws Exception {
        anonymousSession=true;
        connect(); user.logOnAnonymous(); loggedOn.get(35,TimeUnit.SECONDS);
        events.status("已连接公开浏览会话");
    }

    public synchronized void login(String username, String password, boolean remember,
                      IAuthenticator guard, TokenSink sink) throws Exception {
        connect(); events.status("正在向 Steam 验证账号…");
        AuthSessionDetails details = new AuthSessionDetails();
        details.username=username; details.password=password;
        details.deviceFriendlyName="Workshop Video / Android";
        details.persistentSession=remember; details.authenticator=guard;
        try {
            CredentialsAuthSession auth=client.getAuthentication()
                .beginAuthSessionViaCredentials(details).get(45,TimeUnit.SECONDS);
            // Drop our own password reference as soon as the encrypted auth request completes.
            details.password=null;
            AuthPollResult result=auth.pollingWaitForResult().get(180,TimeUnit.SECONDS);
            if(remember) sink.save(result.getAccountName(),result.getRefreshToken());
            savedUsername=result.getAccountName();savedToken=result.getRefreshToken();
            tokenLogin(savedUsername,savedToken);
        } finally { details.password=null; }
    }

    public synchronized void restore(String username,String token) throws Exception {
        savedUsername=username;savedToken=token;anonymousSession=false;
        ensureAuthenticated();
    }

    public synchronized void ensureAuthenticated() throws Exception {
        if(isAuthenticated())return;
        if(!running)throw new IllegalStateException("Steam 会话已关闭。");
        connect();
        if(anonymousSession) { user.logOnAnonymous(); loggedOn.get(35,TimeUnit.SECONDS); events.status("已连接公开浏览会话"); }
        else if(savedToken!=null)tokenLogin(savedUsername,savedToken);
        else throw new IllegalStateException("请先登录 Steam。");
    }

    private void tokenLogin(String username,String token) throws Exception {
        events.status("正在建立 Steam 内容会话…");
        LogOnDetails details = new LogOnDetails();
        details.setUsername(username); details.setAccessToken(token);
        details.setLoginID(loginId);
        user.logOn(details); loggedOn.get(35,TimeUnit.SECONDS);
        events.status("已登录 " + username);
    }

    public boolean isAuthenticated() { return running && authenticated && client.isConnected(); }
    public int cellId() { return cellId; }

    public CPublishedFile_QueryFiles_Response query(WorkshopQuery query) throws Exception {
        ensureAuthenticated();
        var response=await(published.queryFiles(query.request()));
        if(response.getResult()!=EResult.OK)
            throw new IllegalStateException("Steam 查询失败："+response.getResult());
        return response.getBody().build();
    }

    public PublishedFileDetails details(long itemId) throws Exception {
        ensureAuthenticated();
        var response=await(published.getDetails(CPublishedFile_GetDetails_Request.newBuilder()
            .addPublishedfileids(itemId).setAppid(WorkshopQuery.APP_ID).setLanguage(6)
            .setIncludetags(true).setStripDescriptionBbcode(true).build()));
        if(response.getResult()!=EResult.OK || response.getBody().getPublishedfiledetailsCount()!=1)
            throw new IllegalStateException("无法读取作品详情："+response.getResult());
        PublishedFileDetails item=response.getBody().getPublishedfiledetails(0);
        if(item.getResult()!=1 || item.getConsumerAppid()!=WorkshopQuery.APP_ID)
            throw new IllegalStateException("作品不存在、不可访问或不属于 Wallpaper Engine。");
        return item;
    }

    public static <T extends CallbackMsg> T await(AsyncJobSingle<T> job) throws Exception {
        job.setTimeout(35000);
        return job.toFuture().get(40,TimeUnit.SECONDS);
    }

    @Override public void close() {
        running=false; authenticated=false;
        savedToken=null;savedUsername=null;
        client.disconnect(); pump.interrupt();
        for(Closeable subscription:subscriptions)try{subscription.close();}catch(Exception ignored){}
        OkHttpClient http=client.getConfiguration().getHttpClient();
        http.dispatcher().cancelAll();http.dispatcher().executorService().shutdown();http.connectionPool().evictAll();
    }
}
