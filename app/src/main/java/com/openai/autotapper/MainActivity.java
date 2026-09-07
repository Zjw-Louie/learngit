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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(248,249,252));

        TextView title = new TextView(this);
        title.setText("区域连点器"); title.setTextSize(28); title.setGravity(Gravity.CENTER);
        root.addView(title, mw());

        TextView desc = new TextView(this);
        desc.setText("框选屏幕区域后持续高速点击。支持随机区域/中心点、1~1000ms 间隔、悬浮开始与停止。\n\n使用步骤：\n1. 开启下方无障碍服务\n2. 切换到目标 App\n3. 拖动橙色区域框，双指缩放\n4. 设置速度和模式\n5. 点击开始；停止按钮可立即终止");
        desc.setTextSize(16); desc.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams dlp=mw(); dlp.topMargin=dp(24); root.addView(desc,dlp);

        status = new TextView(this); status.setTextSize(16); status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp=mw(); slp.topMargin=dp(24); root.addView(status,slp);

        Button b=new Button(this); b.setText("打开无障碍设置"); b.setTextSize(17);
        b.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout.LayoutParams blp=mw(); blp.topMargin=dp(16); root.addView(b,blp);

        TextView note=new TextView(this);
        note.setText("1ms 是请求间隔；实际最高频率受 Android 手势调度、设备性能和目标 App 响应速度限制。");
        note.setTextSize(13); note.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams nlp=mw(); nlp.topMargin=dp(20); root.addView(note,nlp);
        setContentView(root);
    }

    @Override protected void onResume(){ super.onResume(); update(); }

    private void update(){
        String e=Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String t=getPackageName()+"/"+AutoTapService.class.getName();
        boolean on=e!=null && e.toLowerCase().contains(t.toLowerCase());
        status.setText(on?"✓ 无障碍服务已开启":"尚未开启无障碍服务");
        status.setTextColor(on?Color.rgb(0,130,75):Color.rgb(190,60,45));
    }
    private LinearLayout.LayoutParams mw(){ return new LinearLayout.LayoutParams(-1,-2); }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
