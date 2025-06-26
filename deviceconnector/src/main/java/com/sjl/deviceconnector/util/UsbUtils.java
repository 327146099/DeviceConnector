package com.sjl.deviceconnector.util;

import android.hardware.usb.UsbDevice;
import android.os.Build;
import com.sjl.deviceconnector.device.usb.UsbHelper;
import com.sjl.deviceconnector.entity.UsbInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class UsbUtils {

    /**
     * 查询系统的串口列表
     *
     * @return 返回串口列表
     */
    public static List<UsbInfo> listUsbDevices(Predicate<UsbDevice> filter) {
        List<UsbDevice> deviceList = UsbHelper.getDeviceList();
        List<UsbInfo> usbInfos = new ArrayList<>();
        for (UsbDevice usbDevice : deviceList) {
            if (filter != null && !filter.test(usbDevice)) {
                continue;
            }
            UsbInfo usbInfo = new UsbInfo();
            usbInfo.setDeviceName(usbDevice.getDeviceName());
            usbInfo.setProductId(usbDevice.getProductId());
            usbInfo.setVendorId(usbDevice.getVendorId());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                usbInfo.setProductName(usbDevice.getProductName());
                usbInfo.setManufacturerName(usbDevice.getManufacturerName());
            }
            if (usbInfo.getDeviceName() == null) {
                usbInfo.setDeviceName("Unknown");
            } else {
                usbInfo.setDeviceName(usbInfo.getDeviceName().trim());
            }
            if (usbInfo.getProductName() == null) {
                usbInfo.setProductName("Unknown");
            } else {
                usbInfo.setProductName(usbInfo.getProductName().trim());
            }
            if (usbInfo.getManufacturerName() == null) {
                usbInfo.setManufacturerName("Unknown");
            } else {
                usbInfo.setManufacturerName(usbInfo.getManufacturerName().trim());
            }
            usbInfos.add(usbInfo);
        }
        return usbInfos;
    }

    public static List<UsbInfo> listUsbDevices() {
        return listUsbDevices(null);
    }


}
