package cc.iteachyou.printservice.ui;

import cc.iteachyou.printservice.util.AppIcons;
import cc.iteachyou.printservice.util.MachineCode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 授权许可对话框（SWT 实现）
 *
 * 内容两行：
 * - 第一行：机器码（由程序获取本机机器码展示，只读不可更改）
 * - 第二行：授权码（多行输入框，由用户输入）
 */
public class LicenseDialog {

    private static final Logger log = LoggerFactory.getLogger(LicenseDialog.class);

    private final Display display;
    private Shell shell;

    public LicenseDialog(Display display) {
        this.display = display;
    }

    /**
     * 打开对话框（须在 UI 线程调用；托盘菜单事件本身在 UI 线程）
     */
    public void show() {
        createShell();
        shell.open();
        // 居中于主显示器
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
        // 加大机器码与授权码两行之间的垂直间距
        layout.verticalSpacing = 18;
        shell.setLayout(layout);
        AppIcons.applyTo(shell);

        // 第一行：机器码（只读）
        Label machineLabel = new Label(shell, SWT.NONE);
        machineLabel.setText("机器码:");
        machineLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));

        Text machineText = new Text(shell, SWT.BORDER | SWT.READ_ONLY);
        GridData machineData = new GridData(SWT.FILL, SWT.FILL, true, false);
        machineData.widthHint = 420;
        machineText.setLayoutData(machineData);
        machineText.setText(MachineCode.getMachineCode());
        machineText.setEditable(false);

        // 第二行：授权码（多行输入框）
        Label codeLabel = new Label(shell, SWT.NONE);
        codeLabel.setText("授权码:");
        codeLabel.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));

        Text codeText = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData codeData = new GridData(SWT.FILL, SWT.FILL, true, true);
        codeData.widthHint = 420;
        codeData.heightHint = 120;
        codeText.setLayoutData(codeData);
        codeText.setFocus();

        // 按钮行：确定 / 取消
        Button okBtn = new Button(shell, SWT.PUSH);
        okBtn.setText("确定");
        okBtn.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
        okBtn.addListener(SWT.Selection, e -> shell.close());

        shell.setDefaultButton(okBtn);
        shell.pack();
    }
}
