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
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Random;

public class AutoTapService extends AccessibilityService {
    public static volatile AutoTapService INSTANCE;
    private static final long[] INTERVALS={20,50,100,200,500,1000};
    private final Handler h=new Handler(Looper.getMainLooper());
    private final Random rnd=new Random();
    private WindowManager wm;
    private RegionView region;
    private LinearLayout panel;
    private WindowManager.LayoutParams rp,pp;
    private TextView status;
    private Button runButton;
    private boolean running=false;
    private boolean randomMode=true;
    private boolean controllerVisible=false;
    private int idx=2; // 100 ms default
    private int cancelled=0;
    private int generation=0;

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        INSTANCE=this;
        running=false;
        controllerVisible=false;
        cancelled=0;
        generation++;
        removeOverlays();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){ emergencyStop(); }
    @Override public void onDestroy(){ emergencyStop(); removeOverlays(); if(INSTANCE==this)INSTANCE=null; super.onDestroy(); }

    public void showController(){
        h.post(()->{
            if(controllerVisible)return;
            running=false; cancelled=0; generation++;
            buildOverlays();
            controllerVisible=true;
            update();
        });
    }

    public void hideController(){ h.post(()->{ emergencyStop(); removeOverlays(); controllerVisible=false; }); }

    @Override protected boolean onKeyEvent(KeyEvent event){
        if(running && event.getKeyCode()==KeyEvent.KEYCODE_VOLUME_DOWN){
            if(event.getAction()==KeyEvent.ACTION_DOWN) emergencyStop();
            return true;
        }
        return false;
    }

    private void buildOverlays(){
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm=getResources().getDisplayMetrics();
        int rw=Math.max(dp(180),dm.widthPixels/2), rh=Math.max(dp(140),dm.heightPixels/4);
        rp=new WindowManager.LayoutParams(rw,rh,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        rp.gravity=Gravity.TOP|Gravity.START; rp.x=(dm.widthPixels-rw)/2; rp.y=Math.max(dp(180),(dm.heightPixels-rh)/2);
        region=new RegionView(); wm.addView(region,rp);

        panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(8),dp(6),dp(8),dp(8));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xEE202126); bg.setCornerRadius(dp(12)); panel.setBackground(bg);
        TextView header=new TextView(this); header.setText("AutoTapper v1.2 Safe · 拖动面板"); header.setTextColor(Color.WHITE); header.setTextSize(13); panel.addView(header);
        dragPanel(header);
        status=new TextView(this); status.setTextColor(Color.WHITE); status.setTextSize(13); status.setPadding(0,dp(4),0,dp(4)); panel.addView(status);

        LinearLayout r1=new LinearLayout(this);
        Button left=btn("←"), right=btn("→"), up=btn("↑"), down=btn("↓");
        r1.addView(left);r1.addView(right);r1.addView(up);r1.addView(down);panel.addView(r1);
        LinearLayout r2=new LinearLayout(this);
        Button smaller=btn("缩小"), larger=btn("放大"), mode=btn("随机/中心");
        r2.addView(smaller);r2.addView(larger);r2.addView(mode);panel.addView(r2);
        LinearLayout r3=new LinearLayout(this);
        Button faster=btn("更快"), slower=btn("更慢"), hide=btn("关闭控制器");
        r3.addView(faster);r3.addView(slower);r3.addView(hide);panel.addView(r3);
        runButton=btn("开始（3秒倒计时）"); panel.addView(runButton);

        left.setOnClickListener(v->move(-dp(24),0)); right.setOnClickListener(v->move(dp(24),0));
        up.setOnClickListener(v->move(0,-dp(24))); down.setOnClickListener(v->move(0,dp(24)));
        smaller.setOnClickListener(v->resize(-dp(36),-dp(28))); larger.setOnClickListener(v->resize(dp(36),dp(28)));
        mode.setOnClickListener(v->{ if(!running){randomMode=!randomMode;update();} });
        faster.setOnClickListener(v->{ if(!running&&idx>0){idx--;update();} });
        slower.setOnClickListener(v->{ if(!running&&idx<INTERVALS.length-1){idx++;update();} });
        hide.setOnClickListener(v->hideController());
        runButton.setOnTouchListener((v,e)->{ if(e.getActionMasked()==MotionEvent.ACTION_DOWN){ if(running) emergencyStop(); else beginCountdown(); return true;} return true; });

        pp=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        pp.gravity=Gravity.TOP|Gravity.START; pp.x=dp(12); pp.y=dp(70); wm.addView(panel,pp);
    }

    private void beginCountdown(){
        if(running||panel==null)return;
        final int token=++generation;
        runButton.setEnabled(false);
        countdown(token,3);
    }
    private void countdown(int token,int n){
        if(token!=generation||panel==null)return;
        if(n<=0){ runButton.setEnabled(true); running=true; cancelled=0; update(); dispatchBatch(token); return; }
        status.setText("将在 "+n+" 秒后开始。音量减 = 紧急停止");
        h.postDelayed(()->countdown(token,n-1),1000);
    }

    private void emergencyStop(){
        generation++;
        running=false;
        cancelled=0;
        h.removeCallbacksAndMessages(null);
        if(runButton!=null){runButton.setEnabled(true);runButton.setText("开始（3秒倒计时）");}
        update();
    }

    private void dispatchBatch(final int token){
        if(!running||token!=generation)return;
        long interval=INTERVALS[idx];
        int max=GestureDescription.getMaxStrokeCount();
        int count=Math.max(1,Math.min(max,(int)(160/Math.max(1,interval))+1));
        GestureDescription.Builder b=new GestureDescription.Builder();
        long maxDur=GestureDescription.getMaxGestureDuration(); int added=0;
        for(int i=0;i<count;i++){
            long t=i*interval; if(t+1>=maxDur)break;
            float[] p=point(); Path path=new Path(); path.moveTo(p[0],p[1]);
            b.addStroke(new GestureDescription.StrokeDescription(path,t,1)); added++;
        }
        if(added==0){ emergencyStop(); return; }
        boolean ok=dispatchGesture(b.build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription g){ if(running&&token==generation){cancelled=0;h.postDelayed(()->dispatchBatch(token),INTERVALS[idx]);} }
            @Override public void onCancelled(GestureDescription g){ if(running&&token==generation){cancelled++; if(cancelled>=3) emergencyStop(); else h.postDelayed(()->dispatchBatch(token),Math.max(50,INTERVALS[idx]));} }
        },h);
        if(!ok) emergencyStop();
    }

    private float[] point(){
        int mx=Math.max(dp(8),(int)(rp.width*.1f)), my=Math.max(dp(8),(int)(rp.height*.1f));
        if(!randomMode)return new float[]{rp.x+rp.width/2f,rp.y+rp.height/2f};
        int l=rp.x+mx,t=rp.y+my,r=rp.x+rp.width-mx,b=rp.y+rp.height-my;
        return new float[]{l+rnd.nextFloat()*Math.max(1,r-l),t+rnd.nextFloat()*Math.max(1,b-t)};
    }

    private void move(int dx,int dy){ if(running||rp==null)return; DisplayMetrics dm=getResources().getDisplayMetrics(); rp.x=clamp(rp.x+dx,0,Math.max(0,dm.widthPixels-rp.width)); rp.y=clamp(rp.y+dy,0,Math.max(0,dm.heightPixels-rp.height)); wm.updateViewLayout(region,rp); }
    private void resize(int dw,int dh){ if(running||rp==null)return; DisplayMetrics dm=getResources().getDisplayMetrics(); rp.width=clamp(rp.width+dw,dp(100),dm.widthPixels); rp.height=clamp(rp.height+dh,dp(100),dm.heightPixels); rp.x=clamp(rp.x,0,Math.max(0,dm.widthPixels-rp.width)); rp.y=clamp(rp.y,0,Math.max(0,dm.heightPixels-rp.height)); wm.updateViewLayout(region,rp); }

    private void update(){
        if(status!=null)status.setText((running?"运行中":"待机")+" | "+INTERVALS[idx]+" ms | "+(randomMode?"随机区域":"中心点")+(running?" | 音量减紧急停止":""));
        if(runButton!=null&&!running&&runButton.isEnabled())runButton.setText("开始（3秒倒计时）");
        if(runButton!=null&&running)runButton.setText("停止");
        if(region!=null)region.invalidate();
    }
    private Button btn(String s){ Button b=new Button(this); b.setText(s); b.setTextSize(11); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(7),0,dp(7),0); return b; }
    private void dragPanel(View v){ final float[] d=new float[2]; final int[] s=new int[2]; v.setOnTouchListener((x,e)->{ if(pp==null)return false; switch(e.getActionMasked()){ case MotionEvent.ACTION_DOWN:d[0]=e.getRawX();d[1]=e.getRawY();s[0]=pp.x;s[1]=pp.y;return true; case MotionEvent.ACTION_MOVE:pp.x=Math.round(s[0]+e.getRawX()-d[0]);pp.y=Math.round(s[1]+e.getRawY()-d[1]);wm.updateViewLayout(panel,pp);return true;} return true; }); }
    private void removeOverlays(){ if(wm==null)return; try{if(region!=null)wm.removeView(region);}catch(Exception ignored){} try{if(panel!=null)wm.removeView(panel);}catch(Exception ignored){} region=null;panel=null;rp=null;pp=null;status=null;runButton=null;controllerVisible=false; }
    private int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    private class RegionView extends View{
        private final Paint fill=new Paint(1),stroke=new Paint(1),text=new Paint(1);
        RegionView(){ super(AutoTapService.this); fill.setColor(0x24FF9800); stroke.setColor(0xFFFF9800); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(dp(3)); text.setColor(0xFFFFA726); text.setTextSize(dp(14)); }
        @Override protected void onDraw(Canvas c){ super.onDraw(c); float i=dp(3); c.drawRect(i,i,getWidth()-i,getHeight()-i,fill); c.drawRect(i,i,getWidth()-i,getHeight()-i,stroke); c.drawText("点击区域（永久触摸透传）",dp(10),dp(24),text); }
    }
}
