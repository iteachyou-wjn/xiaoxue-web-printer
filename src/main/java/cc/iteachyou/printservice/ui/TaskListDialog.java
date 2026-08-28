package cc.iteachyou.printservice.ui;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cc.iteachyou.printservice.MainApp;
import cc.iteachyou.printservice.util.AppIcons;
import cc.iteachyou.printservice.websocket.PrintTask;
import cc.iteachyou.printservice.websocket.PrintWebSocketServer;

/**
 * 任务列表对话框（SWT 实现）
 *
 * 显示所有打印任务的状态，支持筛选、刷新、清除等操作。
 * 任务列表数据来自 WebSocket 服务端，通过定时刷新保持实时性。
 */
public class TaskListDialog {

    private static final Logger log = LoggerFactory.getLogger(TaskListDialog.class);

    private final MainApp mainApp;
    private final Display display;

    private Shell shell;
    private Table table;
    private Combo filterCombo;
    private Label statusLabel;
    private Label titleLabel;

    /** 标题加粗字体（12号） */
    private Font windowBoldFont;

    /** 工具栏图标（统一释放资源） */
    private final java.util.List<Image> icons = new java.util.ArrayList<>();

    /** 图标尺寸 */
    private static final int ICON_SIZE = 20;

    /** 定时刷新周期（毫秒） */
    private static final int REFRESH_INTERVAL = 1000;

    public TaskListDialog(MainApp mainApp, Display display) {
        this.mainApp = mainApp;
        this.display = display;
    }

    /**
     * 从 classpath 的 images 目录加载图标（resources/images），统一缩放到 ICON_SIZE
     */
    private Image loadImage(String name) {
        try (java.io.InputStream is = TaskListDialog.class.getResourceAsStream("/images/" + name)) {
            if (is == null) {
                log.warn("图标资源不存在: /images/{}", name);
                return null;
            }
            ImageData data = new ImageData(is).scaledTo(ICON_SIZE, ICON_SIZE);
            Image img = new Image(display, data);
            icons.add(img);
            return img;
        } catch (Exception e) {
            log.warn("加载图标失败: {}", name, e);
            return null;
        }
    }

    /**
     * 打开对话框
     */
    public void show() {
        createShell();
        refreshTable();
        shell.open();

        // 定时刷新任务状态（任务状态由 WebSocket 服务端更新，UI 需要轮询同步）
        startAutoRefresh();
    }

