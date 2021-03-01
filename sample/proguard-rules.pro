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

# proguard-android.txt为不优化的默认混淆文件,proguard-android-optimize.txt为优化的默认混淆文件
# 优化可能会造成一些潜在风险,不能保证在所有版本的Dalvik上都正常运行。
# 默认混淆文件中已经包含了部分规则,module的混淆文件为补充
# 删除代码中Log相关的代码等操作需要需要启动优化,将proguard-android.txt改为proguard-android-optimize.txt
# 默认文件已经不混淆任何包含native方法的类的类名以及native方法名,但和native有关的自定义的类的参数可能会被混淆,会在jni初始化
# 时报错,需要自己手动将和native方法有关的自定义类也添加到不混淆
# 使用json作为数据传输并使用gson等工具解析时,需要保证数据类不被混淆
# 如果使用webview和js还需要添加一些混淆

-dontskipnonpubliclibraryclassmembers   # 指定不去忽略包可见的库类的成员
-ignorewarnings                         # 屏蔽警告

#-keepattributes Signature                      # 避免混淆泛型, 用于JSON实体映射
#-keepattributes SourceFile,LineNumberTable     # 抛出异常时保留代码行号,会增大体积
#-repackageclasses ''                           # 把执行后的类重新放在某一个目录下，后跟一个目录名(未测试,估计要开启优化)

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.support.multidex.MultiDexApplication
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
-keep public class * extends android.support.annotation.**
-keep public class * extends android.graphics.drawable.Drawable{*;}

-keepclassmembers public class * extends android.view.View {
   public <init>(android.content.Context);
   public <init>(android.content.Context, android.util.AttributeSet);
   public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 指定了继承Serizalizable的类的如下成员不被移除混淆
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
# 保留Parcelable序列化的类不被混淆
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
# 对于带有回调函数的onXXEvent、**On*Listener的，不能被混淆,是针对一些特殊的类库
#-keepclassmembers class * {
#    void *(**On*Event);
#    void *(**On*Listener);
#}

#webView和js需要额外处理
#-keepclassmembers class com.example.Webview {
#   public *;
#}
#-keepclassmembers class * extends android.webkit.WebViewClient {
#    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
#    public boolean *(android.webkit.WebView, java.lang.String);
#}
#-keepclassmembers class * extends android.webkit.WebViewClient {
#    public void *(android.webkit.WebView, jav.lang.String);
#}
#在app中与HTML5的JavaScript的交互进行特殊处理,确保js要调用的原生方法不能够被混淆：
#-keepclassmembers class com.example.JSInterface {
#    <methods>;
#}

# 删除代码中Log相关的代码,需要启动优化才会生效,将proguard-android.txt改为proguard-android-optimize.txt
#-assumenosideeffects class android.util.Log {
#    public static boolean isLoggable(java.lang.String, int);
#    public static int v(...);
#    public static int i(...);
#    public static int w(...);
#    public static int d(...);
#    public static int e(...);
#}

# 保持测试相关的代码
#-dontnote junit.framework.**
#-dontnote junit.runner.**
#-dontwarn android.test.**
#-dontwarn android.support.test.**
#-dontwarn org.junit.**