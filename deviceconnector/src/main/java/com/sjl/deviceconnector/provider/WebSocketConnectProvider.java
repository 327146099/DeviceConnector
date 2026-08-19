package com.sjl.deviceconnector.provider;

import android.util.Printer;

import com.sjl.deviceconnector.ErrorCode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketConnectProvider extends BaseIoConnectProvider {
    private WebSocket mWebSocket;
    private OkHttpClient mClient;
    protected String url;
    private int connectTimeout, readTimeout;
    private CountDownLatch mConnectLatch;
    private volatile byte[] mReceivedData;
    private CountDownLatch mReadLatch;

    public WebSocketConnectProvider(String url, int connectTimeout, int readTimeout) {
        this.url = url;
        this.connectTimeout = connectTimeout < 0 ? 10 * 1000 : connectTimeout;
        this.readTimeout = readTimeout < 0 ? 10 * 1000 : readTimeout;
    }

    @Override
    public int open() {
        int state = getState();
        if (state == ErrorCode.ERROR_OK) {
            return state;
        }
        mConnectLatch = new CountDownLatch(1);
        mClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .build();
        mClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                mWebSocket = webSocket;
                mConnectState = true;
                mConnectLatch.countDown();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                handleReceivedData(text.getBytes());
            }

            @Override
            public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
                super.onMessage(webSocket, bytes);
                handleReceivedData(bytes.toByteArray());
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                mConnectState = false;
                mConnectLatch.countDown();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                super.onClosed(webSocket, code, reason);
                mConnectState = false;
            }
        });
        try {
            if (mConnectLatch.await(connectTimeout, TimeUnit.MILLISECONDS)) {
                if (mConnectState) {
                    return ErrorCode.ERROR_OK;
                }
            }
            close();
            return ErrorCode.ERROR_FAIL;
        } catch (InterruptedException e) {
            close();
            return ErrorCode.ERROR_FAIL;
        }
    }

    private void handleReceivedData(byte[] data) {
        mReceivedData = data;
        if (mReadLatch != null) {
            mReadLatch.countDown();
        }
    }

    @Override
    public synchronized int read(byte[] buffer, int timeout) {
        final Printer logging = mLogging;
        try {
            mReadLatch = new CountDownLatch(1);
            mReceivedData = null;
            if (mReadLatch.await(timeout, TimeUnit.MILLISECONDS)) {
                if (mReceivedData != null) {
                    if (logging != null) {
                        logging.println("<<<<< 收：" + com.sjl.deviceconnector.util.ByteUtils.byteArrToHexString(mReceivedData));
                    }
                    int realLength = mReceivedData.length;
                    System.arraycopy(mReceivedData, 0, buffer, 0, Math.min(realLength, buffer.length));
                    return realLength;
                }
            }
            return ErrorCode.ERROR_TIMEOUT;
        } catch (InterruptedException e) {
            return ErrorCode.ERROR_RECEIVE;
        }
    }

    @Override
    public synchronized int write(byte[] sendParams, int timeout) {
        int i = ErrorCode.ERROR_TIMEOUT;
        try {
            if (mWebSocket != null && getState() == ErrorCode.ERROR_OK) {
                mWebSocket.send(okio.ByteString.of(sendParams));
                i = ErrorCode.ERROR_OK;
            }
            return i;
        } catch (Exception e) {
            com.sjl.deviceconnector.util.LogUtils.e("write error.", e);
        }
        return i;
    }

    @Override
    public void close() {
        mConnectState = false;
        if (mWebSocket != null) {
            mWebSocket.close(1000, "normal closure");
            mWebSocket = null;
        }
        if (mClient != null) {
            mClient.dispatcher().executorService().shutdown();
            mClient.connectionPool().evictAll();
            mClient = null;
        }
    }

    @Override
    public InputStream getInputStream() {
        if (mReceivedData != null) {
            return new ByteArrayInputStream(mReceivedData);
        }
        return null;
    }

    @Override
    public void clearReadBuffer() {
        mReceivedData = null;
    }
}
