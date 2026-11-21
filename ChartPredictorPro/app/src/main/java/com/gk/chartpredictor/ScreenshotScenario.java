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
        // Historical visual analogs: compare the last 9 normalized candle changes
        // to earlier subsequences, then examine observed FOLLOWING candle shapes.
        // This is a resemblance experiment, not a backtested market predictor.
        double[] closes=new double[n],delta=new double[n];
        for(int i=0;i<n;i++)closes[i]=filtered.get(i).close;
        for(int i=1;i<n;i++)delta[i]=closes[i]-closes[i-1];
        final int lookback=9;
        double[] recent=Arrays.copyOfRange(delta,Math.max(1,n-20),n);
        double driftMedian=median(recent,recent.length);
        double[] absDeviation=new double[recent.length];
        for(int i=0;i<recent.length;i++)absDeviation[i]=Math.abs(recent[i]-driftMedian);
        double noise=Math.max(.9,Math.max(.28*medRange,1.4826*median(absDeviation,absDeviation.length)));
        double trendShort=(closes[n-1]-closes[n-7])/6.0;
        double trendMid=(closes[n-1]-closes[n-16])/15.0;
        double slope=clamp(.4*driftMedian+.4*trendShort+.2*trendMid,-.9*noise,.9*noise);

        // A complete analog needs earlier observations after the matching window.
        // Forecast horizons larger than available history use an explicitly
        // identified fallback, never silently fabricate a supposed analog.
        int longest=Math.min(horizon,Math.max(0,n-lookback-5));
        ArrayList<Integer> ends=new ArrayList<>();
        ArrayList<Double> distances=new ArrayList<>();
        for(int end=lookback+1;end<=n-1-longest;end++){
            double d=0;
            double pastNoise=0;
            for(int j=0;j<lookback;j++)pastNoise+=Math.abs(delta[end-j]);
            pastNoise=Math.max(1.0,pastNoise/lookback);
            for(int j=0;j<lookback;j++){
                double target=clamp(delta[n-1-j]/noise,-4,4);
                double candidate=clamp(delta[end-j]/pastNoise,-4,4);
                double err=target-candidate;
                d+=err*err*(j<4?1.25:0.7);
            }
            ends.add(end);
            distances.add(d);
        }
        int[] best=new int[Math.min(3,ends.size())];
        double[] bestScore=new double[best.length];
        Arrays.fill(best,-1);
        Arrays.fill(bestScore,Double.POSITIVE_INFINITY);
        for(int i=0;i<distances.size();i++){
            double distance=distances.get(i);
            for(int k=0;k<best.length;k++){
                if(distance<bestScore[k]){
                    for(int j=best.length-1;j>k;j--){
                        bestScore[j]=bestScore[j-1];
                        best[j]=best[j-1];
                    }
                    bestScore[k]=distance;
                    best[k]=ends.get(i);
                    break;
                }
            }
        }
        double[] weights=new double[best.length];
        double sumW=0;
        for(int j=0;j<best.length;j++){
            if(best[j]>=0){
                weights[j]=1/(2.0+bestScore[j]);
                sumW+=weights[j];
            }
        }
        if(sumW>0)for(int j=0;j<weights.length;j++)weights[j]/=sumW;
        // Near-term path = observed analog developments mixed with a small
        // momentum component. Do not force every next candle to be the same.
        ChartAnalysis.Candle[] projection=new ChartAnalysis.Candle[horizon];
        double[] upper=new double[horizon],lower=new double[horizon];
        double previous=closes[n-1],lastMove=delta[n-1];
        double cumulative=0;
        for(int h=1;h<=horizon;h++){
            double meanAnalog=0;
            if(h<=longest&&sumW>0){
                for(int j=0;j<best.length;j++)if(best[j]>=0){
                    int idx=best[j]+h;
                    // Scale analog movement to present pixel-candle volatility.
                    double pastNoise=0;
                    for(int k=0;k<lookback;k++)pastNoise+=Math.abs(delta[best[j]-k]);
                    pastNoise=Math.max(1,pastNoise/lookback);
                    double scaled=clamp(delta[idx]*(noise/pastNoise),-2.1*noise,2.1*noise);
                    meanAnalog+=weights[j]*scaled;
                }
            }
            double candidate=(sumW>0&&h<=longest)?(.72*meanAnalog+.28*slope):(.45*slope+.15*lastMove);
            candidate=clamp(candidate,-1.35*noise,1.35*noise);
            // Accumulate the relative path; no exponential shrinking to zero.
            double next=previous+candidate;
            if(!Double.isFinite(next))next=previous;
            // Image pixel coordinates are arbitrary, so negative relative
            // levels are allowed and should never be labeled market prices.
            double range=noise*(1.4*Math.sqrt(h)+.25*h);
            lower[h-1]=next-range;
            upper[h-1]=next+range;
            double wick=Math.max(.28*medRange,.30*noise);
            projection[h-1]=new ChartAnalysis.Candle(previous,
                Math.max(previous,next)+wick,
                Math.min(previous,next)-wick,next,Double.NaN);
            lastMove=candidate;
            previous=next;
            cumulative+=candidate;
        }
        String tilt=cumulative>noise*.8?"UPWARD VISUAL TILT":
                    cumulative<-noise*.8?"DOWNWARD VISUAL TILT":"MIXED / FLAT";
        report.append("Method: nearest historical screenshot-shape analogs (top ")
              .append(best.length).append("), with recent visual momentum.\n");
        report.append("Earlier matching sequences: ").append(ends.size())
              .append(". No independent price or forecast-skill validation.\n");
        if(longest<horizon){
            report.append("Not enough earlier observed candles for the full horizon; later bars use a labeled fallback.\n");
        }
        double support=Double.POSITIVE_INFINITY,resistance=Double.NEGATIVE_INFINITY;
        int window=Math.min(20,filtered.size());
        for(int i=filtered.size()-window;i<filtered.size();i++){
            support=Math.min(support,filtered.get(i).low);
            resistance=Math.max(resistance,filtered.get(i).high);
        }
        report.append(String.format(Locale.US,
                "RELATIVE PIXEL LEVELS ONLY — S: %.1f, last: %.1f, R: %.1f\n",
                support,closes[n-1],resistance));
        report.append("VISUAL TILT: ").append(tilt).append("\n");
        report.append("Displayed next ").append(horizon).append(" candles: conditional illustrative guess ONLY.\n");
        report.append("Blue range: illustrative image-coordinate alternative levels, NOT a probability interval.\n\n");
        report.append("THIS IS NOT A VERIFIED FORECAST. It is a shape-based extrapolation in relative pixel coordinates, ")
              .append("and may differ greatly from actual market movements. ")
              .append("Even a visually convincing pattern is not proof of predictive accuracy. ")
              .append("Do not use this screenshot sketch alone to enter an option/futures trade.\n\n");
        report.append("For real indicator values, price targets and holdout error metrics, import at least ~200 chronological OHLCV candles from the same market/timeframe.");
        return new ChartAnalysis.Result(actual,projection,report.toString(),lower,upper);
    }
}
