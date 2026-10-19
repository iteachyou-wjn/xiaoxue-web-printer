package cc.iteachyou.printservice.print.jasper;

import cc.iteachyou.printservice.print.TemplateDirs;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JasperReport 报表模板填充与 PDF 导出（JasperReport 7.x）。
 *
 * 模板和字体分两种来源：
 * <ul>
 *   <li>本地调试：classpath 上的 resources/reports/jr、resources/fonts</li>
 *   <li>打包安装：安装目录（与 exe 同级）下的 reports/jr、fonts/</li>
 * </ul>
 * 前端通过 WebSocket 传模板文件名 + 参数 Map + 列表数据 List，服务端填充后输出 PDF byte[]，
 * 复用现有预览窗 / 打印管线。
 */
public class JasperReportManager {

    private static final Logger log = LoggerFactory.getLogger(JasperReportManager.class);

    private static final List<String> SUPPORTED_EXT = Arrays.asList(".jasper", ".jrxml");

    /** Jasper 通过 ThreadLocal 读取字体扩展；预览/打印可能在不同线程，需每次填充前绑定到当前线程 */
    private static volatile net.sf.jasperreports.extensions.ExtensionsRegistry fontExtensionsRegistry;

    static {
        net.sf.jasperreports.engine.DefaultJasperReportsContext ctx =
                net.sf.jasperreports.engine.DefaultJasperReportsContext.getInstance();
        ctx.setProperty("net.sf.jasperreports.default.font.name", "微软雅黑");
        ctx.setProperty("net.sf.jasperreports.default.pdf.font.name", "微软雅黑");
        ctx.setProperty("net.sf.jasperreports.default.pdf.encoding", "Identity-H");
        ctx.setProperty("net.sf.jasperreports.default.pdf.embedded", "true");
        registerFonts();
        ensureFontExtensionsRegistry();
        applyFontExtensionsToCurrentThread();
    }

    /**
     * 扫描字体目录下的 .ttf/.ttc 并注册到 JVM，供 JasperReport 渲染中文使用。
     */
    private static void registerFonts() {
        try {
            File fontsDir = TemplateDirs.fontsDir();
            if (fontsDir == null) {
                log.info("fonts 目录不存在，跳过字体注册");
                return;
            }
            java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
            File[] fonts = fontsDir.listFiles((d, name) -> {
                String n = name.toLowerCase();
                return n.endsWith(".ttf") || n.endsWith(".ttc");
            });
            int count = 0;
            if (fonts != null) {
                for (File font : fonts) {
                    try {
                        java.awt.Font f = java.awt.Font.createFont(
                                java.awt.Font.TRUETYPE_FONT, font);
                        ge.registerFont(f);
                        count++;
                        log.info("注册字体: {} -> {}", font.getName(), f.getFontName());
                    } catch (Exception e) {
                        log.warn("注册字体失败: {}, {}", font.getName(), e.getMessage());
                    }
                }
            }
            log.info("字体注册完成，共 {} 个", count);
        } catch (Exception e) {
            log.warn("初始化字体目录失败: {}", e.getMessage());
        }
    }

    /**
     * 定位 JasperReport 模板目录 reports/jr。
     * 本地调试读 classpath resources/reports/jr，安装后读安装目录 reports/jr。
     */
    public static File getReportsDir() {
        return TemplateDirs.jasperDir();
    }

    /**
     * 列出 reports 目录下所有可用模板（.jasper / .jrxml）
     */
    public static List<Map<String, String>> listTemplates() {
        List<Map<String, String>> result = new ArrayList<>();
        File dir = getReportsDir();
        if (dir == null) {
            log.info("reports/jr 目录不存在");
            return result;
        }
        File[] files = dir.listFiles((d, name) -> {
            String n = name.toLowerCase();
            return SUPPORTED_EXT.stream().anyMatch(n::endsWith);
        });
        if (files != null) {
            for (File f : files) {
                Map<String, String> m = new HashMap<>();
                m.put("name", f.getName());
                m.put("file", f.getName());
                m.put("size", String.valueOf(f.length()));
                result.add(m);
            }
        }
        log.info("找到 JasperReport 模板 {} 个", result.size());
        return result;
    }

