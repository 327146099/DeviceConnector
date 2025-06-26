package com.sjl.deviceconnector.util;

import android.content.Context;
import android.util.Log;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.XXPermissions;
import com.sjl.deviceconnector.DeviceContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PermissionUtils {

    public static final String TAG = "PermissionUtils";

    public static PermissionResult checkPermissions(String[] permissions) {
        Context context = DeviceContext.getContext();
        if (context == null) {
            throw new RuntimeException("未初始化DeviceContext");
        }

        if (XXPermissions.isGranted(context, permissions)) {
            PermissionResult permissionResult = new PermissionResult();
            permissionResult.setSuccess(true);
            return permissionResult;
        }

        CompletableFuture<PermissionResult> future = new CompletableFuture<>();

        XXPermissions.with(DeviceContext.getContext()).permission(permissions).request(new OnPermissionCallback() {
            @Override
            public void onGranted(List<String> permissions, boolean allGranted) {
                PermissionResult permissionResult = new PermissionResult();
                permissionResult.setSuccess(allGranted);
                permissionResult.setPermissions(permissions);
                permissionResult.setAllGranted(allGranted);
                future.complete(permissionResult);
            }

            @Override
            public void onDenied(List<String> permissions, boolean doNotAskAgain) {
                PermissionResult permissionResult = new PermissionResult();
                permissionResult.setSuccess(false);
                permissionResult.setPermissions(permissions);
                permissionResult.setDoNotAskAgain(doNotAskAgain);
            }
        });

        boolean res = false;
        try {
            PermissionResult permissionResult = future.get(30, TimeUnit.SECONDS);
            return permissionResult;
        } catch (Exception e) {
            Log.e(TAG, "申请权限异常: ", e);
            PermissionResult permissionResult = new PermissionResult();
            permissionResult.setSuccess(false);
            return permissionResult;
        }
    }


    public static class PermissionResult {
        private boolean success;
        private List<String> permissions;
        private boolean doNotAskAgain;
        private boolean allGranted;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public List<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<String> permissions) {
            this.permissions = permissions;
        }

        public boolean isDoNotAskAgain() {
            return doNotAskAgain;
        }

        public void setDoNotAskAgain(boolean doNotAskAgain) {
            this.doNotAskAgain = doNotAskAgain;
        }

        public boolean isAllGranted() {
            return allGranted;
        }

        public void setAllGranted(boolean allGranted) {
            this.allGranted = allGranted;
        }
    }

}
