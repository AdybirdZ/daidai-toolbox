package org.daidai.modules.messaging;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.ScrollView;

/** The conversation owns its scroll gesture instead of the surrounding connection form. */
final class HistoryScrollView extends ScrollView {
    HistoryScrollView(Context context){
        super(context);
        setFillViewport(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN&&getParent()!=null)
            getParent().requestDisallowInterceptTouchEvent(canScrollVertically(-1)||canScrollVertically(1));
        boolean handled=super.dispatchTouchEvent(event);
        if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&getParent()!=null)
            getParent().requestDisallowInterceptTouchEvent(false);
        return handled;
    }
}
