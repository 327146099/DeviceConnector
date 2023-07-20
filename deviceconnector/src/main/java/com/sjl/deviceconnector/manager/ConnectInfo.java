package com.sjl.deviceconnector.manager;

public class ConnectInfo {

    public static final int SERIAL_PORT = 1;
    public static final int USB_COM = 2;

    public static final int BLUETOOTH = 3;

    public static final int USB = 4;

    public static final int BLUETOOTH_BLE = 5;
    /**
     * 串口
     */
    private String port;
    /**
     * 波特率
     */
    private Integer baudRate;

    /**
     * 1 串口  2 usb2串口 3 蓝牙 4. usb
     */
    private Integer type = 1;

    /**
     * usb vendorId
     */
    private Integer vendorId;

    /**
     * usb productId
     */
    private Integer productId;

    /**
     * 蓝牙地址
     */
    private String mac;

    /*是否自动回收
     */
    private boolean autoClose = true;


    public ConnectInfo() {
    }

    /**
     * 串口连接
     */
    public ConnectInfo(String port, int baudRate) {
        this.port = port;
        this.baudRate = baudRate;
        this.type = 1;
    }

    /**
     * 蓝牙连接
     */
    public ConnectInfo(String mac) {
        this.mac = mac;
        this.type = 3;
    }

    /**
     * usb转串口连接
     */
    public ConnectInfo(String port, int baudRate, int vendorId, int productId) {
        this.port = port;
        this.baudRate = baudRate;
        this.vendorId = vendorId;
        this.productId = productId;
        this.type = 2;
    }

    /**
     * usb
     */
    public ConnectInfo(int vendorId, int productId) {
        this.vendorId = vendorId;
        this.productId = productId;
        this.type = 4;
    }

    public String getKey() {
        if (type == 1) {
            return port + ":" + baudRate;
        } else if (type == 2) {
            return vendorId + ":" + productId + ":" + port + ":" + baudRate;
        } else if (type == 3) {
            return mac;
        }
        if (type == 4) {
            return vendorId + ":" + productId;
        }
        if (type == 5) {
            return mac;
        }
        return null;
    }

    public String getPort() {
        return port;
    }

    public int getBaudRate() {
        return baudRate;
    }

    public int getType() {
        return type;
    }

    public int getVendorId() {
        return vendorId;
    }

    public int getProductId() {
        return productId;
    }

    public String getMac() {
        return mac;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isAutoClose() {
        return autoClose;
    }

    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }
}
