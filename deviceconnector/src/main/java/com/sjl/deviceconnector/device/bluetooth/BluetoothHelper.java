package com.sjl.deviceconnector.device.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import com.hjq.permissions.Permission;
import com.sjl.deviceconnector.DeviceContext;
import com.sjl.deviceconnector.device.bluetooth.scanner.AbstractBluetoothScanner;
import com.sjl.deviceconnector.device.bluetooth.scanner.BluetoothClassicScanner;
import com.sjl.deviceconnector.entity.BluetoothScanResult;
import com.sjl.deviceconnector.listener.BluetoothScanListener;
import com.sjl.deviceconnector.listener.ConnectedListener;
import com.sjl.deviceconnector.listener.ReceiverObservable;
import com.sjl.deviceconnector.util.BluetoothUtils;
import com.sjl.deviceconnector.util.LogUtils;
import com.sjl.deviceconnector.util.PermissionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * 蓝牙辅助类
 *
 * @author Kelly
 * @version 1.0.0
 * @filename BluetoothHelper
 * @time 2022/7/26 15:55
 * @copyright(C) 2022 song
 */
public class BluetoothHelper implements ReceiverObservable {
    private BroadcastReceiver mBroadcastReceiver;

    private AbstractBluetoothScanner bluetoothScanner;
    protected List<ConnectedListener<BluetoothDevice>> connectedListener;
    private static final int DEFAULT_SCAN_TIME = 8 * 1000;
    /**
     * 扫描时间,单位毫秒
     */
    private int mScanTime = DEFAULT_SCAN_TIME;

    private BluetoothHelper() {
        bluetoothScanner = new BluetoothClassicScanner();
    }

    public static BluetoothHelper getInstance() {
        return BluetoothHelperHolder.singleton;
    }


    private static final class BluetoothHelperHolder {
        private static BluetoothHelper singleton = new BluetoothHelper();
    }

    /**
     * 连接状态监听
     *
     * @param connectedListener
     */
    public synchronized void setConnectedListener(ConnectedListener<BluetoothDevice> connectedListener) {
        if (this.connectedListener == null) {
            this.connectedListener = new ArrayList<>();
        }
        this.connectedListener.add(connectedListener);
    }


    /**
     * 设置蓝牙扫描策略
     *
     * @param bluetoothScanner
     */
    public void setBluetoothScanner(AbstractBluetoothScanner bluetoothScanner) {
        this.bluetoothScanner = bluetoothScanner;
    }

    public void requireBluetoothPermission() {
        if (!BluetoothUtils.isEnabled()) {
            throw new RuntimeException("蓝牙未开启");
        }
        List<String> mPermissionList = new ArrayList<>();
        String[] permissions;
        if (Build.VERSION.SDK_INT >= 31) {
            // Android 版本大于等于 Android12 时
            mPermissionList.add(Permission.BLUETOOTH_SCAN);
            mPermissionList.add(Permission.BLUETOOTH_ADVERTISE);
            mPermissionList.add(Permission.BLUETOOTH_CONNECT);
            mPermissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            mPermissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mPermissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
                mPermissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
            } else {
            }
        }

        PermissionUtils.PermissionResult permissionResult = PermissionUtils.checkPermissions(mPermissionList.toArray(new String[0]));
        if (!permissionResult.isSuccess()) {
            throw new RuntimeException("蓝牙权限未授权");
        }
    }

    /**
     * 查找蓝牙设备
     *
     * @param scanTime 扫描时间
     * @return 蓝牙设备列表
     */
    public List<BluetoothScanResult> listBluetooth(int scanTime) {

        requireBluetoothPermission();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        ConcurrentHashMap<String, BluetoothScanResult> map = new ConcurrentHashMap<>();
        BluetoothHelper.getInstance().setScanTime(scanTime * 1000);
        BluetoothHelper.getInstance().startScan(new BluetoothScanListener() {
            @Override
            public void onDeviceFound(BluetoothScanResult bluetoothScanResult) {
                map.putIfAbsent(bluetoothScanResult.getAddress(), bluetoothScanResult);
            }

            @Override
            public void onScanFinish() {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException ignored) {
        }

        return new ArrayList<>(map.values());
    }

    /**
     * 设置扫描时间
     *
     * @param scanTime 单位毫秒
     */
    public void setScanTime(int scanTime) {
        if (scanTime < 2 * 1000) {
            this.mScanTime = DEFAULT_SCAN_TIME;
            return;
        }
        this.mScanTime = scanTime;
    }

    /**
     * 蓝牙扫描,需要先注册广播
     *
     * @param bluetoothScanListener
     */
    public void startScan(BluetoothScanListener bluetoothScanListener) {
        startScan(null, bluetoothScanListener);
    }

    /**
     * 根据蓝牙地址扫描指定设备
     *
     * @param address
     * @param bluetoothScanListener
     */
    public void startScan(String address, BluetoothScanListener bluetoothScanListener) {
        bluetoothScanner.setBluetoothScanListener(bluetoothScanListener);
        bluetoothScanner.setAddress(address);
        bluetoothScanner.startScan();
        DeviceContext.mainHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                bluetoothScanner.stopScan();
            }
        }, mScanTime);
    }


    /**
     * 取消扫描
     */
    public void stopScan() {
        DeviceContext.mainHandler().removeCallbacksAndMessages(null);
        bluetoothScanner.stopScan();
    }


    @Override
    public void registerReceiver() {
        if (mBroadcastReceiver != null) {
            return;
        }

        Context context = DeviceContext.getContext();
        IntentFilter intent = new IntentFilter();

        intent.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intent.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
//        intent.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);// 配对开始时，配对成功时
//        intent.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);// 搜索模式改变
//        intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);// 蓝牙开关状态
        mBroadcastReceiver = new MyBroadcastReceiver();
        context.registerReceiver(mBroadcastReceiver, intent);
    }


    @Override
    public void unregisterReceiver() {
        Context context = DeviceContext.getContext();
        if (mBroadcastReceiver != null) {
            context.unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
        this.connectedListener = null;
    }


    private final class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String name = device.getName();
                LogUtils.i("发现蓝牙设备连接成功，name:" + name);
                if (connectedListener != null) {
                    for (ConnectedListener<BluetoothDevice> bluetoothDeviceConnectedListener : connectedListener) {
                        bluetoothDeviceConnectedListener.onResult(device, true);
                    }

                }

            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String name = device.getName();
                LogUtils.w("发现蓝牙设备断开连接，name:" + name);
                if (connectedListener != null) {
                    for (ConnectedListener<BluetoothDevice> bluetoothDeviceConnectedListener : connectedListener) {
                        bluetoothDeviceConnectedListener.onResult(device, false);
                    }
                }
            }
        }
    }

}