    /**
     * 创建窗口内容
     */
    private void createShell() {
        shell = new Shell(display, SWT.SHELL_TRIM);
        shell.setText("打印任务列表");
        shell.setSize(1080, 600);
        shell.setMinimumSize(1080, 600);
        shell.setLayout(new GridLayout(1, false));
        AppIcons.applyTo(shell);

        // ===== 顶部工具栏 =====
        Composite toolbar = new Composite(shell, SWT.NONE);
        toolbar.setLayout(new GridLayout(5, false));
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        titleLabel = new Label(toolbar, SWT.NONE);
        titleLabel.setText("打印任务列表");
        // 仅标题使用 12 号加粗，其余控件保持系统默认字号
        windowBoldFont = createFont(12, true);
        titleLabel.setFont(windowBoldFont);

        // 弹性占位：吃掉水平多余空间，将右侧控件（全部/刷新/清除已完成）推到最右对齐
        Label spacer = new Label(toolbar, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // 筛选下拉框
        filterCombo = new Combo(toolbar, SWT.READ_ONLY);
        filterCombo.setItems(new String[]{"全部", "待打印", "打印中", "已完成", "失败"});
        filterCombo.select(0);
        filterCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshTable();
            }
        });

        // 刷新按钮（图标 image_18）
        Button refreshBtn = new Button(toolbar, SWT.PUSH);
        refreshBtn.setImage(loadImage("image_18.png"));
        refreshBtn.setToolTipText("刷新");
        refreshBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                refreshTable();
            }
        });

        // 清除已完成按钮（图标 image_17）
        Button clearBtn = new Button(toolbar, SWT.PUSH);
        clearBtn.setImage(loadImage("image_17.png"));
        clearBtn.setToolTipText("清除已完成");
        clearBtn.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                clearCompleted();
            }
        });

        // ===== 任务表格 =====
        table = new Table(shell, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        table.setLayoutData(tableData);

        // 列定义
        String[] titles = {"任务ID", "任务名称", "打印机", "状态", "创建时间", "完成时间", "错误信息"};
        int[] widths = {100, 200, 120, 100, 150, 150, 180};
        for (int i = 0; i < titles.length; i++) {
            TableColumn column = new TableColumn(table, SWT.NONE);
            column.setText(titles[i]);
            column.setWidth(widths[i]);
        }

        // ===== 底部状态栏 =====
        Composite statusBar = new Composite(shell, SWT.NONE);
        statusBar.setLayout(new GridLayout(2, false));
        statusBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        statusLabel = new Label(statusBar, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label wsLabel = new Label(statusBar, SWT.NONE);
        wsLabel.setText("WebSocket: 端口 " + MainApp.WS_PORT);
        wsLabel.setForeground(new Color(display, 39, 174, 96));

        // 除标题外全部使用系统默认字号，不额外设置窗体字体

        // 窗口关闭时释放字体资源
        shell.addListener(SWT.Dispose, e -> {
            if (windowBoldFont != null && !windowBoldFont.isDisposed()) {
                windowBoldFont.dispose();
            }
            for (Image icon : icons) {
                if (icon != null && !icon.isDisposed()) {
                    icon.dispose();
                }
            }
        });
    }

    /**
     * 刷新表格数据
     */
    private void refreshTable() {
        if (table == null || table.isDisposed()) {
            return;
        }
        PrintWebSocketServer server = getServer();
        if (server == null) {
            return;
        }

        List<PrintTask> tasks = server.getTaskList();
        String filter = filterCombo != null ? filterCombo.getText() : "全部";

        table.removeAll();
        for (PrintTask task : tasks) {
            // 应用筛选
            if (!"全部".equals(filter) && !task.getStatus().getDisplayName().equals(filter)) {
                continue;
            }
            TableItem item = new TableItem(table, SWT.NONE);
            item.setText(new String[]{
                    task.getId(),
                    task.getName(),
                    task.getPrinterName(),
                    task.getStatus().getDisplayName(),
                    task.getCreateTimeFormatted(),
                    task.getFinishTimeFormatted(),
                    task.getErrorMessage() == null ? "" : task.getErrorMessage()
            });
            // 状态列颜色
            item.setForeground(3, getStatusColor(task.getStatus()));
        }

        updateStatusLabel();
    }

    /**
     * 更新状态栏文本
     */
    private void updateStatusLabel() {
        PrintWebSocketServer server = getServer();
        if (server == null || statusLabel == null || statusLabel.isDisposed()) {
            return;
        }
        List<PrintTask> tasks = server.getTaskList();
        int total = tasks.size();
        int pending = countByStatus(tasks, PrintTask.Status.PENDING);
        int printing = countByStatus(tasks, PrintTask.Status.PRINTING);
        int completed = countByStatus(tasks, PrintTask.Status.COMPLETED);
        int failed = countByStatus(tasks, PrintTask.Status.FAILED);
        int clients = server.getClientCount();

        statusLabel.setText(String.format(
                "共 %d 个任务 | 待打印: %d | 打印中: %d | 已完成: %d | 失败: %d | 客户端连接: %d",
                total, pending, printing, completed, failed, clients));
    }

    private int countByStatus(List<PrintTask> tasks, PrintTask.Status status) {
        int count = 0;
        for (PrintTask t : tasks) {
            if (t.getStatus() == status) {
                count++;
            }
        }
        return count;
    }

    /**
     * 清除已完成和失败的任务
     */
    private void clearCompleted() {
        PrintWebSocketServer server = getServer();
        if (server == null) {
            return;
        }
        org.eclipse.swt.widgets.MessageBox box =
                new org.eclipse.swt.widgets.MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
        box.setText("确认清除");
        box.setMessage("确定要清除所有已完成和失败的任务吗？");
        if (box.open() == SWT.YES) {
            server.getTaskList().removeIf(t ->
                    t.getStatus() == PrintTask.Status.COMPLETED ||
                    t.getStatus() == PrintTask.Status.FAILED);
            refreshTable();
        }
    }

    /**
     * 启动自动刷新（窗口关闭时自动停止，因为 Timer 绑定在 display 上）
     */
    private void startAutoRefresh() {
        display.timerExec(REFRESH_INTERVAL, new Runnable() {
            @Override
            public void run() {
                if (shell == null || shell.isDisposed()) {
                    return;
                }
                refreshTable();
                // 继续下一次刷新
                display.timerExec(REFRESH_INTERVAL, this);
            }
        });
    }

    /**
     * 获取状态颜色
     */
    private Color getStatusColor(PrintTask.Status status) {
        switch (status) {
            case PENDING:   return new Color(display, 243, 156, 18);
            case PRINTING:  return new Color(display, 52, 152, 219);
            case COMPLETED: return new Color(display, 39, 174, 96);
            case FAILED:    return new Color(display, 231, 76, 60);
            default:        return new Color(display, 127, 140, 141);
        }
    }

    /**
     * 获取 WebSocket 服务引用
     */
    private PrintWebSocketServer getServer() {
        return mainApp.getWebSocketServer();
    }

    /**
     * 创建字体
     */
    private org.eclipse.swt.graphics.Font createFont(int size, boolean bold) {
        return new org.eclipse.swt.graphics.Font(display,
                display.getSystemFont().getFontData()[0].getName(), size,
                bold ? SWT.BOLD : SWT.NORMAL);
    }
}
