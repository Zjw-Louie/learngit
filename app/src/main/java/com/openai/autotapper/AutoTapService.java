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
    private static final long[] INTERVALS={1,2,5,10,20,50,100,200,500,1000};
    private final Handler h=new Handler(Looper.getMainLooper());
    private final Random rnd=new Random();
    private WindowManager wm;
    private RegionView region;
    private LinearLayout panel;
    private WindowManager.LayoutParams rp,pp;
    private TextView status;
    private Button runButton;
    private CornerHandle[] handles=new CornerHandle[4];
    private WindowManager.LayoutParams[] hp=new WindowManager.LayoutParams[4];
    private CenterHandle centerHandle;
    private WindowManager.LayoutParams cp;
    private boolean running=false;
    private boolean randomMode=true;
    private boolean controllerVisible=false;
    private int idx=6;
    private int cancelled=0;
    private int generation=0;

    @Override protected void onServiceConnected(){ super.onServiceConnected(); INSTANCE=this; running=false; controllerVisible=false; cancelled=0; generation++; removeOverlays(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){ emergencyStop(); }
    @Override public void onDestroy(){ emergencyStop(); removeOverlays(); if(INSTANCE==this)INSTANCE=null; super.onDestroy(); }

    public void showController(){ h.post(()->{ if(controllerVisible)return; running=false; cancelled=0; generation++; buildOverlays(); controllerVisible=true; update(); }); }
    public void hideController(){ h.post(()->{ emergencyStop(); removeOverlays(); controllerVisible=false; }); }

    @Override protected boolean onKeyEvent(KeyEvent event){
        if(running && event.getKeyCode()==KeyEvent.KEYCODE_VOLUME_DOWN){ if(event.getAction()==KeyEvent.ACTION_DOWN) emergencyStop(); return true; }
        return false;
    }

    private void buildOverlays(){
        wm=(WindowManager)getSystemService(WINDOW_SERVICE); DisplayMetrics dm=getResources().getDisplayMetrics();
        int rw=Math.max(dp(180),dm.widthPixels/2), rh=Math.max(dp(140),dm.heightPixels/4);
        rp=new WindowManager.LayoutParams(rw,rh,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        rp.gravity=Gravity.TOP|Gravity.START; rp.x=(dm.widthPixels-rw)/2; rp.y=Math.max(dp(180),(dm.heightPixels-rh)/2);
        region=new RegionView(); wm.addView(region,rp);
        buildHandles();
        buildCenterHandle();

        panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(8),dp(6),dp(8),dp(8));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xEE202126); bg.setCornerRadius(dp(12)); panel.setBackground(bg);
        TextView header=new TextView(this); header.setText("AutoTapper v1.4 · 拖动面板"); header.setTextColor(Color.WHITE); header.setTextSize(13); panel.addView(header); dragPanel(header);
        status=new TextView(this); status.setTextColor(Color.WHITE); status.setTextSize(13); status.setPadding(0,dp(4),0,dp(4)); panel.addView(status);

        LinearLayout r1=new LinearLayout(this); Button left=btn("←"),right=btn("→"),up=btn("↑"),down=btn("↓"); r1.addView(left);r1.addView(right);r1.addView(up);r1.addView(down);panel.addView(r1);
        LinearLayout r2=new LinearLayout(this); Button mode=btn("随机/中心"),faster=btn("更快"),slower=btn("更慢"); r2.addView(mode);r2.addView(faster);r2.addView(slower);panel.addView(r2);
        LinearLayout r3=new LinearLayout(this); Button hide=btn("关闭控制器"); r3.addView(hide); panel.addView(r3);
        runButton=btn("开始（3秒倒计时）"); panel.addView(runButton);

        left.setOnClickListener(v->move(-dp(16),0)); right.setOnClickListener(v->move(dp(16),0)); up.setOnClickListener(v->move(0,-dp(16))); down.setOnClickListener(v->move(0,dp(16)));
        mode.setOnClickListener(v->{ if(!running){randomMode=!randomMode;update();} });
        faster.setOnClickListener(v->{ if(!running&&idx>0){idx--;update();} }); slower.setOnClickListener(v->{ if(!running&&idx<INTERVALS.length-1){idx++;update();} });
        hide.setOnClickListener(v->hideController());
        runButton.setOnTouchListener((v,e)->{ if(e.getActionMasked()==MotionEvent.ACTION_DOWN){ if(running) emergencyStop(); else beginCountdown(); return true;} return true; });

        pp=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        pp.gravity=Gravity.TOP|Gravity.START; pp.x=dp(12); pp.y=dp(70); wm.addView(panel,pp);
        updateHandlePositions();
    }

    private void buildHandles(){
        int size=dp(28);
        for(int i=0;i<4;i++){
            handles[i]=new CornerHandle(i);
            hp[i]=new WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
            hp[i].gravity=Gravity.TOP|Gravity.START; wm.addView(handles[i],hp[i]);
        }
    }

    private void buildCenterHandle(){
        int size=dp(36);
        centerHandle=new CenterHandle();
        cp=new WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        cp.gravity=Gravity.TOP|Gravity.START; wm.addView(centerHandle,cp);
    }

    private void updateHandlePositions(){
        if(rp==null)return; int half=dp(14);
        int[][] p={{rp.x-half,rp.y-half},{rp.x+rp.width-half,rp.y-half},{rp.x-half,rp.y+rp.height-half},{rp.x+rp.width-half,rp.y+rp.height-half}};
        for(int i=0;i<4;i++) if(handles[i]!=null){ hp[i].x=p[i][0]; hp[i].y=p[i][1]; try{wm.updateViewLayout(handles[i],hp[i]);}catch(Exception ignored){} }
        if(centerHandle!=null&&cp!=null){ int ch=dp(18); cp.x=rp.x+rp.width/2-ch; cp.y=rp.y+rp.height/2-ch; try{wm.updateViewLayout(centerHandle,cp);}catch(Exception ignored){} }
    }

    private void setHandlesVisible(boolean visible){
        for(CornerHandle v:handles) if(v!=null)v.setVisibility(visible?View.VISIBLE:View.GONE);
        if(centerHandle!=null) centerHandle.setVisibility(visible?View.VISIBLE:View.GONE);
    }

    private void beginCountdown(){ if(running||panel==null)return; final int token=++generation; runButton.setEnabled(false); countdown(token,3); }
    private void countdown(int token,int n){ if(token!=generation||panel==null)return; if(n<=0){ runButton.setEnabled(true); running=true; cancelled=0; setHandlesVisible(false); update(); dispatchBatch(token); return; } status.setText("将在 "+n+" 秒后开始。音量减 = 紧急停止"); h.postDelayed(()->countdown(token,n-1),1000); }

    private void emergencyStop(){ generation++; running=false; cancelled=0; h.removeCallbacksAndMessages(null); setHandlesVisible(true); if(runButton!=null){runButton.setEnabled(true);runButton.setText("开始（3秒倒计时）");} update(); }

    private void dispatchBatch(final int token){
        if(!running||token!=generation)return;
        long interval=INTERVALS[idx]; int max=GestureDescription.getMaxStrokeCount();
        int count=Math.max(1,Math.min(max,(int)(240/Math.max(1,interval))+1));
        GestureDescription.Builder b=new GestureDescription.Builder(); long maxDur=GestureDescription.getMaxGestureDuration(); int added=0;
        for(int i=0;i<count;i++){
            long t=i*interval; if(t+1>=maxDur)break; float[] p=point(); Path path=new Path(); path.moveTo(p[0],p[1]); b.addStroke(new GestureDescription.StrokeDescription(path,t,1)); added++;
        }
        if(added==0){ emergencyStop(); return; }
        boolean ok=dispatchGesture(b.build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription g){ if(running&&token==generation){cancelled=0; h.post(()->dispatchBatch(token));} }
            @Override public void onCancelled(GestureDescription g){ if(running&&token==generation){cancelled++; if(cancelled>=3) emergencyStop(); else h.postDelayed(()->dispatchBatch(token),Math.max(20,INTERVALS[idx]));} }
        },h);
        if(!ok) emergencyStop();
    }

    private float[] point(){ int mx=Math.max(dp(4),(int)(rp.width*.05f)),my=Math.max(dp(4),(int)(rp.height*.05f)); if(!randomMode)return new float[]{rp.x+rp.width/2f,rp.y+rp.height/2f}; int l=rp.x+mx,t=rp.y+my,r=rp.x+rp.width-mx,b=rp.y+rp.height-my; return new float[]{l+rnd.nextFloat()*Math.max(1,r-l),t+rnd.nextFloat()*Math.max(1,b-t)}; }

    private void move(int dx,int dy){ if(running||rp==null)return; DisplayMetrics dm=getResources().getDisplayMetrics(); rp.x=clamp(rp.x+dx,0,Math.max(0,dm.widthPixels-rp.width)); rp.y=clamp(rp.y+dy,0,Math.max(0,dm.heightPixels-rp.height)); wm.updateViewLayout(region,rp); updateHandlePositions(); update(); }

    private void moveTo(float rawX,float rawY,int grabDx,int grabDy){
        if(running||rp==null)return; DisplayMetrics dm=getResources().getDisplayMetrics();
        int nx=Math.round(rawX)-grabDx; int ny=Math.round(rawY)-grabDy;
        rp.x=clamp(nx,0,Math.max(0,dm.widthPixels-rp.width)); rp.y=clamp(ny,0,Math.max(0,dm.heightPixels-rp.height));
        wm.updateViewLayout(region,rp); updateHandlePositions(); update();
    }

    private void resizeFromCorner(int corner,float rawX,float rawY){
        if(running||rp==null)return; DisplayMetrics dm=getResources().getDisplayMetrics(); int minW=dp(48),minH=dp(48);
        int left=rp.x,top=rp.y,right=rp.x+rp.width,bottom=rp.y+rp.height; int x=Math.round(rawX),y=Math.round(rawY);
        if(corner==0||corner==2) left=clamp(x,0,right-minW); else right=clamp(x,left+minW,dm.widthPixels);
        if(corner==0||corner==1) top=clamp(y,0,bottom-minH); else bottom=clamp(y,top+minH,dm.heightPixels);
        rp.x=left; rp.y=top; rp.width=right-left; rp.height=bottom-top;
        wm.updateViewLayout(region,rp); updateHandlePositions(); update();
    }

    private void update(){
        if(status!=null)status.setText((running?"运行中":"待机")+" | "+INTERVALS[idx]+" ms | "+(randomMode?"随机区域":"中心点")+" | "+(rp!=null?rp.width+"×"+rp.height+" px":"")+(running?" | 音量减急停":""));
        if(runButton!=null&&!running&&runButton.isEnabled())runButton.setText("开始（3秒倒计时）"); if(runButton!=null&&running)runButton.setText("停止"); if(region!=null)region.invalidate();
    }

    private Button btn(String s){ Button b=new Button(this); b.setText(s); b.setTextSize(11); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(7),0,dp(7),0); return b; }
    private void dragPanel(View v){ final float[] d=new float[2]; final int[] s=new int[2]; v.setOnTouchListener((x,e)->{ if(pp==null)return false; switch(e.getActionMasked()){ case MotionEvent.ACTION_DOWN:d[0]=e.getRawX();d[1]=e.getRawY();s[0]=pp.x;s[1]=pp.y;return true; case MotionEvent.ACTION_MOVE:pp.x=Math.round(s[0]+e.getRawX()-d[0]);pp.y=Math.round(s[1]+e.getRawY()-d[1]);wm.updateViewLayout(panel,pp);return true;} return true; }); }
    private void removeOverlays(){ if(wm==null)return; for(int i=0;i<4;i++){ try{if(handles[i]!=null)wm.removeView(handles[i]);}catch(Exception ignored){} handles[i]=null;hp[i]=null;} try{if(centerHandle!=null)wm.removeView(centerHandle);}catch(Exception ignored){} centerHandle=null;cp=null; try{if(region!=null)wm.removeView(region);}catch(Exception ignored){} try{if(panel!=null)wm.removeView(panel);}catch(Exception ignored){} region=null;panel=null;rp=null;pp=null;status=null;runButton=null;controllerVisible=false; }
    private int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));} private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private class RegionView extends View{
        private final Paint fill=new Paint(1),stroke=new Paint(1),text=new Paint(1);
        RegionView(){ super(AutoTapService.this); fill.setColor(0x24FF9800); stroke.setColor(0xFFFF9800); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(dp(3)); text.setColor(0xFFFFA726); text.setTextSize(dp(14)); }
        @Override protected void onDraw(Canvas c){ super.onDraw(c); float i=dp(3); c.drawRect(i,i,getWidth()-i,getHeight()-i,fill); c.drawRect(i,i,getWidth()-i,getHeight()-i,stroke); c.drawText("点击区域（框体永久透传）",dp(10),dp(24),text); }
    }

    private class CornerHandle extends View{
        private final int corner; private final Paint p=new Paint(1);
        CornerHandle(int c){ super(AutoTapService.this); corner=c; p.setColor(0xFFFF9800); }
        @Override protected void onDraw(Canvas c){ super.onDraw(c); c.drawCircle(getWidth()/2f,getHeight()/2f,dp(9),p); }
        @Override public boolean onTouchEvent(MotionEvent e){ if(running)return false; if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE){ resizeFromCorner(corner,e.getRawX(),e.getRawY()); return true; } return e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL; }
    }

    private class CenterHandle extends View{
        private final Paint p=new Paint(1), cross=new Paint(1); private int grabDx,grabDy;
        CenterHandle(){ super(AutoTapService.this); p.setColor(0xFFFF9800); cross.setColor(Color.WHITE); cross.setStrokeWidth(dp(2)); }
        @Override protected void onDraw(Canvas c){ super.onDraw(c); float cx=getWidth()/2f,cy=getHeight()/2f; c.drawCircle(cx,cy,dp(12),p); c.drawLine(cx-dp(5),cy,cx+dp(5),cy,cross); c.drawLine(cx,cy-dp(5),cx,cy+dp(5),cross); }
        @Override public boolean onTouchEvent(MotionEvent e){ if(running)return false; switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN: grabDx=Math.round(e.getRawX())-rp.x; grabDy=Math.round(e.getRawY())-rp.y; return true;
            case MotionEvent.ACTION_MOVE: moveTo(e.getRawX(),e.getRawY(),grabDx,grabDy); return true;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: return true;
        } return true; }
    }
}
