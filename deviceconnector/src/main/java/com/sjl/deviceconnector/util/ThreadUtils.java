package com.sjl.deviceconnector.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadUtils {

    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public static void execute(Runnable runnable) {
        executorService.execute(runnable);
    }

}
