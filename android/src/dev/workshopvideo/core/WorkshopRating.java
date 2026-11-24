package dev.workshopvideo.core;

/** Workshop rating as Steam presents it: a 0–5 star band from the score, plus the raw vote counts. */
public final class WorkshopRating {
    /** Steam shows "not enough ratings" below this many votes. */
    public static final int MIN_VOTES=10;
    private WorkshopRating(){}

    public static boolean enough(int up,int down){return (long)Math.max(0,up)+Math.max(0,down)>=MIN_VOTES;}

    /** Stars 0–5 from Steam's normalized score (0..1); half stars are not shown by Steam. */
    public static int stars(float score,int up,int down){
        if(!enough(up,down)||!Float.isFinite(score))return 0;
        return Math.max(1,Math.min(5,Math.round(score*5)));
    }

    /** Five-point rating with one decimal (Steam's score x 5), or 0 when too few people voted. */
    public static float rating(float score,int up,int down){
        if(!enough(up,down)||!Float.isFinite(score))return 0;
        return Math.max(.5f,Math.min(5f,Math.round(Math.max(0,score)*50)/10f));
    }

    /** "4.6" for display next to a star. */
    public static String ratingText(float score,int up,int down){return String.format(java.util.Locale.ROOT,"%.1f",rating(score,up,down));}

    /** Share of up votes, 0–100, rounded down so 99.6% never reads as 100%. */
    public static int approval(int up,int down){
        long total=(long)Math.max(0,up)+Math.max(0,down);
        return total==0?0:(int)(Math.max(0,up)*100L/total);
    }

    /** Short count label: 999, 1.2万, 34万. */
    public static String count(long votes){
        if(votes<10000)return Long.toString(Math.max(0,votes));
        double tenThousands=votes/10000d;
        return (tenThousands<10?String.format(java.util.Locale.ROOT,"%.1f",Math.floor(tenThousands*10)/10):Long.toString((long)tenThousands))+"万";
    }
}
