package dev.workshopvideo.core;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;import static dev.workshopvideo.core.CoreChecks.*;
final class DownloadTaskChecks {
 static void run()throws Exception {
  DownloadTasks tasks=new DownloadTasks();CountDownLatch active=new CountDownLatch(1),gate=new CountDownLatch(1),activeDone=new CountDownLatch(1),queuedDone=new CountDownLatch(2);AtomicInteger ranQueued=new AtomicInteger(),finishedQueued=new AtomicInteger(),aborted=new AtomicInteger();AtomicReference<DownloadTasks.Stop> activeStop=new AtomicReference<>();
  try {
   check(tasks.submit("active",new DownloadTasks.Work(){public void run(DownloadTasks.Token token)throws Exception{token.transport(aborted::incrementAndGet);active.countDown();gate.await();}public void finished(DownloadTasks.Token token,Throwable failure){activeStop.set(token.stop());activeDone.countDown();}}),"active download submitted");check(active.await(2,TimeUnit.SECONDS),"download running before controls");
   check(!tasks.submit("active",new DownloadTasks.Work(){public void run(DownloadTasks.Token token){}public void finished(DownloadTasks.Token token,Throwable failure){}}),"duplicate transfer cannot run twice");
   AtomicReference<DownloadTasks.Stop> paused=new AtomicReference<>(),cancelled=new AtomicReference<>();
   tasks.submit("paused",new DownloadTasks.Work(){public void run(DownloadTasks.Token token){ranQueued.incrementAndGet();}public void finished(DownloadTasks.Token token,Throwable failure){paused.set(token.stop());finishedQueued.incrementAndGet();queuedDone.countDown();}});
   tasks.submit("cancelled",new DownloadTasks.Work(){public void run(DownloadTasks.Token token){ranQueued.incrementAndGet();}public void finished(DownloadTasks.Token token,Throwable failure){cancelled.set(token.stop());finishedQueued.incrementAndGet();queuedDone.countDown();}});
   check(tasks.request("paused",DownloadTasks.Stop.PAUSE),"queued pause accepted");check(tasks.request("cancelled",DownloadTasks.Stop.CANCEL),"queued cancel accepted");check(queuedDone.await(2,TimeUnit.SECONDS),"queued stops finish without waiting for active transfer");check(ranQueued.get()==0&&finishedQueued.get()==2,"queued stopped jobs never start and finish exactly once");check(paused.get()==DownloadTasks.Stop.PAUSE&&cancelled.get()==DownloadTasks.Stop.CANCEL,"pause preserves distinct cleanup intention from cancel");
   check(tasks.request("active",DownloadTasks.Stop.CANCEL),"active cancellation accepted");check(activeDone.await(2,TimeUnit.SECONDS),"active cancellation interrupts blocked worker");check(aborted.get()>=1&&activeStop.get()==DownloadTasks.Stop.CANCEL,"active HTTP transport cancellation accompanies worker interrupt");check(!tasks.request("unknown",DownloadTasks.Stop.PAUSE),"unknown task does not change another download");
   CountDownLatch resumed=new CountDownLatch(1);tasks.submit("paused",new DownloadTasks.Work(){public void run(DownloadTasks.Token token)throws Exception{token.check();resumed.countDown();}public void finished(DownloadTasks.Token token,Throwable failure){}});check(resumed.await(2,TimeUnit.SECONDS),"resumed task gets a fresh token and no stale interrupt");
  }finally{gate.countDown();tasks.close();}
  DownloadTasks.Token token=new DownloadTasks.Token();AtomicInteger owned=new AtomicInteger();token.transport(owned::incrementAndGet);check(!token.stopped()&&owned.get()==0,"transport registered for normal transfer is not cancelled");
 }
}
