package com.gk.chartpredictor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Experimental sketch based on pixels, NOT a statistical market price forecast.
 * Everything here is in relative pixel-height units. There is no price axis,
 * volume series, timestamp, complete/incomplete-candle flag, or backtest.
 */
public final class ScreenshotScenario {
    private ScreenshotScenario(){}
    private static double median(double[] data,int n){
        if(n<=0)return 0;
        double[] a=Arrays.copyOf(data,n);
        Arrays.sort(a);
        int m=n/2;
        return n%2==0?(a[m-1]+a[m])*.5:a[m];
    }
    private static double clamp(double v,double a,double b){
        return Math.min(b,Math.max(a,v));
    }
    public static ChartAnalysis.Result build(
        List<ChartAnalysis.Candle> candidates,
        int rejectedColorRegions,int horizon,String timeframe,boolean showGuess
    ){
        double[] ranges=new double[candidates.size()],bodies=new double[candidates.size()];
        for(int i=0;i<candidates.size();i++){
            ChartAnalysis.Candle c=candidates.get(i);
            ranges[i]=c.high-c.low;
            bodies[i]=Math.abs(c.close-c.open);
        }
        double medRange=Math.max(1.0,median(ranges,ranges.length));
        double medBody=Math.max(.6,median(bodies,bodies.length));
        List<ChartAnalysis.Candle> filtered=new ArrayList<>();
        int suspect=0;
        // Reject extreme bars of a different scale: most often mistaken axes,
        // drawings, indicators, grid markers or joined candle bodies. These
        // could also be genuine rare market bars: don't present them as verified.
        double rangeCap=Math.max(26.0,medRange*3.8);
        double bodyCap=Math.max(20.0,medBody*4.8);
        for(ChartAnalysis.Candle c:candidates){
            if(c.high-c.low>rangeCap || Math.abs(c.close-c.open)>bodyCap){
                suspect++;
            }else{
                filtered.add(c);
            }
        }
        int n=filtered.size();
        if(n>90)filtered=new ArrayList<>(filtered.subList(n-90,n));
        n=filtered.size();
        ChartAnalysis.Candle[] actual=filtered.toArray(new ChartAnalysis.Candle[0]);
        StringBuilder report=new StringBuilder();
        report.append("SCREENSHOT WHAT-IF ANALYSIS — EXPERIMENTAL\n");
        report.append("Timeframe chosen: ").append(timeframe)
              .append(" · Requested future candles: ").append(horizon).append("\n");
        report.append("Approximate candidates: ").append(candidates.size())
              .append(" · Suspicious oversized bars excluded: ").append(suspect)
              .append(" · Color regions rejected: ").append(rejectedColorRegions).append("\n");
        report.append("Usable visual candle candidates: ").append(n).append("\n\n");
        if(n<25){
            report.append("INSUFFICIENT RELIABLE IMAGE DATA. No future candles will be drawn. ")
                  .append("Crop closer to the price chart or import OHLCV CSV. ");
            report.append("The image contains too few usable candle candidates to produce even a useful visual what-if sketch.");
            return new ChartAnalysis.Result(actual,new ChartAnalysis.Candle[0],report.toString());
        }
        report.append("VISUAL CANDLE DETECTION IS APPROXIMATE. ");
        report.append("Outlier bars are excluded from the reconstruction, not asserted to be invalid trades. ");
        report.append("No actual price, volume, order-flow, trained AI model or measured prediction skill is available.\n\n");
        if(!showGuess){
            report.append("Experimental screenshot scenario is OFF. Turn it on to see a purely illustrative next-candle sketch; ");
            report.append("alternatively import OHLCV CSV for model evaluation and error estimates.");
            return new ChartAnalysis.Result(actual,new ChartAnalysis.Candle[0],report.toString());
        }
        double[] closes=new double[n],delta=new double[n-1];
        for(int i=0;i<n;i++)closes[i]=filtered.get(i).close;
        for(int i=1;i<n;i++)delta[i-1]=closes[i]-closes[i-1];
        double[] recent=Arrays.copyOfRange(delta,Math.max(0,delta.length-16),delta.length);
        double driftMed=median(recent,recent.length);
        double[] absDev=new double[recent.length];
        for(int i=0;i<recent.length;i++)absDev[i]=Math.abs(recent[i]-driftMed);
        double robustVol=Math.max(.7,Math.max(.32*medRange,1.4826*median(absDev,absDev.length)));
        // A limited, explainable visual scenario: median change + smoothed trend,
        // shrunk toward no-change. The trend is capped relative to visual noise.
        double trendShort=(closes[n-1]-closes[n-7])/6.0;
        double trendMid=(closes[n-1]-closes[n-16])/15.0;
        double step=clamp(.38*driftMed+.28*trendShort+.16*trendMid,
                           -.38*robustVol,.38*robustVol);
        String tilt=Math.abs(step)<.045*robustVol?"MIXED / FLAT":
                step>0?"UPWARD VISUAL TILT":"DOWNWARD VISUAL TILT";
        ChartAnalysis.Candle[] projection=new ChartAnalysis.Candle[horizon];
        double[] upper=new double[horizon],lower=new double[horizon];
        double previous=closes[n-1];
        for(int h=1;h<=horizon;h++){
            double next=previous+step*Math.pow(.76,h-1);
            // Broad what-if ranges are NOT 80% or 95% prediction intervals.
            double width=robustVol*(1.6*Math.sqrt(h)+.30*h);
            lower[h-1]=Math.max(.001,next-width);
            upper[h-1]=next+width;
            double wick=Math.max(.35*medRange,.4*robustVol);
            double high=Math.max(previous,next)+wick;
            double low=Math.max(.001,Math.min(previous,next)-wick);
            projection[h-1]=new ChartAnalysis.Candle(previous,high,low,next,Double.NaN);
            previous=next;
        }
        report.append("VISUAL TILT: ").append(tilt).append("\n");
        report.append("Displayed next ").append(horizon).append(" candles: conditional illustrative guess ONLY.\n");
        report.append("Blue range: alternative up/down drawing envelope, not a probability interval.\n\n");
        report.append("THIS IS NOT A VERIFIED FORECAST. It is a shape-based extrapolation in relative pixel coordinates, ")
              .append("and may differ greatly from actual market movements. ")
              .append("Even a visually convincing pattern is not proof of predictive accuracy. ")
              .append("Do not use this screenshot sketch alone to enter an option/futures trade.\n\n");
        report.append("For real indicator values, price targets and holdout error metrics, import at least ~200 chronological OHLCV candles from the same market/timeframe.");
        return new ChartAnalysis.Result(actual,projection,report.toString(),lower,upper);
    }
}
