package org.daidai.prototype.qa;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.*;
import org.daidai.modules.music.*;
import org.daidai.modules.shared.Texts;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Callable;

/** Uses only generated audio in the test APK's SAF provider, never user media. */
public final class MusicLibrary050DeviceChecks {
    private static Instrumentation runner;
    private static Activity activity;
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError("Music 0.5.0 library: "+message);}
    private static String t(String key,Object...args){return Texts.get(activity,key,args);}
    private static void main(Runnable action){final Throwable[] error={null};runner.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});runner.waitForIdleSync();if(error[0]!=null)throw new AssertionError("Library UI action failed",error[0]);}
    private static View top(){List<View> roots=WindowInspector.getGlobalWindowViews();return roots.isEmpty()?activity.getWindow().getDecorView():roots.get(roots.size()-1);}
    private static <T extends View> ArrayList<T> views(View root,Class<T> type){ArrayList<T> found=new ArrayList<>();if(type.isInstance(root))found.add(type.cast(root));if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)found.addAll(views(group.getChildAt(i),type));}return found;}
    private static boolean text(View root,String label){for(TextView row:views(root,TextView.class))if(row.getText().toString().equals(label))return true;return false;}
    private static Button button(String label){for(Button row:views(top(),Button.class))if(row.getText().toString().equals(label))return row;throw new AssertionError("Missing button: "+label);}
    private static Button described(String label){for(Button row:views(top(),Button.class)){CharSequence description=row.getContentDescription();if(description!=null&&label.contentEquals(description))return row;}throw new AssertionError("Missing described button: "+label);}
    private static EditText tagged(String tag){EditText field=top().findViewWithTag(tag);if(field==null)throw new AssertionError("Missing editor: "+tag);return field;}
    private static CheckBox allSongs(){for(CheckBox row:views(top(),CheckBox.class))if(row.getText().toString().equals(t("music060_select_all")))return row;throw new AssertionError("Missing select-all checkbox");}
    private static int namedSongs(View root,String prefix){int count=0;for(Button row:views(root,Button.class))if(row.getText().toString().startsWith(prefix))count++;return count;}
    private static void waitMain(Callable<Boolean> condition)throws Exception{long deadline=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<deadline){final boolean[] ok={false};main(()->{try{ok[0]=condition.call();}catch(Exception e){throw new RuntimeException(e);}});if(ok[0])return;Thread.sleep(40);}throw new AssertionError("Timed out waiting for library UI");}
    private static ArrayList<Dialog> trackedDialogs(){
        try{
            Field instances=MusicLibraryScreen.class.getDeclaredField("INSTANCES");instances.setAccessible(true);Object screen=((Map<?,?>)instances.get(null)).get(activity);if(screen==null)return new ArrayList<>();Field dialogs=MusicLibraryScreen.class.getDeclaredField("dialogs");dialogs.setAccessible(true);return new ArrayList<>((Collection<Dialog>)dialogs.get(screen));
        }catch(Exception error){throw new AssertionError("Could not inspect this Activity's library dialogs",error);}
    }
    private static ArrayList<View> ownedModals(){
        View base=activity.getWindow().getDecorView();ArrayList<View> roots=new ArrayList<>(WindowInspector.getGlobalWindowViews());Set<android.os.IBinder> tokens=new HashSet<>();Set<View> owned=new HashSet<>(),known=new HashSet<>();
        if(base.getWindowToken()!=null)tokens.add(base.getWindowToken());if(base.getApplicationWindowToken()!=null)tokens.add(base.getApplicationWindowToken());ViewGroup.LayoutParams baseParams=base.getLayoutParams();if(baseParams instanceof WindowManager.LayoutParams&&((WindowManager.LayoutParams)baseParams).token!=null)tokens.add(((WindowManager.LayoutParams)baseParams).token);
        for(Dialog dialog:trackedDialogs())if(dialog.getWindow()!=null)known.add(dialog.getWindow().getDecorView());
        // Application dialogs have their own IWindow token. LayoutParams.token links them to
        // the Activity app token; panels and attached dialogs may instead link to a parent IWindow.
        for(int pass=0;pass<=roots.size();pass++){boolean added=false;for(View root:roots){
            if(root==base||owned.contains(root)||!root.isAttachedToWindow()||root.getVisibility()!=View.VISIBLE)continue;ViewGroup.LayoutParams raw=root.getLayoutParams();if(!(raw instanceof WindowManager.LayoutParams))continue;WindowManager.LayoutParams params=(WindowManager.LayoutParams)raw;boolean dialogType=params.type==WindowManager.LayoutParams.TYPE_APPLICATION||params.type>=WindowManager.LayoutParams.FIRST_SUB_WINDOW&&params.type<=WindowManager.LayoutParams.LAST_SUB_WINDOW;if(!dialogType)continue;
            if(known.contains(root)||params.token!=null&&tokens.contains(params.token)){owned.add(root);added=true;if(root.getWindowToken()!=null)tokens.add(root.getWindowToken());if(root.getApplicationWindowToken()!=null)tokens.add(root.getApplicationWindowToken());}
        }if(!added)break;}
        ArrayList<View> result=new ArrayList<>();for(View root:roots)if(owned.contains(root))result.add(root);return result;
    }
    private static void dismiss(){
        long deadline=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<deadline){final boolean[] done={false};main(()->{
            for(Dialog dialog:trackedDialogs())if(dialog.isShowing())dialog.dismiss();ArrayList<View> modals=ownedModals();if(modals.isEmpty()){done[0]=true;return;}View modal=modals.get(modals.size()-1);Button close=modal.findViewById(android.R.id.button2);if(close!=null&&close.getVisibility()==View.VISIBLE){close.performClick();return;}Button positive=modal.findViewById(android.R.id.button1);if(positive!=null&&positive.getVisibility()==View.VISIBLE&&positive.getText().toString().equals(t("music_close"))){positive.performClick();return;}
            // Dispatch only to this known dialog. Never send global BACK, which could finish
            // the player or a separate Activity after a dialog's exit animation has started.
            modal.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));modal.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));
        });if(done[0])return;SystemClock.sleep(80);}
        throw new AssertionError("Library dialogs did not close");
    }
    private static void checkTornDown(){main(()->{check(!activity.isFinishing()&&!activity.isDestroyed(),"dialog cleanup preserves the owning player Activity");check(ownedModals().isEmpty(),"library suite leaves no attached dialogs or child panels owned by the player");for(Dialog dialog:trackedDialogs())check(!dialog.isShowing(),"library suite leaves no registered dialog showing");});}
    private static Uri create(Context context,Uri parent,String type,String name)throws Exception{Uri uri=DocumentsContract.createDocument(context.getContentResolver(),parent,type,name);if(uri==null)throw new IOException("No test document created");return uri;}
    private static void writeFixture(Context context,Context test,Uri uri)throws Exception{try(InputStream in=test.getAssets().open("music-tags/tagged.flac");OutputStream out=context.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();byte[] bytes=new byte[8192];int size;while((size=in.read(bytes))!=-1)out.write(bytes,0,size);}}
    private static boolean exists(Context c,Uri uri){try(Cursor row=c.getContentResolver().query(uri,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},null,null,null)){return row!=null&&row.moveToFirst();}catch(Exception absent){return false;}}
    private static byte[] digest(Context context,Uri uri)throws Exception{java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");try(InputStream input=context.getContentResolver().openInputStream(uri)){if(input==null)throw new IOException();byte[] bytes=new byte[8192];int size;while((size=input.read(bytes))!=-1)hash.update(bytes,0,size);}return hash.digest();}
    public static int run(Instrumentation instrumentation,Activity screen)throws Exception{
        runner=instrumentation;activity=screen;checks=0;Context context=runner.getTargetContext(),test=runner.getContext();SharedPreferences prefs=context.getSharedPreferences("music_module",0);Map<String,?> before=new HashMap<>(prefs.getAll());ArrayList<Uri> created=new ArrayList<>();
        try{
            // Finishing an artwork Activity can leave its decor registered during the exit animation.
            // Close only dialogs owned by this Activity; never send BACK to unrelated or departing windows.
            dismiss();waitMain(()->activity.getWindow().getDecorView().hasWindowFocus());prefs.edit().remove("catalogue").remove("queue").remove("collections").remove("metadata_manually_edited").apply();
            Uri root=DocumentsContract.buildDocumentUri(MusicMetadataDocuments.AUTHORITY,"root");
            main(()->activity.startActivity(new Intent().setComponent(new ComponentName(test.getPackageName(),"org.daidai.prototype.qa.MusicMetadataGrantActivity")).putExtra("targetPackage",context.getPackageName())));
            long permissionDeadline=SystemClock.uptimeMillis()+12000;while(!exists(context,root)&&SystemClock.uptimeMillis()<permissionDeadline)SystemClock.sleep(80);check(exists(context,root),"fixture provider owner grants real read/write URI access");waitMain(()->activity.getWindow().getDecorView().hasWindowFocus());
            String prefix="library050-"+System.nanoTime();
            Uri chosen=create(context,root,DocumentsContract.Document.MIME_TYPE_DIR,prefix+"-chosen");created.add(chosen);Uri outside=create(context,root,DocumentsContract.Document.MIME_TYPE_DIR,prefix+"-outside");created.add(outside);
            Uri first=create(context,chosen,"audio/flac","first.flac"),second=create(context,chosen,"audio/flac","second.flac"),external=create(context,outside,"audio/flac","external.flac");created.add(first);created.add(second);created.add(external);for(Uri uri:new Uri[]{first,second,external})writeFixture(context,test,uri);
            Uri tree=DocumentsContract.buildTreeDocumentUri(MusicMetadataDocuments.AUTHORITY,DocumentsContract.getDocumentId(chosen));prefs.edit().putString("storage_tree_uri",tree.toString()).apply();
            check(MusicFolder.contains(context,tree.toString(),first.toString()),"selected folder accepts child identity");check(!MusicFolder.contains(context,tree.toString(),external.toString()),"selected folder rejects another folder under the same provider");check(!MusicFolder.contains(context,tree.toString(),"file:///not-selected.flac"),"non-SAF source reference cannot be edited or deleted");
            byte[] sourceBefore=digest(context,external),existingBefore=digest(context,first);Uri copyA=MusicImporter.copyToFolder(context,external),copyB=MusicImporter.copyToFolder(context,external);created.add(copyA);created.add(copyB);
            check(!DocumentsContract.getDocumentId(copyA).equals(DocumentsContract.getDocumentId(copyB))&&!copyA.equals(external)&&!copyB.equals(external),"repeated copy imports create distinct destination identities");check(MusicLibrary.title(context,copyA).equals("external.flac")&&MusicLibrary.title(context,copyB).equals("external (1).flac"),"duplicate copied file names receive an application-generated numeric suffix");check(Arrays.equals(sourceBefore,digest(context,external))&&Arrays.equals(existingBefore,digest(context,first)),"repeated copying preserves the source and existing destination files");check(Arrays.equals(sourceBefore,digest(context,copyA))&&Arrays.equals(sourceBefore,digest(context,copyB)),"both destination copies preserve all source bytes");Uri alreadyStored=MusicImporter.copyToFolder(context,first);check(DocumentsContract.getDocumentId(alreadyStored).equals(DocumentsContract.getDocumentId(first))&&MusicFolder.loadChecked(context,tree.toString()).size()==4,"file already in the selected folder retains its identity without another copy");DocumentsContract.deleteDocument(context.getContentResolver(),copyA);DocumentsContract.deleteDocument(context.getContentResolver(),copyB);
            ArrayList<MusicLibrary.Track> scanned=MusicFolder.loadChecked(context,tree.toString());check(scanned.size()==2,"folder scan includes exactly its two generated songs");MusicLibrary.discover(context,scanned);MusicLibrary.Track firstTrack=MusicLibrary.find(context,first.toString()),secondTrack=MusicLibrary.find(context,second.toString());check(firstTrack!=null&&firstTrack.bpmText.equals("123.4")&&!firstTrack.coverPath.isEmpty(),"SAF scan imports exact embedded decimal BPM and artwork");
            MusicLibrary.Track externalTrack=MusicLibrary.readMetadata(context,external);MusicLibrary.discover(context,Collections.singleton(externalTrack));
            main(()->MusicLibraryScreen.edit(activity,externalTrack,()->{}));waitMain(()->text(top(),t("library050_edit_requires_folder")));main(()->check(views(top(),EditText.class).isEmpty(),"out-of-folder metadata editor is blocked"));dismiss();
            boolean rejected=false;try{MusicFolder.delete(context,tree.toString(),external.toString());}catch(IOException expected){rejected=true;}check(rejected&&exists(context,external),"out-of-folder deletion is rejected and keeps the file");
            main(()->MusicLibraryScreen.edit(activity,firstTrack,()->{}));waitMain(()->top().findViewWithTag("music060_meta_bpm")!=null);
            main(()->{EditText bpm=tagged("music060_meta_bpm");check(bpm.getText().toString().equals("123.4"),"BPM editor shows embedded decimal unchanged");check((bpm.getInputType()&android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)!=0,"BPM keyboard accepts a decimal separator");tagged("music060_meta_title").setText("Duplicate display title");bpm.setText("123.12345678901234567890123456789");EditText composer=tagged("music060_person_composer");composer.setText("Earth, Wind & Fire");described(t("music060_add_person",t("music060_role_composer"))).performClick();ArrayList<EditText> people=new ArrayList<>();for(EditText field:views(top(),EditText.class))if("music060_person_composer".equals(field.getTag()))people.add(field);check(people.size()==2,"plus adds one individually editable composer");people.get(1).setText("Last, First");button(t("music_save")).performClick();});
            main(()->MusicLibraryScreen.edit(activity,secondTrack,()->{}));waitMain(()->top().findViewWithTag("music060_meta_bpm")!=null);main(()->{tagged("music060_meta_title").setText("Duplicate display title");button(t("music_save")).performClick();});
            check(MusicPeople.split(MusicLibrary.find(context,first.toString()).composer).equals(Arrays.asList("Earth, Wind & Fire","Last, First")),"individual personnel fields retain commas without re-splitting");
            check(MusicLibrary.find(context,first.toString()).bpmText.equals("123.12345678901234567890123456789"),"metadata editor persists arbitrary decimal precision");check(MusicLibrary.find(context,first.toString()).title.equals(MusicLibrary.find(context,second.toString()).title)&&!firstTrack.uri.equals(secondTrack.uri),"two file identities safely use the same display title");check(MusicLibrary.title(context,first).equals("first.flac")&&MusicLibrary.title(context,second).equals("second.flac"),"display title edits leave actual file names unchanged");
            MusicLibrary.Track canonicalFirst=MusicLibrary.find(context,first.toString()),canonicalSecond=MusicLibrary.find(context,second.toString());MusicLibrary.addToQueue(context,Arrays.asList(canonicalFirst,canonicalSecond));MusicCollections.toggleFavorite(context,canonicalFirst.uri);MusicCollections.createPlaylist(context,"SAF deletion");MusicCollections.add(context,"SAF deletion",Arrays.asList(canonicalFirst.uri,canonicalSecond.uri));
            main(()->MusicLibraryScreen.open(activity,()->{}));waitMain(()->namedSongs(top(),"Duplicate display title")==2);
            final View[] libraryRoot={null};main(()->{libraryRoot[0]=top();check(allSongs()!=null&&!allSongs().isChecked(),"My songs has a labeled unselected select-all checkbox");check(!described(t("music060_manage_playlist",t("music060_all_songs"))).isEnabled(),"all-songs built-in playlist cannot be managed");check(!text(top(),t("library_add_all")),"My songs removes add-all-to-queue control");MusicCollections.createPlaylist(context,"SAF one song");MusicCollections.add(context,"SAF one song",Collections.singleton(canonicalSecond.uri));button(t("library_playlists")).performClick();check(text(top(),t("music060_all_songs"))&&!text(top(),t("library_favorites")),"playlist chooser includes all songs and excludes favorites");button("SAF one song").performClick();});
            waitMain(()->top()==libraryRoot[0]&&text(top(),"SAF one song")&&namedSongs(top(),"Duplicate display title")==1);
            main(()->{check(described(t("music060_manage_playlist","SAF one song")).isEnabled(),"custom playlist has its own management control");button(t("library_playlists")).performClick();button(t("music060_all_songs")).performClick();});waitMain(()->top()==libraryRoot[0]&&namedSongs(top(),"Duplicate display title")==2);
            main(()->{allSongs().performClick();check(allSongs().isChecked()&&button(t("music060_selected_to_playlist")).isEnabled(),"select all enables adding selected songs to a playlist");button(t("music060_selected_to_playlist")).performClick();button("SAF one song").performClick();});waitMain(()->top()==libraryRoot[0]);check(MusicCollections.playlist(context,"SAF one song").containsAll(Arrays.asList(canonicalFirst.uri,canonicalSecond.uri)),"bulk add uses selected stable file identities");main(()->allSongs().performClick());
            main(()->{for(Button b:views(top(),Button.class))if(b.getText().toString().startsWith("Duplicate display title")){b.performClick();break;}button(t("library050_delete_file")).performClick();check(text(top(),t("library050_delete_file_confirm","Duplicate display title")),"actual file deletion has explicit confirmation");button(t("music_cancel")).performClick();});
            check(exists(context,first)&&exists(context,second)&&MusicLibrary.load(context).size()==2,"cancelled deletion leaves files and queue intact");
            main(()->{button(t("library050_delete_file")).performClick();button(t("library_delete")).performClick();});waitMain(()->MusicLibrary.find(context,first.toString())==null&&top()==libraryRoot[0]&&namedSongs(top(),"Duplicate display title")==1);
            check(!exists(context,first)&&exists(context,second),"confirmed deletion removes only the chosen same-title file");check(MusicLibrary.load(context).size()==1&&MusicLibrary.load(context).get(0).uri.equals(canonicalSecond.uri),"confirmed deletion cleans only its playback queue entry");check(!MusicCollections.isFavorite(context,canonicalFirst.uri)&&!MusicCollections.playlist(context,"SAF deletion").contains(canonicalFirst.uri)&&MusicCollections.playlist(context,"SAF deletion").contains(canonicalSecond.uri),"confirmed deletion cleans likes and playlists while preserving the other song");
            Uri third=create(context,chosen,"audio/flac","third.flac");created.add(third);writeFixture(context,test,third);main(()->button(t("library050_refresh_folder")).performClick());waitMain(()->views(top(),CheckBox.class).size()==3);main(()->{allSongs().performClick();button(t("music060_delete_selected")).performClick();check(text(top(),t("music060_delete_selected_confirm",2)),"bulk file deletion confirms count and scope");button(t("music_cancel")).performClick();});check(exists(context,second)&&exists(context,third),"cancelling bulk deletion preserves both actual audio files");main(()->{button(t("music060_delete_selected")).performClick();button(t("library_delete")).performClick();});waitMain(()->!exists(context,second)&&!exists(context,third)&&MusicLibrary.find(context,canonicalSecond.uri)==null&&views(top(),CheckBox.class).size()==1);check(MusicLibrary.load(context).isEmpty()&&MusicCollections.playlist(context,"SAF deletion").isEmpty(),"confirmed bulk deletion cleans catalogue queue and playlist entries");check(exists(context,external),"bulk deletion never reaches the generated out-of-folder source");
            dismiss();checkTornDown();return checks;
        }finally{
            try{dismiss();checkTornDown();}finally{for(int i=created.size()-1;i>=0;i--)try{DocumentsContract.deleteDocument(context.getContentResolver(),created.get(i));}catch(Exception ignored){}
            SharedPreferences.Editor restore=prefs.edit().clear();for(Map.Entry<String,?> entry:before.entrySet()){Object value=entry.getValue();String key=entry.getKey();if(value instanceof String)restore.putString(key,(String)value);else if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value);else if(value instanceof Integer)restore.putInt(key,(Integer)value);else if(value instanceof Long)restore.putLong(key,(Long)value);else if(value instanceof Float)restore.putFloat(key,(Float)value);else if(value instanceof Set)restore.putStringSet(key,new HashSet<>((Set<String>)value));}restore.commit();}
        }
    }
}
