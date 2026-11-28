package org.daidai.toolbox;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;

public final class StatusRefreshPolicyTests {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        check(StatusRefreshPolicy.tickMillis(false,false,false)==1000,"idle status page avoids unnecessary polling");
        check(StatusRefreshPolicy.tickMillis(false,false,true)==250,"visible live readings use a responsive refresh cadence");
        check(StatusRefreshPolicy.tickMillis(true,false,false)==100,"route recording keeps the animation cadence");
        check(StatusRefreshPolicy.tickMillis(false,true,false)==100,"route playback keeps the animation cadence");
        check(StatusRefreshPolicy.SENSOR_TEXT_MS==250,"visible sensor text refreshes at 4 Hz");
        check(StatusRefreshPolicy.TREASURE_TEXT_MS==500,"place guidance refresh remains bounded");
        check(StatusRefreshPolicy.due(1000,600,400),"interval is due at the boundary");
        check(!StatusRefreshPolicy.due(999,600,400),"interval is not early");
        check(StatusRefreshPolicy.SENSOR_TEXT_MS>=StatusRefreshPolicy.ACTIVE_TICK_MS,"sensor text is not refreshed faster than the active page");
        StatusSatelliteStore<Integer> store=new StatusSatelliteStore<>();
        List<Integer> values=Arrays.asList(1,-1);StatusSatelliteStore.Snapshot<Integer> first=store.publish(values,1);
        check(first.revision==1&&first.capturedAt==1&&first.values.equals(values),"satellite snapshot carries values and revision together");
        check(store.snapshot().values==first.values,"readers share the immutable published snapshot");
        boolean immutable=false;try{first.values.add(2);}catch(UnsupportedOperationException expected){immutable=true;}
        check(immutable,"published satellite data cannot be changed by readers");
        AtomicBoolean done=new AtomicBoolean(),inconsistent=new AtomicBoolean();AtomicInteger reads=new AtomicInteger();CountDownLatch ready=new CountDownLatch(3),start=new CountDownLatch(1);
        Thread writer=new Thread(()->{ready.countDown();await(start);for(int n=0;n<5000;n++){store.publish(Arrays.asList(n,-n),n);if((n&31)==0)Thread.yield();}done.set(true);});
        Runnable read=()->{ready.countDown();await(start);long revision=0;while(!done.get()){StatusSatelliteStore.Snapshot<Integer> state=store.snapshot();reads.incrementAndGet();if(state.revision<revision)inconsistent.set(true);revision=state.revision;if(state.values.size()!=2||state.values.get(1)!=-state.values.get(0)||state.capturedAt!=state.values.get(0))inconsistent.set(true);}};
        Thread readerA=new Thread(read),readerB=new Thread(read);writer.start();readerA.start();readerB.start();
        try{ready.await();start.countDown();writer.join();readerA.join();readerB.join();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}
        check(reads.get()>0,"concurrent snapshot test exercised UI readers during publication");
        check(!inconsistent.get(),"concurrent satellite readers see complete ordered snapshots");
        check(store.snapshot().revision==5001,"snapshot revision advances once per published update");
        check(store.publish(Collections.emptyList(),0).values.isEmpty(),"stopping GNSS can publish an empty state");
        System.out.println("StatusRefreshPolicyTests: "+checks+" checks passed");
    }
    private static void await(CountDownLatch start){try{start.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}}
}
