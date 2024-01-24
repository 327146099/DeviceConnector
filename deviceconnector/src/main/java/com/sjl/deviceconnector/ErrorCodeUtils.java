package com.sjl.deviceconnector;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class ErrorCodeUtils {

    public static final Map<Integer, String> codeMap = new HashMap<>();

    static {
        Field[] declaredFields = ErrorCode.class.getDeclaredFields();

        for (Field declaredField : declaredFields) {
            if (Modifier.isStatic(declaredField.getModifiers()) && declaredField.getType() == int.class) {
                try {
                    codeMap.put(declaredField.getInt(null), declaredField.getName());
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }

    public static String getErrorCodeName(int code) {
        if (codeMap.containsKey(code)) {
            return codeMap.get(code);
        }
        return "未知异常";
    }

}
