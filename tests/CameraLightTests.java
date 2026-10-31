package org.simpleweather.app;

/** Exercise reordered Camera2 callbacks, discarded frames and distinct exposure profiles. */
public final class CameraLightTests {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        CameraLightSamples s=new CameraLightSamples();
        s.metadata(10,0,200000000,1000,1);
        check(s.get(0)==null,"Metadata alone is not a brightness measurement");
        s.image(20,22,100);s.image(10,180,101);
        check(s.get(0).brightness==180&&s.get(1)==null,"Out-of-order image matches its own metadata");
        s.metadata(20,1,5000000,50,1);
        check(s.get(1).brightness==22&&s.get(1).iso==50&&s.get(1).exposureNanos==5000000,"Image-first outdoor callback retains actual parameters");
        check(s.get(0).brightness==180&&s.get(0).iso==1000,"Profiles do not overwrite each other");
        s.metadata(30,0,25000000,640,1);s.image(30,99,200);
        s.metadata(25,0,200000000,1000,1);s.image(25,255,201);
        check(s.get(0).timestamp==30&&s.get(0).brightness==99,"Delayed old captures cannot replace a newer measurement");
        s.metadata(40,1,5000000,50,3);s.image(40,33,202);
        check(s.get(1).timestamp==20,"Automatic exposure cannot be mislabeled as outdoor manual exposure");
        s.image(50,Float.NaN,203);s.metadata(50,0,10,100,1);
        check(s.get(0).timestamp==30,"Reject invalid brightness");
        s.clear();check(s.get(0)==null&&s.get(1)==null,"Stopping clears stale measurements");
        s.metadata(60,2,10000000,100,2);s.image(60,100,300);
        check(s.get(2).mode==2&&s.get(0)==null&&s.get(1)==null,"Automatic fallback has its own bucket");
        s.metadata(70,1,5000000,50,1);
        for(int i=100;i<160;i++)s.image(i,10,400);
        s.image(70,77,401);check(s.get(1)==null,"Evicted metadata never mislabels a late frame");
        check(CameraExposure.defaultIso(0)==1000&&CameraExposure.defaultMs(0)==200,"Indoor defaults retained");
        check(CameraExposure.defaultIso(1)==50&&CameraExposure.defaultMs(1)==5,"Outdoor defaults");
        check(CameraExposure.iso(0,1)==50&&CameraExposure.milliseconds(-1,1)==5,"Invalid outdoor preferences use outdoor defaults");
        check(CameraExposure.isoKey(0).equals("status_camera_iso")&&CameraExposure.msKey(0).equals("status_camera_exposure_ms"),"Legacy parameters migrate without loss");
        System.out.println("Camera light: "+checks+" behavior checks passed");
    }
}
