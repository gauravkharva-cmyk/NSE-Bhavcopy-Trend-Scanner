package com.gk.chartpredictor;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import static org.junit.Assert.*;

/** Tests use synthetic, clearly labeled candles, never real market records. */
public class ForecastEngineTest {
    private String sample(int n,double slope){
        StringBuilder b=new StringBuilder("Open,High,Low,Close,Volume\n");
        double last=110.0;
        for(int i=0;i<n;i++){
            double open=last;
            double close=Math.max(1.0,open+slope+1.1*Math.sin(i*.34)+.6*Math.cos(i*.71));
            double high=Math.max(open,close)+1.2;
            double low=Math.min(open,close)-1.0;
            b.append(String.format(Locale.US,"%.5f,%.5f,%.5f,%.5f,%d\n",open,high,low,close,50000+i*70));
            last=close;
        }
        return b.toString();
    }
    @Test public void forecastProvidesCausalHistoryAndIntervals()throws Exception{
        String csv=sample(240,.15);
        ChartAnalysis.Result r=ChartAnalysis.fromCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),5,"5 min");
        assertEquals(5,r.projected.length);
        assertEquals(5,r.lowBand.length);
        assertEquals(5,r.highBand.length);
        assertTrue(r.report.contains("OFFLINE ADAPTIVE FORECAST"));
        assertTrue(r.report.contains("no-change"));
        assertTrue(r.report.contains("holdout"));
        for(int i=0;i<5;i++){
            assertTrue(Double.isFinite(r.projected[i].close));
            assertTrue(r.lowBand[i]<=r.highBand[i]);
            assertTrue(r.projected[i].close>0);
        }
    }
    @Test public void wideHorizonsKeepFiniteValues()throws Exception{
        ChartAnalysis.Result r=ChartAnalysis.fromCsv(
                new ByteArrayInputStream(sample(260,-.10).getBytes(StandardCharsets.UTF_8)),20,"1 hour");
        assertEquals(20,r.projected.length);
        assertTrue(r.report.contains("Horizon 20 holdout"));
        for(int i=0;i<20;i++){
            assertTrue(Double.isFinite(r.projected[i].high));
            assertTrue(r.projected[i].high>=r.projected[i].low);
        }
    }
    @Test public void insufficientHistoricalDataMustAbstain()throws Exception{
        ChartAnalysis.Result r=ChartAnalysis.fromCsv(
                new ByteArrayInputStream(sample(45,0).getBytes(StandardCharsets.UTF_8)),10,"Daily");
        assertTrue(r.report.contains("INSUFFICIENT HISTORY"));
        assertTrue(r.report.contains("NO DEMONSTRATED EDGE"));
    }
}
