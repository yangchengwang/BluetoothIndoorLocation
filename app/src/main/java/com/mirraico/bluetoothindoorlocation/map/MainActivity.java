package com.mirraico.bluetoothindoorlocation.map;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

import com.fengmap.android.map.FMMap;
import com.fengmap.android.map.FMMapExtent;
import com.fengmap.android.map.FMMapView;

import com.fengmap.android.map.FMViewMode;
import com.fengmap.android.map.event.OnFMMapClickListener;
import com.fengmap.android.map.event.OnFMMapInitListener;
import com.fengmap.android.map.geometry.FMMapCoord;
import com.fengmap.android.map.layer.FMLocationLayer;
import com.fengmap.android.map.marker.FMLocationMarker;
import com.fengmap.android.map.style.FMLocationMarkerStyle;
import com.mirraico.bluetoothindoorlocation.BaseActivity;
import com.mirraico.bluetoothindoorlocation.beacon.BeaconService;
import com.mirraico.bluetoothindoorlocation.R;
import com.mirraico.bluetoothindoorlocation.info.InfoThread;
import com.mirraico.bluetoothindoorlocation.info.TimerThread;
import com.mirraico.bluetoothindoorlocation.network.SendThread;
import com.mirraico.bluetoothindoorlocation.network.TCPConnection;
import com.mirraico.bluetoothindoorlocation.pedometer.PedometerService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;


public class MainActivity extends BaseActivity {

    private static String TAG = MainActivity.class.getSimpleName();

    //用于测试
    //private TextView recvView;
    //private TextView sendView;
    private TextView statusView;

    //消息类型
    public final static int TYPE_LOCATE = 1; //定位消息
    public final static int TYPE_ANGLE = 2; //航向角消息
    public final static int TYPE_SERVER_DOWN = 10; //服务器关闭消息
    //public final static int TYPE_DEBUG_SEND = 99; //调试的发送消息

    //地图
    private FMMapView mapView;
    private FMMap map;
    private String mapId = "1561101080390313";
    private FMMapExtent ex; //地图范围
    private FMLocationLayer locLayer; //定位图层
    private FMLocationMarkerStyle style; //定位图层样式

    //航向角平滑队列
    private int[] angleArray = new int[3];
    private int angleCnt = 0;

    //服务器设定
    private String serverIp = "123.207.9.36";
    private int serverPort = 8888;

    private BeaconService beaconService; //beacon服务
    private Intent pedometerServiceIntent; //计步服务
    private TimerThread timerThread; //计时器

    //Handler，用于更新地图、状态显示
    public static Handler handler;

