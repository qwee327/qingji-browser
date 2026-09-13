# 轻级浏览器 ProGuard 规则
# WebView 相关类由系统提供，无需混淆配置
-dontwarn android.webkit.**
-keepattributes JavascriptInterface