    /**
     * 加载模板：优先 .jasper，其次 .jrxml（运行时编译）
     */
    private static JasperReport loadTemplate(String templateName) throws Exception {
        File dir = getReportsDir();
        if (dir == null) {
            throw new IllegalStateException("reports/jr 目录不存在。本地调试请放在 resources/reports/jr，安装后请放在安装目录 reports/jr");
        }
        File jasper = new File(dir, templateName);
        if (!jasper.exists()) {
            String base = templateName;
            int dot = base.lastIndexOf('.');
            if (dot > 0) {
                base = base.substring(0, dot);
            }
            File jasperFallback = new File(dir, base + ".jasper");
            if (jasperFallback.exists()) {
                jasper = jasperFallback;
            }
        }
        if (!jasper.exists()) {
            for (String ext : SUPPORTED_EXT) {
                File f = new File(dir, templateName.endsWith(ext) ? templateName : templateName + ext);
                if (f.exists()) {
                    jasper = f;
                    break;
                }
            }
        }
        if (!jasper.exists()) {
            throw new IllegalArgumentException("模板文件不存在: " + templateName);
        }
        if (jasper.getName().toLowerCase().endsWith(".jrxml")) {
            try {
                return JasperCompileManager.compileReport(jasper.getAbsolutePath());
            } catch (Exception e) {
                log.error("编译 jrxml 失败: {}, 文件: {}", e.getMessage(), jasper.getAbsolutePath(), e);
                throw e;
            }
        }
        return (JasperReport) JRLoader.loadObject(jasper);
    }

    /**
     * 填充模板并导出 PDF。
     *
     * @param templateName 模板文件名（.jasper 或 .jrxml）
     * @param params       参数 Map（报表 ${} 字段）
     * @param dataList     列表数据（空则传 null，报表无 Detail band 时不需要）
     * @return PDF 字节数组
     */
    public static byte[] fillToPdf(String templateName, Map<String, Object> params,
                                   List<Map<String, Object>> dataList) throws Exception {
        applyFontExtensionsToCurrentThread();
        JasperReport report = loadTemplate(templateName);

        Map<String, Object> map = new HashMap<>();
        if (params != null) {
            map.putAll(params);
        }

        // 如果传了 qrContent，生成二维码图片
        if (map.get("qrContent") != null && map.get("qrImage") == null) {
            try {
                String content = String.valueOf(map.get("qrContent"));
                com.google.zxing.BarcodeFormat format = com.google.zxing.BarcodeFormat.QR_CODE;
                java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.HashMap<>();
                hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
                hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
                com.google.zxing.common.BitMatrix matrix =
                        new com.google.zxing.MultiFormatWriter().encode(content, format, 200, 200, hints);
                java.awt.image.BufferedImage qrImg = new java.awt.image.BufferedImage(
                        200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int x = 0; x < 200; x++) {
                    for (int y = 0; y < 200; y++) {
                        qrImg.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                    }
                }
                map.put("qrImage", qrImg);
                log.info("二维码已生成: {} -> {}x{}", content, 200, 200);
            } catch (Exception e) {
                log.warn("二维码生成失败: {}", e.getMessage());
            }
        }

        JRBeanCollectionDataSource dataSource;
        if (dataList != null && !dataList.isEmpty()) {
            dataSource = new JRBeanCollectionDataSource(dataList);
        } else {
            dataSource = new JRBeanCollectionDataSource(new ArrayList<>());
        }

        JasperPrint print = JasperFillManager.fillReport(report, map, dataSource);
        byte[] pdf = JasperExportManager.exportReportToPdf(print);
        log.info("JasperReport 填充完成: {} -> PDF {} bytes", templateName, pdf == null ? 0 : pdf.length);
        if (pdf == null) {
            throw new IllegalStateException("JasperReport PDF 导出失败，返回空字节");
        }
        return pdf;
    }

