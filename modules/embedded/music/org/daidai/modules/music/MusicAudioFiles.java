package org.daidai.modules.music;

import java.util.Locale;

/** Shared eligibility check before copying or passing a document to audio metadata readers. */
public final class MusicAudioFiles {
    private MusicAudioFiles() {}
    public static boolean accepts(String name,String mime) {
        String type=mime==null?"":mime.toLowerCase(Locale.ROOT).split(";",2)[0].trim();
        String file=name==null?"":name.toLowerCase(Locale.ROOT);
        int dot=file.lastIndexOf('.');String extension=dot<0?"":file.substring(dot+1);
        // Sidecar lyrics, artwork and documents must never become songs, even with a bad provider MIME.
        if(java.util.Arrays.asList("lrc","srt","txt","pdf","jpg","jpeg","png","gif","webp","bmp","svg","json","xml","zip","rar","7z","apk","ddapp","mkv","webm","mov","avi").contains(extension))return false;
        if(type.startsWith("image/")||type.startsWith("text/")||type.startsWith("video/")||type.equals("application/pdf"))return false;
        return type.startsWith("audio/")||java.util.Arrays.asList("mp3","m4a","aac","wav","flac","ogg","opus","amr","aiff","aif","wma","mid","midi","ape","m4b").contains(extension);
    }
}
