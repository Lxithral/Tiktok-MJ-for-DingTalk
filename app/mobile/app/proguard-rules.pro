# 无障碍服务由系统通过清单反射实例化, 保留入口类
-keep class com.lxithral.mjegg.egg.MjAccessibilityService { *; }
-keep class com.lxithral.mjegg.MainActivity { *; }

# 崩溃堆栈保留行号(混淆后仍可读)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
