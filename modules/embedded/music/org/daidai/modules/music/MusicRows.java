package org.daidai.modules.music;

import android.os.SystemClock;
import android.widget.LinearLayout;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Give input and drawing a turn between small batches of song rows. */
final class MusicRows {
    private MusicRows() {}
    static void append(LinearLayout host,int count,BooleanSupplier current,IntConsumer add){
        host.postOnAnimation(new Runnable(){
            private int next;
            public void run(){
                if(!current.getAsBoolean()||!host.isAttachedToWindow())return;
                long start=SystemClock.uptimeMillis();int limit=Math.min(count,next+12);
                do{if(next>=count)return;add.accept(next++);}while(next<limit&&SystemClock.uptimeMillis()-start<6);
                if(next<count)host.postOnAnimation(this);
            }
        });
    }
}
