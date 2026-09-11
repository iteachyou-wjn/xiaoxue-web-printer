package cc.iteachyou.printservice.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import cc.iteachyou.printservice.MainApp;
import cc.iteachyou.printservice.util.AppIcons;

/**
 * 关于对话框（SWT 实现）
 */
public class AboutDialog {

    private final Display display;
    private Shell shell;

    public AboutDialog(Display display) {
        this.display = display;
    }

    /**
     * 打开对话框
     */
    public void show() {
        createShell();
        shell.open();
    }

    private void createShell() {
        shell = new Shell(display, SWT.SHELL_TRIM & ~SWT.RESIZE);
        shell.setText("关于打印控件");
        shell.setSize(560, 400);
        shell.setLayout(new GridLayout(1, false));
        AppIcons.applyTo(shell);

        // 应用名称
        Label nameLabel = new Label(shell, SWT.CENTER);
        nameLabel.setText("打印控件");
        FontData[] nameFd = display.getSystemFont().getFontData();
        Font nameFont = new Font(display, nameFd[0].getName(), 18, SWT.BOLD);
        nameLabel.setFont(nameFont);
        nameLabel.setForeground(new Color(display, 44, 62, 80));
        nameLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        // 版本号
        Label versionLabel = new Label(shell, SWT.CENTER);
        versionLabel.setText("版本 " + cc.iteachyou.printservice.util.VersionUtil.getVersion());
        versionLabel.setForeground(new Color(display, 127, 140, 141));
        versionLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        // 分隔线
        new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // 功能描述
        Label desc1 = new Label(shell, SWT.CENTER);
        desc1.setText("基于 SWT 的打印控件");
        desc1.setForeground(new Color(display, 52, 73, 94));
        desc1.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        Label desc2 = new Label(shell, SWT.CENTER);
        desc2.setText("系统托盘驻留 + WebSocket 远程打印");
        desc2.setForeground(new Color(display, 52, 73, 94));
        desc2.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        // 分隔线
        new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // 技术信息
        Label wsLabel = new Label(shell, SWT.CENTER);
        wsLabel.setText("WebSocket 端口: " + MainApp.WS_PORT);
        wsLabel.setForeground(new Color(display, 39, 174, 96));
        wsLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        Label javaLabel = new Label(shell, SWT.CENTER);
        javaLabel.setText("Java " + System.getProperty("java.version"));
        javaLabel.setForeground(new Color(display, 127, 140, 141));
        javaLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        Label osLabel = new Label(shell, SWT.CENTER);
        osLabel.setText(System.getProperty("os.name") + " " + System.getProperty("os.version"));
        osLabel.setForeground(new Color(display, 127, 140, 141));
        osLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

        // 分隔线
        new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL)
                .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // 版权信息
        Label copyrightLabel = new Label(shell, SWT.CENTER);
        copyrightLabel.setText("© 2024 晓雪WEB打印控件. All rights reserved.");
        copyrightLabel.setForeground(new Color(display, 149, 165, 166));
        copyrightLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
    }
}
