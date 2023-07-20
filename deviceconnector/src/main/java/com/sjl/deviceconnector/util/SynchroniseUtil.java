package com.sjl.deviceconnector.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SynchroniseUtil<T> {
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    private T result;

    public T get() throws InterruptedException {
        countDownLatch.await();
        return this.result;
    }

    public T get(long timeout, TimeUnit timeUnit) throws Exception {
        if (countDownLatch.await(timeout, timeUnit)) {
            return this.result;
        } else {
            throw new RuntimeException("超时");
        }
    }

    public void setResult(T result) {
        this.result = result;
        countDownLatch.countDown();
    }

}
