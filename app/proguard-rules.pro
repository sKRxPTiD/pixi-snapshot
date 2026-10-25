# JSch selects its algorithm classes by name from configuration strings.
-keep class com.jcraft.jsch.** { *; }
# Optional integrations not used by this app.
-dontwarn com.sun.jna.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
# Keep the small app implementation inspectable by release instrumentation.
-keep class org.nullprotocol.pixisnapshot.** { *; }
# Desktop-only Kerberos and Unix-domain socket integrations are not used.
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
