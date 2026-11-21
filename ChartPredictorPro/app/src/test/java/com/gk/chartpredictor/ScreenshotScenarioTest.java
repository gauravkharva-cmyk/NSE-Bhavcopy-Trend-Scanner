package com.gk.chartpredictor;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Synthetic patterns only; screenshots do not constitute a validated market forecast. */
public class ScreenshotScenarioTest {
    private List<ChartAnalysis.Candle> sample(int number){
        List<ChartAnalysis.Candle> data=new ArrayList<>();
        double p=90;
        for(int i=0;i<number;i++){
            double next=p+1.8*Math.sin(i*.49)+.75*Math.cos(i*.13);
            double upper=.45+1.1*Math.abs(Math.cos(.62*i));
            double lower=.42+1.0*Math.abs(Math.sin(.78*i));
            data.add(new ChartAnalysis.Candle(p,Math.max(p,next)+upper,
                    Math.min(p,next)-lower,next,Double.NaN));
            p=next;
        }
        return data;
    }
    @Test public void replaysThreeHistoricalCandleSequences(){
        List<ChartAnalysis.Candle> candles=sample(90);
        candles.add(32,new ChartAnalysis.Candle(100,290,20,40,Double.NaN));
        ChartAnalysis.Result r=ScreenshotScenario.build(candles,2,10,"5 min",true);
        assertNotNull(r.scenarioPaths);
        assertEquals(3,r.scenarioPaths.length);
        assertEquals(3,r.scenarioNames.length);
        assertEquals(10,r.projected.length);
        assertEquals(10,r.lowBand.length);
        assertEquals(10,r.highBand.length);
        assertTrue(r.report.contains("Three distinct complete candle-sequence replays"));
        assertTrue(r.report.contains("Oversized candidates excluded: 1"));
        assertTrue(r.report.contains("NOT a calibrated confidence interval"));
        double lowerEnd=r.scenarioPaths[0][9].close;
        double midEnd=r.scenarioPaths[1][9].close;
        double upperEnd=r.scenarioPaths[2][9].close;
        assertTrue(lowerEnd<=midEnd);
        assertTrue(midEnd<=upperEnd);
        boolean bodyDiffers=false,wickDiffers=false;
        for(int i=1;i<10;i++){
            ChartAnalysis.Candle c=r.scenarioPaths[1][i];
            ChartAnalysis.Candle prior=r.scenarioPaths[1][i-1];
            if(Math.abs((c.close-c.open)-(prior.close-prior.open))>.001)bodyDiffers=true;
            if(Math.abs((c.high-Math.max(c.close,c.open))-(prior.high-Math.max(prior.close,prior.open)))>.001)wickDiffers=true;
            assertTrue(r.lowBand[i]<=r.highBand[i]);
            assertTrue(Double.isFinite(c.close));
        }
        assertTrue("Replay must preserve differing historical candle bodies",bodyDiffers);
        assertTrue("Replay must preserve wick variation",wickDiffers);
    }
    @Test public void optOutShowsHistoryOnly(){
        ChartAnalysis.Result r=ScreenshotScenario.build(sample(65),0,10,"30 min",false);
        assertEquals(0,r.projected.length);
        assertNull(r.scenarioPaths);
        assertTrue(r.report.contains("OFF"));
    }
    @Test public void tooFewCandlesAbstains(){
        ChartAnalysis.Result r=ScreenshotScenario.build(sample(10),2,5,"1 min",true);
        assertEquals(0,r.projected.length);
        assertTrue(r.report.contains("INSUFFICIENT"));
    }
    @Test public void noFullReplayHistoryAbstains(){
        ChartAnalysis.Result r=ScreenshotScenario.build(sample(29),1,20,"5 min",true);
        assertEquals(0,r.projected.length);
        assertTrue(r.report.contains("INSUFFICIENT MATCHED HISTORY"));
    }
}
