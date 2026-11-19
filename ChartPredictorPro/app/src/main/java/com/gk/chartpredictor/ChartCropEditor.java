package com.gk.chartpredictor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A real, zoomed crop selector for uploaded chart screenshots.
 *  Crop the price candles only. Leave out top/bottom UI and volume bars.
 */
public final class ChartCropEditor {
    private ChartCropEditor(){}
    public interface OnCrop {
        void onCropped(Bitmap cropped);
    }
    private static int dp(Activity a,float value){
        return (int)(a.getResources().getDisplayMetrics().density*value+.5f);
    }
    public static void show(Activity activity,Bitmap bitmap,OnCrop callback){
        final LinearLayout root=new LinearLayout(activity);
        root.setPadding(dp(activity,8),dp(activity,8),dp(activity,8),dp(activity,8));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(9,17,30));
        TextView hint=new TextView(activity);
        hint.setText("Drag inside to move. Drag the corners or borders to resize. Select ONLY candle price area; exclude volume, labels and toolbars.");
        hint.setTextColor(Color.rgb(215,230,246));
        hint.setTextSize(13);
        hint.setPadding(dp(activity,10),dp(activity,8),dp(activity,10),dp(activity,10));
        root.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        CropView view=new CropView(activity,bitmap);
        int panelHeight=Math.min(dp(activity,660),(int)(activity.getResources().getDisplayMetrics().heightPixels*.75f));
        root.addView(view,new LinearLayout.LayoutParams(-1,panelHeight));
        new AlertDialog.Builder(activity)
           .setTitle("Crop to the real candlestick plot")
           .setView(root)
           .setPositiveButton("ANALYZE THIS AREA",(dialog,which)->{
               Bitmap crop=view.getCrop();
               if(crop!=null)callback.onCropped(crop);
           })
           .setNegativeButton("CANCEL",null)
           .show();
    }
    private static final class CropView extends View {
        private final Bitmap source;
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF dest=new RectF();
        private float left=.06f,top=.11f,right=.94f,bottom=.73f;
        private float downX,downY,fromL,fromT,fromR,fromB;
        private int mode=0;
        private final int margin;
        private final float hit;

        CropView(Activity context, Bitmap source){
            super(context);
            this.source=source;
            margin=dp(context,11);
            hit=dp(context,29);
            setBackgroundColor(Color.rgb(14,27,43));
        }
        private static float clip(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
        private void layoutImage(){
            float availW=Math.max(1,getWidth()-2*margin);
            float availH=Math.max(1,getHeight()-2*margin);
            float scale=Math.min(availW/source.getWidth(),availH/source.getHeight());
            float w=scale*source.getWidth(),h=scale*source.getHeight();
            dest.set((getWidth()-w)/2f,(getHeight()-h)/2f,(getWidth()+w)/2f,(getHeight()+h)/2f);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            layoutImage();
            p.setAlpha(255);p.setStyle(Paint.Style.FILL);
            canvas.drawBitmap(source,null,dest,p);
            float l=dest.left+left*dest.width(),t=dest.top+top*dest.height();
            float r=dest.left+right*dest.width(),b=dest.top+bottom*dest.height();
            p.setColor(Color.argb(148,6,14,27));
            canvas.drawRect(dest.left,dest.top,dest.right,t,p);
            canvas.drawRect(dest.left,b,dest.right,dest.bottom,p);
            canvas.drawRect(dest.left,t,l,b,p);
            canvas.drawRect(r,t,dest.right,b,p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp((Activity)getContext(),2.5f));
            p.setColor(Color.rgb(42,224,188));
            canvas.drawRect(l,t,r,b,p);
            p.setStyle(Paint.Style.FILL);
            for(float x:new float[]{l,r}){
                for(float y:new float[]{t,b}){
                    p.setColor(Color.WHITE);
                    canvas.drawCircle(x,y,dp((Activity)getContext(),7),p);
                    p.setColor(Color.rgb(21,159,208));
                    canvas.drawCircle(x,y,dp((Activity)getContext(),4),p);
                }
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            layoutImage();
            float x=event.getX(),y=event.getY();
            float l=dest.left+left*dest.width(),t=dest.top+top*dest.height();
            float r=dest.left+right*dest.width(),b=dest.top+bottom*dest.height();
            if(event.getAction()==MotionEvent.ACTION_DOWN){
                downX=x;downY=y;
                fromL=left;fromT=top;fromR=right;fromB=bottom;
                if(Math.abs(x-l)<hit&&Math.abs(y-t)<hit)mode=2;
                else if(Math.abs(x-r)<hit&&Math.abs(y-b)<hit)mode=3;
                else if(Math.abs(x-l)<hit&&Math.abs(y-b)<hit)mode=8;
                else if(Math.abs(x-r)<hit&&Math.abs(y-t)<hit)mode=9;
                else if(Math.abs(x-l)<hit&&y>=t&&y<=b)mode=4;
                else if(Math.abs(x-r)<hit&&y>=t&&y<=b)mode=5;
                else if(Math.abs(y-t)<hit&&x>=l&&x<=r)mode=6;
                else if(Math.abs(y-b)<hit&&x>=l&&x<=r)mode=7;
                else if(x>=l&&x<=r&&y>=t&&y<=b)mode=1;
                else mode=0;
                return true;
            }
            if(event.getAction()==MotionEvent.ACTION_MOVE&&mode!=0){
                float dx=(x-downX)/Math.max(1,dest.width());
                float dy=(y-downY)/Math.max(1,dest.height());
                if(mode==1){
                    float ww=fromR-fromL,hh=fromB-fromT;
                    left=clip(fromL+dx,0,1-ww);right=left+ww;
                    top=clip(fromT+dy,0,1-hh);bottom=top+hh;
                }else{
                    if(mode==2||mode==4||mode==8)left=clip(fromL+dx,0,right-.08f);
                    if(mode==3||mode==5||mode==9)right=clip(fromR+dx,left+.08f,1);
                    if(mode==2||mode==6||mode==9)top=clip(fromT+dy,0,bottom-.08f);
                    if(mode==3||mode==7||mode==8)bottom=clip(fromB+dy,top+.08f,1);
                }
                invalidate();return true;
            }
            if(event.getAction()==MotionEvent.ACTION_UP){mode=0;return true;}
            return true;
        }
        Bitmap getCrop(){
            int l=(int)(left*source.getWidth()),t=(int)(top*source.getHeight());
            int r=(int)(right*source.getWidth()),b=(int)(bottom*source.getHeight());
            r=Math.min(source.getWidth(),Math.max(l+1,r));
            b=Math.min(source.getHeight(),Math.max(t+1,b));
            if(r-l<12||b-t<12)return null;
            return Bitmap.createBitmap(source,l,t,r-l,b-t);
        }
    }
}
