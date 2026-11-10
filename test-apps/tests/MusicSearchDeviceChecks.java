package org.daidai.prototype.qa;

import android.app.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.daidai.modules.music.*;
import org.daidai.modules.shared.Texts;

/** Runs on Android because catalogue initials use the platform ICU transliterator. */
public final class MusicSearchDeviceChecks {
    private static int count;
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
    private static MusicLibrary.Track track(String uri,String title,String composer,String arranger,String vocalist,int bpm,int rating){return new MusicLibrary.Track(uri,title,vocalist,composer,arranger,"","","",bpm,rating,false,180000,"");}
    public static int run(){
        count=0;MusicLibrary.Track sweet=track("test:sweet","甜蜜出逃","iKz、阿良良木健","iKz;Zeta","歌手甲",120,0),blank=track("test:blank","安静","","","",0,-1),englishA=track("test:apple","Apple","Alice","","",99,8),englishZ=track("test:zebra","Zebra","Zed","","",161,10),other=track("test:other","かな","","","",130,6);List<MusicLibrary.Track> tracks=Arrays.asList(englishZ,other,sweet,englishA,blank);
        check(MusicSearch.key(sweet,MusicSearch.Category.TITLE).equals("zh:T"),"Chinese title uses pinyin T");
        List<MusicSearch.Group> groups=MusicSearch.groups(tracks,"",MusicSearch.Category.TITLE);List<String> keys=keys(groups);
        check(keys.indexOf("zh:A")<keys.indexOf("zh:T"),"Chinese initials sorted");check(keys.indexOf("zh:T")<keys.indexOf("en:A"),"Chinese before English");check(keys.indexOf("en:A")<keys.indexOf("en:Z"),"English initials sorted");check(keys.indexOf("en:Z")<keys.indexOf("other:か"),"English before other languages");
        groups=MusicSearch.groups(tracks,"iKz",MusicSearch.Category.TITLE);check(groups.size()==5,"Search preserves every category");check(groups.get(0).key.equals("zh:T"),"Matching category moves first");check(groups.get(0).highlighted("iKz"),"Matching category highlighted");check(groups.get(0).matches.size()==1&&groups.get(0).matches.get(0).uri.equals("test:sweet"),"Composer search finds title category");check(groups.get(1).matches.isEmpty(),"Nonmatching category remains with no matched rows");
        List<String> composer=MusicSearch.keys(sweet,MusicSearch.Category.COMPOSER);check(composer.contains("en:I")&&composer.contains("zh:A"),"Multiple composers join their language and initial groups");List<String> arranger=MusicSearch.keys(sweet,MusicSearch.Category.ARRANGER);check(arranger.contains("en:I")&&arranger.contains("en:Z"),"Multiple arrangers join multiple initials");
        check(MusicSearch.key(sweet,MusicSearch.Category.RATING).equals("rating:0"),"Explicit zero rating retained");check(MusicSearch.key(blank,MusicSearch.Category.RATING).equals("missing"),"Unset rating separate from zero");
        check(MusicSearch.key(englishA,MusicSearch.Category.BPM).equals("bpm:under100"),"BPM below 100");check(MusicSearch.key(other,MusicSearch.Category.BPM).equals("bpm:100-130"),"BPM 130 in lower boundary group");check(MusicSearch.key(englishZ,MusicSearch.Category.BPM).equals("bpm:over160"),"BPM over 160");check(MusicSearch.filter(tracks,"3:00").size()==tracks.size(),"Duration search uses visible minutes and seconds");
        MusicLibrary.Track names=track("test:people","Name punctuation",MusicPeople.join(Arrays.asList("Earth, Wind & Fire","Last, First")),MusicPeople.join(Collections.singletonList("Alpha, Zulu")),MusicPeople.join(Collections.singletonList("Singer; Band")),120,-1);
        check(MusicSearch.keys(names,MusicSearch.Category.COMPOSER).equals(Arrays.asList("en:E","en:L")),"encoded names group by actual person without comma fragments");
        check(MusicSearch.keys(names,MusicSearch.Category.ARRANGER).equals(Collections.singletonList("en:A")),"encoded arranger does not create a second comma group");
        check(MusicSearch.value(names,MusicSearch.Category.COMPOSER).equals("Earth, Wind & Fire; Last, First")&&MusicSearch.matches(names,"Earth, Wind & Fire")&&!MusicSearch.matches(names,"DDMP1"),"search reads decoded personnel and excludes storage markers");
        MusicLibrary.Track az=track("test:az","Z",MusicPeople.join(Collections.singletonList("Az")),"","",120,-1),alpha=track("test:alpha","A",MusicPeople.join(Collections.singletonList("Alpha, Longer")),"","",120,-1);List<MusicSearch.Group> byComposer=MusicSearch.groups(Arrays.asList(az,alpha),"",MusicSearch.Category.COMPOSER);check(byComposer.get(0).all.get(0).uri.equals(alpha.uri),"composer sort uses decoded name rather than encoded length prefix");return count;
    }
    private static List<String> keys(List<MusicSearch.Group> groups){ArrayList<String> out=new ArrayList<>();for(MusicSearch.Group g:groups)out.add(g.key);return out;}
    public static int runUi(Instrumentation instrumentation,Activity activity){
        final int before=count;instrumentation.runOnMainSync(()->{
            List<MusicLibrary.Track> source=Arrays.asList(track("test:sweet","甜蜜出逃","iKz","iKz","",120,0),track("test:apple","Apple","Alice","","",110,-1));
            Dialog dialog=MusicSearchScreen.open(activity,source,t->{});View root=dialog.getWindow().getDecorView();ArrayList<Button> groups=groupButtons(root);check(groups.size()==2,"Search page includes all title categories");for(Button b:groups)check(Boolean.FALSE.equals(b.getTag()),"Search groups initially collapsed");groups.get(0).performClick();check(Boolean.TRUE.equals(groups.get(0).getTag()),"Category expands on tap");
            findButton(root,Texts.get(activity,"music_search_by_composer")).performClick();for(Button b:groupButtons(root))check(Boolean.FALSE.equals(b.getTag()),"Switching categories folds all groups");
            findButton(root,Texts.get(activity,"music_search_by_title")).performClick();EditText input=findInput(root);input.setText("iKz");findButton(root,Texts.get(activity,"music_search_apply")).performClick();groups=groupButtons(root);check(groups.size()==2,"Search keeps nonmatching category");check(groups.get(0).getText().toString().contains("T"),"Matching T group moves first in UI");check(groups.get(0).getCurrentTextColor()==0xffffffff,"Matching group uses white text");
            findButton(root,Texts.get(activity,"music_search_reset")).performClick();check(input.getText().length()==0,"Clear empties input");for(Button b:groupButtons(root))check(b.getCurrentTextColor()!=0xffffffff,"Clear removes highlighted categories");findButton(root,Texts.get(activity,"music_search_back")).performClick();check(!dialog.isShowing(),"Back closes search page");
        });return count-before;
    }
    private static ArrayList<Button> groupButtons(View view){ArrayList<Button> out=new ArrayList<>();walk(view,out);return out;}
    private static void walk(View view,List<Button> out){if(view instanceof Button&&view.getTag() instanceof Boolean)out.add((Button)view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)walk(group.getChildAt(i),out);}}
    private static Button findButton(View view,String label){if(view instanceof Button&&((Button)view).getText().toString().equals(label))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button found=findButton(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private static EditText findInput(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){EditText found=findInput(group.getChildAt(i));if(found!=null)return found;}}return null;}
}
