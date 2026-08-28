package cc.iteachyou.printservice.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * 应用版本号工具。
 *
 * <p>版本号由 Maven 在打包时通过资源过滤注入到 {@code version.properties}
 * （{@code app.version=${project.version}}），运行时读取，与 pom.xml 中的版本保持一致。</p>
 */
public final class VersionUtil {

    private static final Logger log = LoggerFactory.getLogger(VersionUtil.class);

    private static final String VERSION;

    static {
        String v = "未知";
        try (InputStream in = VersionUtil.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                v = p.getProperty("app.version", "未知");
            }
        } catch (Exception e) {
            log.warn("读取应用版本号失败: {}", e.getMessage());
        }
        VERSION = v;
    }

    private VersionUtil() {
    }

    public static String getVersion() {
        return VERSION;
    }
}
