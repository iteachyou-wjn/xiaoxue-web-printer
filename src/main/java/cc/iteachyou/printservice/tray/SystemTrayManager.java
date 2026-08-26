package cc.iteachyou.printservice.tray;

import cc.iteachyou.printservice.MainApp;
import cc.iteachyou.printservice.ui.AboutDialog;
import cc.iteachyou.printservice.ui.LicenseDialog;
import cc.iteachyou.printservice.ui.TaskListDialog;
import cc.iteachyou.printservice.util.MachineCode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DirectColorModel;
import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 系统托盘管理器（SWT 原生实现）
 *
 * 使用 SWT 的 Tray / TrayItem / Menu，托盘图标和菜单都是操作系统原生组件：
 * - 外观原生，中文不会乱码
 * - 不依赖任何第三方托盘库
 *
 * 注意：SWT 组件必须在 UI 线程（创建 Display 的线程）上创建和访问。
 * 菜单事件回调本身就在 UI 线程的事件循环中，可以直接打开 SWT 对话框。
 */
public class SystemTrayManager {

    private static final Logger log = LoggerFactory.getLogger(SystemTrayManager.class);

    private final MainApp mainApp;
    private final Display display;
    private final Shell shell;

    private TrayItem trayItem;
    private Menu popupMenu;
    /** 菜单图标（统一释放资源） */
    private final java.util.List<Image> menuIcons = new java.util.ArrayList<>();

    /** 开机自启动注册表 Run 键 */
    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    /** 注册表中的自启动值名称 */
    private static final String RUN_VALUE = "DreamerPrintService";

    public SystemTrayManager(MainApp mainApp, Display display, Shell shell) {
        this.mainApp = mainApp;
        this.display = display;
        this.shell = shell;
    }

    /**
     * 初始化系统托盘
     */
    public void init() {
        // 获取系统托盘（Windows/macOS 支持，Linux 需桌面环境支持）
        Tray tray = display.getSystemTray();
        if (tray == null) {
            log.error("当前系统不支持系统托盘");
            return;
        }

        // 创建托盘项
        trayItem = new TrayItem(tray, SWT.NONE);
        trayItem.setToolTipText("打印控件");
        trayItem.setImage(loadTrayIcon());

        // 创建右键弹出菜单（POP_UP 类型，挂载到隐藏的 Shell 上）
        popupMenu = new Menu(shell, SWT.POP_UP);

        // 菜单项：任务列表
        MenuItem taskListItem = new MenuItem(popupMenu, SWT.PUSH);
        taskListItem.setText("任务列表");
        taskListItem.setImage(loadImage("image_13.png"));
        taskListItem.addListener(SWT.Selection, e -> showTaskList());

        // 菜单项：开机自启（checkbox 图标表示当前状态）
        MenuItem autoStartItem = new MenuItem(popupMenu, SWT.PUSH);
        autoStartItem.setText("开机自启");
        Image cbOff = drawCheckbox(false);
        Image cbOn = drawCheckbox(true);
        autoStartItem.setImage(isAutoStartEnabled() ? cbOn : cbOff);
        autoStartItem.addListener(SWT.Selection, e -> {
            setAutoStart(!isAutoStartEnabled());
            autoStartItem.setImage(isAutoStartEnabled() ? cbOn : cbOff);
        });

        // 菜单项：授权许可（关于之前）
        MenuItem licenseItem = new MenuItem(popupMenu, SWT.PUSH);
        licenseItem.setText("授权许可");
        licenseItem.setImage(loadImage("image_07.png"));
        licenseItem.addListener(SWT.Selection, e -> showLicense());

        // 菜单项：关于
        MenuItem aboutItem = new MenuItem(popupMenu, SWT.PUSH);
        aboutItem.setText("关于");
        aboutItem.setImage(loadImage("image_15.png"));
        aboutItem.addListener(SWT.Selection, e -> showAbout());

        // 分隔线
        new MenuItem(popupMenu, SWT.SEPARATOR);

        // 菜单项：退出
        MenuItem exitItem = new MenuItem(popupMenu, SWT.PUSH);
        exitItem.setText("退出");
        exitItem.setImage(loadImage("image_14.png"));
        exitItem.addListener(SWT.Selection, e -> exitApplication());

        // 右键（MenuDetect）弹出菜单；打开前同步刷新自启动 checkbox 状态（读取注册表）
        trayItem.addListener(SWT.MenuDetect, e -> {
            if (!popupMenu.isDisposed()) {
                autoStartItem.setImage(isAutoStartEnabled() ? cbOn : cbOff);
                popupMenu.setVisible(true);
            }
        });

        // 左键双击打开任务列表
        trayItem.addListener(SWT.DefaultSelection, e -> showTaskList());

        // 预热机器码：wmic 硬件查询较慢（约 3 秒），后台预取并缓存，
        // 避免首次点击"授权许可"时在 UI 线程卡顿
        Thread warmup = new Thread(() -> {
            try {
                MachineCode.getMachineCode();
            } catch (Exception ignored) {
                // 预热失败不影响功能，打开对话框时再计算
            }
        }, "machine-code-warmup");
        warmup.setDaemon(true);
        warmup.start();

        log.info("系统托盘已初始化（SWT 原生）");
    }

