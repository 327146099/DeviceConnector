package com.sjl.deviceconnector.manager;

import com.sjl.deviceconnector.ErrorCode;
import com.sjl.deviceconnector.device.bluetooth.BluetoothHelper;
import com.sjl.deviceconnector.device.usb.UsbHelper;
import com.sjl.deviceconnector.entity.SerialPortConfig;
import com.sjl.deviceconnector.provider.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<String, Connect> container = new ConcurrentHashMap<>();

    private final Lock lock = new ReentrantLock();

    /**
     * 连接存活时间
     */
    private long ideaTime = 60;

    private final Thread checkThread;

    public ConnectManager() {
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
                close(new ConnectInfo(device.getAddress()));
            }
        });

        UsbHelper.getInstance().setConnectedListener((device, connected) -> {
            if (!connected) {
                close(new ConnectInfo(device.getVendorId(), device.getProductId()));
            }
        });

        // 注册蓝牙事件
        BluetoothHelper.getInstance().registerReceiver();
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
                serialPortConfig = SerialPortConfig.newBuilder(connectInfo.getPort(), connectInfo.getBaudRate()).build();
                baseConnectProvider = new UsbComConnectProvider(connectInfo.getVendorId(), connectInfo.getProductId(), serialPortConfig);
                break;
            case 3:
                baseConnectProvider = new BluetoothConnectProvider(connectInfo.getMac());
                break;
            case 4:
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


}
