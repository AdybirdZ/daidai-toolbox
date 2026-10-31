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
        String[] regions={UiText.get("map_001"),UiText.get("map_002"),UiText.get("astronomy_027"),UiText.get("map_003"),UiText.get("map_004"),UiText.get("map_005"),UiText.get("astronomy_031"),UiText.get("astronomy_033")};String[] paths={"chinaall","huabei","dongbei","huadong","huazhong","huanan","xinan","xibei"};int region=Math.max(0,Math.min(7,a.preferences.getInt("radar_region",0)));
        String[] labels={UiText.get("map_006")+regions[region],UiText.get("map_007"),UiText.get("map_008"),UiText.get("map_009"),UiText.get("map_010"),UiText.get("map_011")};String base="https://www.nmc.cn/publish/";urls=new String[]{base+"radar/"+paths[region]+".html",base+"observations/24hour-precipitation.html",base+"precipitation/1-day.html",base+"observations/hourly-temperature.html",base+"satellite/fy4b-visible.htm",base+"observations/china/dm/weatherchart-h000.htm"};
        LinearLayout controls=a.column();controls.setPadding(a.dp(10),a.dp(8),a.dp(10),a.dp(8));controls.setBackground(a.shape(a.WHITE,14));controls.addView(a.text(UiText.get("map_012"),13,a.TITLE,true));a.gap(controls,6);
        String[] captions={UiText.get("map_006")+regions[region],UiText.get("map_013"),UiText.get("map_014"),UiText.get("map_015"),UiText.get("map_010"),UiText.get("map_011")};
        for(int line=0;line<2;line++){
            LinearLayout buttons=a.row();
            for(int n=line*3;n<line*3+3;n++){final int product=n;TextView pick=mapButton(captions[n],()->select(product));pick.setContentDescription(labels[n]);choices[n]=pick;buttons.addView(pick,cell());}
            controls.addView(buttons);
        }
        LinearLayout row=a.row();TextView note=a.text(UiText.get("map_016"),11,a.MUTED,false);
        LinearLayout.LayoutParams noteParams=new LinearLayout.LayoutParams(0,-2,2);noteParams.setMargins(a.dp(2),a.dp(2),a.dp(6),a.dp(2));row.addView(note,noteParams);
        TextView browser=a.text(UiText.get("map_017")+" ↗",12,a.BLUE,false);browser.setMinHeight(a.dp(48));browser.setPadding(a.dp(8),a.dp(6),a.dp(8),a.dp(6));browser.setGravity(Gravity.CENTER);a.markLink(browser);a.tap(browser,android.graphics.Color.TRANSPARENT,8,()->{if(url!=null)a.open(url);else a.toast(UiText.get("map_018"));});row.addView(browser,new LinearLayout.LayoutParams(-2,-2));controls.addView(row);
        a.body.addView(controls);a.gap(a.body,6);status=a.text(UiText.get("map_019"),12,a.MUTED,false);a.body.addView(status);a.gap(a.body,4);
        FrameLayout frame=new FrameLayout(a);frame.setPadding(a.dp(1),a.dp(1),a.dp(1),a.dp(1));frame.setBackground(a.shape(a.WHITE,0));a.body.addView(frame,new LinearLayout.LayoutParams(-1,0,1));web=new WebView(a);frame.addView(web,new FrameLayout.LayoutParams(-1,-1));WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setGeolocationEnabled(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView v,int p){if(!failed){status.setText(p<100?UiText.get("map_020")+p+"%":UiText.get("earthquake_053")+Uri.parse(url).getHost()+UiText.get("map_021"));}}});
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){if(!r.isForMainFrame())return false;String target=r.getUrl().toString();if(OfficialPageActivity.trusted(target))return false;if("http".equals(r.getUrl().getScheme())){String secure=target.replaceFirst("^http:","https:");if(OfficialPageActivity.trusted(secure)){v.loadUrl(secure);return true;}}if("https".equals(r.getUrl().getScheme())||"http".equals(r.getUrl().getScheme()))a.open(target);return true;}@Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error){if(r.isForMainFrame()){failed=true;status.setText(UiText.get("map_022"));}}@Override public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();failed=true;status.setText(UiText.get("map_023"));}});
        if(loadInitial)select(Math.max(0,Math.min(5,a.preferences.getInt("map_product",0))));else status.setText(UiText.get("map_024"));
    }
    private LinearLayout.LayoutParams cell(){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,a.dp(48),1);params.setMargins(a.dp(2),a.dp(2),a.dp(2),a.dp(2));return params;}
    private TextView mapButton(String label,Runnable click){TextView button=a.button(label,false,click);button.setTextSize(12);button.setPadding(a.dp(3),a.dp(6),a.dp(3),a.dp(6));button.setMaxLines(2);button.setLineSpacing(0,1);return button;}
    private void select(int product){url=urls[product];a.preferences.edit().putInt("map_product",product).apply();for(int n=0;n<choices.length;n++){final int id=n;choices[n].setTextColor(n==product?a.buttonPrimaryText():a.buttonSecondaryText());choices[n].setTag(n==product?"theme-primary":"theme-secondary");a.tap(choices[n],n==product?a.buttonPrimaryColor():a.buttonSecondaryColor(),12,()->select(id));}failed=false;status.setText(UiText.get("map_025"));web.loadUrl(url);}

    void pause(){if(web!=null&&!destroyed)web.onPause();}
    void resume(){if(web!=null&&!destroyed)web.onResume();}
    void destroy(){if(web!=null&&!destroyed){destroyed=true;web.stopLoading();web.destroy();}}
}
