package org.simpleweather.app;
import android.content.*;
final class TodoStore {
    static TodoModel load(Context c){String raw=c.getSharedPreferences("todos",0).getString("data","");try{return raw.isEmpty()?new TodoModel():TodoModel.parse(raw);}catch(Exception e){throw new IllegalStateException("Invalid saved tasks",e);}}
    static void save(Context c,TodoModel m){try{String raw=m.json().toString();TodoModel.parse(raw);if(!c.getSharedPreferences("todos",0).edit().putString("data",raw).commit())throw new IllegalStateException();}catch(Exception e){throw new IllegalStateException("Could not save tasks",e);}}
}
