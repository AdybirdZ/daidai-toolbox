package org.daidai.toolbox;

import java.util.*;

/** Two fixed label positions only; process higher sky points before lower ones. */
public final class SkyLabels {
    private SkyLabels(){}
    public static final class Box {
        public final float left,top,right,bottom;
        public Box(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
        public boolean intersects(Box b){return left<b.right&&right>b.left&&top<b.bottom&&bottom>b.top;}
    }
    public static final class Candidate {
        public final String id;public final float x,y,width,ascent,descent;public final double magnitude;public final boolean alwaysShow;
        public Candidate(String id,float x,float y,float width,float ascent,float descent){this(id,x,y,width,ascent,descent,Double.NaN,false);}
        public Candidate(String id,float x,float y,float width,float ascent,float descent,double magnitude,boolean alwaysShow){this.id=id;this.x=x;this.y=y;this.width=width;this.ascent=ascent;this.descent=descent;this.magnitude=magnitude;this.alwaysShow=alwaysShow;}
    }
    public static final class Placed {
        public final Candidate candidate;public final float baseline;public final Box box;
        public Placed(Candidate c,float y,Box b){candidate=c;baseline=y;box=b;}
    }
    public static List<Placed> place(List<Candidate> candidates,List<Box> reserved,float width,float height,float gap,float padX,float padY){
        List<Candidate> sorted=new ArrayList<>(candidates);sorted.sort(Comparator.comparingDouble((Candidate c)->c.y).thenComparing(c->c.id));
        return placeInOrder(sorted,reserved,width,height,gap,padX,padY);
    }
    /** Keep the old fixed placement whenever every label fits. Under crowding, reveal brighter labels first as zoom increases. */
    public static List<Placed> placeAdaptive(List<Candidate> candidates,List<Box> reserved,float width,float height,float gap,float padX,float padY,double magnitudeLimit){
        List<Placed> complete=place(candidates,reserved,width,height,gap,padX,padY);if(complete.size()==candidates.size())return complete;
        List<Candidate> visible=new ArrayList<>();
        for(Candidate candidate:candidates)if(candidate.alwaysShow||Double.isFinite(candidate.magnitude)&&candidate.magnitude<=magnitudeLimit+1e-9)visible.add(candidate);
        visible.sort(Comparator.comparing((Candidate c)->!c.alwaysShow).thenComparingDouble(c->Double.isFinite(c.magnitude)?c.magnitude:Double.POSITIVE_INFINITY).thenComparing(c->c.id));
        return placeInOrder(visible,reserved,width,height,gap,padX,padY);
    }
    /** Keep label bounds inside a circular sky-chart viewport instead of clipping text at its edge. */
    public static List<Placed> placeAdaptive(List<Candidate> candidates,List<Box> reserved,float width,float height,float gap,float padX,float padY,double magnitudeLimit,float centerX,float centerY,float radius){
        List<Candidate> sorted=new ArrayList<>(candidates);sorted.sort(Comparator.comparingDouble((Candidate c)->c.y).thenComparing(c->c.id));
        List<Placed> complete=placeInOrder(sorted,reserved,width,height,gap,padX,padY,centerX,centerY,radius);if(complete.size()==candidates.size())return complete;
        List<Candidate> visible=new ArrayList<>();
        for(Candidate candidate:candidates)if(candidate.alwaysShow||Double.isFinite(candidate.magnitude)&&candidate.magnitude<=magnitudeLimit+1e-9)visible.add(candidate);
        visible.sort(Comparator.comparing((Candidate c)->!c.alwaysShow).thenComparingDouble(c->Double.isFinite(c.magnitude)?c.magnitude:Double.POSITIVE_INFINITY).thenComparing(c->c.id));
        return placeInOrder(visible,reserved,width,height,gap,padX,padY,centerX,centerY,radius);
    }
    private static List<Placed> placeInOrder(List<Candidate> candidates,List<Box> reserved,float width,float height,float gap,float padX,float padY){
        return placeInOrder(candidates,reserved,width,height,gap,padX,padY,Float.NaN,Float.NaN,Float.NaN);
    }
    private static List<Placed> placeInOrder(List<Candidate> candidates,List<Box> reserved,float width,float height,float gap,float padX,float padY,float centerX,float centerY,float radius){
        List<Box> used=new ArrayList<>(reserved);List<Placed> result=new ArrayList<>();
        for(Candidate c:candidates)for(float baseline:new float[]{c.y-gap,c.y+gap-c.ascent-c.descent}){
            Box b=new Box(c.x-c.width/2-padX,baseline+c.ascent-padY,c.x+c.width/2+padX,baseline+c.descent+padY);
            boolean fits=b.left>=0&&b.top>=0&&b.right<=width&&b.bottom<=height;
            if(Float.isFinite(radius))fits&=insideCircle(b,centerX,centerY,radius);
            for(Box other:used)if(b.intersects(other)){fits=false;break;}
            if(fits){used.add(b);result.add(new Placed(c,baseline,b));break;}
        }
        return result;
    }
    private static boolean insideCircle(Box b,float cx,float cy,float radius){
        float left=b.left-cx,right=b.right-cx,top=b.top-cy,bottom=b.bottom-cy;
        return left*left+top*top<=radius*radius&&right*right+top*top<=radius*radius&&left*left+bottom*bottom<=radius*radius&&right*right+bottom*bottom<=radius*radius;
    }
}
