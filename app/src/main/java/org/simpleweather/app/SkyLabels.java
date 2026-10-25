package org.simpleweather.app;

import java.util.*;

/** Two fixed label positions only; process higher sky points before lower ones. */
public final class SkyLabels {
    private SkyLabels(){}
    public static final class Box {
        public final float left,top,right,bottom;
        public Box(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
        boolean intersects(Box b){return left<b.right&&right>b.left&&top<b.bottom&&bottom>b.top;}
    }
    public static final class Candidate {
        public final String id;public final float x,y,width,ascent,descent;
        public Candidate(String id,float x,float y,float width,float ascent,float descent){this.id=id;this.x=x;this.y=y;this.width=width;this.ascent=ascent;this.descent=descent;}
    }
    public static final class Placed {
        public final Candidate candidate;public final float baseline;public final Box box;
        Placed(Candidate c,float y,Box b){candidate=c;baseline=y;box=b;}
    }
    public static List<Placed> place(List<Candidate> candidates,List<Box> reserved,float width,float height,float gap,float padX,float padY){
        List<Candidate> sorted=new ArrayList<>(candidates);sorted.sort(Comparator.comparingDouble((Candidate c)->c.y).thenComparing(c->c.id));
        List<Box> used=new ArrayList<>(reserved);List<Placed> result=new ArrayList<>();
        for(Candidate c:sorted)for(float baseline:new float[]{c.y-gap,c.y+gap-c.ascent-c.descent}){
            Box b=new Box(c.x-c.width/2-padX,baseline+c.ascent-padY,c.x+c.width/2+padX,baseline+c.descent+padY);
            boolean fits=b.left>=0&&b.top>=0&&b.right<=width&&b.bottom<=height;
            for(Box other:used)if(b.intersects(other)){fits=false;break;}
            if(fits){used.add(b);result.add(new Placed(c,baseline,b));break;}
        }
        return result;
    }
}
