package com.sjl.deviceconnector.provider;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.util.Printer;

import com.sjl.deviceconnector.DeviceContext;
import com.sjl.deviceconnector.ErrorCode;
import com.sjl.deviceconnector.Waiter;
import com.sjl.deviceconnector.device.bluetooth.ble.BluetoothGattWrap;
import com.sjl.deviceconnector.device.bluetooth.ble.BluetoothLeClient;
import com.sjl.deviceconnector.device.bluetooth.ble.BluetoothLeNotifyListener;
import com.sjl.deviceconnector.device.bluetooth.ble.BluetoothLeServiceListener;
import com.sjl.deviceconnector.device.bluetooth.ble.request.BluetoothLeRequest;
import com.sjl.deviceconnector.device.bluetooth.ble.request.CharacteristicReadRequest;
import com.sjl.deviceconnector.device.bluetooth.ble.request.CharacteristicWriteRequest;
import com.sjl.deviceconnector.device.bluetooth.ble.request.DescriptorReadRequest;
import com.sjl.deviceconnector.device.bluetooth.ble.request.DescriptorWriteRequest;
import com.sjl.deviceconnector.device.bluetooth.ble.request.IndicateRequest;
import com.sjl.deviceconnector.device.bluetooth.ble.request.MtuRequest;
import com.sjl.deviceconnector.device.bluetooth.ble.request.NotifyRequest;
import com.sjl.deviceconnector.device.bluetooth.ble.request.RemoteRssiRequest;
import com.sjl.deviceconnector.device.bluetooth.ble.response.BluetoothLeResponse;
import com.sjl.deviceconnector.exception.ProviderTimeoutException;
import com.sjl.deviceconnector.util.ByteUtils;
import com.sjl.deviceconnector.util.LogUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import androidx.annotation.RequiresApi;

/**
 * 蓝牙Ble连接提供者
 *
 * <p>连接，发现服务，发送和接收数据，断开连接</p>
 *
 * @author Kelly
 * @version 1.0.0
 * @filename BluetoothLeConnectProvider.java
 * @time 2023/3/3 9:41
 * @copyright(C) 2023 song
 */
public class BluetoothLeConnectProvider extends BaseConnectProvider {

    private final BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private MyBluetoothGattCallback mGattCallback;

    private CountDownLatch mCountDownLatch;

    private static final Waiter DEFAULT_WAITER = new Waiter();
    private final Waiter waiter;

    private BluetoothGattWrap mBluetoothGattWrap;
    private BluetoothLeClient mBluetoothLeClient;
    private Object object = new Object();
    private BluetoothLeResponse resultTempBuffer = new BluetoothLeResponse();
    private boolean resultReceived;
    private boolean resultFailed;
    private int errorCode = -1;
    private BluetoothLeNotifyListener mBluetoothLeNotifyListener;
    private BluetoothLeServiceListener mBluetoothLeServiceListener;
    private static final int DEFAULT_RECONNECT_COUNT = 3;
    /**
     * 重连次数
     */
    private int reconnectCount = 0;
    private boolean init = false;

    /**
     * 默认写特征值所在服务
     */
    private UUID mDefaultWriteService;
    /**
     * 默认写特征值
     */
    private UUID mDefaultWriteCharacter;
    /**
     * 默认读（通知）特征值所在服务
     */
    private UUID mDefaultReadService;
    /**
     * 默认读（通知）特征值，用于流式 read 的数据来源
     */
    private UUID mDefaultReadCharacter;
    /**
     * 通知队列最大容量，防止消费速度跟不上表头 notify 推送速度时无界堆积导致内存溢出
     */
    private static final int MAX_NOTIFY_QUEUE_SIZE = 100;
    /**
     * 通知数据缓冲队列，由 onCharacteristicChanged 写入，read 消费
     */
    private final LinkedBlockingQueue<byte[]> mNotifyQueue = new LinkedBlockingQueue<>(MAX_NOTIFY_QUEUE_SIZE);
    /**
     * 上一次 read 未消费完的通知分片
     */
    private byte[] mNotifyLeftover;
    private int mNotifyLeftoverOffset;

