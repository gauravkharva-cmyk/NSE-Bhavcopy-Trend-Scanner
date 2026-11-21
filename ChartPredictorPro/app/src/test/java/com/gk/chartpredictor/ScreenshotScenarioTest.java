package com.gk.chartpredictor;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Synthetic visual-only candles; no live prices, no model-skill claims. */
public class ScreenshotScenarioTest {
    private List<ChartAnalysis.Candle> sample(int number){
        List<ChartAnalysis.Candle> data=new ArrayList<>();
        double p=90;
        for(int i=0;i<number;i++){
            double next=p+1.0*Math.sin(i*.5)+.45*Math.cos(i*.09);
            data.add(new ChartAnalysis.Candle(p,Math.max(p,next)+2,Math.min(p,next)-2,next,Double.NaN));
            p=next;
        }
        return data;
    }
    @Test public void optInDrawsFiveUncalibratedCandles(){
        List<ChartAnalysis.Candle> d=sample(65);
        d.add(32,new ChartAnalysis.Candle(100,290,20,40,Double.NaN));
        ChartAnalysis.Result result=ScreenshotScenario.build(d,2,5,"5 min",true);
        assertEquals(5,result.projected.length);
        assertTrue(result.report.contains("OVERSIZED")||result.report.contains("oversized"));
        assertTrue(result.report.contains("NOT A VERIFIED FORECAST"));
        assertEquals(5,result.lowBand.length);
        for(int i=0;i<5;i++){
            assertTrue(result.lowBand[i]<=result.highBand[i]);
            assertTrue(Double.isFinite(result.projected[i].close));
        }
    }
    @Test public void optOutHidesFuture(){
        ChartAnalysis.Result r=ScreenshotScenario.build(sample(60),0,10,"30 min",false);
        assertEquals(0,r.projected.length);
        assertTrue(r.report.contains("OFF"));
    }
    @Test public void tooFewCandleCandidatesAbstains(){
        ChartAnalysis.Result r=ScreenshotScenario.build(sample(10),3,5,"1 min",true);
        assertEquals(0,r.projected.length);
        assertTrue(r.report.contains("INSUFFICIENT"));
    }
}