    /**
     * 显示任务列表对话框（SWT 菜单回调在 UI 线程，可直接打开）
     */
    private void showTaskList() {
        try {
            TaskListDialog dialog = new TaskListDialog(mainApp, display);
            dialog.show();
        } catch (Exception ex) {
            log.error("显示任务列表失败", ex);
        }
    }

    /**
     * 显示授权许可对话框
     */
    private void showLicense() {
        try {
            LicenseDialog dialog = new LicenseDialog(display);
            dialog.show();
        } catch (Exception ex) {
            log.error("显示授权许可对话框失败", ex);
        }
    }

    /**
     * 显示关于对话框
     */
    private void showAbout() {
        try {
            AboutDialog dialog = new AboutDialog(display);
            dialog.show();
        } catch (Exception ex) {
            log.error("显示关于对话框失败", ex);
        }
    }

    /**
     * 退出程序（确认对话框）
     */
    private void exitApplication() {
        org.eclipse.swt.widgets.MessageBox box =
                new org.eclipse.swt.widgets.MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
        box.setText("确认退出");
        box.setMessage("确定要退出打印控件吗？\n退出后打印服务将停止。");
        if (box.open() == SWT.YES) {
            mainApp.exit();
        }
    }

    /**
     * 清理系统托盘资源
     */
    public void cleanup() {
        if (trayItem != null && !trayItem.isDisposed()) {
            trayItem.dispose();
            trayItem = null;
            log.info("系统托盘图标已移除");
        }
        if (popupMenu != null && !popupMenu.isDisposed()) {
            popupMenu.dispose();
            popupMenu = null;
        }
        for (Image icon : menuIcons) {
            if (icon != null && !icon.isDisposed()) {
                icon.dispose();
            }
        }
        menuIcons.clear();
    }

    /**
     * 显示错误消息（记录日志）
     */
    public void showErrorMessage(String message) {
        log.error(message);
    }    /**
     * 显示信息消息
     */
    public void showInfoMessage(String title, String message) {
        log.info("{}: {}", title, message);
    }

    // ================= 开机自启动（注册表 Run 键） =================

    /**
     * 程序化绘制 checkbox 图标（空方框 / 勾选方框），用于菜单项显示当前自启动状态
     */
    private Image drawCheckbox(boolean checked) {
        int size = 20;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 方框
        g.setColor(new java.awt.Color(70, 70, 70));
        g.drawRect(3, 3, size - 7, size - 7);
        if (checked) {
            // 对勾
            g.setColor(new java.awt.Color(0, 120, 215));
            g.setStroke(new java.awt.BasicStroke(2.2f));
            g.drawPolyline(new int[]{6, 9, 15}, new int[]{11, 14, 5}, 3);
        }
        g.dispose();
        Image swtImg = new Image(display, convertAWTToSWT(img));
        menuIcons.add(swtImg);
        return swtImg;
    }

    /**
     * 是否已启用开机自启动（注册表 Run 键中是否存在本应用的值）
     */
    private boolean isAutoStartEnabled() {
        try {
            Process p = new ProcessBuilder("reg", "query", RUN_KEY, "/v", RUN_VALUE)
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return out.contains(RUN_VALUE);
        } catch (Exception e) {
            log.warn("查询开机自启动状态失败", e);
            return false;
        }
    }

