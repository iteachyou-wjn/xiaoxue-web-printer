package cc.iteachyou.printservice.ui;

import cc.iteachyou.printservice.util.AppIcons;
import cc.iteachyou.printservice.util.LicenseManager;
import cc.iteachyou.printservice.util.MachineCode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 授权许可对话框（SWT 实现，支持离线非对称签名授权）。
 *
 * <p>内容：授权状态 + 机器码（只读）+ 授权码输入框。
 * 点击「确定」对授权码进行验签校验，通过后持久化并激活。</p>
 */
public class LicenseDialog {

    private static final Logger log = LoggerFactory.getLogger(LicenseDialog.class);

    private final Display display;
    private Shell shell;
    private Label statusLabel;
    private Text codeText;

    public LicenseDialog(Display display) {
        this.display = display;
    }

    /**
     * 打开对话框（须在 UI 线程调用）
     */
    public void show() {
        createShell();
        shell.open();
        org.eclipse.swt.graphics.Rectangle displayBounds =
                display.getPrimaryMonitor().getClientArea();
        org.eclipse.swt.graphics.Rectangle shellBounds = shell.getBounds();
        shell.setLocation(displayBounds.x + (displayBounds.width - shellBounds.width) / 2,
                displayBounds.y + (displayBounds.height - shellBounds.height) / 2);
    }

