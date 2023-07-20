package com.sjl.deviceconnector.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.sjl.deviceconnector.DeviceContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PermissionUtils {

    private static Map<Integer, SynchroniseUtil> map = new ConcurrentHashMap<>();

    private static final AtomicInteger atomicInteger = new AtomicInteger(0);

    public static boolean checkPermission(String permission) {
        return checkPermissions(new String[]{permission});
    }

    public static boolean checkPermissions(String[] permissions) {
        //在Android6.0之后才需要获取动态权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> pers = new ArrayList<>();
            for (String permission : permissions) {
                //监测permission权限是否批准
                if (ContextCompat.checkSelfPermission(DeviceContext.getContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                    pers.add(permission);
                }
            }
            if (pers.isEmpty()) {
                return true;
            }

            Integer requestCode = atomicInteger.addAndGet(1);
            //请求系统弹窗，申请权限
            ActivityCompat.requestPermissions(getActivityByContext(DeviceContext.getContext()), pers.toArray(new String[]{}), requestCode);
            SynchroniseUtil<Object> synchroniseUtil = new SynchroniseUtil<>();
            map.put(requestCode, synchroniseUtil);
            int[] grantResults = null;
            try {
                grantResults = (int[]) synchroniseUtil.get();
            } catch (InterruptedException ignored) {
            }
            return checkGrant(grantResults);
        }
        return true;
    }

    //判断是否所有权限都被批准
    public static boolean checkGrant(int[] grantResults) {
        if (grantResults != null) {
            for (int grant : grantResults) {
                if (grant == PackageManager.PERMISSION_DENIED) {
                    return false;
                }

            }
        }
        return true;
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

    public static void notify(int requestCode, Object result) {
        SynchroniseUtil synchroniseUtil = map.remove(requestCode);
        if (synchroniseUtil != null) {
            synchroniseUtil.setResult(result);
        }
    }

}
