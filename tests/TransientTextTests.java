package org.simpleweather.app;

/** Behavioral checks for notice punctuation without altering detailed diagnostics. */
public final class TransientTextTests {
    private static int checks;
    private static void expect(String input,String expected){checks++;String actual=TransientText.notice(input);if(!expected.equals(actual))throw new AssertionError("Expected ["+expected+"] but got ["+actual+"]");}
    public static void main(String[] args){
        expect("设置已保存。","设置已保存");expect("Settings saved.","Settings saved");
        expect("正在载入...","正在载入...");expect("Loading…","Loading…");
        expect("失败：\n详细信息。","失败：\n详细信息。");
        expect("输入值为 3.5","输入值为 3.5");expect("Failed: track.mp3","Failed: track.mp3");
        expect("重试？","重试？");expect("Done.  ","Done  ");expect(null,"");
        String detail=String.join("",java.util.Collections.nCopies(170,"x"))+".";expect(detail,detail);
        System.out.println("PASS: "+checks+" transient notice checks");
    }
}