    private void createShell() {
        shell = new Shell(display, SWT.SHELL_TRIM);
        shell.setText("授权许可");
        GridLayout layout = new GridLayout(2, false);
        layout.verticalSpacing = 12;
        shell.setLayout(layout);
        AppIcons.applyTo(shell);

        // 授权状态
        statusLabel = new Label(shell, SWT.WRAP);
        GridData statusData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        statusData.widthHint = 420;
        statusLabel.setLayoutData(statusData);
        refreshStatus();

        // 机器码（只读）
        Label machineLabel = new Label(shell, SWT.NONE);
        machineLabel.setText("机器码：");
        machineLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
        Text machineText = new Text(shell, SWT.BORDER | SWT.READ_ONLY);
        GridData machineData = new GridData(SWT.FILL, SWT.FILL, true, false);
        machineData.widthHint = 420;
        machineText.setLayoutData(machineData);
        machineText.setText(MachineCode.getMachineCode());
        machineText.setEditable(false);

        // 授权码（多行输入框）
        Label codeLabel = new Label(shell, SWT.NONE);
        codeLabel.setText("授权码：");
        codeLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
        codeText = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData codeData = new GridData(SWT.FILL, SWT.FILL, true, true);
        codeData.widthHint = 420;
        codeData.heightHint = 120;
        codeText.setLayoutData(codeData);
        String stored = LicenseManager.getStoredLicenseKey();
        if (stored != null) {
            codeText.setText(stored);
        }
        codeText.setFocus();

        // 底行：左侧邮箱链接 + 右侧按钮（确定 / 取消授权）
        Composite btnRow = new Composite(shell, SWT.NONE);
        GridLayout rowLayout = new GridLayout(3, false);
        rowLayout.marginWidth = 0;
        rowLayout.marginHeight = 0;
        rowLayout.horizontalSpacing = 20;
        btnRow.setLayout(rowLayout);
        btnRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        // 邮箱链接
        Link emailLink = new Link(btnRow, SWT.NONE);
        emailLink.setText("<a>iteachyou@foxmail.com</a>");
        emailLink.setToolTipText("购买授权许可请联系作者");
        emailLink.addListener(SWT.Selection, e -> sendEmail());

        // 弹性占位：把按钮推到最右
        Label spacer = new Label(btnRow, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // 按钮栏
        Composite btnBar = new Composite(btnRow, SWT.NONE);
        GridLayout btnLayout = new GridLayout(2, false);
        btnLayout.marginWidth = 0;
        btnLayout.marginHeight = 0;
        btnLayout.horizontalSpacing = 2; // 调小确定与取消授权的间距
        btnBar.setLayout(btnLayout);
        btnBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

        Button okBtn = new Button(btnBar, SWT.PUSH);
        okBtn.setText("确定");
        okBtn.addListener(SWT.Selection, e -> onOk());

        Button revokeBtn = new Button(btnBar, SWT.PUSH);
        revokeBtn.setText("取消授权");
        revokeBtn.addListener(SWT.Selection, e -> onRevoke());

        shell.setDefaultButton(okBtn);
        shell.pack();
    }

    private void refreshStatus() {
        LicenseManager.LicenseInfo info = LicenseManager.getStatus();
        Color red = display.getSystemColor(SWT.COLOR_RED);
        Color green = display.getSystemColor(SWT.COLOR_DARK_GREEN);
        switch (info.status) {
            case VALID:
                setStatus("当前授权：已授权，有效期至 " + info.expire + "（类型 " + info.type + "）", green);
                break;
            case NOT_LICENSED:
                setStatus("当前授权：未授权，请输入授权码激活", null);
                break;
            case EXPIRED:
                setStatus("当前授权：已过期（有效期至 " + info.expire + "），请更新授权码", red);
                break;
            case MACHINE_MISMATCH:
                setStatus("当前授权：授权码与本机机器码不匹配", red);
                break;
            case INVALID:
                setStatus("当前授权：授权码无效或已被篡改", red);
                break;
            case ERROR:
            default:
                setStatus("当前授权：校验出错", red);
                break;
        }
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        if (color != null) {
            statusLabel.setForeground(color);
        }
    }

    private void onOk() {
        String key = codeText.getText().trim();
        if (key.isEmpty()) {
            setStatus("请输入授权码", display.getSystemColor(SWT.COLOR_RED));
            return;
        }
        LicenseManager.LicenseInfo info = LicenseManager.validate(key);
        if (info.status == LicenseManager.Status.VALID) {
            if (LicenseManager.save(key)) {
                setStatus("授权成功，有效期至 " + info.expire + "（类型 " + info.type + "）",
                        display.getSystemColor(SWT.COLOR_DARK_GREEN));
                // 授权成功后自动关闭
                Display.getCurrent().timerExec(600, () -> shell.close());
            } else {
                setStatus("保存授权码失败，请重试", display.getSystemColor(SWT.COLOR_RED));
            }
        } else {
            setStatus("授权失败：" + failReason(info), display.getSystemColor(SWT.COLOR_RED));
        }
    }

    private String failReason(LicenseManager.LicenseInfo info) {
        switch (info.status) {
            case EXPIRED:
                return "授权码已过期（有效期至 " + info.expire + "）";
            case MACHINE_MISMATCH:
                return "授权码与本机机器码不匹配";
            case INVALID:
                return "授权码无效或已被篡改";
            default:
                return "校验出错";
        }
    }

    /**
     * 取消授权：删除本地授权码，恢复未授权状态
     */
    private void onRevoke() {
        LicenseManager.revoke();
        codeText.setText("");
        refreshStatus();
        setStatus("已取消授权，当前为未授权状态", display.getSystemColor(SWT.COLOR_RED));
    }

    /**
     * 打开系统邮件客户端，向 iteachyou@foxmail.com 发送邮件
     */
    private void sendEmail() {
        try {
            String mailto = "mailto:iteachyou@foxmail.com";
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", mailto).start();
            } else {
                java.awt.Desktop.getDesktop().mail(new java.net.URI(mailto));
            }
        } catch (Exception e) {
            log.error("打开邮件客户端失败", e);
            org.eclipse.swt.widgets.MessageBox box =
                    new org.eclipse.swt.widgets.MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
            box.setText("打开邮件客户端失败");
            box.setMessage("无法打开邮件客户端：" + e.getMessage());
            box.open();
        }
    }
}
