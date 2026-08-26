package cc.iteachyou.printservice;

import cc.iteachyou.printservice.tray.SystemTrayManager;
import cc.iteachyou.printservice.util.AppIcons;
import cc.iteachyou.printservice.websocket.PrintWebSocketServer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 打印控件主程序入口（基于 SWT）
 *
 * 功能：
 * 1. 程序启动后不显示主界面，直接隐藏到系统托盘
 * 2. 系统托盘右键菜单：任务列表、关于、退出（SWT 原生托盘）
 * 3. 启动 WebSocket 服务，仅监听 127.0.0.1:54321
 *
 * SWT 说明：
 * - 所有 SWT UI 操作必须在创建 Display 的线程（UI 线程）上执行
 * - 程序通过 while(display.readAndDispatch()) 事件循环维持运行，
 *   即使没有可见窗口也能响应托盘菜单
 */
public class MainApp {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    /** WebSocket 仅绑定本机回环，避免局域网未授权访问 */
    public static final String WS_HOST = "127.0.0.1";
    /** WebSocket 服务监听端口 */
    public static final int WS_PORT = 54321;

    private Display display;
    private Shell shell;
    private PrintWebSocketServer webSocketServer;
    private SystemTrayManager trayManager;

    public static void main(String[] args) {
        MainApp app = new MainApp();
        app.start();
    }

    /**
     * 启动应用
     */
    public void start() {
        // 创建 Display 和隐藏的 Shell（不显示，仅作为托盘菜单的父窗口）
        display = new Display();
        shell = new Shell(display, SWT.NONE);
        // 设置应用图标（标题栏 / 任务栏 / Alt-Tab）
        AppIcons.applyTo(shell);

        log.info("打印控件启动中...");

        // 1. 初始化系统托盘（SWT 原生）
        trayManager = new SystemTrayManager(this, display, shell);
        trayManager.init();

        // 2. 启动 WebSocket 服务（在后台线程运行，不阻塞 UI 事件循环）
        startWebSocketServer();

        log.info("打印控件已启动，WebSocket 监听 {}:{}", WS_HOST, WS_PORT);

        // 3. 进入 SWT 事件循环（无可见窗口也能响应托盘菜单）
        while (!display.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        // 4. 清理资源
        cleanup();
        display.dispose();
        log.info("打印控件已退出");
    }

    /**
     * 启动 WebSocket 服务
     */
    private void startWebSocketServer() {
        try {
            webSocketServer = new PrintWebSocketServer(WS_HOST, WS_PORT);
            // 注入 Display，供 doPreview 指令在 UI 线程打开打印预览窗体
            webSocketServer.setDisplay(display);
            // WebSocket 服务在独立线程中运行，start() 是非阻塞的
            webSocketServer.start();
            log.info("WebSocket 服务已启动，{}:{}", WS_HOST, WS_PORT);
        } catch (Exception e) {
            log.error("WebSocket 服务启动失败", e);
        }
    }

    /**
     * 清理资源
     */
    private void cleanup() {
        // 关闭 WebSocket 服务
        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
                log.info("WebSocket 服务已关闭");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("关闭 WebSocket 服务时被中断", e);
            }
        }

        // 移除系统托盘图标
        if (trayManager != null) {
            trayManager.cleanup();
        }

        // 关闭隐藏窗口
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }

        // 释放应用图标缓存
        AppIcons.disposeAll();
    }

    /**
     * 退出应用（在主线程事件循环外安全退出）
     */
    public void exit() {
        if (display != null && !display.isDisposed()) {
            // 在 UI 线程上执行退出，确保资源顺序释放
            display.asyncExec(display::dispose);
        }
    }

    public Display getDisplay() {
        return display;
    }

    public Shell getShell() {
        return shell;
    }

    public PrintWebSocketServer getWebSocketServer() {
        return webSocketServer;
    }

    public SystemTrayManager getTrayManager() {
        return trayManager;
    }
}
