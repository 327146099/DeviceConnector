package com.sjl.deviceconnector.entity;

/**
 * <p>蓝牙信息</p>
 */
public class BluetoothInfo {

    private String deviceName;

    private String mac;

    private int rssi;

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getKey() {
        return mac;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }
}
