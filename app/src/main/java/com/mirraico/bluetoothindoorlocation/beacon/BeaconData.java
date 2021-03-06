package com.mirraico.bluetoothindoorlocation.beacon;

import android.util.Log;

import java.util.Arrays;

public class BeaconData {

    private static String TAG = BeaconData.class.getSimpleName();

    private String MAC;
    private int RSSArraySize = 5;
    private int RSSArrayCnt = 0;
    private int[] RSSArray;

    public BeaconData(String MAC) {
        this.MAC = MAC;
        RSSArray = new int[RSSArraySize];
    }

    public String getMAC() {
        return MAC;
    }

    //数组未满时放满，满了时向前滑动，也可以用循环队列实现
    public void pushRSS(int rss) {
        if(RSSArrayCnt < RSSArraySize) RSSArray[RSSArrayCnt++] = rss;
        else {
            for(int i = 0; i < RSSArraySize - 1; i++) {
                RSSArray[i] = RSSArray[i+1];
            }
            RSSArray[RSSArraySize-1] = rss;
        }
    }

    //搜索不到beacon时需要立刻清空数组
    public void clearRSS() {
        RSSArrayCnt = 0;
    }

    //返回beacon中位数RSS
    public int getAverageRSS() {
        if(isEmpty()) return 0;

        //Log.e(TAG, getMAC() + " RSS ARRAYS: " + RSSArray[0] + " " + RSSArray[1] + " " + RSSArray[2] + " " + RSSArray[3] + " " + RSSArray[4]);
        int[] sortArray = Arrays.copyOf(RSSArray, RSSArrayCnt);
        Arrays.sort(sortArray);
        //Log.e(TAG, getMAC() + " RSS MID: " + sortArray[RSSArrayCnt/2]);
        return sortArray[RSSArrayCnt/2];

        /*
        int aveRss = 0;
        for(int i = 0; i < RSSArrayCnt; i++) {
            aveRss += RSSArray[i];
        }
        return aveRss / RSSArrayCnt;
        */
    }

    public boolean isEmpty() {
        return RSSArrayCnt == 0;
    }
}