    /**
     * 初始化一个蓝牙Ble提供者
     *
     * @param address
     */
    public BluetoothLeConnectProvider(String address) {
        this(address, null, null, null, null);
    }


    /**
     * 初始化一个蓝牙Ble提供者
     *
     * @param bluetoothDevice
     */
    public BluetoothLeConnectProvider(BluetoothDevice bluetoothDevice) {
        this(bluetoothDevice, null, null, null, null);
    }

    /**
     * 初始化一个蓝牙Ble提供者，并指定默认读写特征值
     *
     * @param address
     * @param writeService  默认写特征值所在服务UUID，为null则write返回不支持
     * @param writeCharacter 默认写特征值UUID
     * @param readService   默认读（通知）特征值所在服务UUID，为null则read返回不支持
     * @param readCharacter 默认读（通知）特征值UUID
     */
    public BluetoothLeConnectProvider(String address, UUID writeService, UUID writeCharacter,
                                      UUID readService, UUID readCharacter) {
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter == null) {
            throw new NullPointerException("设备不支持蓝牙");
        }
        this.mBluetoothDevice = defaultAdapter.getRemoteDevice(address);
        this.waiter = DEFAULT_WAITER;
        this.mDefaultWriteService = writeService;
        this.mDefaultWriteCharacter = writeCharacter;
        this.mDefaultReadService = readService;
        this.mDefaultReadCharacter = readCharacter;
        initParams();
    }

    /**
     * 初始化一个蓝牙Ble提供者，并指定默认读写特征值
     *
     * @param bluetoothDevice
     * @param writeService  默认写特征值所在服务UUID，为null则write返回不支持
     * @param writeCharacter 默认写特征值UUID
     * @param readService   默认读（通知）特征值所在服务UUID，为null则read返回不支持
     * @param readCharacter 默认读（通知）特征值UUID
     */
    public BluetoothLeConnectProvider(BluetoothDevice bluetoothDevice, UUID writeService, UUID writeCharacter,
                                      UUID readService, UUID readCharacter) {
        this.mBluetoothDevice = bluetoothDevice;
        this.waiter = DEFAULT_WAITER;
        this.mDefaultWriteService = writeService;
        this.mDefaultWriteCharacter = writeCharacter;
        this.mDefaultReadService = readService;
        this.mDefaultReadCharacter = readCharacter;
        initParams();
    }

    private void initParams() {
        mBluetoothGattWrap = new BluetoothGattWrap();
        mBluetoothLeClient = new BluetoothLeClient(mBluetoothGattWrap);
    }

    @Override
    public int open() {
        int state = getState();
        if (state == ErrorCode.ERROR_OK) {
            return state;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mGattCallback = new MyBluetoothGattCallback();
            }
            if (mGattCallback == null) {
                return ErrorCode.ERROR_NOT_SUPPORTED;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mCountDownLatch = new CountDownLatch(1);
                mBluetoothGatt = mBluetoothDevice.connectGatt(DeviceContext.getContext(), false,
                        mGattCallback, BluetoothDevice.TRANSPORT_LE);
                // 在等待连接就绪前先把gatt交给client，确保服务发现回调时client可用
                mBluetoothLeClient.setBluetoothGatt(mBluetoothGatt);
                waitOpenReady();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mCountDownLatch = new CountDownLatch(1);
                mBluetoothGatt = mBluetoothDevice.connectGatt(DeviceContext.getContext(), false, mGattCallback);
                mBluetoothLeClient.setBluetoothGatt(mBluetoothGatt);
                waitOpenReady();
            }else {
                return ErrorCode.ERROR_NOT_SUPPORTED;
            }
            if (mConnectState){
                return ErrorCode.ERROR_OK;
            }else {
                return ErrorCode.ERROR_OPEN_FAIL;
            }
        } catch (Exception e) {
            LogUtils.e("蓝牙连接异常", e);
            close();
            return ErrorCode.ERROR_FAIL;
        }finally {
            init = true;
        }
    }

    /**
     * 重连, 请先执行一次open,在进行重连
     *
     * @return
     */
    public int reconnect() {
        if (!init){

            return ErrorCode.ERROR_NOT_INIT;
        }
        if (reconnectCount <= 0) {
            reconnectCount = DEFAULT_RECONNECT_COUNT;
        }
        int tempReconnectCount = 0;
        do {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mCountDownLatch = new CountDownLatch(1);
                mBluetoothGatt = mBluetoothDevice.connectGatt(DeviceContext.getContext(), false,
                        mGattCallback, BluetoothDevice.TRANSPORT_LE);
                mBluetoothLeClient.setBluetoothGatt(mBluetoothGatt);
                waitOpenReady();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mCountDownLatch = new CountDownLatch(1);
                mBluetoothGatt = mBluetoothDevice.connectGatt(DeviceContext.getContext(), false, mGattCallback);
                mBluetoothLeClient.setBluetoothGatt(mBluetoothGatt);
                waitOpenReady();
            }
            tempReconnectCount++;
        } while (!mConnectState && tempReconnectCount <= reconnectCount);
        if (mConnectState){
            return ErrorCode.ERROR_OK;
        }else {
            return ErrorCode.ERROR_OPEN_FAIL;
        }
    }


    /**
     * 写数据，向构造器传入的默认写特征值写入
     *
     * @param sendParams 发送数据
     * @param timeout    超时时间，单位ms
     * @return 0 写成功，-1超时，-2发送失败，-7数据为空，-9未配置默认写特征值
     */
    @Override
    public int write(byte[] sendParams, int timeout) {
        if (mDefaultWriteService == null || mDefaultWriteCharacter == null) {
            return ErrorCode.ERROR_NOT_SUPPORTED;
        }
        if (sendParams == null || sendParams.length == 0) {
            return ErrorCode.ERROR_DATA_NULL;
        }
        if (getState() != ErrorCode.ERROR_OK) {
            return ErrorCode.ERROR_NOT_CONNECTED;
        }
        try {
            CharacteristicWriteRequest request = new CharacteristicWriteRequest();
            request.setService(mDefaultWriteService);
            request.setCharacter(mDefaultWriteCharacter);
            request.setBytes(sendParams);
            sendRequest(request, null, timeout);
            return ErrorCode.ERROR_OK;
        } catch (ProviderTimeoutException e) {
            LogUtils.e("ble write超时", e);
            return ErrorCode.ERROR_TIMEOUT;
        } catch (Exception e) {
            LogUtils.e("ble write异常", e);
            return ErrorCode.ERROR_SEND;
        }
    }

    /**
     * 读数据，从通知缓冲队列中消费（数据来源为默认读特征值的通知）
     *
     * @param buffer  临时缓冲区
     * @param timeout 超时时间，单位ms
     * @return >0读取数据成功（代表数据长度），-1读取超时，-3接收数据失败，-7数据为空，-9未配置默认读特征值
     */
    @Override
    public synchronized int read(byte[] buffer, int timeout) {
        if (buffer == null || buffer.length == 0) {
            return ErrorCode.ERROR_DATA_NULL;
        }
        if (mDefaultReadService == null || mDefaultReadCharacter == null) {
            return ErrorCode.ERROR_NOT_SUPPORTED;
        }
        if (getState() != ErrorCode.ERROR_OK) {
            return ErrorCode.ERROR_NOT_CONNECTED;
        }
        int total = consumeLeftover(buffer, 0);
        long deadline = System.currentTimeMillis() + timeout;
        try {
            while (total < buffer.length) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                // 还没有数据时阻塞等待首个通知分片；已有数据则非阻塞尽量多取
                byte[] chunk = (total == 0)
                        ? mNotifyQueue.poll(remaining, TimeUnit.MILLISECONDS)
                        : mNotifyQueue.poll();
                if (chunk == null || chunk.length == 0) {
                    break;
                }
                int copy = Math.min(chunk.length, buffer.length - total);
                System.arraycopy(chunk, 0, buffer, total, copy);
                total += copy;
                if (copy < chunk.length) {
                    // 当前分片超出缓冲区剩余容量，留待下次 read 消费
                    mNotifyLeftover = chunk;
                    mNotifyLeftoverOffset = copy;
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return total > 0 ? total : ErrorCode.ERROR_TIMEOUT;
        }
        if (total == 0) {
            return ErrorCode.ERROR_TIMEOUT;
        }
        Printer logging = mLogging;
        if (logging != null) {
            logging.println("<<<<< 收：" + ByteUtils.byteArrToHexString(Arrays.copyOfRange(buffer, 0, total)));
        }
        return total;
    }

    /**
     * 写并读数据，先向默认写特征值写入，再从通知缓冲队列读取
     *
     * @param sendParams 发送命令
     * @param buffer     临时缓冲区
     * @param timeout    超时时间，单位ms
     * @return >0读取数据成功（代表数据长度），-1读取超时，-2发送失败，-7数据为空
     */
    @Override
    public synchronized int read(byte[] sendParams, byte[] buffer, int timeout) {
        if (sendParams == null || sendParams.length == 0) {
            return ErrorCode.ERROR_DATA_NULL;
        }
        Printer logging = mLogging;
        if (logging != null) {
            logging.println(">>>>> 发：" + ByteUtils.byteArrToHexString(sendParams));
        }
        int ret = write(sendParams, timeout);
        if (ret == ErrorCode.ERROR_OK) {
            return read(buffer, timeout);
        }
        return ErrorCode.ERROR_SEND;
    }

    /**
     * 清空通知缓冲队列与遗留分片
     * <p>keepAlive 模式下，每次读取前由 BalanceService 调用，
     * 避免上一次未消费完的通知分片或表头持续 notify 推送的历史数据
     * 在下次 read 中被当作新数据返回，导致读数延迟。</p>
     */
    @Override
    public synchronized void clearReadBuffer() {
        mNotifyQueue.clear();
        mNotifyLeftover = null;
        mNotifyLeftoverOffset = 0;
    }

    /**
     * 消费上一次 read 遗留的通知分片
     *
     * @param buffer 缓冲区
     * @param offset  已写入偏移
     * @return 消费后的偏移
     */
    private int consumeLeftover(byte[] buffer, int offset) {
        if (mNotifyLeftover == null || mNotifyLeftoverOffset >= mNotifyLeftover.length) {
            return offset;
        }
        int available = mNotifyLeftover.length - mNotifyLeftoverOffset;
        int copy = Math.min(available, buffer.length - offset);
        System.arraycopy(mNotifyLeftover, mNotifyLeftoverOffset, buffer, offset, copy);
        mNotifyLeftoverOffset += copy;
        if (mNotifyLeftoverOffset >= mNotifyLeftover.length) {
            mNotifyLeftover = null;
            mNotifyLeftoverOffset = 0;
        }
        return offset + copy;
    }

    /**
     * 发送Ble请求
     *
     * @param request Ble请求
     * @param timeout，单位ms
     * @return
     */
    public int sendRequest(BluetoothLeRequest request, int timeout) throws Exception {
        processRequest(request, null, timeout);
        return ErrorCode.ERROR_OK;
    }

    /**
     * 发送Ble请求,带返回数据
     *
     * @param request  Ble请求
     * @param response Ble响应
     * @param timeout  请求超时时间，单位ms
     * @return
     */
    public int sendRequest(BluetoothLeRequest request, BluetoothLeResponse response, int timeout) throws Exception {
        if (request == null) {
            throw new NullPointerException("request 不能为空.");
        }
        processRequest(request, response, timeout);
        return ErrorCode.ERROR_OK;
    }

    /**
     * 处理Ble请求
     *
     * @param request  Ble请求
     * @param response Ble响应
     * @param timeout  请求超时时间
     */
    private void processRequest(BluetoothLeRequest request, BluetoothLeResponse response, int timeout) throws InterruptedException, ExecutionException {
        int state = getState();
        if (state != ErrorCode.ERROR_OK) {
            throw new RuntimeException("连接已断开");
        }
        synchronized (object) {
            resultFailed = false;
            resultReceived = false;
            resultTempBuffer.reset();
            boolean ret;
            boolean needWait = true;
            if (request instanceof CharacteristicWriteRequest) {
                CharacteristicWriteRequest characteristicWriteRequest = (CharacteristicWriteRequest) request;
                ret = mBluetoothLeClient.writeCharacteristic(characteristicWriteRequest.getService(), characteristicWriteRequest.getCharacter(), characteristicWriteRequest.getBytes());

            } else if (request instanceof CharacteristicReadRequest) {
                CharacteristicReadRequest characteristicReadRequest = (CharacteristicReadRequest) request;
                ret = mBluetoothLeClient.readCharacteristic(characteristicReadRequest.getService(), characteristicReadRequest.getCharacter());

            } else if (request instanceof DescriptorWriteRequest) {
                DescriptorWriteRequest descriptorWriteRequest = (DescriptorWriteRequest) request;
                ret = mBluetoothLeClient.writeDescriptor(descriptorWriteRequest.getService(), descriptorWriteRequest.getCharacter()
                        , descriptorWriteRequest.getDescriptor(), descriptorWriteRequest.getBytes());

            } else if (request instanceof DescriptorReadRequest) {
                DescriptorReadRequest descriptorReadRequest = (DescriptorReadRequest) request;

                ret = mBluetoothLeClient.readDescriptor(descriptorReadRequest.getService(), descriptorReadRequest.getCharacter(), descriptorReadRequest.getDescriptor());

            } else if (request instanceof NotifyRequest) {
                needWait = false;
                NotifyRequest notifyRequest = (NotifyRequest) request;
                ret = mBluetoothLeClient.setCharacteristicNotification(notifyRequest.getService(), notifyRequest.getCharacter(), notifyRequest.isEnable());

            } else if (request instanceof IndicateRequest) {
                needWait = false;
                IndicateRequest indicateRequest = (IndicateRequest) request;
                ret = mBluetoothLeClient.setCharacteristicIndication(indicateRequest.getService(), indicateRequest.getCharacter(), indicateRequest.isEnable());

            } else if (request instanceof RemoteRssiRequest) {
                ret = mBluetoothLeClient.readRemoteRssi();

            } else if (request instanceof MtuRequest) {
                MtuRequest mtuRequest = (MtuRequest) request;
                ret = mBluetoothLeClient.requestMtu(mtuRequest.getMtu());

            } else {
                throw new RuntimeException("未知请求");
            }
            if (!ret && response != null){
                //请求失败
                resultTempBuffer.setCode(ErrorCode.ERROR_SEND);
                response.copy(resultTempBuffer);
                return;
            }
            if (!needWait){
                resultTempBuffer.setCode(ErrorCode.ERROR_OK);
            }else {
                waitRequest(timeout);
                if (resultFailed) {
                    throw new ExecutionException(new Exception("错误码：" + errorCode));
                } else if (!resultReceived) {
                    throw new ProviderTimeoutException("通讯超时");
                }
            }
            if (response != null) {
                response.copy(resultTempBuffer);
            }

        }

    }

    @Override
    public void close() {
        mBluetoothLeNotifyListener = null;
        mBluetoothLeServiceListener = null;
        resultFailed = false;
        resultReceived = false;
        resultTempBuffer.reset();
        mGattCallback = null;
        mConnectState = false;
        mNotifyQueue.clear();
        mNotifyLeftover = null;
        mNotifyLeftoverOffset = 0;
        clearConnect();
    }

    /**
     * 清除连接
     * <p>在断开连接之后再次连接经常会出现133错误</p>
     */
    public synchronized void clearConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            refreshDeviceCache();
            mBluetoothGatt.close();
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public synchronized void refreshDeviceCache() {
        try {
            final Method refresh = BluetoothGatt.class.getMethod("refresh");
            if (refresh != null && mBluetoothGatt != null) {
                refresh.setAccessible(true);
                boolean ret = (Boolean) refresh.invoke(mBluetoothGatt);
                LogUtils.i("refreshDeviceCache, ret:  " + ret);
            }
        } catch (Exception e) {
            LogUtils.i("refreshDeviceCache exception: " + e.getMessage());
        }
    }


    private void waitOpenReady() {
        try {
            if (mCountDownLatch == null) {
                return;
            }
            // 等待连接 + 服务发现完成，加超时防止 onServicesDiscovered 不回调时永久阻塞
            if (!mCountDownLatch.await(15, TimeUnit.SECONDS)) {
                LogUtils.e("等待连接就绪超时（连接+服务发现）");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void notifyOpenReady() {
        if (mCountDownLatch != null) {
            mCountDownLatch.countDown();
            mCountDownLatch = null;
        }
    }


    private void waitRequest(int timeout) throws InterruptedException {
        waiter.waitForTimeout(object, timeout);
    }

    private void notifyRequest() {
        waiter.notifyAll(object);
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void discoveredServices() {
        mBluetoothGatt.discoverServices();

    }

    /**
     * 需要跳过的通用 BLE 服务（不承载业务数据）
     */
    private static final UUID SERVICE_GAP = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb");
    private static final UUID SERVICE_GATT = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb");
    private static final UUID SERVICE_DEVICE_INFO = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    private static final UUID SERVICE_BATTERY = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");

    /**
     * 服务发现后自动挑选默认读写特征值
     * <p>当构造时未显式指定（如经 {@link ConnectManager} 单参构造的 BLE 连接）时，
     * 跳过通用服务，选取首个带 NOTIFY/INDICATE 属性的特征值作为默认读特征值，
     * 选取首个带 WRITE/WRITE_NO_RESPONSE 属性的特征值作为默认写特征值。
     * 适用于蓝牙表头等单一业务服务的 BLE 设备。已显式指定的不会被覆盖。</p>
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void autoPickDefaultCharacteristics() {
        boolean needRead = mDefaultReadService == null || mDefaultReadCharacter == null;
        boolean needWrite = mDefaultWriteService == null || mDefaultWriteCharacter == null;
        if (!needRead && !needWrite) {
            return;
        }
        List<BluetoothGattService> services = mBluetoothGattWrap.getServices();
        if (services == null || services.isEmpty()) {
            return;
        }
        UUID pickedReadService = null;
        UUID pickedReadChar = null;
        UUID pickedWriteService = null;
        UUID pickedWriteChar = null;
        for (BluetoothGattService service : services) {
            UUID serviceUuid = service.getUuid();
            if (isGenericService(serviceUuid)) {
                continue;
            }
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                int props = characteristic.getProperties();
                if (pickedReadService == null
                        && ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        || (props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)) {
                    pickedReadService = serviceUuid;
                    pickedReadChar = characteristic.getUuid();
                }
                if (pickedWriteService == null
                        && ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                        || (props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)) {
                    pickedWriteService = serviceUuid;
                    pickedWriteChar = characteristic.getUuid();
                }
                if (pickedReadService != null && pickedWriteService != null) {
                    break;
                }
            }
            if (pickedReadService != null && pickedWriteService != null) {
                break;
            }
        }
        if (needRead && pickedReadService != null) {
            mDefaultReadService = pickedReadService;
            mDefaultReadCharacter = pickedReadChar;
            LogUtils.i("自动挑选默认读特征值: service=" + pickedReadService + ", char=" + pickedReadChar);
        }
        if (needWrite && pickedWriteService != null) {
            mDefaultWriteService = pickedWriteService;
            mDefaultWriteCharacter = pickedWriteChar;
            LogUtils.i("自动挑选默认写特征值: service=" + pickedWriteService + ", char=" + pickedWriteChar);
        }
    }

    private boolean isGenericService(UUID serviceUuid) {
        if (serviceUuid == null) {
            return true;
        }
        return SERVICE_GAP.equals(serviceUuid)
                || SERVICE_GATT.equals(serviceUuid)
                || SERVICE_DEVICE_INFO.equals(serviceUuid)
                || SERVICE_BATTERY.equals(serviceUuid);
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public class MyBluetoothGattCallback extends BluetoothGattCallback {

        /**
         * 连接状态回调
         *
         * @param gatt     GATT client
         * @param status   用于返回操作是否成功,会返回异常码， 如BluetoothGatt#GATT_SUCCESS,下面的方法跟这里一样
         * @param newState 返回连接状态，如BluetoothProfile#STATE_DISCONNECTED、BluetoothProfile#STATE_CONNECTED
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            /**
             *	错误代码:
             *	133 ：连接超时或未找到设备。
             *	8 ： 设备超出范围
             *	22 ：表示本地设备终止了连接
             */
            LogUtils.e("连接状态改变，status:" + status + ",newState:" + newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED){
                    mConnectState = true;
                    //发现服务，onServicesDiscovered 回调里才会释放 open 等待，
                    // 确保自动挑选默认读写特征值在 open 返回前完成
                    discoveredServices();
                }else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                    mConnectState = false;
                    //清除连接
                    clearConnect();
                    notifyOpenReady();
                }else {
                    LogUtils.e("重新连接");
                   /* //重新连接
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.connect();
                    }*/
                    notifyOpenReady();
                }
            } else {
                close();
                notifyOpenReady();
                //连接前先断开连接,开始重连，建议外部调用open之后调用
//                reconnect();
            }

        }


        /**
         * 服务发现回调
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (BluetoothGatt.GATT_SUCCESS == status) {
                mBluetoothGattWrap.addServices(gatt.getServices());
                // 未显式配置默认读写特征值时（如通过 ConnectManager 单参构造）自动挑选，
                // 适用于蓝牙表头等单一业务服务的 BLE 设备
                autoPickDefaultCharacteristics();
            } else {
                mBluetoothGattWrap.clear();
                LogUtils.e("服务发现失败 status:" + status);
            }
            if (mBluetoothLeServiceListener != null){
                mBluetoothLeServiceListener.onServicesDiscovered(status,mBluetoothGattWrap.getServices());
            }
            // 服务发现成功后，自动开启默认读（通知）特征值的通知，供流式 read 消费
            if (BluetoothGatt.GATT_SUCCESS == status
                    && mDefaultReadService != null && mDefaultReadCharacter != null) {
                boolean ok = mBluetoothLeClient.setCharacteristicNotification(
                        mDefaultReadService, mDefaultReadCharacter, true);
                LogUtils.i("开启默认读特征值通知: " + ok);
            }
            // 服务发现完成（无论成功失败），释放 open 等待，
            // 确保自动挑选默认读写特征值在 open 返回前完成
            notifyOpenReady();
        }

        /**
         * 读取特征值回调
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                         int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            synchronized (object) {
                if (BluetoothGatt.GATT_SUCCESS == status) {
                    resultReceived = true;
                    resultTempBuffer.setData(characteristic.getValue());
                } else {
                    resultTempBuffer.setCode(status);
                    LogUtils.e("读取特征值失败 status:" + status);
                    errorCode = status;
                    resultFailed = true;
                }
                notifyRequest();
            }

        }

        /**
         * 特征写入回调
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            synchronized (object) {
                if (BluetoothGatt.GATT_SUCCESS == status) {

                    resultReceived = true;
                    resultTempBuffer.setData(characteristic.getValue());
                } else {
                    resultTempBuffer.setCode(status);
                    LogUtils.e("特征写入失败 status:" + status);
                    errorCode = status;
                    resultFailed = true;
                }
                notifyRequest();
            }

        }

        /**
         * 监听外设特征值改变,双向通信使用，前提是该Characteristic具有NOTIFY属性，即监听服务端参数改变时回调（外设自身修改硬件参数）
         * 当写入完特征值后，外设修改自己的特征值进行回复时，手机端会触发BluetoothGattCallback#onCharacteristicChanged()方法，获取到外设回复的值，从而实现双向通信。
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            UUID serviceUuid = characteristic.getService().getUuid();
            UUID charUuid = characteristic.getUuid();
            byte[] value = characteristic.getValue();
            // 仅缓存默认读（通知）特征值的数据，供流式 read 消费
            if (mDefaultReadService != null && mDefaultReadService.equals(serviceUuid)
                    && mDefaultReadCharacter != null && mDefaultReadCharacter.equals(charUuid)) {
                if (value != null && value.length > 0) {
                    // 队列满时丢弃最旧的通知，确保最新数据能入队，避免读数长时间停留在陈旧值
                    if (!mNotifyQueue.offer(value)) {
                        mNotifyQueue.poll();
                        mNotifyQueue.offer(value);
                    }
                }
            }
            if (mBluetoothLeNotifyListener  != null){
                mBluetoothLeNotifyListener.onNotify(serviceUuid, charUuid, value);
            }
        }


        /**
         * 描述读取回调
         */
        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                     int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            synchronized (object) {
                if (BluetoothGatt.GATT_SUCCESS == status) {
                    resultReceived = true;
                    resultTempBuffer.setData(descriptor.getValue());
                } else {
                    resultTempBuffer.setCode(status);
                    LogUtils.e("描述读取失败 status:" + status);
                    errorCode = status;
                    resultFailed = true;
                }
                notifyRequest();
            }

        }

        /**
         * 描述写入回调
         */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            synchronized (object) {
                if (BluetoothGatt.GATT_SUCCESS == status) {
                    resultReceived = true;
                    resultTempBuffer.setData(descriptor.getValue());
                } else {
                    resultTempBuffer.setCode(status);
                    LogUtils.e("描述写入失败 status:" + status);
                    errorCode = status;
                    resultFailed = true;
                }
                notifyRequest();
            }

        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            synchronized (object) {
                if (BluetoothGatt.GATT_SUCCESS == status) {
                    resultReceived = true;
                    resultTempBuffer.setRssi(rssi);
                } else {
                    resultTempBuffer.setCode(status);
                    LogUtils.e("读取Rssi失败 status:" + status);
                    errorCode = status;
                    resultFailed = true;
                }
                notifyRequest();
            }
        }

        /**
         * MTU修改回调
         */
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            synchronized (object) {
                if (BluetoothGatt.GATT_SUCCESS == status) {
                    resultReceived = true;
                    resultTempBuffer.setMtu(mtu);
                } else {
                    resultTempBuffer.setCode(status);
                    LogUtils.e("Mtu修改失败 status:" + status);
                    errorCode = status;
                    resultFailed = true;
                }
                notifyRequest();
            }

        }

    }



    /**
     * 监听服务端发来的消息
     *
     * @param bluetoothLeNotifyListener
     */
    public void setBluetoothLeNotifyListener(BluetoothLeNotifyListener bluetoothLeNotifyListener) {
        this.mBluetoothLeNotifyListener = bluetoothLeNotifyListener;
    }

    /**
     * 服务发现监听
     * @param bluetoothLeServiceListener
     */
    public void setBluetoothLeServiceListener(BluetoothLeServiceListener bluetoothLeServiceListener) {
        this.mBluetoothLeServiceListener = bluetoothLeServiceListener;
    }

    /**
     * 设置重连次数
     * @param reconnectCount
     */
    public void setReconnectCount(int reconnectCount) {
        this.reconnectCount = reconnectCount;
    }


    public BluetoothGatt getBluetoothGatt() {
        return mBluetoothGatt;
    }
}
