package org.simpleweather.app;
import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
public final class TextTestLauncher {
    public static void main(String[] args)throws Exception{
        final Map<String,String> texts=new HashMap<>();final Map<String,List<String>> arrays=new HashMap<>();
        for(File file:new File(args[0]).listFiles((d,n)->n.endsWith(".xml"))){
            Document doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
            NodeList strings=doc.getElementsByTagName("string");for(int i=0;i<strings.getLength();i++){Element e=(Element)strings.item(i);texts.put(e.getAttribute("name"),unescape(e.getTextContent()));}
            NodeList lists=doc.getElementsByTagName("string-array");for(int i=0;i<lists.getLength();i++){Element e=(Element)lists.item(i);List<String> items=new ArrayList<>();NodeList children=e.getElementsByTagName("item");for(int n=0;n<children.getLength();n++)items.add(unescape(children.item(n).getTextContent()));arrays.put(e.getAttribute("name"),items);}
        }
        final Map<String,String> labels=new HashMap<>();List<String> raw=arrays.get("identity_keys"),display=arrays.get("identity_label_keys");for(int n=0;n<raw.size();n++)labels.put(texts.get(raw.get(n)),display.get(n));
        UiText.install(new UiText.Provider(){public String get(String key){String s=texts.get(key);if(s==null)throw new IllegalArgumentException(key);return s;}public String label(String id){String k=labels.get(id);return k==null?id:get(k);}});
        Class.forName(args[1]).getMethod("main",String[].class).invoke(null,(Object)Arrays.copyOfRange(args,2,args.length));
    }
    static String unescape(String text){if(text.startsWith("\"")&&text.endsWith("\""))text=text.substring(1,text.length()-1);return text.replace("\\n","\n").replace("\\r","\r").replace("\\t","\t").replace("\\'","'").replace("\\\"","\"").replace("\\\\","\\");}
}
