package com.openai.autotapper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private Button controller;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(28),dp(24),dp(24)); root.setGravity(Gravity.CENTER_HORIZONTAL); root.setBackgroundColor(Color.rgb(248,249,252));
        TextView title=new TextView(this); title.setText("区域连点器 v1.3"); title.setTextSize(26); title.setGravity(Gravity.CENTER); root.addView(title,mw());
        TextView desc=new TextView(this); desc.setText("新增：\n• 四角橙色小手柄，可连续拖拽，自定义点击区域宽高\n• 区域框本体仍永久触摸透传\n• 极速档开放到 1ms 请求间隔\n• 默认仍为 100ms\n• 开始前 3 秒倒计时\n• 运行时按音量减立即停止\n• 连续 3 次手势取消或调度失败自动停止\n• 开机后服务只待命，不自动显示、不自动点击\n\n使用：开启无障碍服务 → 回到本页 → 点击‘显示控制器’ → 拖四角手柄设置区域 → 选择速度 → 开始。"); desc.setTextSize(15); desc.setTextColor(Color.DKGRAY); LinearLayout.LayoutParams dlp=mw(); dlp.topMargin=dp(18); root.addView(desc,dlp);
        status=new TextView(this); status.setTextSize(16); status.setGravity(Gravity.CENTER); LinearLayout.LayoutParams slp=mw(); slp.topMargin=dp(20); root.addView(status,slp);
        Button access=new Button(this); access.setText("打开无障碍设置"); access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))); LinearLayout.LayoutParams alp=mw(); alp.topMargin=dp(14); root.addView(access,alp);
        controller=new Button(this); controller.setText("显示控制器"); controller.setOnClickListener(v->{ AutoTapService s=AutoTapService.INSTANCE; if(s!=null)s.showController(); update(); }); LinearLayout.LayoutParams clp=mw(); clp.topMargin=dp(10); root.addView(controller,clp);
        Button hide=new Button(this); hide.setText("关闭控制器 / 紧急停止"); hide.setOnClickListener(v->{ AutoTapService s=AutoTapService.INSTANCE; if(s!=null)s.hideController(); update(); }); LinearLayout.LayoutParams hlp=mw(); hlp.topMargin=dp(8); root.addView(hide,hlp);
        TextView note=new TextView(this); note.setText("1ms 表示请求调度间隔，不代表手机或目标 App 一定能实际处理 1000 次/秒。首次测试仍建议从 100ms 开始，确认音量减急停正常后再逐步加速。"); note.setTextSize(13); note.setTextColor(Color.rgb(160,70,35)); LinearLayout.LayoutParams nlp=mw(); nlp.topMargin=dp(18); root.addView(note,nlp);
        setContentView(root);
    }
    @Override protected void onResume(){ super.onResume(); update(); }
    private void update(){
        String e=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String t=getPackageName()+"/"+AutoTapService.class.getName();
        boolean enabled=e!=null&&e.toLowerCase().contains(t.toLowerCase());
        boolean bound=AutoTapService.INSTANCE!=null;
        status.setText(enabled?(bound?"✓ 无障碍服务已开启并待命":"✓ 无障碍服务已开启，等待系统绑定"):"尚未开启无障碍服务");
        status.setTextColor(enabled?Color.rgb(0,130,75):Color.rgb(190,60,45));
        controller.setEnabled(bound);
    }
    private LinearLayout.LayoutParams mw(){return new LinearLayout.LayoutParams(-1,-2);} private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
