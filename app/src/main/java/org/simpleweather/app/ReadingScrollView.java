package org.simpleweather.app;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.ScrollView;

/** Live values must not scroll back to the last focused button during remeasurement. */
final class ReadingScrollView extends ScrollView {
    private boolean keepPosition;
    ReadingScrollView(Context context){super(context);}
    void updateValues(Runnable change){
        boolean pending=keepPosition;keepPosition=true;
        try{change.run();}finally{if(!isLayoutRequested())keepPosition=pending;}
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        // Read immediately before layout so an ongoing drag/fling keeps its latest position.
        int x=getScrollX(),y=getScrollY();boolean keep=keepPosition;
        super.onLayout(changed,left,top,right,bottom);
        if(keep)scrollTo(x,y);keepPosition=false;
    }
    @Override public boolean requestChildRectangleOnScreen(View child,Rect rect,boolean immediate){
        return !keepPosition&&super.requestChildRectangleOnScreen(child,rect,immediate);
    }
    @Override public void requestChildFocus(View child,View focused){
        if(!keepPosition)super.requestChildFocus(child,focused);
    }
}
