package com.gk.chartpredictor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Offline, causal, horizon-specific forecasting ensemble.
 *
 * Each horizon is evaluated against an expanding/rolling chronological validation window,
 * then tested on a separate later walk-forward holdout. Every forecast at time t uses
 * only candles up to t. Nearest-neighbor labels must end at or before t.
 *
 * This is not a pretrained AI model, nor a guarantee of statistical trading edge.
 */
final class WalkForwardEnsemble {
    static final class Forecast {
        final ChartAnalysis.Candle[] candles;
        final double[] lowBand, highBand;
        final String report;
        Forecast(ChartAnalysis.Candle[] c,double[] l,double[] u,String s){
            candles=c; lowBand=l; highBand=u; report=s;
        }
    }
    private static final String[] MODELS={
        "No-change","Volatility-adjusted momentum","AR(2) ridge","Historical analog kNN",
        "EMA mean reversion","Range/volume breakout"
    };
    private static final int M=MODELS.length, K=7;
    private final List<ChartAnalysis.Candle> candles;
    private final int n;
    private final double[] logPrice,ret,localVol;
    private final double[] volume;
    private final double[] low,high;
    private WalkForwardEnsemble(List<ChartAnalysis.Candle> candles){
        this.candles=candles;n=candles.size();
        logPrice=new double[n];ret=new double[n];localVol=new double[n];
        volume=new double[n];low=new double[n];high=new double[n];
        for(int i=0;i<n;i++){
            ChartAnalysis.Candle c=candles.get(i);
            logPrice[i]=Math.log(c.close);
            volume[i]=c.volume;low[i]=c.low;high[i]=c.high;
            if(i>0)ret[i]=logPrice[i]-logPrice[i-1];
        }
        for(int i=0;i<n;i++)localVol[i]=sigma(i);
    }
    private static double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}
    private static String fmt(double x){return String.format(Locale.US,"%.4f",x);}
    private static String pc(double x){return String.format(Locale.US,"%.2f%%",100*x);}
    private static double quantile(double[] x,int length,double fraction){
        if(length<=0)return Double.NaN;
        double[] copy=Arrays.copyOf(x,length);Arrays.sort(copy);
        return copy[(int)clamp(Math.ceil(fraction*length)-1,0,length-1)];
    }
    private double sigma(int t){
        int start=Math.max(1,t-27);int len=t-start+1;
        if(len<3)return .008;
        double mean=0,m2=0;
        for(int i=start;i<=t;i++){
            double r=clamp(ret[i],-.45,.45);
            mean+=r;
        }
        mean/=len;
        for(int i=start;i<=t;i++){
            double d=clamp(ret[i],-.45,.45)-mean;
            m2+=d*d;
        }
        return Math.max(0.00003,Math.sqrt(m2/Math.max(1,len-1)));
    }
    private double emaLog(int t,int span){
        int start=Math.max(0,t-4*span);
        double alpha=2.0/(span+1),e=logPrice[start];
        for(int i=start+1;i<=t;i++)e+=alpha*(logPrice[i]-e);
        return e;
    }
    private double momentum(int t,int horizon){
        double sum=0,total=0;
        for(int lag=0;lag<16&&t-lag>=1;lag++){
            double weight=Math.exp(-lag/5.0);
            sum+=weight*clamp(ret[t-lag],-4*localVol[t],4*localVol[t]);
            total+=weight;
        }
        double avg=sum/Math.max(1e-9,total);
        return avg*horizon*0.7;
    }
    private double ar2(int t,int h){
        int start=Math.max(3,t-160);
        if(t-start<25)return 0;
        double mean=0;int c=0;
        for(int i=start;i<=t;i++){mean+=ret[i];c++;}
        mean/=c;
        double a=0,b=0,d=0,e=0,z=0;
        for(int i=start+2;i<=t;i++){
            double x=clamp(ret[i-1]-mean,-.2,.2),y=clamp(ret[i-2]-mean,-.2,.2);
            double target=clamp(ret[i]-mean,-.2,.2);
            a+=x*x;b+=x*y;d+=y*y;e+=x*target;z+=y*target;
        }
        double ridge=(a+d)*.045+.000001;
        a+=ridge;d+=ridge;
        double determinant=a*d-b*b;
        if(Math.abs(determinant)<1e-14)return mean*h;
        double b1=clamp((d*e-b*z)/determinant,-.85,.85);
        double b2=clamp((a*z-b*e)/determinant,-.7,.7);
        double last=ret[t],prev=ret[t-1],result=0;
        for(int i=0;i<h;i++){
            double next=clamp(mean+b1*(last-mean)+b2*(prev-mean),-3*localVol[t],3*localVol[t]);
            result+=next;prev=last;last=next;
        }
        return result;
    }
    private double analogKnn(int t,int h){
        if(t-h<32)return 0;
        double[] bestDist=new double[K],bestReturn=new double[K];
        Arrays.fill(bestDist,Double.POSITIVE_INFINITY);
        double scale=Math.max(localVol[t],.00005);
        for(int end=Math.max(20,t-550);end<=t-h;end++){
            double pastScale=Math.max(localVol[end],.00005);
            double dist=0;
            for(int lag=0;lag<10;lag++){
                double cur=clamp(ret[t-lag]/scale,-4,4);
                double past=clamp(ret[end-lag]/pastScale,-4,4);
                double delta=cur-past;
                dist+=delta*delta*(lag<4?1.3:0.75);
            }
            double v1=volume[t],v0=volume[end];
            if(Double.isFinite(v1)&&Double.isFinite(v0)&&v1>0&&v0>0){
                // Penalize large liquidity-regime differences without letting volume dominate.
                double a=volumeRatio(t),b=volumeRatio(end);
                dist+=.18*Math.pow(clamp(a-b,-2,2),2);
            }
            if(dist>=bestDist[K-1])continue;
            double actual=logPrice[end+h]-logPrice[end];
            double bound=3.7*pastScale*Math.sqrt(h);
            actual=clamp(actual,-bound,bound);
            int pos=K-1;
            while(pos>0&&dist<bestDist[pos-1]){
                bestDist[pos]=bestDist[pos-1];bestReturn[pos]=bestReturn[pos-1];
                pos--;
            }
            bestDist[pos]=dist;bestReturn[pos]=actual;
        }
        double sum=0,w=0;
        for(int i=0;i<K;i++)if(Double.isFinite(bestDist[i])){
            double weight=1/(2.5+bestDist[i]);
            sum+=bestReturn[i]*weight;w+=weight;
        }
        return w>0?sum/w:0;
    }
    private double volumeRatio(int t){
        if(t<5||!Double.isFinite(volume[t])||volume[t]<=0)return 0;
        double sum=0;int count=0;
        for(int i=Math.max(0,t-20);i<t;i++)if(Double.isFinite(volume[i])&&volume[i]>0){
            sum+=volume[i];count++;
        }
        if(count<5||sum<=0)return 0;
        return clamp(Math.log(volume[t]/(sum/count)),-3,3);
    }
    private double reversion(int t,int h){
        double z=logPrice[t]-emaLog(t,24);
        return clamp(-z*(1-Math.exp(-h/6.0)), -3*localVol[t]*Math.sqrt(h),3*localVol[t]*Math.sqrt(h));
    }
    private double breakout(int t,int h){
        if(t<22)return 0;
        double hi=Double.NEGATIVE_INFINITY,lo=Double.POSITIVE_INFINITY;
        for(int i=t-20;i<t;i++){hi=Math.max(hi,high[i]);lo=Math.min(lo,low[i]);}
        double p=Math.exp(logPrice[t]);
        double span=Math.max(1e-10,hi-lo);
        double pos=(p-lo)/span;
        double v=Math.exp(clamp(volumeRatio(t),-1,1));
        double strength=clamp(2*(pos-.5),-1.5,1.5);
        return strength*localVol[t]*Math.sqrt(h)*.6*clamp(v,.6,1.5);
    }
    private double[] base(int t,int h){
        double sigma=Math.max(localVol[t],.00003);
        double limit=3.5*sigma*Math.sqrt(h);
        double[] m=new double[]{0,momentum(t,h),ar2(t,h),analogKnn(t,h),reversion(t,h),breakout(t,h)};
        for(int i=1;i<M;i++){
            if(!Double.isFinite(m[i]))m[i]=0;
            m[i]=clamp(m[i],-limit,limit);
        }
        return m;
    }
    private static double weighted(double[] values,double[] weights){
        double sum=0;for(int i=0;i<values.length;i++)sum+=values[i]*weights[i];
        return sum;
    }
    private static final class Evaluation {
        double[] weights;
        int validation,holdout;
        double mae,baselineMae,directionalAccuracy;
        double empiricalError80;
        boolean noEdge;
        int directionalSamples;
        String topModel;
    }
    private Evaluation evaluate(int horizon){
        Evaluation e=new Evaluation();
        e.weights=new double[M];
        e.weights[0]=1;
        int holdout=Math.min(34,Math.max(18,(n-100-horizon)/4));
        int validCount=Math.min(58,Math.max(25,(n-100-horizon)/3));
        int latest=n-horizon-1; // Outcome for origin t only known when t+h <= n-1.
        int valEnd=latest-holdout;
        int valStart=valEnd-validCount+1;
        // Long enough prior history for AR and analog matching.
        if(valStart<80||valEnd<=valStart){
            e.noEdge=true;
            return e;
        }
        double[] errorSum=new double[M],preds=new double[M];
        double baseline=0;int valN=0;
        for(int t=valStart;t<=valEnd;t++){
            preds=base(t,horizon);
            double actual=logPrice[t+horizon]-logPrice[t];
            baseline+=Math.abs(actual);
            for(int m=0;m<M;m++)errorSum[m]+=Math.abs(actual-preds[m]);
            valN++;
        }
        e.validation=valN;
        baseline/=valN;
        double[] weight=new double[M];
        weight[0]=.55;
        for(int m=1;m<M;m++){
            double mae=errorSum[m]/valN;
            if(mae<baseline*.995){
                // Only beat-baseline models can receive substantial weight.
                weight[m]=Math.min(1.1,.15+5*Math.max(0,(baseline-mae)/Math.max(baseline,1e-9)));
            }
        }
        double wSum=0;for(double w:weight)wSum+=w;
        for(int m=0;m<M;m++)weight[m]/=wSum;
        e.weights=weight;
        int top=0;for(int i=1;i<M;i++)if(weight[i]>weight[top])top=i;
        e.topModel=MODELS[top];

        int testStart=valEnd+1,testEnd=latest,testN=0,correct=0,nonzero=0;
        double totalMae=0,baseMae=0;
        double[] err=new double[holdout+4];
        for(int t=testStart;t<=testEnd;t++){
            double trueRet=logPrice[t+horizon]-logPrice[t];
            double pred=weighted(base(t,horizon),weight);
            totalMae+=Math.abs(trueRet-pred);
            baseMae+=Math.abs(trueRet);
            err[testN]=Math.abs(trueRet-pred);
            double significance=localVol[t]*Math.sqrt(horizon)*.08;
            if(Math.abs(pred)>significance&&Math.abs(trueRet)>significance){
                nonzero++;
                if(Math.signum(pred)==Math.signum(trueRet))correct++;
            }
            testN++;
        }
        e.holdout=testN;
        e.mae=totalMae/Math.max(1,testN);
        e.baselineMae=baseMae/Math.max(1,testN);
        e.directionalSamples=nonzero;
        e.directionalAccuracy=nonzero>0?(double)correct/nonzero:Double.NaN;
        e.empiricalError80=quantile(err,testN,.80);
        e.noEdge= e.mae>=e.baselineMae*.98 || nonzero<10 || (Double.isFinite(e.directionalAccuracy)&&e.directionalAccuracy<.52);
        return e;
    }
    static Forecast generate(List<ChartAnalysis.Candle> candles,int selected,String timeframe){
        WalkForwardEnsemble x=new WalkForwardEnsemble(candles);
        return x.predict(selected,timeframe);
    }
    private Forecast predict(int count,String timeframe){
        double initial=Math.exp(logPrice[n-1]);
        double[] predicted=new double[count],lower=new double[count],upper=new double[count];
        Evaluation[] evals=new Evaluation[count];
        int insufficient=0;
        for(int h=1;h<=count;h++){
            Evaluation e=evaluate(h);
            evals[h-1]=e;
            if(e.holdout==0)insufficient++;
            double[] b=base(n-1,h);
            double logMove=weighted(b,e.weights);
            // Model should be explicitly conservative if it failed its independent test.
            if(e.noEdge)logMove*=.15;
            double uncertainty=Math.max(e.empiricalError80,
                    1.15*localVol[n-1]*Math.sqrt(h));
            if(!Double.isFinite(uncertainty))uncertainty=1.3*localVol[n-1]*Math.sqrt(h);
            predicted[h-1]=initial*Math.exp(clamp(logMove,-.5,.5));
            lower[h-1]=initial*Math.exp(clamp(logMove-uncertainty,-.65,.65));
            upper[h-1]=initial*Math.exp(clamp(logMove+uncertainty,-.65,.65));
        }
        double atr=0;int atrN=0;
        for(int i=Math.max(0,n-20);i<n;i++){
            double spread=candles.get(i).high-candles.get(i).low;
            if(spread>=0){atr+=spread;atrN++;}
        }
        atr=atrN>0?atr/atrN:initial*.005;
        double lastClose=initial;
        ChartAnalysis.Candle[] result=new ChartAnalysis.Candle[count];
        for(int h=0;h<count;h++){
            double next=predicted[h],wick=Math.max(atr*.25,next*localVol[n-1]*.14);
            double high=Math.max(next,lastClose)+wick,low=Math.max(1e-9,Math.min(next,lastClose)-wick);
            result[h]=new ChartAnalysis.Candle(lastClose,high,low,next,Double.NaN);
            lastClose=next;
        }
        Evaluation finalEval=evals[count-1];
        StringBuilder report=new StringBuilder();
        report.append("OFFLINE ADAPTIVE FORECAST — HORIZON ").append(count)
            .append(" candles (").append(timeframe).append(")\n");
        report.append("Historical price candles: ").append(n).append(" (CSV input)\n");
        report.append("Model family: no-change, momentum, AR(2), analog kNN, EMA reversion, breakout.\n");
        report.append("Selection: strictly chronological validation; later walk-forward test.\n\n");
        if(insufficient>0){
            report.append("INSUFFICIENT HISTORY FOR VALIDATED FORECAST. ")
                .append("At least ~").append(175+count).append(" chronological candles recommended. ")
                .append("No trade signal can be justified.\n");
        }
        if(finalEval.holdout>0){
            report.append("Horizon ").append(count).append(" holdout: ")
                .append(finalEval.holdout).append(" origins (not random split).\n");
            report.append("Validation origins: ").append(finalEval.validation).append("\n");
            report.append("Absolute log-return error: ensemble ").append(fmt(finalEval.mae))
                .append(" vs no-change ").append(fmt(finalEval.baselineMae)).append("\n");
            report.append("Directional hit rate on non-negligible signals: ")
                .append(Double.isFinite(finalEval.directionalAccuracy)?pc(finalEval.directionalAccuracy):"N/A")
                .append(" (").append(finalEval.directionalSamples).append(" observations)\n");
            report.append("Largest model weight: ").append(finalEval.topModel).append("\n");
        }
        if(finalEval.noEdge){
            report.append("NO DEMONSTRATED EDGE — projected change is strongly shrunk toward unchanged price. Do not force a trade.\n");
        }else{
            report.append("Model beat no-change on this limited holdout; that does NOT prove future profit or reliable probabilities.\n");
        }
        report.append("\nLatest close: ").append(fmt(initial)).append("\n");
        report.append("Estimated close after ").append(count).append(": ").append(fmt(predicted[count-1])).append("\n");
        report.append("Historical-error-based ~80% range: ")
            .append(fmt(lower[count-1])).append(" – ").append(fmt(upper[count-1])).append("\n");
        report.append("Band is empirical and may fail during regime shifts; 80% is a target reference, NOT a guaranteed calibrated probability.\n\n");
        report.append("Data must be oldest to newest, one instrument and exactly the selected timeframe, with no missing/split-adjustment issues. No transaction costs, slippage or order execution are included. Research only.");
        return new Forecast(result,lower,upper,report.toString());
    }
}
