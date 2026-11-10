package org.daidai.modules.music;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Local playlists and favourites. Only URI strings are stored; audio files remain where the user selected them. */
public final class MusicCollections {
    private static final String PREFS = "music_module";
    private static final String KEY = "collections";
    private MusicCollections() {}

    private static JSONObject read(Context c) {
        try { return new JSONObject(c.getSharedPreferences(PREFS, 0).getString(KEY, "{}")); }
        catch (Exception ignored) { return new JSONObject(); }
    }
    private static void write(Context c, JSONObject value) {
        c.getSharedPreferences(PREFS, 0).edit().putString(KEY, value.toString()).apply();
    }
    private static LinkedHashSet<String> arrayToSet(JSONArray a) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) { String s = a.optString(i, "").trim(); if (!s.isEmpty()) out.add(s); }
        return out;
    }
    private static JSONArray setToArray(Collection<String> values) {
        JSONArray a = new JSONArray(); for (String v : values) a.put(v); return a;
    }
    public static synchronized LinkedHashSet<String> favorites(Context c) {
        JSONObject root = read(c); return arrayToSet(root.optJSONArray("favorites"));
    }
    public static synchronized boolean isFavorite(Context c, String uri) { return favorites(c).contains(uri); }
    public static synchronized boolean toggleFavorite(Context c, String uri) {
        JSONObject root = read(c); LinkedHashSet<String> f = arrayToSet(root.optJSONArray("favorites"));
        boolean added = f.add(uri); if (!added) f.remove(uri); put(root,"favorites",setToArray(f)); write(c, root); return added;
    }
    public static synchronized List<String> playlistNames(Context c) {
        JSONObject root = read(c); ArrayList<String> names = new ArrayList<>(); JSONArray order = root.optJSONArray("playlistOrder");
        if (order != null) for (int i = 0; i < order.length(); i++) { String n = order.optString(i, "").trim(); if (!n.isEmpty() && root.opt(n) instanceof JSONArray) names.add(n); }
        Iterator<String> keys = root.keys(); while (keys.hasNext()) { String n = keys.next(); if ("favorites".equals(n) || "playlistOrder".equals(n)) continue; if (root.opt(n) instanceof JSONArray && !names.contains(n)) names.add(n); }
        return names;
    }
    public static synchronized LinkedHashSet<String> playlist(Context c, String name) {
        return arrayToSet(read(c).optJSONArray(name));
    }
    public static synchronized boolean createPlaylist(Context c, String name) {
        name = cleanName(name); if (name.isEmpty() || reserved(name)) return false; JSONObject root = read(c); if (root.has(name)) return false;
        put(root,name,new JSONArray()); JSONArray order = root.optJSONArray("playlistOrder"); if (order == null) order = new JSONArray(); order.put(name); put(root,"playlistOrder",order); write(c, root); return true;
    }
    public static synchronized boolean renamePlaylist(Context c, String oldName, String newName) {
        oldName = cleanName(oldName); newName = cleanName(newName); if (oldName.isEmpty() || newName.isEmpty() || reserved(oldName) || reserved(newName) || oldName.equals(newName)) return false;
        JSONObject root = read(c); if (!(root.opt(oldName) instanceof JSONArray) || root.has(newName)) return false; JSONArray values = root.optJSONArray(oldName); root.remove(oldName); put(root,newName,values);
        JSONArray order = root.optJSONArray("playlistOrder"); if (order != null) for (int i = 0; i < order.length(); i++) if (oldName.equals(order.optString(i))) try { order.put(i, newName); } catch (Exception ignored) {} put(root,"playlistOrder",order == null ? new JSONArray().put(newName) : order); write(c, root); return true;
    }
    public static synchronized void deletePlaylist(Context c, String name) { JSONObject root = read(c); root.remove(cleanName(name)); JSONArray order = root.optJSONArray("playlistOrder"); if (order != null) { JSONArray n = new JSONArray(); for (int i=0;i<order.length();i++) if (!cleanName(name).equals(order.optString(i))) n.put(order.optString(i)); put(root,"playlistOrder",n); } write(c, root); }
    public static synchronized void add(Context c, String name, Collection<String> uris) { update(c, name, uris, true); }
    public static synchronized void remove(Context c, String name, Collection<String> uris) { update(c, name, uris, false); }
    private static void update(Context c, String name, Collection<String> uris, boolean add) { JSONObject root = read(c); name = cleanName(name); if (!(root.opt(name) instanceof JSONArray)) return; LinkedHashSet<String> set = arrayToSet(root.optJSONArray(name)); if (add) set.addAll(uris); else set.removeAll(uris); put(root,name,setToArray(set)); write(c, root); }
    public static synchronized void prune(Context c, Collection<String> availableUris) {
        HashSet<String> allowed = new HashSet<>(availableUris); JSONObject root = read(c); LinkedHashSet<String> f = arrayToSet(root.optJSONArray("favorites")); f.retainAll(allowed); put(root,"favorites",setToArray(f)); for (String n : playlistNames(c)) { LinkedHashSet<String> p = arrayToSet(root.optJSONArray(n)); p.retainAll(allowed); put(root,n,setToArray(p)); } write(c, root);
    }
    private static void put(JSONObject root,String key,Object value){try{root.put(key,value);}catch(Exception ignored){}}
    private static boolean reserved(String name){return "favorites".equals(name)||"playlistOrder".equals(name)||"@favorites".equals(name);}
    private static String cleanName(String name) { return name == null ? "" : name.trim().replaceAll("[\\\\/:*?\"<>|]", "_"); }
}
