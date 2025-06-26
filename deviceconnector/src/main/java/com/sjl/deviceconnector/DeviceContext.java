package com.sjl.deviceconnector;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.util.Log;
import com.sjl.deviceconnector.util.LogUtils;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 设备上下文
 *
 * @author Kelly
 */
public class DeviceContext {
    private volatile static Context context;

    private volatile static Activity activity;

    private static Lock lock = new ReentrantLock();

    private static Handler mMainHandler;

    public static void init(Context context, boolean debug) {
        init(context, null, debug);
    }

    public static void init(Context context, Activity activity, boolean debug) {
        lock.lock();
        if (DeviceContext.context != null) {
            Log.d("DeviceContext", "DeviceContext已经初始化");
            return;
        }
        DeviceContext.context = context;
        DeviceContext.activity = activity;
        LogUtils.init(debug);
        lock.unlock();
        Log.d("DeviceContext", "DeviceContext初始化完成");
    }

    public static Context getContext() {
        checkContext();
        return context;
    }


    private static void checkContext() {
        if (context == null) {
            throw new RuntimeException("DeviceContext未初始化");
        }
    }

    public static Handler mainHandler() {
        checkContext();
        if (mMainHandler == null) {
            mMainHandler = new Handler(context.getMainLooper());
        }
        return mMainHandler;
    }

    public static Activity getActivity() {
        if (activity != null) {
            return activity;
        }
        return getActivityByContext(context);
    }

    private static Activity getActivityByContext(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static void setContext(Context context) {
        DeviceContext.context = context;
    }

    public static void setActivity(Activity activity) {
        DeviceContext.activity = activity;
    }
}
