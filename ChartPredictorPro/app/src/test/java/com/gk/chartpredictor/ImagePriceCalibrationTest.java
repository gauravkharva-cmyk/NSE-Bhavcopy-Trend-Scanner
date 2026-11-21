package com.gk.chartpredictor;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure numerical tests: top/bottom prices are user-provided axis anchors. */
public class ImagePriceCalibrationTest {
    @Test public void recoversBothPricesAtGuideLines(){
        int width=720,height=540;
        ImagePriceCalibration.Calibration c=
                new ImagePriceCalibration.Calibration(26000,25000,.20f,.80f);
        int y1=Math.min(height-2,(int)(height*.975));
        double upperRelative=y1-height*.20;
        double lowerRelative=y1-height*.80;
        assertEquals(26000,c.mapLevel(upperRelative,width,height),.03);
        assertEquals(25000,c.mapLevel(lowerRelative,width,height),.03);
        assertEquals(25500,c.mapLevel((upperRelative+lowerRelative)/2,width,height),.03);
    }
    @Test public void convertsHistoricalAndScenarioBodiesWithoutChangingShapes(){
        ChartAnalysis.Candle a=new ChartAnalysis.Candle(240,275,227,265,Double.NaN);
        ChartAnalysis.Candle[] hist={a};
        ChartAnalysis.Candle first=new ChartAnalysis.Candle(265,285,254,280,Double.NaN);
        ChartAnalysis.Candle[] path={first};
        ChartAnalysis.Candle[][] paths={path,path,path};
        ChartAnalysis.Result input=new ChartAnalysis.Result(hist,path,"SCREENSHOT WHAT-IF",
                new double[]{250},new double[]{290},paths,
                new String[]{"lower","middle","upper"},false);
        ImagePriceCalibration.Calibration cal=
                new ImagePriceCalibration.Calibration(1500,1400,.15f,.85f);
        ChartAnalysis.Result result=ImagePriceCalibration.apply(input,cal,720,540);
        assertTrue(result.manuallyCalibrated);
        assertEquals(3,result.scenarioPaths.length);
        assertTrue(result.actual[0].high>=result.actual[0].close);
        assertTrue(result.actual[0].low<=result.actual[0].open);
        assertTrue(result.scenarioPaths[1][0].close>result.scenarioPaths[1][0].open);
        assertTrue(result.lowBand[0]<=result.highBand[0]);
        assertTrue(result.report.contains("MANUAL TWO-POINT"));
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsUpsideDownPrices(){
        new ImagePriceCalibration.Calibration(25000,26000,.2f,.8f);
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsOverlappingGuides(){
        new ImagePriceCalibration.Calibration(26000,25000,.4f,.43f);
    }
}
