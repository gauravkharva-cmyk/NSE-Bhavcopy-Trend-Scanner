package com.gk.chartpredictor;

import java.util.Locale;

/**
 * Two-point, user-controlled price-axis calibration for an already CROPPED
 * screenshot. The numeric prices must correspond exactly to two draggable
 * horizontal guide positions in that cropped image. No OCR or exchange quote
 * is inferred. Converted levels remain approximate because candle detection
 * and screenshot scale can contain errors.
 */
public final class ImagePriceCalibration {
    private ImagePriceCalibration(){}

    public static final class Calibration {
        public final double upperPrice,lowerPrice;
        public final float upperFraction,lowerFraction;
        public Calibration(double upperPrice,double lowerPrice,float upperFraction,float lowerFraction){
            if(!Double.isFinite(upperPrice)||!Double.isFinite(lowerPrice)||
               upperPrice<=lowerPrice||lowerPrice<=0){
                throw new IllegalArgumentException("Upper price must be greater than lower price, both positive.");
            }
            if(!Float.isFinite(upperFraction)||!Float.isFinite(lowerFraction)||
               upperFraction<0||lowerFraction>1||
               lowerFraction-upperFraction<.10f){
                throw new IllegalArgumentException("Guide lines must be at least 10% of the chart height apart.");
            }
            this.upperPrice=upperPrice;this.lowerPrice=lowerPrice;
            this.upperFraction=upperFraction;this.lowerFraction=lowerFraction;
        }
        public double mapLevel(double relativeLevel,int originalWidth,int originalHeight){
            if(!Double.isFinite(relativeLevel)||originalWidth<=0||originalHeight<=0){
                throw new IllegalArgumentException("Invalid chart dimensions or coordinate");
            }
            int scaledHeight=Math.min(1200,Math.max(210,
                    Math.round(originalHeight*(720f/originalWidth))));
            int y1=Math.min(scaledHeight-2,(int)(scaledHeight*.975));
            double fraction=((double)y1-relativeLevel)/scaledHeight;
            return upperPrice+
                    (fraction-upperFraction)*(lowerPrice-upperPrice)/(lowerFraction-upperFraction);
        }
    }
    private static ChartAnalysis.Candle mapCandle(
            ChartAnalysis.Candle candle,Calibration cal,int width,int height){
        if(candle==null)return null;
        double o=cal.mapLevel(candle.open,width,height);
        double h=cal.mapLevel(candle.high,width,height);
        double l=cal.mapLevel(candle.low,width,height);
        double c=cal.mapLevel(candle.close,width,height);
        if(!Double.isFinite(o)||!Double.isFinite(h)||!Double.isFinite(l)||!Double.isFinite(c)){
            throw new IllegalArgumentException("Calibration produced invalid numerical levels.");
        }
        return new ChartAnalysis.Candle(o,h,l,c,candle.volume);
    }
    private static ChartAnalysis.Candle[] mapCandles(
            ChartAnalysis.Candle[] candles,Calibration cal,int width,int height){
        if(candles==null)return null;
        ChartAnalysis.Candle[] a=new ChartAnalysis.Candle[candles.length];
        for(int i=0;i<candles.length;i++)a[i]=mapCandle(candles[i],cal,width,height);
        return a;
    }
    public static ChartAnalysis.Result apply(
            ChartAnalysis.Result original,Calibration cal,int croppedWidth,int croppedHeight){
        if(original==null||cal==null)throw new IllegalArgumentException("Missing screenshot or calibration.");
        ChartAnalysis.Candle[] historical=mapCandles(original.actual,cal,croppedWidth,croppedHeight);
        ChartAnalysis.Candle[] projected=mapCandles(original.projected,cal,croppedWidth,croppedHeight);
        ChartAnalysis.Candle[][] alternatives=null;
        if(original.scenarioPaths!=null){
            alternatives=new ChartAnalysis.Candle[original.scenarioPaths.length][];
            for(int i=0;i<alternatives.length;i++){
                alternatives[i]=mapCandles(original.scenarioPaths[i],cal,croppedWidth,croppedHeight);
            }
        }
        double[] low=null,high=null;
        if(original.lowBand!=null){
            low=new double[original.lowBand.length];
            for(int i=0;i<low.length;i++)low[i]=cal.mapLevel(original.lowBand[i],croppedWidth,croppedHeight);
        }
        if(original.highBand!=null){
            high=new double[original.highBand.length];
            for(int i=0;i<high.length;i++)high[i]=cal.mapLevel(original.highBand[i],croppedWidth,croppedHeight);
        }
        // Avoid presenting impossible negative market prices due to incorrect anchors.
        for(ChartAnalysis.Candle c:historical)if(c.low<=0){
            throw new IllegalArgumentException("Axis points produce nonpositive chart levels. Check guide positions and price labels.");
        }
        for(ChartAnalysis.Candle[] path:alternatives==null?new ChartAnalysis.Candle[0][]:alternatives){
            for(ChartAnalysis.Candle c:path)if(c.low<=0){
                throw new IllegalArgumentException("Calibrated projection falls below zero. Verify the chart scale.");
            }
        }
        StringBuilder summary=new StringBuilder(original.report);
        summary.append("\n\nMANUAL TWO-POINT IMAGE CALIBRATION (APPROXIMATE)\n")
               .append(String.format(Locale.US,
                     "Upper guide %.1f%% = %.2f; lower guide %.1f%% = %.2f\n",
                     cal.upperFraction*100,cal.upperPrice,cal.lowerFraction*100,cal.lowerPrice));
        if(historical.length>0){
            double support=Double.POSITIVE_INFINITY,resistance=Double.NEGATIVE_INFINITY;
            for(int i=Math.max(0,historical.length-20);i<historical.length;i++){
                support=Math.min(support,historical[i].low);
                resistance=Math.max(resistance,historical[i].high);
            }
            summary.append(String.format(Locale.US,
                "Estimated image-derived S %.2f · LAST %.2f · R %.2f\n",
                support,historical[historical.length-1].close,resistance));
        }
        if(alternatives!=null&&alternatives.length>=3){
            summary.append(String.format(Locale.US,
                "Approximate replay end levels — lower %.2f · middle %.2f · upper %.2f\n",
                alternatives[0][alternatives[0].length-1].close,
                alternatives[1][alternatives[1].length-1].close,
                alternatives[2][alternatives[2].length-1].close));
        }
        summary.append("Numbers depend entirely on your guide positions and price inputs. ")
               .append("They are not independently verified exchange prices and do not validate forecast performance.");
        return new ChartAnalysis.Result(historical,projected,summary.toString(),
                low,high,alternatives,original.scenarioNames,true);
    }
}
