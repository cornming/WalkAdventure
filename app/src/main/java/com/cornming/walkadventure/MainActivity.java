package com.cornming.walkadventure;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity implements SensorEventListener {
    private GameView view;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private SharedPreferences prefs;
    private float sensorSteps = -1;
    private long todaySteps = 0;
    private long lastCountedSensor = -1;
    private String dayKey;
    private static final int REQ = 20;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("walk_adventure", MODE_PRIVATE);
        dayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        view = new GameView(this);
        setContentView(view);
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQ);
    }
    @Override protected void onResume() { super.onResume(); register(); }
    @Override protected void onPause() { super.onPause(); if(sensorManager!=null) sensorManager.unregisterListener(this); save(); }
    private void register() {
        if(stepSensor != null) sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        else { view.sensorMissing = true; view.invalidate(); }
    }
    @Override public void onSensorChanged(SensorEvent e) {
        if(e.sensor.getType()!=Sensor.TYPE_STEP_COUNTER) return;
        sensorSteps = e.values[0];
        String now = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if(!now.equals(dayKey)) { dayKey=now; prefs.edit().putString("day",dayKey).putFloat("baseline",sensorSteps).apply(); todaySteps=0; }
        float baseline = prefs.getFloat("baseline", -1);
        if(baseline < 0 || sensorSteps < baseline) { baseline=sensorSteps; prefs.edit().putFloat("baseline",baseline).putString("day",dayKey).apply(); }
        todaySteps = Math.max(0, (long)(sensorSteps - baseline));
        long last = prefs.getLong("last_sensor_steps", -1);
        if(last >= 0 && sensorSteps > last) {
            long delta = (long)(sensorSteps-last);
            prefs.edit().putLong("lifetime", prefs.getLong("lifetime",0)+delta).apply();
            checkEventsAndChests();
        }
        prefs.edit().putLong("last_sensor_steps",(long)sensorSteps).apply();
        view.invalidate();
    }
    private void checkEventsAndChests() {
        long lifetime=prefs.getLong("lifetime",0);
        long claimed=prefs.getLong("claimed_chest",0);
        long milestone=(lifetime/1000)*1000;
        if(milestone>claimed) {
            int coins=20+new Random().nextInt(31);
            prefs.edit().putLong("claimed_chest",milestone).putLong("coins",prefs.getLong("coins",0)+coins).apply();
            view.message="🎁 寶箱！獲得 "+coins+" 金幣";
            Toast.makeText(this,view.message,Toast.LENGTH_SHORT).show();
        }
        long eventBucket=lifetime/500;
        long seen=prefs.getLong("event_bucket",0);
        if(eventBucket>seen) {
            prefs.edit().putLong("event_bucket",eventBucket).apply();
            String[] events={"🧙 遇見旅行 NPC！送你一份祝福","👾 小怪獸出現了！你用腳步擊退牠","🍀 撿到幸運道具！","✨ 神秘光芒照亮前方道路"};
            view.message=events[new Random().nextInt(events.length)];
            Toast.makeText(this,view.message,Toast.LENGTH_SHORT).show();
        }
    }
    private void save() { prefs.edit().putString("day",dayKey).apply(); }
    @Override public void onAccuracyChanged(Sensor s,int a) {}

    class GameView extends android.view.View {
        android.graphics.Paint p=new android.graphics.Paint(1); Random r=new Random(); String message="準備出發！每一步都是冒險進度。"; boolean sensorMissing=false;
        GameView(Context c){ super(c); p.setTypeface(android.graphics.Typeface.create("sans",0)); setBackgroundColor(android.graphics.Color.rgb(255,247,232)); }
        void text(android.graphics.Canvas c,String s,float x,float y,float size,boolean bold){p.setTextSize(size);p.setColor(android.graphics.Color.rgb(58,42,32));p.setTypeface(android.graphics.Typeface.create("sans",bold?1:0));c.drawText(s,x,y,p);}
        @Override protected void onDraw(android.graphics.Canvas c){ super.onDraw(c); float w=getWidth(); long life=prefs.getLong("lifetime",0), coins=prefs.getLong("coins",0); int level=(int)(life/1000)+1; long xp=life%1000; float progress=xp/1000f;
            text(c,"走走冒險",28,55,30,true); text(c,"🪙 "+coins, w-145,55,20,true);
            p.setColor(android.graphics.Color.rgb(255,225,170)); c.drawRoundRect(20,78,w-20,190,28,28,p);
            text(c,"🧑‍🚀",38,143,55,false); text(c,"Lv."+level,105,118,24,true); text(c,"冒險者",105,151,17,false);
            p.setColor(android.graphics.Color.rgb(230,210,180)); c.drawRoundRect(105,164,w-45,181,10,10,p); p.setColor(android.graphics.Color.rgb(116,181,120)); c.drawRoundRect(105,164,105+(w-150)*progress,181,10,10,p); text(c,xp+" / 1000 XP",105,207,14,false);
            text(c,"今日步數",25,250,18,true); text(c,""+todaySteps,25,300,48,true); text(c,"每 1000 步探索一格地圖",25,326,15,false);
            // adventure map
            text(c,"冒險地圖",25,370,20,true); float y=405; for(int i=0;i<8;i++){ float x=45+i*65; p.setColor(i < (life/1000)%9 ? android.graphics.Color.rgb(112,175,116) : android.graphics.Color.rgb(220,205,180)); c.drawCircle(x,y,23,p); text(c,i < (life/1000)%9 ? "★":"·",x-8,y+8,22,true); if(i<7){p.setColor(android.graphics.Color.rgb(190,170,140));c.drawRect(x+23,y-3,x+42,y+3,p);} }
            text(c,"目前探索："+(life/1000)+" 格",25,455,16,false); text(c,"🎁 下一個寶箱："+((life/1000)+1)*1000+" 步",25,485,16,false);
            p.setColor(android.graphics.Color.rgb(255,239,190));c.drawRoundRect(20,510,w-20,585,20,20,p);text(c,message,38,555,15,true);
            if(sensorMissing) text(c,"此裝置沒有步數感測器，無法取得實際步數。",25,625,14,true); else text(c,"步數感測器已連線 • 關閉 App 也會保留進度",25,625,13,false);
            text(c,"今天走得越多，冒險世界就越遠。",25,665,16,true);
        }
    }
}
