package com.sjl.deviceconnector.provider;

import com.sjl.deviceconnector.ErrorCode;
import com.sjl.deviceconnector.device.socket.SocketHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class SwSocketConnectProvider extends SocketConnectProvider {

    // 心跳间隔时间（毫秒）
    private static final long HEARTBEAT_INTERVAL = 30 * 1000;

    // 心跳失败最大次数
    private static final int MAX_HEARTBEAT_FAIL_COUNT = 3;

    // 心跳数据心跳数据为0字节int类型
    private static final byte[] HEARTBEAT_DATA = new byte[0];

    // 心跳定时器
    private Timer mHeartbeatTimer;

    // 心跳失败计数
    private int mHeartbeatFailCount;

    // 上次发送数据的时间戳
    private long mLastSendTime;

    // 重连线程
    private Thread mReconnectThread;

    /**
     * 初始化Socket连接提供者
     *
     * @param ip             ip地址
     * @param port           端口号
     * @param connectTimeout 连接超时时间，毫秒
     * @param readTimeout    读取超时时间，毫秒
     */
    public SwSocketConnectProvider(String ip, int port, int connectTimeout, int readTimeout) {
        super(ip, port, connectTimeout, readTimeout);
    }

    @Override
    public int open() {
        int result = super.open();
        if (result == ErrorCode.ERROR_OK) {
            // 启动心跳定时器
            startHeartbeatTimer();
            // 启动重连线程
            startReconnectThread();
        }
        return result;
    }

    @Override
    public synchronized int write(byte[] sendParams, int timeout) {
        try {
            DataOutputStream dataOutputStream = new DataOutputStream(mOutputStream);
            dataOutputStream.writeInt(sendParams.length);
            if (sendParams.length > 0) {
                dataOutputStream.write(sendParams);
            }
            dataOutputStream.flush();
            return sendParams.length;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public synchronized int read(byte[] buffer, int timeout) {
        // 读取4个byte转成int
        DataInputStream dataInputStream = new DataInputStream(mInputStream);
        try {
            int realLength = dataInputStream.readInt();
            if (realLength == -1) {
                return -1;
            }
            byte[] bytes = new byte[realLength];
            dataInputStream.readFully(bytes, 0, realLength);
            System.arraycopy(bytes, 0, buffer, 0, realLength);
            return realLength;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] intToBytes(int value) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) (value >> 24);
        bytes[1] = (byte) (value >> 16);
        bytes[2] = (byte) (value >> 8);
        bytes[3] = (byte) value;
        return bytes;
    }

    @Override
    public void close() {
        // 停止心跳定时器
        stopHeartbeatTimer();
        // 停止重连线程
        stopReconnectThread();
        super.close();
    }

    public void superClose() {
        super.close();
    }

    /**
     * 启动心跳定时器
     */
    private void startHeartbeatTimer() {
        stopHeartbeatTimer(); // 先停止之前的定时器
        mHeartbeatTimer = new Timer();
        mHeartbeatTimer.schedule(new HeartbeatTask(), HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL);
        mLastSendTime = System.currentTimeMillis();
        mHeartbeatFailCount = 0;
    }

    /**
     * 停止心跳定时器
     */
    private void stopHeartbeatTimer() {
        if (mHeartbeatTimer != null) {
            mHeartbeatTimer.cancel();
            mHeartbeatTimer = null;
        }
    }

    /**
     * 启动重连线程
     */
    private void startReconnectThread() {
        stopReconnectThread(); // 先停止之前的重连线程
        mReconnectThread = new Thread(new ReconnectRunnable());
        mReconnectThread.setDaemon(true);
        mReconnectThread.start();
    }

    /**
     * 停止重连线程
     */
    private void stopReconnectThread() {
        if (mReconnectThread != null) {
            mReconnectThread.interrupt();
            mReconnectThread = null;
        }
    }

    /**
     * 重连任务
     */
    private class ReconnectRunnable implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                // 如果已连接，等待3秒后重试
                if (getState() == ErrorCode.ERROR_OK) {
                    try {
                        TimeUnit.SECONDS.sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }
                try {
                    // 尝试重连
                    open();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 心跳任务
     */
    private class HeartbeatTask extends TimerTask {

        @Override
        public void run() {
            // 检查是否需要发送心跳
            long currentTime = System.currentTimeMillis();
            if (currentTime - mLastSendTime >= HEARTBEAT_INTERVAL) {
                // 发送心跳数据
                int result = write(HEARTBEAT_DATA, 1000);
                if (result < 0) {
                    // 心跳发送失败，增加失败计数
                    mHeartbeatFailCount++;
                    if (mHeartbeatFailCount >= MAX_HEARTBEAT_FAIL_COUNT) {
                        // 连续失败达到最大次数，关闭连接
                        superClose();
                        // 连接失败通知
                        SocketHelper.getInstance().notifyDisconnected(ip, port);
                    }
                } else {
                    // 心跳发送成功，重置失败计数
                    mHeartbeatFailCount = 0;
                }
            }
        }
    }
}
