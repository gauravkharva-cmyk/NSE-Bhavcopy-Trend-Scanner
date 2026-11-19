package com.gk.chartpredictor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.InputStream;

/** Offline learning gallery: original synthetic illustrations, not real trade setups. */
public final class PatternLibrary {
    private PatternLibrary() {}
    public static final String[] NAMES={
        "Double Top", "Double Bottom", "Head and Shoulders", "Inverse Head and Shoulders",
        "Ascending Triangle", "Descending Triangle", "Symmetrical Triangle",
        "Bull Flag", "Bear Flag", "Rising Wedge", "Falling Wedge",
        "Cup and Handle", "Rounding Bottom", "Bullish Rectangle", "Bearish Rectangle",
        "Ascending Channel", "Descending Channel", "Doji", "Hammer", "Shooting Star",
        "Bullish Engulfing", "Bearish Engulfing", "Morning Star", "Evening Star"
    };
    private static final String[] EXPLAIN={
        "A potential bearish reversal after two similar highs. Confirmation requires a break below the neckline, ideally with strong volume. Two highs alone are insufficient.",
        "A potential bullish reversal after two similar lows. Look for a breakout above the middle swing high, not merely the second low.",
        "Three swing peaks with the middle peak tallest. A neckline breakdown may signal reversal. Check trend context and avoid assuming confirmation before the break.",
        "Three swing troughs with the middle trough lowest. The neckline breakout can confirm a potential bullish reversal, subject to false breakouts.",
        "Relatively horizontal resistance and rising swing lows. Often bullish if price closes beyond resistance with participation; breakdown remains possible.",
        "Relatively flat support and lower swing highs. Often bearish on confirmed breakdown, but can produce traps near support.",
        "Converging swing highs and lows compress the range. Breakout direction is not known in advance; use confirmation rather than guessing.",
        "A strong price advance, then a narrow countertrend consolidation. A sustained break above the flag may continue the prior trend.",
        "A sharp selloff followed by a narrow corrective channel. A breakdown may resume the original downtrend; reversals remain possible.",
        "Higher highs and higher lows converging upward. It can lose upward momentum, but the pattern itself is not an automatic sell signal.",
        "Lower highs and lows converge downward. May foreshadow a positive reversal after breakout; beware of repeated false breaks.",
        "Rounded decline/recovery followed by a smaller handle pullback. Classical continuation setup; requires breakout confirmation and good chart context.",
        "Gradually softening downtrend turning into rising prices. This is a visual tendency, not a proven turning point by itself.",
        "Repeated highs and lows in a horizontal range after a rise. A break through resistance may signal continuation; a breakdown invalidates that bias.",
        "Repeated highs and lows in a horizontal range after a fall. A break below support may signal continuation; an upside break may invalidate the setup.",
        "Rising parallel trend boundaries. Shows upward progression but not guaranteed continuation; use channel boundaries and trend strength.",
        "Falling parallel trend boundaries. Downward progression remains in force until evidence of a break or reversal develops.",
        "A candle with a very small real body. Indicates indecision; interpretation requires prior trend, surrounding candles, and volume.",
        "Small body with a long lower wick and short upper wick, often after a decline. Possible rejection of lower prices; subsequent confirmation matters.",
        "Small body near candle low and a long upper shadow, often after an advance. Possible rejection of higher prices; not an automatic short trade.",
        "A larger bullish real body engulfs the prior bearish real body. Stronger signal near support or after a decline; false signals occur.",
        "A larger bearish real body engulfs the prior bullish real body. Context and confirmation are needed before interpreting reversal strength.",
        "Three-candle bullish reversal: long bearish candle, smaller indecision candle, then a strong bullish candle. Examine gaps and candle placement.",
        "Three-candle bearish reversal: long bullish candle, smaller indecision candle, then a strong bearish candle. Require appropriate trend context."
    };
    private static int dp(Activity a,float n){return (int)(n*a.getResources().getDisplayMetrics().density+.5f);}
    private static GradientDrawable panel(Activity a){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.rgb(16,29,46));
        bg.setCornerRadius(dp(a,18));
        return bg;
    }
    private static TextView text(Activity a,String value,int size,int color){
        TextView t=new TextView(a);t.setText(value);t.setTextColor(color);t.setTextSize(size);return t;
    }
    public static void show(Activity a){
        new AlertDialog.Builder(a)
            .setTitle("OFFLINE PATTERN ATLAS  ·  24 EXAMPLES")
            .setItems(NAMES,(dialog,index)->showPattern(a,index))
            .setNegativeButton("Close",null)
            .show();
    }
    private static void showPattern(Activity a,int idx){
        ScrollView scroll=new ScrollView(a);
        scroll.setBackgroundColor(Color.rgb(9,16,29));
        LinearLayout col=new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(a,16),dp(a,18),dp(a,16),dp(a,18));
        scroll.addView(col);
        TextView title=text(a,(idx+1)+"/"+NAMES.length+"  "+NAMES[idx],21,Color.WHITE);
        col.addView(title);
        TextView sub=text(a,"Illustrative pattern diagram · generated offline",12,Color.rgb(156,179,198));
        sub.setPadding(0,dp(a,8),0,dp(a,14));col.addView(sub);

        ImageView img=new ImageView(a);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setAdjustViewBounds(true);
        img.setBackground(panel(a));
        try(InputStream in=a.getAssets().open(String.format(java.util.Locale.US,"patterns/pattern_%02d.png",idx+1))){
            Bitmap b=BitmapFactory.decodeStream(in);
            if(b==null)throw new Exception("Invalid gallery image");
            img.setImageBitmap(b);
        }catch(Exception e){
            img.setImageDrawable(null);
            col.addView(text(a,"Unable to open illustration: "+e.getMessage(),14,Color.RED));
        }
        col.addView(img,new LinearLayout.LayoutParams(-1,dp(a,265)));
        TextView description=text(a,EXPLAIN[idx],16,Color.rgb(222,230,241));
        description.setLineSpacing(dp(a,4),1f);
        description.setPadding(0,dp(a,18),0,dp(a,12));col.addView(description);
        TextView notice=text(a,"Educational example, not market data. Pattern appearance does not establish probability or a future price. Verify using actual OHLCV data.",13,Color.rgb(150,182,201));
        notice.setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,12));notice.setBackground(panel(a));
        col.addView(notice);
        AlertDialog d=new AlertDialog.Builder(a).setView(scroll).setPositiveButton("Close",null).create();
        d.show();
    }
}
