package com.sjl.deviceconnector.device.usb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import com.sjl.deviceconnector.DeviceContext;
import com.sjl.deviceconnector.listener.ConnectedListener;
import com.sjl.deviceconnector.listener.ReceiverObservable;
import com.sjl.deviceconnector.listener.UsbPermissionListener;
import com.sjl.deviceconnector.listener.UsbPlugListener;
import com.sjl.deviceconnector.util.LogUtils;
import com.sjl.deviceconnector.util.SynchroniseUtil;
import com.sjl.deviceconnector.util.ThreadUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * usb辅助类，设备管理，权限管理，插拔管理
 *
 * @author Kelly
 * @version 1.0.0
 * @filename UsbHelper
 * @time 2022/6/15 17:30
 * @copyright(C) 2022 song
 */
public class UsbHelper implements ReceiverObservable {

    private static final String INTENT_ACTION_GRANT_USB = "com.sjl.deviceconnector.GRANT_USB";
    private final static String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";

    private BroadcastReceiver mBroadcastReceiver;
    private List<ConnectedListener<UsbDevice>> connectedListener;
    private UsbPermissionListener usbPermissionListener;
    private List<UsbPlugListener> usbPlugListeners = new ArrayList<>();

    private static final AtomicInteger atomicInteger = new AtomicInteger(0);


    private UsbHelper() {
    }


    public static UsbHelper getInstance() {
        return UsbHelperHolder.singleton;
    }


    private static final class UsbHelperHolder {
        private static UsbHelper singleton = new UsbHelper();
    }


    /**
     * 连接状态监听
     *
     * @param connectedListener
     */
    public synchronized void setConnectedListener(ConnectedListener<UsbDevice> connectedListener) {
        if (this.connectedListener == null) {
            this.connectedListener = new ArrayList<>();
        }
        this.connectedListener.add(connectedListener);
    }

    /**
     * Usb插拔监听
     *
     * @param usbPlugListener
     */
    public void setUsbPlugListener(UsbPlugListener usbPlugListener) {
        this.usbPlugListeners.add(usbPlugListener);
    }

    /**
     * 删除Usb插拔监听
     *
     * @param usbPlugListener
     */
    public void removeUsbPlugListener(UsbPlugListener usbPlugListener) {
        this.usbPlugListeners.remove(usbPlugListener);
    }

    /**
     * 删除Usb插拔监听
     */
    public void removeUsbPlugListener() {
        this.usbPlugListeners.clear();
    }

    /**
     * 申请Usb设备权限
     *
     * @param vendorId  产商id
     * @param productId 产品id
     */
    public void requestPermission(int vendorId, int productId) {
        UsbDevice usbDevice = getDevice(vendorId, productId);
        requestPermission(usbDevice, null);
    }

    /**
     * 申请Usb设备权限
     *
     * @param vendorId  产商id
     * @param productId 产品id
     */
    public void requestPermission(int vendorId, int productId, UsbPermissionListener usbPermissionListener) {
        UsbDevice usbDevice = getDevice(vendorId, productId);
        requestPermission(usbDevice, usbPermissionListener);
    }

