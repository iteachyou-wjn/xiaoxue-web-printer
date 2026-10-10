package cc.iteachyou.printservice.print;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;

/**
 * 模板和字体目录。
 * <ul>
 *   <li>本地调试：classpath 上的 resources 目录</li>
 *   <li>打包安装：安装目录（与 exe 同级）下的同名目录</li>
 * </ul>
 * JasperReport 模板在 {@code reports/jr}，BarTender 模板在 {@code reports/bt}，字体在 {@code fonts}。
 */
public final class TemplateDirs {

    private static final Logger log = LoggerFactory.getLogger(TemplateDirs.class);

    private TemplateDirs() {
    }

    /** JasperReport 模板目录 reports/jr */
    public static File jasperDir() {
        return resolve("reports/jr");
    }

    /** BarTender 模板目录 reports/bt */
    public static File bartenderDir() {
        return resolve("reports/bt");
    }

    /** 字体目录 fonts */
    public static File fontsDir() {
        return resolve("fonts");
    }

    /**
     * 已安装时只使用安装目录下的 folder；本地调试使用 classpath 上的同名资源目录。
     * folder 可用斜杠表示子目录，例如 reports/jr。
     */
    public static File resolve(String folder) {
        String normalized = folder.replace('\\', '/');
        File install = installationDir();
        if (install != null) {
            File dir = new File(install, normalized);
            if (dir.isDirectory()) {
                log.info("使用安装目录 {}: {}", normalized, dir.getAbsolutePath());
                return dir;
            }
            log.warn("安装目录下未找到 {}: {}", normalized, dir.getAbsolutePath());
            return null;
        }
        URL url = TemplateDirs.class.getClassLoader().getResource(normalized + "/");
        if (url == null) {
            url = TemplateDirs.class.getClassLoader().getResource(normalized);
        }
        if (url == null || !"file".equalsIgnoreCase(url.getProtocol())) {
            log.warn("未找到 classpath 资源目录 {}", normalized);
            return null;
        }
        try {
            File dir = new File(url.toURI());
            if (dir.isDirectory()) {
                log.info("使用 classpath {}: {}", normalized, dir.getAbsolutePath());
                return dir;
            }
        } catch (Exception e) {
            log.warn("定位 classpath {} 失败: {}", normalized, e.getMessage());
        }
        return null;
    }

    /**
     * jpackage 安装布局是「安装目录/app/主 jar」。
     * 只有主 jar 位于名为 app 的目录中时，才视为已安装。
     */
    private static File installationDir() {
        try {
            File loc = new File(TemplateDirs.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!loc.isFile()) {
                return null;
            }
            File parent = loc.getParentFile();
            if (parent != null && "app".equalsIgnoreCase(parent.getName())) {
                return parent.getParentFile();
            }
        } catch (Exception e) {
            log.warn("定位安装目录失败: {}", e.getMessage());
        }
        return null;
    }
}
