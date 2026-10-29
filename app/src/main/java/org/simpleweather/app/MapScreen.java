package org.simpleweather.app;

import android.content.*;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;

/** Persistent native product selector above the publisher's complete, unmodified page. */
final class MapScreen {
    private final MainActivity a;
    private WebView web;
    private TextView status;
    private String url;
    private boolean destroyed,failed;
    private String[] urls;
    private final TextView[] choices=new TextView[6];
    MapScreen(MainActivity a){this.a=a;}
    void render(boolean loadInitial){
        String[] regions={"全国","华北","东北","华东","华中","华南","西南","西北"};String[] paths={"chinaall","huabei","dongbei","huadong","huazhong","huanan","xinan","xibei"};int region=Math.max(0,Math.min(7,a.preferences.getInt("radar_region",0)));
        String[] labels={"雷达 · "+regions[region],"全国日降水量","全国 24 小时降水预报","全国逐时气温","卫星云图","天气图"};String base="https://www.nmc.cn/publish/";urls=new String[]{base+"radar/"+paths[region]+".html",base+"observations/24hour-precipitation.html",base+"precipitation/1-day.html",base+"observations/hourly-temperature.html",base+"satellite/fy4b-visible.htm",base+"observations/china/dm/weatherchart-h000.htm"};
        LinearLayout controls=a.column();controls.setPadding(a.dp(10),a.dp(8),a.dp(10),a.dp(8));controls.setBackground(a.shape(a.WHITE,14));controls.addView(a.text("切换天气地图",13,a.INK,true));a.gap(controls,6);
        String[] captions={"雷达 · "+regions[region],"日降水量","降水预报","逐时气温","卫星云图","天气图"};
        for(int line=0;line<2;line++){
            LinearLayout buttons=a.row();
            for(int n=line*3;n<line*3+3;n++){final int product=n;TextView pick=mapButton(captions[n],()->select(product));pick.setContentDescription(labels[n]);choices[n]=pick;buttons.addView(pick,cell());}
            controls.addView(buttons);
        }
        LinearLayout row=a.row();TextView note=a.text("官方原页 · 页面可双指缩放",11,a.MUTED,false);
        LinearLayout.LayoutParams noteParams=new LinearLayout.LayoutParams(0,-2,2);noteParams.setMargins(a.dp(2),a.dp(2),a.dp(6),a.dp(2));row.addView(note,noteParams);
        TextView browser=mapButton("在浏览器中打开",()->{if(url!=null)a.open(url);else a.toast("请先选择天气图类型");});row.addView(browser,cell());controls.addView(row);
        a.body.addView(controls);a.gap(a.body,6);status=a.text("正在加载官方网站…",12,a.MUTED,false);a.body.addView(status);a.gap(a.body,4);
        FrameLayout frame=new FrameLayout(a);frame.setPadding(a.dp(1),a.dp(1),a.dp(1),a.dp(1));frame.setBackground(a.shape(a.WHITE,0));a.body.addView(frame,new LinearLayout.LayoutParams(-1,0,1));web=new WebView(a);frame.addView(web,new FrameLayout.LayoutParams(-1,-1));WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setGeolocationEnabled(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView v,int p){if(!failed){status.setText(p<100?"官方原页加载中 "+p+"%":"来源："+Uri.parse(url).getHost()+" · 原站版权及隐私规则适用");}}});
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){if(!r.isForMainFrame())return false;String target=r.getUrl().toString();if(OfficialPageActivity.trusted(target))return false;if("http".equals(r.getUrl().getScheme())){String secure=target.replaceFirst("^http:","https:");if(OfficialPageActivity.trusted(secure)){v.loadUrl(secure);return true;}}if("https".equals(r.getUrl().getScheme())||"http".equals(r.getUrl().getScheme()))a.open(target);return true;}@Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error){if(r.isForMainFrame()){failed=true;status.setText("官方网页暂时无法加载，请切换图种重试，或在浏览器中打开。");}}@Override public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();failed=true;status.setText("官网证书校验失败，请稍后重试。");}});
        if(loadInitial)select(Math.max(0,Math.min(5,a.preferences.getInt("map_product",0))));else status.setText("缓存已清除，点击上方图种重新加载官方原页。");
    }
    private LinearLayout.LayoutParams cell(){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,a.dp(48),1);params.setMargins(a.dp(2),a.dp(2),a.dp(2),a.dp(2));return params;}
    private TextView mapButton(String label,Runnable click){TextView button=a.button(label,false,click);button.setTextSize(12);button.setPadding(a.dp(3),a.dp(6),a.dp(3),a.dp(6));button.setMaxLines(2);button.setLineSpacing(0,1);return button;}
    private void select(int product){url=urls[product];a.preferences.edit().putInt("map_product",product).apply();for(int n=0;n<choices.length;n++){final int id=n;choices[n].setTextColor(n==product?a.WHITE:a.BLUE);a.tap(choices[n],n==product?a.BLUE:a.PALE,12,()->select(id));}failed=false;status.setText("正在加载官方原页…");web.loadUrl(url);}

    void pause(){if(web!=null&&!destroyed)web.onPause();}
    void resume(){if(web!=null&&!destroyed)web.onResume();}
    void destroy(){if(web!=null&&!destroyed){destroyed=true;web.stopLoading();web.destroy();}}
}
