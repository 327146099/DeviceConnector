package com.sjl.deviceconnector.manager;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import com.sjl.deviceconnector.DeviceContext;
import com.sjl.deviceconnector.ErrorCode;
import com.sjl.deviceconnector.device.bluetooth.BluetoothHelper;
import com.sjl.deviceconnector.device.usb.UsbHelper;
import com.sjl.deviceconnector.entity.SerialPortConfig;
import com.sjl.deviceconnector.listener.UsbPermissionListener;
import com.sjl.deviceconnector.provider.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>连接池</p>
 * <p>创建日期：2023-07-18</p>
 *
 * @author 杨洲 yangzhou@neusoft.com
 */
public class ConnectManager {

    public static ConnectManager getInstance() {
        return ConnectManagerHolder.INSTANCE;
    }

    private static class ConnectManagerHolder {
        static final ConnectManager INSTANCE = new ConnectManager();
    }

    public void init(Context context, boolean debug) {
        try {
            if (DeviceContext.getContext() == null) {
                DeviceContext.init(context, debug);
            }
        } catch (Exception e) {
            DeviceContext.init(context, debug);
        }

        // 创建线程检查连接是否过期
        checkThread = new Thread(() -> {
            while (Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(ideaTime * 1000);
                } catch (InterruptedException ignored) {
                }
                long currentTimeMillis = System.currentTimeMillis();
                for (Map.Entry<String, Connect> entry : container.entrySet()) {
                    Connect connect = entry.getValue();
                    BaseConnectProvider baseConnectProvider = connect.baseConnectProvider;
                    // 如果连接异常，清理
                    if (baseConnectProvider.getState() != ErrorCode.ERROR_OK) {
                        close(entry.getKey());
                        continue;
                    }
                    // 如果不是自动关闭，跳过
                    if (!connect.autoClose) {
                        continue;
                    }
                    if (currentTimeMillis - connect.lastTime > ideaTime * 1000) {
                        close(entry.getKey());
                    }
                }
            }
        });
        checkThread.setDaemon(true);
        checkThread.start();

        BluetoothHelper.getInstance().setConnectedListener((device, connected) -> {
            if (!connected) {
                if (device != null) {
                    close(new ConnectInfo(device.getAddress()));
                }
            }
        });
        // 注册监听器
        UsbHelper.getInstance().registerReceiver();

        UsbHelper.getInstance().setConnectedListener((device, connected) -> {
            if (!connected) {
                if (device != null) {
                    close(new ConnectInfo(device.getVendorId(), device.getProductId()));
                }
            }
        });

        // 注册蓝牙事件
        BluetoothHelper.getInstance().registerReceiver();
    }

    private final Map<String, Connect> container = new ConcurrentHashMap<>();

    private final Lock lock = new ReentrantLock();

    /**
     * 连接存活时间
     */
    private long ideaTime = 60;

    private Thread checkThread;

    private ConnectManager() {
    }

    public void shutdown() {
        if (checkThread != null) {
            checkThread.interrupt();
        }
        for (Connect value : container.values()) {
            value.baseConnectProvider.close();
        }
    }

    /**
     * 获取连接
     */
    public BaseConnectProvider get(ConnectInfo connectInfo) {
        String key = connectInfo.getKey();
        if (container.containsKey(key)) {
            Connect connect = container.get(key);
            connect.lastTime = System.currentTimeMillis();
            return connect.baseConnectProvider;
        } else {
            // 创建连接需要加锁
            lock.lock();
            try {
                if (container.containsKey(key)) {
                    Connect connect = container.get(key);
                    connect.lastTime = System.currentTimeMillis();
                    return connect.baseConnectProvider;
                } else {
                    BaseConnectProvider baseConnectProvider = createConnect(connectInfo);
                    Connect connect = new Connect();
                    connect.baseConnectProvider = baseConnectProvider;
                    connect.lastTime = System.currentTimeMillis();
                    connect.type = connectInfo.getType();
                    connect.connectInfo = connectInfo;
                    container.put(key, connect);
                    return baseConnectProvider;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public void close(ConnectInfo connectInfo) {
        close(connectInfo.getKey());
    }

    public void close(String key) {
        lock.lock();
        Connect connect = container.remove(key);
        if (connect != null) {
            connect.baseConnectProvider.close();
        }
        lock.unlock();
    }

    private BaseConnectProvider createConnect(ConnectInfo connectInfo) {
        int type = connectInfo.getType();
        BaseConnectProvider baseConnectProvider;
        switch (type) {
            case 1:
                SerialPortConfig serialPortConfig = SerialPortConfig.newBuilder(connectInfo.getPort(), connectInfo.getBaudRate()).build();
                baseConnectProvider = new SerialPortConnectProvider(serialPortConfig);
                break;
            case 2:
                // 请求usb权限
                requestUsbPermission(connectInfo.getVendorId(), connectInfo.getProductId());
                serialPortConfig = SerialPortConfig.newBuilder(connectInfo.getBaudRate()).build();
                baseConnectProvider = new UsbComConnectProvider(connectInfo.getVendorId(), connectInfo.getProductId(), serialPortConfig);
                break;
            case 3:
                baseConnectProvider = new BluetoothConnectProvider(connectInfo.getMac());
                break;
            case 4:
                // 请求usb权限
                requestUsbPermission(connectInfo.getVendorId(), connectInfo.getProductId());
                baseConnectProvider = new UsbConnectProvider(connectInfo.getVendorId(), connectInfo.getProductId());
                break;
            case 5:
                baseConnectProvider = new BluetoothLeConnectProvider(connectInfo.getMac());
                break;
            default:
                baseConnectProvider = null;
        }
        if (baseConnectProvider == null) {
            throw new RuntimeException("unsupported connect type");
        }
        int open = baseConnectProvider.open();
        if (open < 0) {
            throw new RuntimeException("open connect failed");
        }
        return baseConnectProvider;
    }

    private static class Connect {

        private BaseConnectProvider baseConnectProvider;

        private ConnectInfo connectInfo;

        private int type;

        private boolean autoClose = true;

        private long lastTime;
    }

    public void setIdeaTime(long ideaTime) {
        this.ideaTime = ideaTime;
    }


    private void requestUsbPermission(int vendorId, int productId) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicBoolean granted = new AtomicBoolean(false);
        UsbHelper.getInstance().requestPermission(vendorId, productId, new UsbPermissionListener() {
            @Override
            public void onGranted(UsbDevice usbDevice) {
                granted.set(true);
                System.out.println("回调授权");
                countDownLatch.countDown();
            }

            @Override
            public void onDenied(UsbDevice usbDevice) {
                System.out.println("回调拒绝授权");
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("等待授权超时");
        } finally {
            // 删除授权listener
            UsbHelper.getInstance().removePermissionListener();
        }
        if (!granted.get()) {
            throw new RuntimeException("usb设备未授权");
        }
    }

}
