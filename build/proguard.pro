# ProGuard 娣锋穯閰嶇疆 鈥斺€?姊︽兂瀹禬EB鎵撳嵃鎺т欢
# 绛栫暐锛氫粎娣锋穯绉佹湁鎴愬憳鍚嶏紙淇濈暀绫诲悕涓?public/protected 鏂规硶锛夛紝闅愯棌鍏挜绛夋晱鎰熺鏈夊瓧娈碉紝灏介噺涓嶅奖鍝?SWT/WebSocket 杩愯
# 娉ㄦ剰锛氬彧瀵圭槮 jar锛堜富椤圭洰浠ｇ爜锛夋贩娣嗭紝渚濊禆搴撲笉娣锋穯锛涜矾寰勭浉瀵规湰鏂囦欢鎵€鍦ㄧ洰褰曪紙build/锛?

-injars  input/dreamer-print-service-1.0.7.jar
-outjars input/dreamer-print-service-1.0.7-obf.jar

# ---- JDK library (jmods) ----
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.base.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.desktop.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.logging.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.xml.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.sql.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.naming.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.management.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.datatransfer.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.prefs.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.rmi.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.security.jgss.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.security.sasl.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.net.http.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.compiler.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.scripting.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.instrument.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/jdk.crypto.ec.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/jdk.unsupported.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.transaction.xa.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.xml.crypto.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.sql.rowset.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.smartcardio.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/jdk.zipfs.jmod"
-libraryjars "D:/Program Files/Java/jdk-17/jmods/java.se.jmod"

# ---- dependency library (libs) ----
# build.ps1 会用 build/libs 下的全部 jar 替换 BEGIN_LIBS / END_LIBS 之间的内容。
# BarTender SDK 里的 JbtCob.class 字节码不完整，必须排除，否则 ProGuard 读 jar 会失败。
# BEGIN_LIBS
-libraryjars libs/bartender-printer-sdk-2.1.1.jar(!cn/databytes/bartender/JbtCob.class)
-libraryjars libs/batik-anim-1.17.jar
-libraryjars libs/batik-awt-util-1.17.jar
-libraryjars libs/batik-bridge-1.17.jar
-libraryjars libs/batik-constants-1.17.jar
-libraryjars libs/batik-css-1.17.jar
-libraryjars libs/batik-dom-1.17.jar
-libraryjars libs/batik-ext-1.17.jar
-libraryjars libs/batik-gvt-1.17.jar
-libraryjars libs/batik-i18n-1.17.jar
-libraryjars libs/batik-parser-1.17.jar
-libraryjars libs/batik-script-1.17.jar
-libraryjars libs/batik-shared-resources-1.17.jar
-libraryjars libs/batik-svg-dom-1.17.jar
-libraryjars libs/batik-util-1.17.jar
-libraryjars libs/batik-xml-1.17.jar
-libraryjars libs/commons-beanutils-1.9.4.jar
-libraryjars libs/commons-collections-3.2.2.jar
-libraryjars libs/commons-collections4-4.4.jar
-libraryjars libs/commons-io-2.11.0.jar
-libraryjars libs/commons-logging-1.2.jar
-libraryjars libs/core-3.5.3.jar
-libraryjars libs/fastjson2-2.0.64.jar
-libraryjars libs/fontbox-2.0.31.jar
-libraryjars libs/graphics2d-0.32.jar
-libraryjars libs/jackson-annotations-2.17.1.jar
-libraryjars libs/jackson-core-2.17.1.jar
-libraryjars libs/jackson-databind-2.17.2.jar
-libraryjars libs/jackson-dataformat-xml-2.17.1.jar
-libraryjars libs/jackson-datatype-jsr310-2.17.2.jar
-libraryjars libs/jai-imageio-core-1.4.0.jar
-libraryjars libs/jasperreports-7.0.1.jar
-libraryjars libs/jasperreports-pdf-7.0.1.jar
-libraryjars libs/javase-3.5.3.jar
-libraryjars libs/Java-WebSocket-1.5.6.jar
-libraryjars libs/jcommander-1.82.jar
-libraryjars libs/jsoup-1.17.2.jar
-libraryjars libs/openhtmltopdf-core-1.0.10.jar
-libraryjars libs/openhtmltopdf-pdfbox-1.0.10.jar
-libraryjars libs/openpdf-1.3.32.jar
-libraryjars libs/org.eclipse.swt.win32.win32.x86_64-3.131.0.jar
-libraryjars libs/org.eclipse.swt-3.131.0.jar
-libraryjars libs/pdfbox-2.0.31.jar
-libraryjars libs/slf4j-api-2.0.13.jar
-libraryjars libs/slf4j-simple-2.0.13.jar
-libraryjars libs/stax2-api-4.2.2.jar
-libraryjars libs/woodstox-core-6.6.2.jar
-libraryjars libs/xml-apis-ext-1.3.04.jar
-libraryjars libs/xmlgraphics-commons-2.9.jar
-libraryjars libs/xmpbox-2.0.31.jar
-libraryjars libs/xmpcore-6.1.11.jar
# END_LIBS

-dontshrink
-dontoptimize
-dontwarn
-ignorewarnings

# 淇濈暀涓婚」鐩被鍚嶏紙浠呮贩娣嗙鏈夋垚鍛橈級
-keep class cc.iteachyou.printservice.**

# 淇濈暀 public / protected 鎴愬憳锛堣鐩?SWT Listener銆乄ebSocket 瑕嗗啓銆丮ainApp.main 绛夛級
-keepclassmembers class cc.iteachyou.printservice.** { public protected *; }

# 淇濈暀鏋氫妇鎴愬憳
-keepclassmembers enum * { *; }