    /**
     * 申请Usb设备权限
     *
     * @param usbDevice
     * @return
     */
    public boolean requestPermissionSync(UsbDevice usbDevice) {
        if (usbDevice == null) {
            throw new NullPointerException("usbDevice is null.");
        }
        if (hasPermission(usbDevice)) {
            return true;
        }

        UsbManager usbManager = (UsbManager) DeviceContext.getContext().getSystemService(Context.USB_SERVICE);
        PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(DeviceContext.getContext(), 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
        usbManager.requestPermission(usbDevice, usbPermissionIntent);
        SynchroniseUtil<Boolean> synchroniseUtil = new SynchroniseUtil<>();
        UsbPermissionListener preUsbPermissionListener = this.usbPermissionListener;
        this.usbPermissionListener = new UsbPermissionListener() {
            @Override
            public void onGranted(UsbDevice usbDevice) {
                synchroniseUtil.setResult(true);
            }

            @Override
            public void onDenied(UsbDevice usbDevice) {
                synchroniseUtil.setResult(false);
            }
        };
        try {
            return synchroniseUtil.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        } finally {
            this.usbPermissionListener = preUsbPermissionListener;
        }
        return false;
    }

    /**
     * 申请Usb设备权限
     *
     * @param usbDevice
     */
    public void requestPermission(UsbDevice usbDevice) {
        requestPermission(usbDevice, null);
    }

    /**
     * 申请Usb设备权限,需要先注册广播
     *
     * @param usbDevice
     * @param usbPermissionListener
     */
    public void requestPermission(UsbDevice usbDevice, UsbPermissionListener usbPermissionListener) {
        if (usbDevice == null) {
            throw new NullPointerException("usbDevice is null.");
        }
        this.usbPermissionListener = usbPermissionListener;
        if (hasPermission(usbDevice)) {
            if (this.usbPermissionListener != null) {
                this.usbPermissionListener.onGranted(usbDevice);
            }
            return;
        }
        Integer requestCode = atomicInteger.addAndGet(1);
        UsbManager usbManager = (UsbManager) DeviceContext.getContext().getSystemService(Context.USB_SERVICE);
        PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(DeviceContext.getContext(), requestCode, new Intent(INTENT_ACTION_GRANT_USB), 0);
        usbManager.requestPermission(usbDevice, usbPermissionIntent);

    }

    public boolean hasPermission(UsbDevice usbDevice) {
        UsbManager usbManager = (UsbManager) DeviceContext.getContext().getSystemService(Context.USB_SERVICE);
        return usbManager.hasPermission(usbDevice);
    }

    /**
     * 根据产商id和产品id获取UsbDevice
     *
     * @param vendorId  产商id
     * @param productId 产品id
     * @return
     */
    public static UsbDevice getDevice(int vendorId, int productId) {
        List<UsbDevice> deviceList = getDeviceList();
        for (UsbDevice usbDevice : deviceList) {
            if (usbDevice.getVendorId() == vendorId && usbDevice.getProductId() == productId) {
                return usbDevice;
            }
        }
        return null;
    }

    /**
     * 根据产商id和产品id获取UsbDevice
     *
     */
    public static UsbDevice getDevice(String deviceName) {
        List<UsbDevice> deviceList = getDeviceList();
        for (UsbDevice usbDevice : deviceList) {
            if (usbDevice.getDeviceName().equals(deviceName)) {
                return usbDevice;
            }
        }
        return null;
    }

    /**
     * 获取所有Usb设备列表
     *
     * @return
     */
    public static List<UsbDevice> getDeviceList() {
        UsbManager usbManager = (UsbManager) DeviceContext.getContext().getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice usbDevice : deviceList.values()) {
            LogUtils.i("vendorId:" + usbDevice.getVendorId() + ",productId:" + usbDevice.getProductId());
        }
        return new ArrayList<>(deviceList.values());
    }


    @Override
    public void registerReceiver() {
        if (mBroadcastReceiver != null) {
            return;
        }
        Context context = DeviceContext.getContext();
        IntentFilter filter = new IntentFilter();
        filter.addAction(INTENT_ACTION_GRANT_USB);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_STATE);
        mBroadcastReceiver = new MyBroadcastReceiver();
        context.registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public void unregisterReceiver() {
        Context context = DeviceContext.getContext();
        if (mBroadcastReceiver != null) {
            context.unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
        removeListener();
    }


    public void removeListener() {
        connectedListener = null;
        usbPermissionListener = null;
        usbPlugListeners.clear();
    }

    public void removePermissionListener() {
        usbPermissionListener = null;
    }

    private final class MyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (INTENT_ACTION_GRANT_USB.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (usbPermissionListener != null) {
                        ThreadUtils.execute(() -> usbPermissionListener.onGranted(device));
                    }
                } else {
                    LogUtils.w("usb 授权拒绝： " + device.getDeviceName());
                    if (usbPermissionListener != null) {
                        ThreadUtils.execute(() -> usbPermissionListener.onDenied(device));
                    }
                }

            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                LogUtils.i("USB插入:" + device.toString());
                if (!usbPlugListeners.isEmpty()) {
                    for (UsbPlugListener usbPlugListener : usbPlugListeners) {
                        ThreadUtils.execute(() -> usbPlugListener.onAttached(device));
                    }

                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                LogUtils.w("USB拔出:" + device.toString());
                if (!usbPlugListeners.isEmpty()) {
                    for (UsbPlugListener usbPlugListener : usbPlugListeners) {
                        ThreadUtils.execute(() -> usbPlugListener.onDetached(device));
                    }
                }
            } else if (ACTION_USB_STATE.equals(action)) {
                boolean connected = intent.getExtras().getBoolean("connected");
                List<UsbDevice> deviceList = getDeviceList();
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                LogUtils.w("connected:" + connected + ",设备数：" + deviceList.size());
                if (deviceList.isEmpty()) {
                    return;
                }
                if (connectedListener != null) {
                    for (ConnectedListener<UsbDevice> usbDeviceConnectedListener : connectedListener) {
                        ThreadUtils.execute(() -> usbDeviceConnectedListener.onResult(device, connected));
                    }
                }
            }
        }
    }

}
