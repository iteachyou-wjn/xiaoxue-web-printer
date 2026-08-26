package cc.iteachyou.printservice.util;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 应用图标工具：统一加载 resources/images/logo.png 并应用到各窗口。
 *
 * SWT Shell.setImage 同时控制窗口标题栏图标、任务栏图标和 Alt-Tab 图标。
 * Logo 在进程内缓存（同一 Display 共用一份 Image），程序退出时统一释放。
 */
public final class AppIcons {

    private static final Logger log = LoggerFactory.getLogger(AppIcons.class);

    private static Image cached;
    private static Display cachedDisplay;

    private AppIcons() {
    }

    /**
     * 获取 Logo 图标（进程内缓存）
     */
    public static Image logo(Display display) {
        if (display == null || display.isDisposed()) {
            return null;
        }
        if (cached != null && cachedDisplay == display && !cached.isDisposed()) {
            return cached;
        }
        try (java.io.InputStream is = AppIcons.class.getResourceAsStream("/images/logo.png")) {
            if (is == null) {
                log.warn("Logo 资源不存在: /images/logo.png");
                return null;
            }
            ImageData data = new ImageData(is);
            cached = new Image(display, data);
            cachedDisplay = display;
            return cached;
        } catch (Exception e) {
            log.warn("加载 Logo 失败", e);
            return null;
        }
    }

    /**
     * 将 Logo 应用到窗口（标题栏 / 任务栏 / Alt-Tab 图标）
     */
    public static void applyTo(Shell shell) {
        if (shell == null || shell.isDisposed()) {
            return;
        }
        Image logo = logo(shell.getDisplay());
        if (logo != null) {
            shell.setImage(logo);
        }
    }

    /**
     * 释放缓存图标（程序退出时调用）
     */
    public static void disposeAll() {
        if (cached != null && !cached.isDisposed()) {
            cached.dispose();
            cached = null;
            cachedDisplay = null;
        }
    }
}
