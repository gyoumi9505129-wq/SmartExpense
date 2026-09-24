# Google Play Services & Drive API
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-dontwarn com.google.api.client.**
-dontwarn org.apache.http.**

# Apache POI
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.logging.log4j.**
-dontwarn net.sf.saxon.**
-dontwarn org.osgi.framework.**
-dontwarn java.awt.**
-dontwarn javax.swing.**
-keep class org.apache.poi.** { *; }
-keepclassmembers class com.smartexpense.ui.club.components.AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
