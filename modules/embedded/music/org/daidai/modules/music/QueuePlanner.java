package org.daidai.modules.music;
import java.util.Random;
/** Playlist selection independent of Android and storage. */
public final class QueuePlanner {
    public enum Mode { ALL, ONE, SHUFFLE }
    public static int next(int size,int index,Mode mode,boolean completed,Random random){
        if(size<=0)return -1;if(index<0||index>=size)return 0;
        if(mode==Mode.ONE&&completed)return index;
        if(mode==Mode.SHUFFLE&&size>1){int n=random.nextInt(size-1);return n>=index?n+1:n;}
        return (index+1)%size;
    }
    public static int previous(int size,int index){if(size<=0)return -1;return (Math.max(0,index)+size-1)%size;}
}