    class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            //TODO: 更新地图、状态显示
            Bundle data = msg.getData();
            int type = data.getInt("type");
            switch(type) {
                case TYPE_LOCATE:
                    boolean flag = data.getBoolean("flag");
                    int x = data.getInt("x");
                    int y = data.getInt("y");
                    statusView.setText("STATUS - " + (flag ? "GET LOCATION SUCCESSFULLY" : "FAILED TO GET LOCATION"));
                    //recvView.setText("RECV - X: " + x + "; Y: " + y);
                    Log.e(TAG, "X: " + x + "; Y: " + y);
                    //x = 0; y = 0;
                    if(flag) {
                        updatePoint(transferX(x), transferY(y));
                    } else {
                        removePoint();
                    }
                    break;
                case TYPE_ANGLE:
                    float angle = data.getFloat("angle");
                    //Log.e(TAG, "ANGLE: " + transferAngle(angle));
                    updateAngle(transferAngle(angle));
                    break;
                case TYPE_SERVER_DOWN:
                    statusView.setText("STATUS - SERVER IS DOWN");
                    removePoint();
                    break;
                /*
                case TYPE_DEBUG_SEND:
                    String debug = data.getString("debug");
                    showDebugSend(debug);
                    break;
                */
                default:
                    break;
            }
        }
    };

    private double transferX(double x) {
        return ex.getMinX() + 0.4 + (x*1.0 / 100);
    }

    private double transferY(double y) {
        return ex.getMaxY() - 0.5 - (y*1.0 / 100);
    }

    private float transferAngle(float angle) {
        return (180.0f - angle + 85.0f) % 360;
    }

    private void updatePoint(double x, double y) {
        if(locLayer == null || locLayer.getAll().size() == 0) {
            FMMapCoord point = new FMMapCoord(x, y, 0.0);
            FMLocationMarker marker = new FMLocationMarker(1, point, style);
            locLayer.addMarker(marker);
            map.updateMap();

        } else {
            FMMapCoord point = new FMMapCoord(x, y, 0.0);
            FMLocationMarker marker = locLayer.getAll().get(0);
            marker.setPosition(point);
            locLayer.updateMarker(marker);
            map.updateMap();
        }
    }

    private void removePoint() {
        if(locLayer != null && locLayer.getAll().size() > 0) {
            FMLocationMarker marker = locLayer.getAll().get(0);
            locLayer.removeMarker(marker);
            map.updateMap();
        }
    }

    private void updateAngle(float angle) {
        if(locLayer == null || locLayer.getAll().size() == 0) return;

        FMLocationMarker marker = locLayer.getAll().get(0);
        ((FMLocationMarkerStyle)marker.getStyle()).setAngle(angle);
        locLayer.updateMarker(marker);
        map.updateMap();
    }

    private void showDebugSend(String msg) {
        StringBuilder show = new StringBuilder();
        DecimalFormat df = new DecimalFormat("0.00"); //取小数点后2位
        show.append("SEND - \n");
        //解析发送内容
        try {
            JSONObject jsonObject = new JSONObject(msg);
            boolean isStep = jsonObject.getBoolean("isStep");
            boolean hasRSS = jsonObject.getBoolean("hasRSS");
            show.append("isStep: " + isStep + ", ");
            show.append("hasRss: " + hasRSS + "\n");
            if(isStep) {
                JSONArray stepArray = new JSONArray(jsonObject.getString("sensors"));
                show.append("stepNum: " + stepArray.length() + "\n");
                for(int i = 0; i < stepArray.length(); i++) {
                    JSONObject stepObject = stepArray.getJSONObject(i);
                    show.append("stepNo: " + stepObject.getInt("stepNo") + ", ");
                    show.append("timeDiff: " + stepObject.getInt("timeDiff") + "\n");
                    JSONArray sensorArray = stepObject.getJSONArray("sensorInfo");
                    show.append("angle: " + df.format(Math.toDegrees(sensorArray.getJSONObject(0).getDouble("azimuthAngle"))) + ", ");
                    show.append("we: " + df.format(sensorArray.getJSONObject(0).getDouble("weAcce")) + ", ");
                    show.append("ns: " + df.format(sensorArray.getJSONObject(0).getDouble("nsAcce")) + ", ");
                    show.append("ud: " + df.format(sensorArray.getJSONObject(0).getDouble("udAcce")) + "\n");
                }
            }
            if(hasRSS) {
                JSONArray rssArray = new JSONArray(jsonObject.getString("rssis"));
                show.append("beaconNum: " + rssArray.length() + "\n");
                for(int i = 0; i < rssArray.length(); i++) {
                    JSONObject macObject = rssArray.getJSONObject(i);
                    show.append("MAC: " + macObject.getString("MAC") + ", ");
                    show.append("RSS: " + macObject.getInt("RSS") + "\n");
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        //sendView.setText(show.toString());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //用于测试

        Log.e(TAG, "-----START TEST-----");
        statusView = (TextView) findViewById(R.id.statusview);
        statusView.setText("STATUS - SERVER IS NOT CONNECTED");
//        recvView = (TextView) findViewById(R.id.recvview);
//        recvView.setText("RECV - X: 0; Y: 0");
//        sendView = (TextView) findViewById(R.id.sendview);
//        sendView.setText("SEND - ");

        //注册handler及处理函数
        handler = new MainHandler();

        //创建并显示地图
        mapView = (FMMapView) findViewById(R.id.mapview);
        map = mapView.getFMMap();
        map.openMapById(mapId);
        //地图事件回调
        map.setOnFMMapInitListener(new OnFMMapInitListener() {
            @Override
            public void onMapInitSuccess(String path) {
                map.showCompass(true);
                map.setFMViewMode(FMViewMode.FMVIEW_MODE_2D);
                map.updateMap();

                //2D 3D转换
                map.setOnFMMapClickListener(new OnFMMapClickListener() {
                    @Override
                    public void onMapClick(float x, float y) {
                        if(map.currentFMViewMode() == FMViewMode.FMVIEW_MODE_2D) {
                            map.setFMViewMode(FMViewMode.FMVIEW_MODE_3D);
                        } else {
                            map.setFMViewMode(FMViewMode.FMVIEW_MODE_2D);
                        }
                        map.updateMap();
                    }
                });

                ex = map.getFMMapExtent();
                locLayer = map.getFMLayerProxy().getFMLocationLayer();
                locLayer.setVisible(true);
                map.addLayer(locLayer);

                style = new FMLocationMarkerStyle();
                style.setActiveImageFromRes(R.drawable.active_p);
                style.setStaticImageFromRes(R.drawable.static_p);
            }
            @Override
            public void onMapInitFailure(String path, int errCode) {}
        });


        //网络连接
        //Log.e(TAG, "START CONNECTION");
        TCPConnection conn = TCPConnection.instance();
        conn.setServerAddr(serverIp, serverPort);
        conn.connect();

        //初始化发送队列
        //Log.e(TAG, "INIT SEND THREAD");
        SendThread.instance().start();

        //初始化信息收集队列
        //Log.e(TAG, "INIT INFO THREAD");
        InfoThread.instance().start();

        //启动beacon服务
        //Log.e(TAG, "START BEACON SERVICE");
        beaconService = new BeaconService();
        beaconService.initService(this);

        //启动计步服务
        //Log.e(TAG, "START PEDOMETER SERVICE");
        pedometerServiceIntent = new Intent(this, PedometerService.class);
        startService(pedometerServiceIntent);

        //启动计时器
        timerThread = new TimerThread();
        timerThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconService.destory();
        stopService(pedometerServiceIntent);
        timerThread.interrupt();
    }
}
