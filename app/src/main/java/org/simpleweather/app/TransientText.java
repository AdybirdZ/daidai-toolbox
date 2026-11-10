package org.simpleweather.app;

/** Display normalization for short, single-line notices; never rewrites detailed errors. */
final class TransientText {
    private TransientText(){}
    static String notice(String message){
        if(message==null)return "";
        if(message.length()>160||message.indexOf('\n')>=0||message.indexOf('\r')>=0)return message;
        int end=message.length();while(end>0&&Character.isWhitespace(message.charAt(end-1)))end--;
        if(end==0)return message;
        char last=message.charAt(end-1);
        if(last!='。'&&last!='.')return message;
        // An ellipsis is meaningful progress/error text, rather than a sentence stop.
        if(last=='.'&&end>1&&message.charAt(end-2)=='.')return message;
        return message.substring(0,end-1)+message.substring(end);
    }
}
