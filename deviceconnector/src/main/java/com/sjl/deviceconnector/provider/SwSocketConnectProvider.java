package com.sjl.deviceconnector.provider;

import com.sjl.deviceconnector.ErrorCode;
import com.sjl.deviceconnector.device.socket.SocketHelper;

import java.util.Timer;
import java.util.TimerTask;

public class SwSocketConnectProvider extends SocketConnectProvider {
    // 心跳间隔时间（毫秒）
    private static final long HEARTBEAT_INTERVAL = 30 * 1000;
    // 心跳失败最大次数
    private static final int MAX_HEARTBEAT_FAIL_COUNT = 3;
    // 心跳数据心跳数据为0字节int类型
    private static final byte[] HEARTBEAT_DATA = new byte[]{0x00, 0x00, 0x00, 0x00};
    // 心跳定时器
    private Timer mHeartbeatTimer;
    // 心跳失败计数
    private int mHeartbeatFailCount;
    // 上次发送数据的时间戳
    private long mLastSendTime;

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
        }
        return result;
    }

    @Override
    public void close() {
        // 停止心跳定时器
        stopHeartbeatTimer();
        super.close();
    }

    @Override
    public synchronized int write(byte[] sendParams, int timeout) {
        int result = super.write(sendParams, timeout);
        if (result == ErrorCode.ERROR_OK) {
            // 更新上次发送数据的时间
            mLastSendTime = System.currentTimeMillis();
            // 重置心跳失败计数
            mHeartbeatFailCount = 0;
        }
        return result;
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
                if (result != ErrorCode.ERROR_OK) {
                    // 心跳发送失败，增加失败计数
                    mHeartbeatFailCount++;
                    if (mHeartbeatFailCount >= MAX_HEARTBEAT_FAIL_COUNT) {
                        // 连续失败达到最大次数，关闭连接
                        close();
                        // 通知 SocketHelper 连接失败，只有异常断开才发送事件
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