    /**
     * 解析成磁盘上的字体文件。本地调试读 resources/fonts，安装后读安装目录 fonts/。
     */
    private static String resolveFontFile(String resourceName) throws Exception {
        String fileName = resourceName.substring(resourceName.lastIndexOf('/') + 1);
        File fontsDir = TemplateDirs.fontsDir();
        if (fontsDir != null) {
            File font = new File(fontsDir, fileName);
            if (font.isFile()) {
                return font.getAbsolutePath();
            }
        }
        throw new IllegalStateException("找不到字体文件: " + fileName);
    }

    private static void ensureFontExtensionsRegistry() {
        if (fontExtensionsRegistry != null) {
            return;
        }
        synchronized (JasperReportManager.class) {
            if (fontExtensionsRegistry != null) {
                return;
            }
            try {
                net.sf.jasperreports.engine.DefaultJasperReportsContext ctx =
                        net.sf.jasperreports.engine.DefaultJasperReportsContext.getInstance();
                String ttfPath = resolveFontFile("fonts/msyh.ttf");
                String simheiPath = resolveFontFile("fonts/simhei.ttf");
                log.info("注册字体文件: msyh={}, simhei={}", ttfPath, simheiPath);

                net.sf.jasperreports.engine.fonts.SimpleFontFace normal =
                        new net.sf.jasperreports.engine.fonts.SimpleFontFace(ctx);
                normal.setTtf(ttfPath, false);
                normal.loadFont();

                net.sf.jasperreports.engine.fonts.SimpleFontFamily family =
                        new net.sf.jasperreports.engine.fonts.SimpleFontFamily(ctx);
                family.setName("微软雅黑");
                family.setNormalFace(normal);
                family.setBoldFace(normal);
                family.setItalicFace(normal);
                family.setBoldItalicFace(normal);
                family.setPdfEncoding("Identity-H");
                family.setPdfEmbedded(true);

                java.util.List<net.sf.jasperreports.engine.fonts.FontFamily> families =
                        java.util.Collections.singletonList(family);
                net.sf.jasperreports.extensions.ExtensionsRegistry systemReg =
                        net.sf.jasperreports.extensions.ExtensionsEnvironment.getExtensionsRegistry();
                fontExtensionsRegistry = new net.sf.jasperreports.extensions.ExtensionsRegistry() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> java.util.List<T> getExtensions(Class<T> extensionType) {
                        if (net.sf.jasperreports.engine.fonts.FontFamily.class.equals(extensionType)) {
                            return (java.util.List<T>) families;
                        }
                        return systemReg.getExtensions(extensionType);
                    }
                };
                log.info("字体扩展注册表已构建");
            } catch (Exception e) {
                log.error("构建字体扩展注册表失败", e);
            }
        }
    }

    private static void applyFontExtensionsToCurrentThread() {
        ensureFontExtensionsRegistry();
        if (fontExtensionsRegistry == null) {
            return;
        }
        try {
            java.lang.reflect.Field trField = net.sf.jasperreports.extensions.ExtensionsEnvironment.class
                    .getDeclaredField("threadRegistry");
            trField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.lang.ThreadLocal<net.sf.jasperreports.extensions.ExtensionsRegistry> tr =
                    (java.lang.ThreadLocal<net.sf.jasperreports.extensions.ExtensionsRegistry>) trField.get(null);
            tr.set(fontExtensionsRegistry);
        } catch (Exception e) {
            log.error("绑定字体扩展到当前线程失败", e);
        }
    }

    /**
     * 检测 JasperReport 是否可用（类加载成功即视为可用）。
     */
    public static boolean isAvailable() {
        try {
            Class.forName("net.sf.jasperreports.engine.JasperFillManager");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
