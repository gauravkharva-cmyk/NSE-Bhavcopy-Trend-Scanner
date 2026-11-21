package com.gk.chartpredictor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Experimental image-shape replay, not a trained or validated price forecast.
 * Replays real OBSERVED following candles of earlier similar visible sequences,
 * preserving candle bodies, real-body gaps, and wick variation. All values
 * remain relative image coordinates unless two user-provided axis points are
 * calibrated. Scenario labels express ordering, not probabilities.
 */
public final class ScreenshotScenario {
    private ScreenshotScenario(){}

    private static final class Match {
        int end;
        double score;
        double scale;
        ChartAnalysis.Candle[] path;
        double finalMove;
        Match(int end,double score,double scale){
            this.end=end;this.score=score;this.scale=scale;
        }
    }

    private static double median(double[] a,int length){
        if(length<=0)return 0;
        double[] copy=Arrays.copyOf(a,length);
        Arrays.sort(copy);
        int m=length/2;
        return length%2==0?(copy[m-1]+copy[m])*.5:copy[m];
    }
    private static double clamp(double x,double min,double max){
        return Math.max(min,Math.min(max,x));
    }
    private static double normalizedRange(ChartAnalysis.Candle c){
        return Math.max(.15,c.high-c.low);
    }
    private static double candleBody(ChartAnalysis.Candle c){
        return c.close-c.open;
    }
    private static double upperWick(ChartAnalysis.Candle c){
        return Math.max(0,c.high-Math.max(c.open,c.close));
    }
    private static double lowerWick(ChartAnalysis.Candle c){
        return Math.max(0,Math.min(c.open,c.close)-c.low);
    }
    private static double scaleAt(List<ChartAnalysis.Candle> data,int end,int bars){
        int from=Math.max(0,end-bars+1);
        double[] vals=new double[end-from+1];
        for(int i=from;i<=end;i++)vals[i-from]=normalizedRange(data.get(i));
        return Math.max(.7,median(vals,vals.length));
    }
    private static double distance(List<ChartAnalysis.Candle> c,int end,int recent,int window,double recentScale,double pastScale){
        double total=0;
        for(int j=0;j<window;j++){
            ChartAnalysis.Candle now=c.get(recent-j),old=c.get(end-j);
            double scaleN=Math.max(.7,recentScale),scaleP=Math.max(.7,pastScale);
            double dBody=clamp(candleBody(now)/scaleN,-3,3)-clamp(candleBody(old)/scaleP,-3,3);
            double dRange=clamp(normalizedRange(now)/scaleN,0,4)-clamp(normalizedRange(old)/scaleP,0,4);
            double dUpper=clamp(upperWick(now)/scaleN,0,3)-clamp(upperWick(old)/scaleP,0,3);
            double dLower=clamp(lowerWick(now)/scaleN,0,3)-clamp(lowerWick(old)/scaleP,0,3);
            double nowGap=0,oldGap=0;
            if(recent-j>0)nowGap=(now.close-c.get(recent-j-1).close)/scaleN;
            if(end-j>0)oldGap=(old.close-c.get(end-j-1).close)/scaleP;
            double dClose=clamp(nowGap,-3,3)-clamp(oldGap,-3,3);
            double weight=j<4?1.25:0.75;
            total+=weight*(1.30*dBody*dBody+.6*dRange*dRange+.33*dUpper*dUpper+
                           .33*dLower*dLower+.8*dClose*dClose);
        }
        return total;
    }
    private static ChartAnalysis.Candle[] replay(List<ChartAnalysis.Candle> data,Match match,int horizon,double fromClose){
        ChartAnalysis.Candle[] projection=new ChartAnalysis.Candle[horizon];
        double previous=fromClose;
        for(int h=1;h<=horizon;h++){
            ChartAnalysis.Candle hist=data.get(match.end+h);
            ChartAnalysis.Candle before=data.get(match.end+h-1);
            // Replay the actual historical next candle with its body/wicks,
            // scaled uniformly to recent visual candle height.
            double open=previous+(hist.open-before.close)*match.scale;
            double close=open+(hist.close-hist.open)*match.scale;
            double high=Math.max(open,close)+upperWick(hist)*match.scale;
            double low=Math.min(open,close)-lowerWick(hist)*match.scale;
            projection[h-1]=new ChartAnalysis.Candle(open,high,low,close,Double.NaN);
            previous=close;
        }
        return projection;
    }

