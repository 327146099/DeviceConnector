package com.sjl.deviceconnector.entity;

/**
 * <p>usb设备信息</p>
 * <p>创建日期：2025-06-11</p>
 */
public class UsbInfo {

    private String deviceName;

    private int productId;

    private int vendorId;

    private String productName;

    private String manufacturerName;

    public String getKey() {
        return vendorId + ":" + productId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getVendorId() {
        return vendorId;
    }

    public void setVendorId(int vendorId) {
        this.vendorId = vendorId;
    }

    public String getManufacturerName() {
        return manufacturerName;
    }

    public void setManufacturerName(String manufacturerName) {
        this.manufacturerName = manufacturerName;
    }
}
