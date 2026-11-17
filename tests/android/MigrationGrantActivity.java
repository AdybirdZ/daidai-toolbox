package org.simpleweather.qa;
import android.app.Activity;import android.content.Intent;import android.net.Uri;import android.os.Bundle;
/** Synthetic provider permission grant, never shipped in the production app. */
public final class MigrationGrantActivity extends Activity {public void onCreate(Bundle b){super.onCreate(b);String target=getIntent().getStringExtra("target");if("org.simpleweather.app".equals(target)||"org.daidai.prototype.music".equals(target))grantUriPermission(target,Uri.parse("content://org.simpleweather.migrationqa.documents"),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);finish();}}
