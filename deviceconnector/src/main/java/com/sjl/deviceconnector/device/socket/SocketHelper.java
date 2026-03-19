package com.sjl.deviceconnector.device.socket;

import java.util.ArrayList;
import java.util.List;

public class SocketHelper {

    private static volatile SocketHelper instance;
    private List<SocketDisconnectListener> disconnectListeners;

    private SocketHelper() {
        disconnectListeners = new ArrayList<>();
    }

    public static SocketHelper getInstance() {
        if (instance == null) {
            synchronized (SocketHelper.class) {
                if (instance == null) {
                    instance = new SocketHelper();
                }
            }
        }
        return instance;
    }

    /**
     * 注册 socket 断开连接监听器
     * @param listener 监听器
     */
    public void registerDisconnectListener(SocketDisconnectListener listener) {
        if (!disconnectListeners.contains(listener)) {
            disconnectListeners.add(listener);
        }
    }

    /**
     * 移除 socket 断开连接监听器
     * @param listener 监听器
     */
    public void unregisterDisconnectListener(SocketDisconnectListener listener) {
        disconnectListeners.remove(listener);
    }

    /**
     * 通知所有监听器 socket 断开连接
     * @param ip socket 的 ip 地址
     * @param port socket 的端口号
     */
    public void notifyDisconnected(String ip, int port) {
        for (SocketDisconnectListener listener : disconnectListeners) {
            listener.onDisconnected(ip, port);
        }
    }

    /**
     * Socket 断开连接监听器
     */
    public interface SocketDisconnectListener {
        /**
         * socket 断开连接回调
         * @param ip socket 的 ip 地址
         * @param port socket 的端口号
         */
        void onDisconnected(String ip, int port);
    }
}
