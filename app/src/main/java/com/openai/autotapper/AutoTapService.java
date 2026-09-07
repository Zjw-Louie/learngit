package com.openai.autotapper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Random;

public class AutoTapService extends AccessibilityService {
    private static final long[] INTERVALS={1,2,5,10,20,50,100,200,500,1000};
    private final Handler h=new Handler(Looper.getMainLooper());
    private final Random rnd=new Random();
    private WindowManager wm;
    private RegionView region;
    private LinearLayout panel;
    private WindowManager.LayoutParams rp,pp;
    private TextView status;
    private boolean running=false, randomMode=true;
    private int idx=3;

    @Override protected void onServiceConnected(){ super.onServiceConnected(); h.post(this::show); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){ stop(); }
    @Override public void onDestroy(){ stop(); remove(); super.onDestroy(); }

    private void show(){
        if(panel!=null)return;
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm=getResources().getDisplayMetrics();
        int rw=Math.max(dp(180),dm.widthPixels/2), rh=Math.max(dp(150),dm.heightPixels/4);
        rp=new WindowManager.LayoutParams(rw,rh,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        rp.gravity=Gravity.TOP|Gravity.START; rp.x=(dm.widthPixels-rw)/2; rp.y=Math.max(dp(150),(dm.heightPixels-rh)/2);
        region=new RegionView(); wm.addView(region,rp);

        panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(8),dp(6),dp(8),dp(8));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xE6222228); bg.setCornerRadius(dp(12)); panel.setBackground(bg);
        TextView header=new TextView(this); header.setText("区域连点器 · 拖动"); header.setTextColor(Color.WHITE); header.setTextSize(13); panel.addView(header);
        dragPanel(header);
        status=new TextView(this); status.setTextColor(Color.WHITE); status.setTextSize(14); status.setPadding(0,dp(4),0,dp(4)); panel.addView(status);

        LinearLayout r1=new LinearLayout(this);
        Button faster=btn("更快"), slower=btn("更慢"), mode=btn("随机/中心");
        r1.addView(faster); r1.addView(slower); r1.addView(mode); panel.addView(r1);
        LinearLayout r2=new LinearLayout(this);
        Button toggle=btn("区域"), start=btn("开始"), stop=btn("停止");
        r2.addView(toggle); r2.addView(start); r2.addView(stop); panel.addView(r2);

        faster.setOnClickListener(v->{ if(idx>0)idx--; update(); });
        slower.setOnClickListener(v->{ if(idx<INTERVALS.length-1)idx++; update(); });
        mode.setOnClickListener(v->{ randomMode=!randomMode; update(); });
        toggle.setOnClickListener(v->{ if(!running)region.setVisibility(region.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE); });
        start.setOnClickListener(v->start()); stop.setOnClickListener(v->stop());