    public static ChartAnalysis.Result build(
            List<ChartAnalysis.Candle> candidates,int rejectedColorRegions,
            int horizon,String timeframe,boolean showGuess){

        if(horizon<1||horizon>40)throw new IllegalArgumentException("Invalid horizon");
        double[] ranges=new double[candidates.size()],bodies=new double[candidates.size()];
        for(int i=0;i<candidates.size();i++){
            ranges[i]=normalizedRange(candidates.get(i));
            bodies[i]=Math.abs(candleBody(candidates.get(i)));
        }
        double medRange=Math.max(1.0,median(ranges,ranges.length));
        double medBody=Math.max(.6,median(bodies,bodies.length));
        double rangeCap=Math.max(26,medRange*3.8);
        double bodyCap=Math.max(20,medBody*4.8);

        ArrayList<ChartAnalysis.Candle> filtered=new ArrayList<>();
        int suspect=0;
        for(ChartAnalysis.Candle c:candidates){
            if(normalizedRange(c)>rangeCap||Math.abs(candleBody(c))>bodyCap)suspect++;
            else filtered.add(c);
        }
        if(filtered.size()>120)filtered=new ArrayList<>(filtered.subList(filtered.size()-120,filtered.size()));
        final int n=filtered.size();
        ChartAnalysis.Candle[] history=filtered.toArray(new ChartAnalysis.Candle[0]);
        StringBuilder report=new StringBuilder();
        report.append("SCREENSHOT WHAT-IF ANALYSIS — EXPERIMENTAL\n")
              .append("Timeframe chosen: ").append(timeframe).append(" · Future candles requested: ").append(horizon).append("\n")
              .append("Approximate candidates: ").append(candidates.size())
              .append(" · Oversized candidates excluded: ").append(suspect)
              .append(" · Color regions rejected: ").append(rejectedColorRegions).append("\n")
              .append("Usable approximate candles: ").append(n).append("\n\n");

        if(n<25){
            report.append("INSUFFICIENT VISUAL DATA. Fewer than 25 candidates after filtering. ")
                  .append("No projected candles can be justified; recrop the candle plot or import OHLCV CSV.");
            return new ChartAnalysis.Result(history,new ChartAnalysis.Candle[0],report.toString());
        }
        if(!showGuess){
            report.append("Screenshot scenario is OFF. Only the approximate historical shape is displayed. ")
                  .append("Import OHLCV CSV for actual-price indicators and evaluated forecasts.");
            return new ChartAnalysis.Result(history,new ChartAnalysis.Candle[0],report.toString());
        }

        final int lookback=9;
        if(n<lookback+horizon+8){
            report.append("INSUFFICIENT MATCHED HISTORY for ").append(horizon)
                  .append(" full future candles. Reduce the horizon or use more history. ")
                  .append("The app will not repeat or fabricate missing candlesticks.");
            return new ChartAnalysis.Result(history,new ChartAnalysis.Candle[0],report.toString());
        }

        double nowScale=scaleAt(filtered,n-1,16);
        ArrayList<Match> matches=new ArrayList<>();
        int latestEnd=n-horizon-1;
        for(int end=lookback;end<=latestEnd;end++){
            double historicalScale=scaleAt(filtered,end,lookback+3);
            double d=distance(filtered,end,n-1,lookback,nowScale,historicalScale);
            if(Double.isFinite(d)){
                matches.add(new Match(end,d,clamp(nowScale/historicalScale,.38,2.30)));
            }
        }
        Collections.sort(matches,Comparator.comparingDouble(m->m.score));

        // Avoid near-duplicate overlapping analog windows in the shortlist.
        ArrayList<Match> shortlist=new ArrayList<>();
        int separation=Math.max(2,Math.min(6,horizon/5));
        for(Match m:matches){
            boolean near=false;
            for(Match prior:shortlist){
                if(Math.abs(prior.end-m.end)<separation){near=true;break;}
            }
            if(!near)shortlist.add(m);
            if(shortlist.size()>=12)break;
        }
        if(shortlist.size()<3){
            report.append("INSUFFICIENT DISTINCT HISTORICAL ANALOGS to show three scenarios. ")
                  .append("Try a larger chart or import OHLCV data.");
            return new ChartAnalysis.Result(history,new ChartAnalysis.Candle[0],report.toString());
        }

        double last=filtered.get(n-1).close;
        for(Match m:shortlist){
            m.path=replay(filtered,m,horizon,last);
            m.finalMove=m.path[horizon-1].close-last;
        }
        Collections.sort(shortlist,Comparator.comparingDouble(m->m.finalMove));
        Match lower=shortlist.get(0);
        Match middle=shortlist.get(shortlist.size()/2);
        Match upper=shortlist.get(shortlist.size()-1);
        ChartAnalysis.Candle[][] paths={lower.path,middle.path,upper.path};
        String[] labels={"LOWER historical replay","MIDDLE historical replay","UPPER historical replay"};
        double[] lowBand=new double[horizon],highBand=new double[horizon];
        for(int i=0;i<horizon;i++){
            lowBand[i]=Math.min(paths[0][i].low,Math.min(paths[1][i].low,paths[2][i].low));
            highBand[i]=Math.max(paths[0][i].high,Math.max(paths[1][i].high,paths[2][i].high));
        }
        report.append("METHOD: Three distinct complete candle-sequence replays chosen from ")
              .append(shortlist.size()).append(" similar historical image-pattern matches.\n")
              .append("Paths preserve each matched candle's body, wick and gap variations. ")
              .append("They are ordered by relative ending movement, NOT by predicted probability.\n")
              .append(String.format(Locale.US,
                "Relative end levels — lower: %.1f · middle: %.1f · upper: %.1f\n",
                paths[0][horizon-1].close,paths[1][horizon-1].close,paths[2][horizon-1].close))
              .append("Blue area covers the three pictured historical replays; it is NOT a calibrated confidence interval.\n");

        double support=Double.POSITIVE_INFINITY,resistance=Double.NEGATIVE_INFINITY;
        for(int i=n-Math.min(20,n);i<n;i++){
            support=Math.min(support,filtered.get(i).low);
            resistance=Math.max(resistance,filtered.get(i).high);
        }
        report.append(String.format(Locale.US,"Relative image levels (not prices) — S %.1f · last %.1f · R %.1f\n\n",
                support,last,resistance))
              .append("WARNING: Screenshot candle extraction and historical analog matching are experimental. ")
              .append("No true OHLCV prices, timestamps, trading volumes or independent forecast validation were available. ")
              .append("Do not treat a displayed replay as a buy/sell recommendation. ")
              .append("Use manual two-point price calibration for approximate price labels or import OHLCV CSV for price-based analysis.");
        return new ChartAnalysis.Result(history,paths[1],report.toString(),lowBand,highBand,paths,labels,false);
    }
}