    /**
     * 设置或取消开机自启动
     * 注意：/d 值含双引号（路径带空格），直接传给 reg.exe 会报"无效命令行参数"，
     * 必须经 cmd /c 包装，cmd 才能正确解析嵌套引号。
     */
    private void setAutoStart(boolean enable) {
        try {
            String regCmd;
            if (enable) {
                String startup = buildStartupCommand();
                if (startup == null || startup.isEmpty()) {
                    log.warn("无法构造开机自启动命令，已取消设置");
                    return;
                }
                String data = startup.replace("\"", "\\\"");
                regCmd = "reg add \"" + RUN_KEY + "\" /v " + RUN_VALUE
                        + " /t REG_SZ /d \"" + data + "\" /f";
            } else {
                regCmd = "reg delete \"" + RUN_KEY + "\" /v " + RUN_VALUE + " /f";
            }
            Process p = new ProcessBuilder("cmd.exe", "/c", regCmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            log.info("开机自启动已{}：{}", enable ? "开启" : "关闭", out.trim());
        } catch (Exception e) {
            log.warn("设置开机自启动失败", e);
        }
    }

    /**
     * 构造开机自启动命令：
     * 打包为 fat jar 时 → javaw.exe -jar xxx.jar
     * 开发/目录模式 → javaw.exe -cp classesDir cc.iteachyou.printservice.MainApp
     */
    private String buildStartupCommand() {
        try {
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw.exe";
            File loc = new File(MainApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String cmd;
            if (loc.isDirectory()) {
                cmd = "\"" + javaBin + "\" -cp \"" + loc.getAbsolutePath() + "\" cc.iteachyou.printservice.MainApp";
            } else {
                cmd = "\"" + javaBin + "\" -jar \"" + loc.getAbsolutePath() + "\"";
            }
            return cmd;
        } catch (Exception e) {
            log.warn("构造开机自启动命令失败", e);
            return null;
        }
    }

    /**
     * 从 classpath 的 images 目录加载菜单图标（resources/images），统一缩放到 24x24
     */
    private Image loadImage(String name) {
        try (java.io.InputStream is = SystemTrayManager.class.getResourceAsStream("/images/" + name)) {
            if (is == null) {
                log.warn("图标资源不存在: /images/{}", name);
                return null;
            }
            ImageData data = new ImageData(is).scaledTo(20, 20);
            Image img = new Image(display, data);
            menuIcons.add(img);
            return img;
        } catch (Exception e) {
            log.warn("加载图标失败: {}", name, e);
            return null;
        }
    }

    /**
     * 加载应用 Logo（resources/images/logo.png）作为托盘图标。
     * 使用 AWT 双三次高质量插值缩放到 32x32（SWT 的 ImageData.scaledTo 为低质量插值，
     * 大图直接缩到 32 会明显模糊）。Logo 加载失败时回退为 AWT 绘制的默认打印机图标。
     */
    private Image loadTrayIcon() {
        try (java.io.InputStream is = SystemTrayManager.class.getResourceAsStream("/images/logo.png")) {
            if (is == null) {
                log.warn("Logo 资源不存在: /images/logo.png，使用默认托盘图标");
                return createTrayIcon();
            }
            ImageData data = new ImageData(is);
            ImageData scaled = scaleImageHighQuality(data, 32, 32);
            if (scaled == null) {
                // 高质量缩放失败时回退 SWT 自带缩放
                scaled = data.scaledTo(32, 32);
            }
            return new Image(display, scaled);
        } catch (Exception e) {
            log.warn("加载 Logo 失败，使用默认托盘图标", e);
            return createTrayIcon();
        }
    }

    /**
     * 高质量缩放：ImageData → AWT BufferedImage → 双三次插值 → 转回 ImageData
     */
    private static ImageData scaleImageHighQuality(ImageData src, int w, int h) {
        if (src == null || src.width <= 0 || src.height <= 0 || w <= 0 || h <= 0) {
            return null;
        }
        try {
            // ImageData → BufferedImage（ARGB）
            BufferedImage srcImg = new BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < src.height; y++) {
                for (int x = 0; x < src.width; x++) {
                    int p = src.getPixel(x, y);
                    RGB rgb = src.palette.getRGB(p);
                    int a = src.getAlpha(x, y);
                    srcImg.setRGB(x, y, (a << 24) | (rgb.red << 16) | (rgb.green << 8) | rgb.blue);
                }
            }
            // 高质量缩放
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(srcImg, 0, 0, w, h, null);
            g.dispose();
            return convertAWTToSWT(dst);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 创建托盘图标：用 AWT 绘制打印机图标，再转换为 SWT Image
     */
    private Image createTrayIcon() {
        BufferedImage buffered = drawIcon();
        ImageData data = convertAWTToSWT(buffered);
        return new Image(display, data);
    }

    /**
     * 用 AWT 绘制打印机风格图标
     */
    private BufferedImage drawIcon() {
        int size = 64;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // 抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 背景圆角矩形
        g.setColor(new Color(52, 152, 219));
        g.fillRoundRect(4, 4, size - 8, size - 8, 12, 12);

        // 打印机主体（白色）
        g.setColor(Color.WHITE);
        g.fillRect(18, 16, 28, 8);   // 纸张入口
        g.fillRect(14, 24, 36, 20);  // 主体
        g.fillRect(18, 44, 28, 6);   // 出纸托盘

        // 纸张（从打印机中伸出）
        g.setColor(new Color(236, 240, 241));
        g.fillRect(22, 10, 20, 10);

        // 按钮（小圆点）
        g.setColor(new Color(46, 204, 113));
        g.fillOval(42, 30, 4, 4);

        g.dispose();
        return image;
    }

    /**
     * AWT BufferedImage 转 SWT ImageData（标准转换，支持 ARGB 透明）
     */
    private static ImageData convertAWTToSWT(BufferedImage bufferedImage) {
        if (bufferedImage.getColorModel() instanceof DirectColorModel) {
            DirectColorModel colorModel = (DirectColorModel) bufferedImage.getColorModel();
            PaletteData palette = new PaletteData(0xFF0000, 0xFF00, 0xFF);
            ImageData data = new ImageData(bufferedImage.getWidth(), bufferedImage.getHeight(),
                    colorModel.getPixelSize(), palette);
            for (int y = 0; y < data.height; y++) {
                for (int x = 0; x < data.width; x++) {
                    int rgb = bufferedImage.getRGB(x, y);
                    int pixel = palette.getPixel(new RGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
                    data.setPixel(x, y, pixel);
                    if (colorModel.hasAlpha()) {
                        data.setAlpha(x, y, (rgb >> 24) & 0xFF);
                    }
                }
            }
            return data;
        }
        // 其他颜色模型（理论上 TYPE_INT_ARGB 走上面的分支）
        return null;
    }
}