        pp=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        pp.gravity=Gravity.TOP|Gravity.START; pp.x=dp(12); pp.y=dp(60); wm.addView(panel,pp); update();
    }

    private Button btn(String s){ Button b=new Button(this); b.setText(s); b.setTextSize(12); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(8),0,dp(8),0); return b; }
    private void start(){ if(running||rp==null)return; running=true; region.setVisibility(View.GONE); update(); dispatchBatch(); }
    private void stop(){ running=false; if(region!=null)region.setVisibility(View.VISIBLE); update(); }

    private void dispatchBatch(){
        if(!running)return;
        long interval=INTERVALS[idx];
        int max=GestureDescription.getMaxStrokeCount();
        int count=Math.max(1,Math.min(max,(int)(240/Math.max(1,interval))+1));
        GestureDescription.Builder b=new GestureDescription.Builder();
        long maxDur=GestureDescription.getMaxGestureDuration(); int added=0;
        for(int i=0;i<count;i++){
            long t=i*interval; if(t+1>=maxDur)break;
            float[] p=point(); Path path=new Path(); path.moveTo(p[0],p[1]);
            b.addStroke(new GestureDescription.StrokeDescription(path,t,1)); added++;
        }
        if(added==0){ h.postDelayed(this::dispatchBatch,Math.max(1,interval)); return; }
        boolean ok=dispatchGesture(b.build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription g){ if(running)h.postDelayed(AutoTapService.this::dispatchBatch,Math.max(0,INTERVALS[idx]-1)); }
            @Override public void onCancelled(GestureDescription g){ if(running)h.postDelayed(AutoTapService.this::dispatchBatch,Math.max(1,INTERVALS[idx])); }
        },h);
        if(!ok&&running)h.postDelayed(this::dispatchBatch,Math.max(5,interval));
    }

    private float[] point(){
        int mx=Math.max(dp(8),(int)(rp.width*.1f)), my=Math.max(dp(8),(int)(rp.height*.1f));
        if(!randomMode)return new float[]{rp.x+rp.width/2f,rp.y+rp.height/2f};
        int l=rp.x+mx,t=rp.y+my,r=rp.x+rp.width-mx,b=rp.y+rp.height-my;
        return new float[]{l+rnd.nextFloat()*Math.max(1,r-l),t+rnd.nextFloat()*Math.max(1,b-t)};
    }

    private void update(){ if(status!=null)status.setText((running?"运行中":"已停止")+" | "+INTERVALS[idx]+" ms | "+(randomMode?"随机区域":"中心点")); }
    private void dragPanel(View v){
        final float[] d=new float[2]; final int[] s=new int[2];
        v.setOnTouchListener((x,e)->{ if(pp==null)return false; switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:d[0]=e.getRawX();d[1]=e.getRawY();s[0]=pp.x;s[1]=pp.y;return true;
            case MotionEvent.ACTION_MOVE:pp.x=Math.round(s[0]+e.getRawX()-d[0]);pp.y=Math.round(s[1]+e.getRawY()-d[1]);wm.updateViewLayout(panel,pp);return true;
        } return true; });
    }
    private void remove(){ if(wm==null)return; try{if(region!=null)wm.removeView(region);}catch(Exception ignored){} try{if(panel!=null)wm.removeView(panel);}catch(Exception ignored){} }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    private class RegionView extends View{
        private final Paint fill=new Paint(1), stroke=new Paint(1), text=new Paint(1);
        private float downX,downY,pinch; private int sx,sy,sw,sh; private boolean pinching=false;
        RegionView(){ super(AutoTapService.this); fill.setColor(0x35FF9800); stroke.setColor(0xFFFF9800); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(dp(3)); text.setColor(0xFFFFA726); text.setTextSize(dp(15)); }
        @Override protected void onDraw(Canvas c){ super.onDraw(c); float i=dp(3); c.drawRect(i,i,getWidth()-i,getHeight()-i,fill); c.drawRect(i,i,getWidth()-i,getHeight()-i,stroke); c.drawText("点击区域 · 拖动 · 双指缩放",dp(10),dp(26),text); }
        @Override public boolean onTouchEvent(MotionEvent e){ if(running)return false; switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:pinching=false;downX=e.getRawX();downY=e.getRawY();sx=rp.x;sy=rp.y;return true;
            case MotionEvent.ACTION_POINTER_DOWN:if(e.getPointerCount()>=2){pinching=true;pinch=dist(e);sw=rp.width;sh=rp.height;}return true;
            case MotionEvent.ACTION_MOVE:
                if(pinching&&e.getPointerCount()>=2){float z=dist(e)/Math.max(1,pinch);DisplayMetrics dm=getResources().getDisplayMetrics();rp.width=clamp(Math.round(sw*z),dp(100),dm.widthPixels);rp.height=clamp(Math.round(sh*z),dp(100),dm.heightPixels);wm.updateViewLayout(this,rp);} 
                else if(e.getPointerCount()==1){DisplayMetrics dm=getResources().getDisplayMetrics();rp.x=clamp(Math.round(sx+e.getRawX()-downX),-rp.width/2,dm.widthPixels-rp.width/2);rp.y=clamp(Math.round(sy+e.getRawY()-downY),0,dm.heightPixels-dp(40));wm.updateViewLayout(this,rp);} return true;
            case MotionEvent.ACTION_POINTER_UP:pinching=false;return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:pinching=false;return true;
        }return true;}
        private float dist(MotionEvent e){ if(e.getPointerCount()<2)return 0;float x=e.getX(0)-e.getX(1),y=e.getY(0)-e.getY(1);return (float)Math.sqrt(x*x+y*y); }
        private int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    }
}
