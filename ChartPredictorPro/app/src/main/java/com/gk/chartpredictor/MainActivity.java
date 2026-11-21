package com.gk.chartpredictor;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.io.InputStream;

public class MainActivity extends Activity {
    private final int bg=Color.rgb(9,16,29);
    private final int panel=Color.rgb(20,31,50);
    private final int fg=Color.WHITE;
    private final int muted=Color.rgb(157,172,194);
    private final int green=Color.rgb(41,204,143);
    private LinearLayout body;
    private ImageView preview;
    private TextView output;
    private Spinner timeframe, horizon;
    private Bitmap uploaded;
    private Bitmap originalUploaded;
    private boolean croppedToPlot=false;
    private TextView cropButton;
    private byte[] csvBytes;
    private int horizonCount=5;
    private String selectedTimeframe="5 min";
    private ForecastChart resultChart;
    private boolean analysisRunning=false;
    private CheckBox screenshotGuess;

    private int dp(float x){ return (int)(x*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable shape(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private TextView txt(String s,int size,int color){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        return t;
    }
    private void gap(int amount){
        Space s=new Space(this);
        body.addView(s,new LinearLayout.LayoutParams(1,dp(amount)));
    }
    private TextView button(String s){
        TextView t=txt(s,16,fg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16),dp(15),dp(16),dp(15));
        t.setBackground(shape(Color.rgb(25,109,191),13));
        return t;
    }
    private Spinner spin(String[] options){
        Spinner s=new Spinner(this);
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
        s.setAdapter(a);
        s.setBackground(shape(Color.rgb(33,49,72),12));
        s.setPadding(dp(12),dp(8),dp(12),dp(8));
        return s;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20),dp(28),dp(20),dp(28));
        scroll.addView(body);
        setContentView(scroll);

        TextView header=txt("CHART PREDICTOR PRO",23,fg);
        header.setTypeface(null,1);
        body.addView(header);
        gap(7);
        body.addView(txt("V7 · Screenshot chart scenarios · OHLCV forecasting · pattern atlas",13,muted));
        gap(22);

        body.addView(txt("1  ·  UPLOAD YOUR CANDLESTICK CHART",15,fg));
        gap(9);
        preview=new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(shape(panel,14));
        preview.setMinimumHeight(dp(190));
        body.addView(preview,new LinearLayout.LayoutParams(-1,dp(300)));
        preview.setOnClickListener(v->{if(originalUploaded!=null)openCropEditor();});
        gap(12);
        TextView upload=button("UPLOAD CHART IMAGE");
        body.addView(upload);
        upload.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i,"Select chart screenshot"),101);
        });
        gap(10);
        cropButton=button("CROP / ZOOM CHART AREA");
        cropButton.setBackground(shape(Color.rgb(23,130,112),13));
        cropButton.setVisibility(View.GONE);
        body.addView(cropButton);
        cropButton.setOnClickListener(v->openCropEditor());
        gap(10);
        TextView importCsv=button("IMPORT OHLCV CSV (REAL INDICATORS)");
        body.addView(importCsv);
        importCsv.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i,"Select candle CSV (oldest row first)"),102);
        });
        gap(10);
        TextView csvHelp=button("CSV FILE FORMAT / HELP");
        csvHelp.setBackground(shape(Color.rgb(36,54,81),13));
        body.addView(csvHelp);
        csvHelp.setOnClickListener(v->new android.app.AlertDialog.Builder(this)
            .setTitle("OHLCV candle data — for price-based forecasts")
            .setMessage("Export candle history for ONE instrument from your trading/data platform. The file must have columns Open, High, Low, Close and preferably Volume.\n\nExample:\nOpen,High,Low,Close,Volume\n240.20,242.10,239.80,241.50,12500\n241.50,244.00,240.60,243.20,13200\n\nPut the OLDEST candles first. Choose the same timeframe in this app as the CSV candles actually represent. Do not mix timeframes, strikes, split-adjustment conventions or instruments.\n\nAim for 200+ contiguous candles. This is an uncertainty-aware research model, not a guaranteed trade signal.")
            .setPositiveButton("Close",null).show());
        gap(21);

        body.addView(txt("2  ·  SELECT FUTURE CANDLES",15,fg));
        gap(8);
        horizon=spin(new String[]{"1 candle","3 candles","5 candles","10 candles","20 candles"});
        horizon.setSelection(2);
        body.addView(horizon);
        gap(17);

        body.addView(txt("3  ·  CHOOSE TIMEFRAME",15,fg));
        gap(8);
        timeframe=spin(new String[]{"1 min","3 min","5 min","15 min","30 min","1 hour","4 hours","Daily","Weekly"});
        timeframe.setSelection(2);
        body.addView(timeframe);
        gap(18);
        body.addView(txt("4  ·  SCREENSHOT-ONLY SCENARIO",15,fg));
        gap(9);
        screenshotGuess=new CheckBox(this);
        screenshotGuess.setText("Show experimental next-candle visual guess");
        screenshotGuess.setTextSize(15);
        screenshotGuess.setTextColor(Color.WHITE);
        screenshotGuess.setButtonTintList(android.content.res.ColorStateList.valueOf(green));
        screenshotGuess.setChecked(true);
        body.addView(screenshotGuess);
        body.addView(txt("This is NOT a validated prediction or market probability. It draws a shape-based possible continuation, with alternative up/down ranges. Turn this off to show detected history only.",13,muted));
        gap(20);

        TextView analyze=button("ANALYZE CHART");
        body.addView(analyze);
        analyze.setOnClickListener(v->analyze());
        gap(18);

        output=txt("Upload a screenshot, crop the candle chart and select your timeframe. Experimental screenshot guessing is available, clearly marked as an untested visual sketch. For data-based forecasting, import OHLCV CSV.",14,muted);
        output.setPadding(dp(16),dp(16),dp(16),dp(16));
        output.setBackground(shape(panel,13));
        body.addView(output);
        gap(15);

        resultChart=new ForecastChart();
        resultChart.setBackground(shape(panel,14));
        resultChart.setVisibility(View.GONE);
        body.addView(resultChart,new LinearLayout.LayoutParams(-1,dp(310)));
        gap(14);

        body.addView(txt("5  ·  OFFLINE PATTERN REFERENCE",15,fg));
        gap(9);
        TextView atlas=button("EXPLORE 24 HIGH-RES PATTERN EXAMPLES");
        body.addView(atlas);
        atlas.setOnClickListener(v->PatternLibrary.show(this));
        gap(13);
        body.addView(txt("All pattern illustrations are original and synthetic. Learn how setups look before using them on actual OHLCV charts.",13,muted));
        gap(22);
        body.addView(txt("Screenshot scenarios are illustrative extrapolations from approximate image pixels, NOT tested forecasts. For price-based indicators, model backtesting, and empirical uncertainty, import OHLCV CSV. No forecast guarantees profits.",12,muted));
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==102&&result==RESULT_OK&&data!=null){
            try(InputStream in=getContentResolver().openInputStream(data.getData())){
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                byte[] buffer=new byte[8192];int read;
                while((read=in.read(buffer))!=-1){
                    if(bytes.size()+read>4000000)throw new Exception("CSV too large (maximum 4 MB)");
                    bytes.write(buffer,0,read);
                }
                csvBytes=bytes.toByteArray();
                uploaded=null;
                originalUploaded=null;
                croppedToPlot=false;
                cropButton.setVisibility(View.GONE);
                preview.setImageDrawable(null);
                resultChart.setVisibility(View.GONE);
                output.setText("OHLCV CSV loaded: "+csvBytes.length+" bytes. Make sure data runs from oldest to newest. Tap ANALYZE CHART.");
            }catch(Exception e){output.setText("Unable to import CSV: "+e.getMessage());}
            return;
        }
        if(request==101&&result==RESULT_OK&&data!=null){
            try(InputStream in=getContentResolver().openInputStream(data.getData())){
                Bitmap raw=BitmapFactory.decodeStream(in);
                if(raw==null)throw new Exception("Unsupported image format");
                int longest=Math.max(raw.getWidth(),raw.getHeight());
                if(longest>1800){
                    float scale=1800f/longest;
                    uploaded=Bitmap.createScaledBitmap(raw,(int)(raw.getWidth()*scale),(int)(raw.getHeight()*scale),true);
                    if(uploaded!=raw)raw.recycle();
                }else uploaded=raw;
                csvBytes=null;
                originalUploaded=uploaded;
                croppedToPlot=false;
                cropButton.setVisibility(View.VISIBLE);
                preview.setImageBitmap(uploaded);
                LinearLayout.LayoutParams previewParams=(LinearLayout.LayoutParams)preview.getLayoutParams();
                previewParams.height=dp(365);
                preview.setLayoutParams(previewParams);
                resultChart.setVisibility(View.GONE);
                output.setText("Image uploaded. Tap CROP / ZOOM CHART AREA and select only the candle plot (exclude toolbar, price labels and volume histogram), then analyze.");
            }catch(Exception e){
                output.setText("Unable to load image: "+e.getMessage());
            }
        }
    }
    private void openCropEditor(){
        if(originalUploaded==null)return;
        ChartCropEditor.show(this,originalUploaded,cropped->{
            uploaded=cropped;
            croppedToPlot=true;
            preview.setImageBitmap(cropped);
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)preview.getLayoutParams();
            lp.height=dp(310);
            preview.setLayoutParams(lp);
            output.setText("Selected chart area is ready. Tap ANALYZE CHART. Touch the image to crop again.");
            resultChart.setVisibility(View.GONE);
        });
    }
    private void analyze(){
        if(analysisRunning)return;
        if(uploaded!=null && !croppedToPlot && csvBytes==null){
            output.setText("Crop the screenshot to the actual candle plot before analysis. It prevents volume bars and UI elements from being mistaken for candles.");
            openCropEditor();
            return;
        }
        if(uploaded==null&&csvBytes==null){
            output.setText("Upload a chart screenshot or import chronological OHLCV CSV first.");
            return;
        }
        String selected=horizon.getSelectedItem().toString();
        horizonCount=Integer.parseInt(selected.split(" ")[0]);
        selectedTimeframe=timeframe.getSelectedItem().toString();
        final int targetCount=horizonCount;
        final String frame=selectedTimeframe;
        final Bitmap img=uploaded;
        final byte[] file=csvBytes;
        final boolean imageGuess=screenshotGuess.isChecked();
        analysisRunning=true;
        output.setText(file==null?"Reading cropped screenshot shapes and drawing experimental, UNVALIDATED scenarios...":"Calculating "+targetCount+"-candle scenarios and evaluating error on later chronological price candles...");
        resultChart.setVisibility(View.GONE);
        new Thread(()->{
            try{
                ChartAnalysis.Result r=file==null?
                        ChartAnalysis.fromImage(img,targetCount,frame,imageGuess):
                        ChartAnalysis.fromCsv(new java.io.ByteArrayInputStream(file),targetCount,frame);
                runOnUiThread(()->{
                    analysisRunning=false;
                    output.setText(r.report);
                    resultChart.setResult(r);
                    resultChart.setVisibility(View.VISIBLE);
                });
            }catch(Exception e){
                final String message=e.getMessage();
                runOnUiThread(()->{
                    analysisRunning=false;
                    output.setText("Analysis unavailable: "+message+"\n\nTry a tighter screenshot crop or import a chronological OHLCV CSV with sufficient history.");
                    resultChart.setVisibility(View.GONE);
                });
            }
        }).start();
    }
    private class ForecastChart extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private ChartAnalysis.Result result;
        ForecastChart(){ super(MainActivity.this); }
        void setResult(ChartAnalysis.Result r){result=r;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(result==null)return;
            float width=getWidth(),height=getHeight();
            float left=dp(14),top=dp(18),cw=width-dp(28),ch=height-dp(54);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(52,67,91));
            for(int i=0;i<5;i++){
                float y=top+(ch/4)*i;
                canvas.drawLine(left,y,width-left,y,paint);
            }
            ChartAnalysis.Candle[] actual=result.actual, future=result.projected;
            int hist=Math.min(actual.length,Math.max(18,45-future.length/2));
            int total=hist+future.length;
            double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
            for(int i=actual.length-hist;i<actual.length;i++){
                lo=Math.min(lo,actual[i].low);hi=Math.max(hi,actual[i].high);
            }
            for(ChartAnalysis.Candle c:future){lo=Math.min(lo,c.low);hi=Math.max(hi,c.high);}
            boolean hasBands=result.lowBand!=null&&result.highBand!=null
                    &&result.lowBand.length==future.length&&result.highBand.length==future.length;
            if(hasBands)for(int i=0;i<future.length;i++){
                lo=Math.min(lo,result.lowBand[i]);hi=Math.max(hi,result.highBand[i]);
            }
            double span=Math.max(.000001,hi-lo);
            lo-=span*.12;hi+=span*.12;
            span=hi-lo;
            float pitch=cw/total,barWidth=Math.max(dp(2),Math.min(dp(11),pitch*.64f));
            if(hasBands){
                Path region=new Path();
                for(int i=0;i<future.length;i++){
                    float x=left+pitch*(hist+i+.5f);
                    float y=top+(float)((hi-result.highBand[i])/span)*ch;
                    if(i==0)region.moveTo(x,y);else region.lineTo(x,y);
                }
                for(int i=future.length-1;i>=0;i--){
                    float x=left+pitch*(hist+i+.5f);
                    float y=top+(float)((hi-result.lowBand[i])/span)*ch;
                    region.lineTo(x,y);
                }
                region.close();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(48,66,149,245));
                canvas.drawPath(region,paint);
                paint.setColor(Color.rgb(106,175,247));
                paint.setStrokeWidth(dp(1.2f));
                for(int i=1;i<future.length;i++){
                    float xa=left+pitch*(hist+i-.5f),xb=left+pitch*(hist+i+.5f);
                    float yu0=top+(float)((hi-result.highBand[i-1])/span)*ch;
                    float yu1=top+(float)((hi-result.highBand[i])/span)*ch;
                    float yl0=top+(float)((hi-result.lowBand[i-1])/span)*ch;
                    float yl1=top+(float)((hi-result.lowBand[i])/span)*ch;
                    canvas.drawLine(xa,yu0,xb,yu1,paint);
                    canvas.drawLine(xa,yl0,xb,yl1,paint);
                }
            }
            for(int i=0;i<total;i++){
                boolean projected=i>=hist;
                ChartAnalysis.Candle c=projected?future[i-hist]:actual[actual.length-hist+i];
                float x=left+pitch*(i+.5f);
                float yOpen=top+(float)((hi-c.open)/span)*ch;
                float yClose=top+(float)((hi-c.close)/span)*ch;
                float yHigh=top+(float)((hi-c.high)/span)*ch;
                float yLow=top+(float)((hi-c.low)/span)*ch;
                paint.setColor(c.close>=c.open?Color.rgb(35,207,139):Color.rgb(244,91,105));
                paint.setAlpha(projected?135:255);
                paint.setStrokeWidth(dp(1.5f));
                canvas.drawLine(x,yHigh,x,yLow,paint);
                canvas.drawRect(x-barWidth/2,Math.min(yOpen,yClose),x+barWidth/2,
                    Math.max(Math.min(yOpen,yClose)+dp(1.5f),Math.max(yOpen,yClose)),paint);
                paint.setAlpha(255);
            }
            float divider=left+pitch*hist;
            paint.setColor(Color.rgb(131,153,182));
            paint.setStrokeWidth(dp(1.2f));
            canvas.drawLine(divider,top,divider,top+ch,paint);
            paint.setColor(Color.rgb(214,223,238));
            paint.setTextSize(dp(11));
            boolean imageOnly=result.report.startsWith("SCREENSHOT");
            canvas.drawText(imageOnly?"Screenshot candle candidates":"OHLCV history",left,height-dp(11),paint);
            if(future.length==0)canvas.drawText("No scenario",Math.max(left,width-dp(145)),height-dp(11),paint);
            else canvas.drawText(imageOnly?"Visual sketch + alternatives":"Forecast + test error range",Math.min(divider+dp(4),width-dp(178)),height-dp(11),paint);
        }
    }
}
