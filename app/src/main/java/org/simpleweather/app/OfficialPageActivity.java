package org.simpleweather.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.*;
import android.graphics.Color;
import android.view.*;
import android.webkit.*;
import android.widget.*;

/** Displays the publisher's complete original page; no scraping, proxy or image republishing. */
public final class OfficialPageActivity extends Activity {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(AppLanguage.wrap(base));}
    private int theme(){return UiColors.seed(this,UiColors.BUTTON);}
    private void buttonColor(Button b){b.setTextColor(ThemeColors.dark(theme()));b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeColors.pale(theme())));}
    private WebView web;
    private TextView progress;
    static boolean trusted(String url){try{Uri u=Uri.parse(url);String h=u.getHost();return "https".equalsIgnoreCase(u.getScheme())&&h!=null&&(h.equals("nmc.cn")||h.endsWith(".nmc.cn")||h.equals("weather.com.cn")||h.endsWith(".weather.com.cn"));}catch(Exception e){return false;}}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);String url=getIntent().getStringExtra("url");if(!trusted(url)){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xFFF5F7FB);root.setFitsSystemWindows(true);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());root.setPadding(i.left,i.top,i.right,i.bottom);}return insets;});
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);Button back=new Button(this);back.setText(UiText.get("action_back"));buttonColor(back);back.setOnClickListener(v->finish());bar.addView(back);TextView title=new TextView(this);title.setText(getIntent().getStringExtra("title"));title.setTextSize(16);title.setTextColor(UiColors.ink(this,UiColors.TITLE));bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView browser=new TextView(this);browser.setText(UiText.get("map_017")+" ↗");UiColors.mark(browser,UiColors.LINK);float density=getResources().getDisplayMetrics().density;browser.setMinHeight(Math.round(48*density));browser.setPadding(Math.round(12*density),Math.round(8*density),Math.round(12*density),Math.round(8*density));browser.setGravity(Gravity.CENTER);browser.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(web.getUrl()==null?url:web.getUrl())));}catch(Exception e){}});bar.addView(browser);root.addView(bar);
        TextView source=new TextView(this);source.setText(UiText.get("official_page_001")+Uri.parse(url).getHost()+UiText.get("official_page_002"));source.setTextSize(11);source.setTextColor(UiColors.ink(this,UiColors.NOTE));source.setPadding(16,8,16,8);root.addView(source);
        progress=new TextView(this);progress.setTextColor(UiColors.ink(this,UiColors.NOTE));progress.setText(UiText.get("map_019"));progress.setPadding(16,8,16,8);root.addView(progress);
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setGeolocationEnabled(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView v,int p){if(p<100){progress.setVisibility(View.VISIBLE);progress.setText(UiText.get("official_page_003")+p+"%");}else progress.setVisibility(View.GONE);}});
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){if(!req.isForMainFrame())return false;String target=req.getUrl().toString();if(trusted(target))return false;if("http".equals(req.getUrl().getScheme())){String secure=target.replaceFirst("^http:","https:");if(trusted(secure)){v.loadUrl(secure);return true;}}if("https".equals(req.getUrl().getScheme())||"http".equals(req.getUrl().getScheme())){try{startActivity(new Intent(Intent.ACTION_VIEW,req.getUrl()));}catch(Exception ignored){}}return true;}
            @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError error){if(req.isForMainFrame()){progress.setVisibility(View.VISIBLE);progress.setText(UiText.get("official_page_004"));}}
            @Override public void onReceivedSslError(WebView v,android.webkit.SslErrorHandler handler,android.net.http.SslError error){handler.cancel();progress.setVisibility(View.VISIBLE);progress.setText(UiText.get("official_page_005"));}
        });
        web.loadUrl(url);
    }
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onPause(){web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
