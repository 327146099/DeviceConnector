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
     * usb设备名称
     */
    private String deviceName;

    /**
     * 蓝牙地址
     */
    private String mac;

    /**
     * 蓝牙uuid
     */
    private String uuid;

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
    public ConnectInfo(int baudRate, int vendorId, int productId) {
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
            if (port == null || baudRate == null) {
                throw new RuntimeException("port or baudRate is null");
            }
            return port + ":" + baudRate;
        } else if (type == 2) {
            if (vendorId == null || productId == null || baudRate == null) {
                throw new RuntimeException("vendorId or productId or baudRate is null");
            }
            return vendorId + ":" + productId + ":" + baudRate;
        } else if (type == 3) {
            if (mac == null) {
                throw new RuntimeException("mac is null");
            }
            return mac;
        } else if (type == 4) {
            if (vendorId == null || productId == null) {
                throw new RuntimeException("vendorId or productId is null");
            }
            return vendorId + ":" + productId + ":" + deviceName;
        } else if (type == 5) {
            if (mac == null) {
                throw new RuntimeException("mac is null");
            }
            return mac;
        }
        return null;
    }

    public String getPort() {
        return port;
    }

    public Integer getBaudRate() {
        return baudRate;
    }

    public Integer getType() {
        return type;
    }

    public Integer getVendorId() {
        return vendorId;
    }

    public Integer getProductId() {
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


    public void setPort(String port) {
        this.port = port;
    }

    public void setBaudRate(Integer baudRate) {
        this.baudRate = baudRate;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public void setVendorId(Integer vendorId) {
        this.vendorId = vendorId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
