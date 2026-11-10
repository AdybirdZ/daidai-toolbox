package org.daidai.prototype.qa;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Runs under the synthetic provider's UID so it can grant its fixture URIs to the player. */
public final class MusicMetadataGrantActivity extends Activity {
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        Uri root=Uri.parse("content://"+MusicMetadataDocuments.AUTHORITY);
        int access=Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
        String target=getIntent().getStringExtra("targetPackage");
        if("org.daidai.prototype.music".equals(target)){
            if(getIntent().getBooleanExtra("revoke",false))revokeUriPermission(target,root,access);
            else grantUriPermission(target,root,access);
        }
        finish();
    }
}
