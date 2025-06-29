package com.sjl.deviceconnector.util;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.os.Build;
import androidx.annotation.RequiresApi;
import com.sjl.deviceconnector.device.bluetooth.BluetoothHelper;
import com.sjl.deviceconnector.entity.BluetoothInfo;
import com.sjl.deviceconnector.entity.BluetoothScanResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * TODO
 *
 * @author Kelly
 * @version 1.0.0
 * @filename BluetoothUtils
 * @time 2023/3/3 15:04
 * @copyright(C) 2023 song
 */
public class BluetoothUtils {


    /**
     * 蓝牙适配器
     *
     * @return
     */
    public static BluetoothAdapter getBluetoothAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * 蓝牙适配器
     *
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static BluetoothLeScanner getBluetoothLeScanner() {
        BluetoothLeScanner bluetoothLeScanner = getBluetoothAdapter().getBluetoothLeScanner();
        return bluetoothLeScanner;
    }

    /**
     * 判断蓝牙开关是否启用
     *
     * @return
     */
    public static boolean isEnabled() {
        return getBluetoothAdapter().isEnabled();
    }

    /**
     * 打开蓝牙
     */
    public static void enable() {
        getBluetoothAdapter().enable();
    }

    /**
     * 关闭蓝牙
     */
    public static void disable() {
        getBluetoothAdapter().disable();
    }

    /**
     * 获取已配对的蓝牙设备
     *
     * @return
     */
    public static List<BluetoothDevice> getBondedDevices() {
        Set<BluetoothDevice> bondedDevices = getBluetoothAdapter().getBondedDevices();
        if (bondedDevices == null) {
            return Collections.emptyList();
        }
        return new ArrayList(bondedDevices);
    }

    /**
     * 获取已配对的蓝牙设备
     *
     * @return
     */
    public static List<BluetoothScanResult> wrapBondedDevices() {
        List<BluetoothDevice> bondedDevices = getBondedDevices();

        List<BluetoothScanResult> arrayList = new ArrayList(bondedDevices.size());
        for (BluetoothDevice bd : bondedDevices) {
            arrayList.add(new BluetoothScanResult(bd, -1, (byte[]) null));
        }
        return arrayList;
    }


    /**
     * 取消配对
     *
     * @param device
     * @return
     * @throws Exception
     */
    public static boolean cancelBond(BluetoothDevice device) throws Exception {
        Method createBondMethod = device.getClass().getMethod("removeBond");
        Boolean returnValue = (Boolean) createBondMethod.invoke(device);
        return returnValue.booleanValue();
    }

    /**
     * 配对蓝牙设备
     *
     * @param device
     * @return
     * @throws Exception
     */
    public static boolean createBond(BluetoothDevice device) throws Exception {
        Method createBondMethod = device.getClass().getMethod("createBond");
        Boolean returnValue = (Boolean) createBondMethod.invoke(device);
        return returnValue.booleanValue();
    }

    /**
     * 扫描蓝牙设备
     *
     * @param scanTime
     * @return
     */
    public static List<BluetoothInfo> listBluetooth(int scanTime, Predicate<BluetoothScanResult> filter) {
        List<BluetoothScanResult> bluetoothScanResults = BluetoothHelper.getInstance().listBluetooth(scanTime);
        List<BluetoothInfo> bluetoothInfos = new ArrayList<>();
        for (BluetoothScanResult bluetoothScanResult : bluetoothScanResults) {
            if (filter != null && !filter.test(bluetoothScanResult)) {
                continue;
            }
            BluetoothInfo bluetoothInfo = new BluetoothInfo();
            // 如果设备名称为空，设置为Unknown Device
            if (bluetoothScanResult.getName() == null || bluetoothScanResult.getName().equals("")) {
                bluetoothInfo.setDeviceName("Unknown");
            } else {
                bluetoothInfo.setDeviceName(bluetoothScanResult.getName());
            }
            bluetoothInfo.setMac(bluetoothScanResult.getAddress());
            bluetoothInfo.setRssi(bluetoothScanResult.getRssi());
            bluetoothInfos.add(bluetoothInfo);
        }
        return bluetoothInfos;
    }

    /**
     * 扫描蓝牙设备
     *
     * @param scanTime
     * @return
     */
    public static List<BluetoothInfo> listBluetooth(int scanTime) {
        return listBluetooth(scanTime, null);
    }
}
