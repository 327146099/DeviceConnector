# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class android.serialport.**{*;}
-keep class * implements com.hoho.android.usbserial.driver.UsbSerialDriver {

        public static java.util.Map getSupportedDevices();
}
-keep class com.sjl.deviceconnector.DeviceContext{*;}
-keep class com.sjl.deviceconnector.device.bluetooth.BluetoothHelper{*;}
-keep class com.sjl.deviceconnector.device.usb.UsbHelper{*;}
-keep class com.sjl.deviceconnector.listener.UsbPlugListener{*;}
-keep class com.sjl.deviceconnector.entity.BluetoothScanResult{*;}
-keep class com.sjl.deviceconnector.listener.BluetoothScanListener{*;}
-keep class com.sjl.deviceconnector.device.serialport.SerialPortHelper{*;}
-keep class com.sjl.deviceconnector.manager.*{*;}
-keep class com.sjl.deviceconnector.provider.*{*;}
-keep class com.sjl.deviceconnector.util.PermissionUtils{*;}
-keep class com.sjl.deviceconnector.manager.ConnectManager{*;}
-keep class com.sjl.deviceconnector.ErrorCode{*;}
-keep class com.sjl.deviceconnector.ErrorCodeUtils{*;}

