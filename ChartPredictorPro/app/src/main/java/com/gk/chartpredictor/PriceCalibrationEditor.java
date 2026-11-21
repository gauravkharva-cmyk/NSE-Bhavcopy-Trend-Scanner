package com.gk.chartpredictor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Move two horizontal guide lines to known prices on a CROPPED chart.
 * Neither OCR nor guessed market quotations are used.
 */
public final class PriceCalibrationEditor {
    private PriceCalibrationEditor(){}
    public interface OnSaved {
        void onSaved(ImagePriceCalibration.Calibration calibration);
    }
    private static int dp(Activity a,float x){
        return (int)(a.getResources().getDisplayMetrics().density*x+.5f);
    }
    private static TextView label(Activity a,String value,int size){
        TextView t=new TextView(a);
        t.setText(value);
        t.setTextColor(Color.rgb(218,234,249));
        t.setTextSize(size);
        return t;
    }
    public static void show(Activity a,Bitmap cropped,
            ImagePriceCalibration.Calibration old,OnSaved callback){
        if(cropped==null)return;
        LinearLayout column=new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,12));
        column.setBackgroundColor(Color.rgb(10,19,34));
        TextView intro=label(a,
                "Drag the CYAN upper line and ORANGE lower line onto two known price levels on your cropped chart. Enter each line's EXACT axis value below. This does not read prices automatically.",13);
        intro.setPadding(0,0,0,dp(a,12));
        column.addView(intro);
        GuidesView guides=new GuidesView(a,cropped,old);
        int height=(int)Math.min(dp(a,450),a.getResources().getDisplayMetrics().heightPixels*.50);
        column.addView(guides,new LinearLayout.LayoutParams(-1,height));

        TextView upperLabel=label(a,"UPPER LINE PRICE (from chart axis)",13);
        upperLabel.setPadding(0,dp(a,12),0,dp(a,3));
        column.addView(upperLabel);
        EditText upper=new EditText(a);
        upper.setSingleLine(true);
        upper.setHint("e.g. 26000");
        upper.setTextColor(Color.WHITE);
        upper.setHintTextColor(Color.rgb(138,159,180));
        upper.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if(old!=null)upper.setText(String.format(java.util.Locale.US,"%.3f",old.upperPrice));
        column.addView(upper);

        TextView lowerLabel=label(a,"LOWER LINE PRICE (from chart axis)",13);
        lowerLabel.setPadding(0,dp(a,9),0,dp(a,3));
        column.addView(lowerLabel);
        EditText lower=new EditText(a);
        lower.setSingleLine(true);
        lower.setHint("e.g. 25000");
        lower.setTextColor(Color.WHITE);
        lower.setHintTextColor(Color.rgb(138,159,180));
        lower.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if(old!=null)lower.setText(String.format(java.util.Locale.US,"%.3f",old.lowerPrice));
        column.addView(lower);
        TextView caveat=label(a,"For reliable alignment, select two prices with known vertical positions. If the price labels were cropped away, refer to the original screenshot. Levels will remain APPROXIMATE because the extracted candle shapes are not verified exchange data.",12);
        caveat.setPadding(0,dp(a,12),0,dp(a,6));
        column.addView(caveat);
        ScrollView scroll=new ScrollView(a);
        scroll.setFillViewport(false);
        scroll.addView(column);
        AlertDialog dialog=new AlertDialog.Builder(a)
                .setTitle("Manual price-axis calibration")
                .setView(scroll)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Apply levels",null)
                .create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                double high,low;
                try{
                    high=Double.parseDouble(upper.getText().toString().trim());
                    low=Double.parseDouble(lower.getText().toString().trim());
                }catch(Exception e){
                    upper.setError("Enter numeric values for both lines.");
                    lower.setError("Enter numeric values for both lines.");
                    return;
                }
                try{
                    ImagePriceCalibration.Calibration result=
                            new ImagePriceCalibration.Calibration(high,low,guides.upper(),guides.lower());
                    callback.onSaved(result);
                    dialog.dismiss();
                }catch(IllegalArgumentException e){
                    upper.setError(e.getMessage());
                }
            });
        });
        dialog.show();
    }
    private static final class GuidesView extends View {
        private final Activity activity;
        private final Bitmap image;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final RectF rect=new RectF();
        private float upper=.20f,lower=.80f;
        private int active=0;
        GuidesView(Activity a,Bitmap bmp,ImagePriceCalibration.Calibration saved){
            super(a);
            activity=a;image=bmp;
            if(saved!=null){upper=saved.upperFraction;lower=saved.lowerFraction;}
            setBackgroundColor(Color.rgb(19,33,50));
        }
        float upper(){return upper;}
        float lower(){return lower;}
        private void layout(){
            float usableW=getWidth()-2*dp(activity,10);
            float usableH=getHeight()-2*dp(activity,10);
            float factor=Math.min(usableW/image.getWidth(),usableH/image.getHeight());
            float w=image.getWidth()*factor,h=image.getHeight()*factor;
            rect.set((getWidth()-w)/2,(getHeight()-h)/2,
                    (getWidth()+w)/2,(getHeight()+h)/2);
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            layout();
            p.setColor(Color.WHITE);
            p.setAlpha(255);
            p.setStyle(Paint.Style.FILL);
            c.drawBitmap(image,null,rect,p);
            float y1=rect.top+upper*rect.height(),y2=rect.top+lower*rect.height();
            drawLine(c,y1,Color.rgb(42,221,220),"UPPER · drag");
            drawLine(c,y2,Color.rgb(249,173,77),"LOWER · drag");
        }
        private void drawLine(Canvas c,float y,int color,String text){
            p.setAlpha(255);
            p.setColor(color);
            p.setStrokeWidth(dp(activity,2));
            c.drawLine(rect.left,y,rect.right,y,p);
            p.setTextSize(dp(activity,12));
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(9,19,30));
            c.drawRect(rect.left,y-dp(activity,16),
                    rect.left+dp(activity,115),y-dp(activity,1),p);
            p.setColor(color);
            c.drawText(text,rect.left+dp(activity,5),y-dp(activity,4),p);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            layout();
            float y=event.getY();
            if(event.getAction()==MotionEvent.ACTION_DOWN){
                float a=Math.abs(y-(rect.top+upper*rect.height()));
                float b=Math.abs(y-(rect.top+lower*rect.height()));
                active=a<=b?1:2;
                return true;
            }
            if(event.getAction()==MotionEvent.ACTION_MOVE&&active!=0){
                float frac=(y-rect.top)/Math.max(1,rect.height());
                if(active==1)upper=Math.max(.02f,Math.min(lower-.12f,frac));
                else lower=Math.min(.98f,Math.max(upper+.12f,frac));
                invalidate();
                return true;
            }
            if(event.getAction()==MotionEvent.ACTION_UP){active=0;return true;}
            return true;
        }
    }
}
