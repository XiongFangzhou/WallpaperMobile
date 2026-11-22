package dev.workshopvideo.core;

import java.io.Closeable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serial transfers with prompt queued/running pause, cancellation and exactly-once completion. */
public final class DownloadTasks implements Closeable {
    public enum Stop { NONE, PAUSE, CANCEL }
    public interface Work {void run(Token token)throws Exception;void finished(Token token,Throwable failure);}
    public static final class Token {
        private Stop stop=Stop.NONE;private Thread thread;private Runnable cancelTransport;
        public synchronized Stop stop(){return stop;}
        public synchronized boolean stopped(){return stop!=Stop.NONE;}
        public void check()throws InterruptedException {if(stopped()||Thread.currentThread().isInterrupted())throw new InterruptedException("下载已停止");}
        public synchronized void transport(Runnable cancel){cancelTransport=cancel;if(stop!=Stop.NONE&&cancel!=null)cancel.run();}
        private synchronized void attach(){thread=Thread.currentThread();if(stop!=Stop.NONE)thread.interrupt();}
        private synchronized void detach(){thread=null;cancelTransport=null;}
        private synchronized void request(Stop request){if(request.ordinal()>stop.ordinal())stop=request;if(cancelTransport!=null)cancelTransport.run();if(thread!=null)thread.interrupt();}
    }
    private final ThreadPoolExecutor queue=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
    private final ExecutorService cleanup=Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String,Task> tasks=new ConcurrentHashMap<>();private volatile boolean closed;
    private final class Task implements Runnable {
        final String id;final Work work;final Token token=new Token();final AtomicBoolean completed=new AtomicBoolean();
        Task(String id,Work work){this.id=id;this.work=work;}
        public void run(){Throwable failure=null;token.attach();try{token.check();work.run(token);}catch(Throwable e){failure=e;}finally{token.detach();Thread.interrupted();complete(failure);}}
        void complete(Throwable failure){if(!completed.compareAndSet(false,true))return;tasks.remove(id,this);work.finished(token,failure);}
    }
    public synchronized boolean submit(String id,Work work){
        if(closed)throw new RejectedExecutionException("下载队列已关闭");Task task=new Task(id,work);if(tasks.putIfAbsent(id,task)!=null)return false;
        try{queue.execute(task);}catch(RuntimeException e){tasks.remove(id,task);throw e;}return true;
    }
    public synchronized boolean request(String id,Stop mode){
        if(mode==Stop.NONE)throw new IllegalArgumentException("无效停止操作");Task task=tasks.get(id);if(task==null)return false;task.token.request(mode);
        if(queue.remove(task))cleanup.execute(()->task.complete(null));return true;
    }
    public int size(){return tasks.size();}
    public synchronized void close(){if(closed)return;closed=true;for(String id:tasks.keySet())request(id,Stop.PAUSE);queue.shutdown();cleanup.shutdown();}
}
