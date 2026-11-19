package com.gk.chartpredictor;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.*;
import java.util.*;

public final class ChartAnalysis {
    public static final class Candle {
        final double open,high,low,close,volume;
        Candle(double o,double h,double l,double c,double v) {
            open=o; high=h; low=l; close=c; volume=v;
        }
    }
    public static final class Result {
        public final Candle[] actual, projected;
        public final String report;
        Result(Candle[] actual,Candle[] projected,String report) {
            this.actual=actual;this.projected=projected;this.report=report;
        }
    }
    private static double clamp(double x,double min,double max){return Math.max(min,Math.min(max,x));}
    private static double ema(double[] prices,int period){
        if(prices.length==0)return Double.NaN;
        double value=prices[0],alpha=2.0/(period+1);
        for(int i=1;i<prices.length;i++)value+=alpha*(prices[i]-value);
        return value;
    }
    private static double rsi(double[] p){
        if(p.length<16)return Double.NaN;
        double gains=0,losses=0;
        int from=Math.max(1,p.length-14);
        for(int i=from;i<p.length;i++){
            double d=p[i]-p[i-1];gains+=Math.max(0,d);losses+=Math.max(0,-d);
        }
        return losses==0?(gains==0?50:100):100-100/(1+gains/losses);
    }
    private static double atr(List<Candle> list){
        if(list.size()<2)return Double.NaN;
        int start=Math.max(1,list.size()-14);
        double s=0;int n=0;
        for(int i=start;i<list.size();i++){
            Candle a=list.get(i),p=list.get(i-1);
            double tr=Math.max(a.high-a.low,Math.max(Math.abs(a.high-p.close),Math.abs(a.low-p.close)));
            s+=tr;n++;
        }
        return s/Math.max(1,n);
    }
    private static String f(double d){return String.format(Locale.US,"%.2f",d);}
    private static String fmt(double d){return Double.isFinite(d)?f(d):"N/A";}
    private static List<String> columns(String line){
        ArrayList<String> cols=new ArrayList<>();
        StringBuilder b=new StringBuilder();boolean in=false;
        for(int i=0;i<line.length();i++){
            char ch=line.charAt(i);
            if(ch=='"'){
                if(in&&i+1<line.length()&&line.charAt(i+1)=='"'){b.append('"');i++;}
                else in=!in;
            }else if(ch==','&&!in){cols.add(b.toString().trim());b.setLength(0);}
            else b.append(ch);
        }
        cols.add(b.toString().trim());
        return cols;
    }
    private static int idx(List<String> names,String key){
        for(int i=0;i<names.size();i++){
            String s=names.get(i).replaceAll("[^A-Z0-9]","");
            if(s.equals(key)||s.equals(key+"PRICE")||s.equals("PRICE"+key)||s.equals("LAST"+key))return i;
        }
        return -1;
    }
    private static int volIdx(List<String> names){
        for(int i=0;i<names.size();i++){
            String s=names.get(i).replaceAll("[^A-Z0-9]","");
            if(s.equals("VOLUME")||s.equals("VOL")||s.equals("TRADINGVOLUME")||s.equals("NETTRDQTY")||s.equals("QTY"))return i;
        }
        return -1;
    }
    private static double num(String s){
        return Double.parseDouble(s.replace(",","").replace(" ","").replace("\uFEFF",""));
    }
    public static Result fromCsv(InputStream stream,int count,String timeframe)throws Exception{
        BufferedReader reader=new BufferedReader(new InputStreamReader(stream,"UTF-8"));
        ArrayList<Candle> data=new ArrayList<>();
        String line;int open=-1,high=-1,low=-1,close=-1,vol=-1;
        boolean header=false;
        while((line=reader.readLine())!=null){
            if(line.trim().isEmpty())continue;
            List<String> c=columns(line);
            if(!header){
                ArrayList<String> upper=new ArrayList<>();
                for(String v:c)upper.add(v.toUpperCase(Locale.US));
                open=idx(upper,"OPEN");high=idx(upper,"HIGH");low=idx(upper,"LOW");close=idx(upper,"CLOSE");vol=volIdx(upper);
                if(open<0||high<0||low<0||close<0)continue;
                header=true;continue;
            }
            try{
                if(Math.max(Math.max(open,high),Math.max(low,close))>=c.size())continue;
                double o=num(c.get(open)),h=num(c.get(high)),l=num(c.get(low)),cl=num(c.get(close));
                double v=vol>=0&&vol<c.size()?num(c.get(vol)):Double.NaN;
                if(!Double.isFinite(o)||!Double.isFinite(h)||!Double.isFinite(l)||!Double.isFinite(cl)||h<Math.max(o,cl)||l>Math.min(o,cl)||h<l)continue;
                data.add(new Candle(o,h,l,cl,v));
                if(data.size()>25000)break;
            }catch(Exception ignored){}
        }
        if(!header)throw new Exception("CSV needs Open, High, Low and Close column headers.");
        if(data.size()<30)throw new Exception("At least 30 valid OHLC candles required; found "+data.size()+".");
        // CSV rows must be chronological, oldest first.
        return calculate(data,count,timeframe,true);
    }
    private static boolean green(int pix){
        int r=Color.red(pix),g=Color.green(pix),b=Color.blue(pix);
        return g>85&&g>r*1.22&&g>b*1.07;
    }
    private static boolean red(int pix){
        int r=Color.red(pix),g=Color.green(pix),b=Color.blue(pix);
        return r>105&&r>g*1.25&&r>b*1.13;
    }
    public static Result fromImage(Bitmap image,int count,String tf)throws Exception{
        if(image==null)throw new Exception("No screenshot selected.");
        int w=600,h=Math.max(230,Math.round(image.getHeight()*(600f/image.getWidth())));
        h=Math.min(760,h);
        Bitmap scaled=Bitmap.createScaledBitmap(image,w,h,true);
        int y0=(int)(h*.13),y1=(int)(h*.76);
        int[] pixels=new int[w*h];scaled.getPixels(pixels,0,w,0,0,w,h);
        int[] hits=new int[w],colorHits=new int[w];
        for(int x=10;x<w-10;x++){
            for(int y=y0;y<y1;y++){
                int p=pixels[y*w+x];if(green(p)){hits[x]++;colorHits[x]++;}
                else if(red(p)){hits[x]++;colorHits[x]--;}
            }
        }
        ArrayList<Candle> candles=new ArrayList<>();
        int x=12;
        while(x<w-12){
            if(hits[x]<3){x++;continue;}
            int s=x,e=x;
            while(e+1<w-12&&hits[e+1]>=2&&(e-s)<36)e++;
            if(e-s<1||e-s>31){x=e+1;continue;}
            int g=0,r=0,rowHits[]=new int[y1-y0];
            int minY=y1,maxY=y0;
            for(int xx=s;xx<=e;xx++)for(int yy=y0;yy<y1;yy++){
                int p=pixels[yy*w+xx];boolean isG=green(p),isR=red(p);
                if(isG||isR){
                    if(isG)g++;else r++;
                    rowHits[yy-y0]++;minY=Math.min(minY,yy);maxY=Math.max(maxY,yy);
                }
            }
            if(g+r>=8&&Math.max(g,r)>.65*(g+r)&&maxY>minY){
                int peak=0;for(int n:rowHits)peak=Math.max(peak,n);
                int top=maxY,bottom=minY;
                for(int i=0;i<rowHits.length;i++)if(rowHits[i]>=Math.max(2,peak*.55)){
                    top=Math.min(top,y0+i);bottom=Math.max(bottom,y0+i);
                }
                if(bottom>=top){
                    double highVal=y1-minY,lowVal=y1-maxY;
                    double a=y1-bottom,b=y1-top;
                    boolean up=g>r;
                    candles.add(new Candle(up?a:b,highVal,lowVal,up?b:a,Double.NaN));
                }
            }
            x=e+1;
        }
        if(candles.size()<20)throw new Exception("Only "+candles.size()+" distinct candidate candles found. Crop the image to the candle area or import OHLC CSV for accurate analysis.");
        if(candles.size()>100)candles=new ArrayList<>(candles.subList(candles.size()-100,candles.size()));
        return calculate(candles,count,tf,false);
    }
    private static Result calculate(List<Candle> data,int count,String tf,boolean real){
        int n=data.size();Candle last=data.get(n-1),prev=data.get(n-2);
        double[] close=new double[n];
        for(int i=0;i<n;i++)close[i]=data.get(i).close;
        double ema9=ema(close,9),ema21=ema(close,21),rsi=rsi(close),a=atr(data);
        double trend=ema9-ema21;
        double recent=(last.close-close[Math.max(0,n-6)])/Math.max(1,Math.min(5,n-1));
        double width=Math.max(a,Math.abs(last.close)*.0001);
        double step=clamp(.19*trend+.28*recent+(rsi-50)/50*.12*width,-.4*width,.4*width);
        String bias=Math.abs(step)<.055*width?"SIDEWAYS / MIXED":step>0?"UPWARD LEAN":"DOWNWARD LEAN";
        ArrayList<String> patterns=new ArrayList<>();
        double range=Math.max(1.e-9,last.high-last.low),body=Math.abs(last.close-last.open);
        if(body<=range*.12)patterns.add("Doji / indecision");
        double upper=last.high-Math.max(last.open,last.close),lower=Math.min(last.open,last.close)-last.low;
        if(lower>Math.max(body*2,range*.45)&&upper<range*.2)patterns.add("Hammer-like candle");
        if(upper>Math.max(body*2,range*.45)&&lower<range*.2)patterns.add("Shooting-star-like candle");
        if(last.close>last.open&&prev.close<prev.open&&last.open<=prev.close&&last.close>=prev.open)patterns.add("Bullish engulfing");
        if(last.close<last.open&&prev.close>prev.open&&last.open>=prev.close&&last.close<=prev.open)patterns.add("Bearish engulfing");
        if(last.high<prev.high&&last.low>prev.low)patterns.add("Inside bar");
        if(n>12){
            double max1=-1e100,max2=-1e100,min1=1e100,min2=1e100;
            for(int i=n-12;i<n-6;i++){max1=Math.max(max1,data.get(i).high);min1=Math.min(min1,data.get(i).low);}
            for(int i=n-6;i<n;i++){max2=Math.max(max2,data.get(i).high);min2=Math.min(min2,data.get(i).low);}
            if(Math.abs(max1-max2)<=a*.4&&last.close<max2-a*.3)patterns.add("Possible double top (unconfirmed)");
            if(Math.abs(min1-min2)<=a*.4&&last.close>min2+a*.3)patterns.add("Possible double bottom (unconfirmed)");
        }
        double[] projected=new double[count];ArrayList<Candle> proj=new ArrayList<>();
        double prior=last.close;
        for(int i=0;i<count;i++){
            double localStep=step+width*.10*Math.sin((i+1)*1.78+last.close*.003);
            double next=prior+localStep;
            double spread=Math.max(width*.26,Math.abs(localStep)*1.2);
            double hi=Math.max(prior,next)+spread*(.28+.09*(i%3));
            double lo=Math.min(prior,next)-spread*(.22+.06*((i+1)%3));
            proj.add(new Candle(prior,hi,lo,next,Double.NaN));prior=next;
        }
        double macd=ema(close,12)-ema(close,26);
        double[] macdSeq=new double[n];
        double e12=close[0],e26=close[0];
        for(int i=0;i<n;i++){
            e12+=(close[i]-e12)*2/13.0;e26+=(close[i]-e26)*2/27.0;
            macdSeq[i]=e12-e26;
        }
        double histogram=macd-ema(macdSeq,9);
        double sum=0,sq=0;
        for(int i=Math.max(0,n-20);i<n;i++){sum+=close[i];sq+=close[i]*close[i];}
        int count20=Math.min(20,n);double mean=sum/count20;
        double sd=Math.sqrt(Math.max(0,sq/count20-mean*mean));
        int shown=Math.min(65,data.size());
        Candle[] actual=new Candle[shown];
        for(int i=0;i<shown;i++)actual[i]=data.get(data.size()-shown+i);
        StringBuilder out=new StringBuilder();
        out.append(real?"OHLCV CSV ANALYSIS":"SCREENSHOT GEOMETRY SCAN");
        out.append("\nTimeframe: ").append(tf).append("  ·  future candles: ").append(count).append("\n");
        out.append("Visual market lean: ").append(bias).append("\n");
        out.append("Candles evaluated: ").append(n).append("\n");
        out.append("Detected patterns: ").append(patterns.isEmpty()?"No supported pattern clearly detected":String.join(", ",patterns)).append("\n");
        if(real){
            out.append("\nIndicators from CSV price history:\n");
            out.append("EMA9: ").append(fmt(ema9)).append(" | EMA21: ").append(fmt(ema21)).append("\n");
            out.append("RSI14: ").append(fmt(rsi)).append(" | MACD: ").append(fmt(macd)).append("\n");
            out.append("MACD histogram: ").append(fmt(histogram)).append(" | ATR14: ").append(fmt(a)).append("\n");
            out.append("Bollinger (20,2) upper/lower: ").append(fmt(mean+2*sd)).append(" / ").append(fmt(mean-2*sd)).append("\n");
            out.append("Last close: ").append(fmt(last.close)).append("\n");
            out.append("\nRows must be oldest to newest. Forecast candles are an illustrative trend/volatility extrapolation, not a tested prediction. No guaranteed targets or probability.");
        }else{
            out.append("\nOnly relative pixel heights were extracted. Exact OHLC prices, volume, RSI, MACD, VWAP, and price targets are unavailable from this screenshot.\n");
            out.append("The next-candle sketch reflects detected color/geometry only; it is NOT a calibrated market prediction. For real indicator values use OHLCV CSV.");
        }
        return new Result(actual,proj.toArray(new Candle[0]),out.toString());
    }
}
