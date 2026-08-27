package cc.iteachyou.printservice.ui;

import cc.iteachyou.printservice.print.HtmlRenderService;
import cc.iteachyou.printservice.print.PaperSizeUtil;
import cc.iteachyou.printservice.print.PrintTaskExecutor;
import cc.iteachyou.printservice.print.bartender.BartenderManager;
import cc.iteachyou.printservice.util.AppIcons;
import cc.iteachyou.printservice.websocket.PrintTask;
import cc.iteachyou.printservice.websocket.PrintWebSocketServer;
import com.alibaba.fastjson2.JSONObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 打印预览窗体（方案 B：OpenHTMLtoPDF 渲染）
 *
 * 顶部工具条：打印 / 页面设置 / 打印机设置 / 导出PDF / 导出图片 /
 * 放大 / 缩小 / 缩放比例 / 首页 / 上一页 / 页数 / 下一页 / 尾页 / 拖动 / 关闭。
 * 中间内容区域按实际纸张规格（PaperSizeUtil）等比缩放居中展示，支持分页、缩放、拖动。
 *
 * 渲染流程：后台线程调用 {@link HtmlRenderService#render} 渲染 PDF，
 * 逐页转图像后回到 UI 线程展示，窗口打开不阻塞；页面设置修改后自动重新渲染。
 */
public class PreviewDialog {

    private static final Logger log = LoggerFactory.getLogger(PreviewDialog.class);

    private static final double CM_TO_POINT = 72.0 / 2.54;

    private final Display display;
    private final String printerName;
    /** WebSocket 服务引用（预览窗打印时同步任务到任务列表、预览去重复用） */
    private final PrintWebSocketServer server;
    /** 预览 ID（由指令 MD5 生成，用于重复预览时激活已有窗体） */
    private final String previewId;

    // 内容与样式（可被页面设置修改）
    private final JSONObject content;
    private String paper;
    private String direction;
    private String fontFamily;
    private int fontSize;
    private double zoomValue = 1.0;
    private String header;
    private String footer;
    private double marginTop, marginRight, marginBottom, marginLeft;

    // 渲染产物
    private PDDocument renderedDoc;
    /** 每页高清图（AWT，导出图片用） */
    private final List<BufferedImage> pageImagesAwt = new ArrayList<>();
    /** 每页 SWT 图像（预览绘制用，统一释放） */
    private final List<Image> pageImagesSwt = new ArrayList<>();
    private volatile boolean rendering = false;
    private volatile String renderError;

    // 视图状态
    private int currentPage = 0;
    private boolean fitWindow = false;
    private double zoomFactor = 1.0;
    private boolean dragMode = false;
    private boolean dragging = false;
    private int offsetX = 0, offsetY = 0;
    private int dragStartX, dragStartY, offsetStartX, offsetStartY;
    private int maxSelX = 0, maxSelY = 0;

    // UI
    private Shell shell;
    private Canvas canvas;
    private Combo zoomCombo;
    private Text pageText;
    private ToolItem dragItem;
    /** 渲染中 loading 覆盖层（ProgressBar + 标签） */
    private Composite loadingOverlay;
    private ProgressBar loadingBar;
    /** 工具条加载的图标（统一释放资源） */
    private final List<Image> icons = new ArrayList<>();

    private static final String[] PAPER_OPTIONS = {
            "A0", "A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8",
            "B4", "B5", "B6", "Letter", "Legal", "Ledger", "Tabloid", "Executive",
            "C4", "C5", "C6", "DL"
    };

    private PreviewDialog(Display display, PrintWebSocketServer server, String previewId,
                          String printerName, JSONObject style, JSONObject content) {
        this.display = display;
        this.server = server;
        this.previewId = previewId;
        this.printerName = printerName;
        this.content = content;

        this.paper = style != null ? style.getString("paper") : null;
        if (paper == null || !PaperSizeUtil.contains(paper)) {
            paper = "A4";
        }
        this.direction = style != null ? style.getString("direction") : null;
        this.fontFamily = style != null ? style.getString("fontFamily") : null;
        this.fontSize = style != null ? style.getIntValue("fontSize") : 12;
        if (fontSize <= 0) {
            fontSize = 12;
        }
        this.zoomValue = style != null ? style.getDoubleValue("zoom") : 1;
        if (zoomValue <= 0) {
            zoomValue = 1;
        }
        this.header = style != null ? style.getString("paperHeader") : null;
        this.footer = style != null ? style.getString("paperFooter") : null;

        JSONObject margin = style != null ? style.getJSONObject("margin") : null;
        marginTop = margin != null ? margin.getDoubleValue("top") * CM_TO_POINT : 0;
        marginRight = margin != null ? margin.getDoubleValue("right") * CM_TO_POINT : 0;
        marginBottom = margin != null ? margin.getDoubleValue("bottom") * CM_TO_POINT : 0;
        marginLeft = margin != null ? margin.getDoubleValue("left") * CM_TO_POINT : 0;
    }

    public static void show(Display display, PrintWebSocketServer server, String previewId,
                            String printerName, JSONObject style, JSONObject content) {
        if (display == null || display.isDisposed()) {
            log.warn("Display 不可用，无法打开预览");
            return;
        }
        display.asyncExec(() -> {
            try {
                new PreviewDialog(display, server, previewId, printerName, style, content).open();
            } catch (Exception e) {
                log.error("打开打印预览失败", e);
            }
        });
    }

    private void open() {
        shell = new Shell(display, SWT.SHELL_TRIM);
        shell.setText("打印预览");
        shell.setSize(1000, 760);
        shell.setMinimumSize(700, 520);
        GridLayout mainLayout = new GridLayout(1, false);
        // 默认 marginHeight/Width 为 5，不置 0 时工具栏与画布之间会多出一条缝
        mainLayout.marginWidth = 0;
        mainLayout.marginHeight = 0;
        mainLayout.marginTop = 0;
        mainLayout.marginLeft = 2;
        mainLayout.marginRight = 2;
        mainLayout.marginBottom = 0;
        mainLayout.verticalSpacing = 0;
        shell.setLayout(mainLayout);
        AppIcons.applyTo(shell);

        buildToolBar();

        // 预览画布（带滚动条）
        canvas = new Canvas(shell, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        GridData canvasData = new GridData(SWT.FILL, SWT.FILL, true, true);
        canvasData.verticalIndent = 10;
        canvas.setLayoutData(canvasData);
        canvas.addListener(SWT.Paint, e -> {
            Rectangle ca = canvas.getClientArea();
            paintPage(e.gc, ca.width, ca.height);
        });
        installDragEvents();
        installScrollBars();
        createLoadingOverlay();

        // 底部状态栏（打印机/纸张/方向），文字在状态栏高度内垂直居中、左对齐
        Label statusLabel = new Label(shell, SWT.NONE);
        statusLabel.setText("打印机: " + (printerName == null || printerName.isEmpty() ? "默认打印机" : printerName)
                + "    纸张: " + paper
                + "    方向: " + ("horizontal".equalsIgnoreCase(direction) ? "横向" : "纵向"));
        GridData statusData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        statusData.heightHint = 30;
        statusData.verticalIndent = 10;
        statusData.verticalAlignment = SWT.CENTER;
        statusLabel.setLayoutData(statusData);

        shell.addListener(SWT.Dispose, e -> {
            if (server != null) {
                server.unregisterPreview(previewId, this);
            }
            disposeRenderedDoc();
            for (Image icon : icons) {
                if (icon != null && !icon.isDisposed()) {
                    icon.dispose();
                }
            }
            disposePageImagesSwt();
        });

        reRender();
        shell.open();
        // 窗口显示后再最大化（SWT 规范：先 open 再 setMaximized，
        // 避免最大化尺寸把 Windows 任务栏区域也算进去导致画布偏高）
        shell.setMaximized(true);
        // 激活窗体，使其展示到用户面前
        bringToFront();

        // 注册预览窗体（供重复预览时激活已有窗体）
        if (server != null) {
            server.registerPreview(previewId, this);
        }
    }

    /**
     * 将预览窗体最大化并强制置前显示。
     * 通过 Win32 API 绕过 Windows 前台锁定（后台进程弹窗无法用纯 SWT 抢焦点）。
     */
    private void bringToFront() {
        if (shell == null || shell.isDisposed()) {
            return;
        }
        shell.setActive();
        shell.forceActive();
        shell.moveAbove(null);
        if ("win32".equals(SWT.getPlatform())) {
            try {
                long hwnd = shell.handle;
                // 仅显示（SW_SHOW 保持当前最大化状态，不用 SW_RESTORE 避免取消最大化）
                org.eclipse.swt.internal.win32.OS.ShowWindow(hwnd, org.eclipse.swt.internal.win32.OS.SW_SHOW);
                org.eclipse.swt.internal.win32.OS.SetForegroundWindow(hwnd);
                org.eclipse.swt.internal.win32.OS.BringWindowToTop(hwnd);
                // 临时置顶再取消，确保窗口弹到最上层
                org.eclipse.swt.internal.win32.OS.SetWindowPos(hwnd, org.eclipse.swt.internal.win32.OS.HWND_TOPMOST,
                        0, 0, 0, 0, org.eclipse.swt.internal.win32.OS.SWP_NOMOVE | org.eclipse.swt.internal.win32.OS.SWP_NOSIZE);
                org.eclipse.swt.internal.win32.OS.SetWindowPos(hwnd, org.eclipse.swt.internal.win32.OS.HWND_NOTOPMOST,
                        0, 0, 0, 0, org.eclipse.swt.internal.win32.OS.SWP_NOMOVE | org.eclipse.swt.internal.win32.OS.SWP_NOSIZE);
            } catch (Throwable t) {
                // 非 Windows 平台或调用失败时忽略，回退纯 SWT 激活
            }
        }
    }

    /**
     * 预览窗体是否已关闭
     */
    public boolean isDisposed() {
        return shell == null || shell.isDisposed();
    }

    /**
     * 激活已存在的预览窗体（还原最小化、显示并置前）。
     * 供 WebSocket 服务在收到相同预览指令时调用。
     */
    public void activate() {
        if (shell == null || shell.isDisposed()) {
            return;
        }
        display.asyncExec(() -> {
            if (shell == null || shell.isDisposed()) {
                return;
            }
            shell.setMinimized(false);
            if (!shell.isVisible()) {
                shell.setVisible(true);
            }
            // 以最大化展示并激活置前
            shell.setMaximized(true);
            bringToFront();
        });
    }

    // ================= 渲染 =================

    /**
     * 后台线程渲染 PDF 并逐页转图像，完成后回到 UI 线程更新预览。
     */
    private void reRender() {
        if (rendering) {
            return;
        }
        rendering = true;
        renderError = null;
        currentPage = 0;
        updatePageLabel();
        showLoading();
        canvas.redraw();

        JSONObject style = currentStyle();
        Thread t = new Thread(() -> {
            try {
                PDDocument doc = null;
                List<BufferedImage> imgs;
                String ctype = content != null ? content.getString("type") : null;
                if ("bartender".equalsIgnoreCase(ctype)) {
                    // BarTender 类型：渲染模板图片作为预览
                    imgs = renderBartenderImages();
                } else {
                    doc = HtmlRenderService.render(style, content);
                    imgs = new ArrayList<>();
                    for (int i = 0; i < doc.getNumberOfPages(); i++) {
                        imgs.add(HtmlRenderService.renderPageImage(doc, i, HtmlRenderService.PREVIEW_DPI));
                    }
                }
                final PDDocument fdoc = doc;
                display.asyncExec(() -> {
                    if (shell == null || shell.isDisposed()) {
                        if (fdoc != null) {
                            try {
                                fdoc.close();
                            } catch (IOException ignored) {
                            }
                        }
                        return;
                    }
                    disposeRenderedDoc();
                    renderedDoc = fdoc;
                    disposePageImagesSwt();
                    pageImagesAwt.clear();
                    pageImagesAwt.addAll(imgs);
                    for (BufferedImage bi : imgs) {
                        pageImagesSwt.add(toSwtImage(bi));
                    }
                    rendering = false;
                    currentPage = 0;
                    hideLoading();
                    updatePageLabel();
                    canvas.redraw();
                    log.info("预览渲染完成，共 {} 页", pageImagesAwt.size());
                });
            } catch (Throwable t2) {
                log.error("预览渲染失败", t2);
                display.asyncExec(() -> {
                    if (shell == null || shell.isDisposed()) {
                        return;
                    }
                    rendering = false;
                    renderError = t2.getMessage() == null ? "渲染失败" : t2.getMessage();
                    hideLoading();
                    updatePageLabel();
                    canvas.redraw();
                });
            }
        }, "preview-render");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 渲染 BarTender 模板为预览图片（单页）。
     * 使用 {@link BartenderManager#preview} 生成 PNG，再解码为 {@link BufferedImage}。
     *
     * @return 预览图片列表（通常为 1 页）
     * @throws IOException BarTender 不可用或渲染失败时抛出
     */
    private List<BufferedImage> renderBartenderImages() throws IOException {
        List<BufferedImage> imgs = new ArrayList<>();
        if (content == null) {
            return imgs;
        }
        String template = content.getString("value");
        byte[] png = BartenderManager.preview(template, bartenderParams(), HtmlRenderService.PREVIEW_DPI, printerName);
        if (png == null) {
            throw new IOException("BarTender 预览渲染失败（本机可能未安装 BarTender 或模板无效）");
        }
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(png));
        if (bi == null) {
            log.warn("BarTender 预览图片解码失败，PNG 字节数={}", png.length);
            throw new IOException("BarTender 预览图片解码失败");
        }
        // 诊断：统计非白像素，判断模板是否渲染出内容
        int w = bi.getWidth(), h = bi.getHeight();
        int colored = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = bi.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                if (!(r > 250 && g > 250 && b > 250)) {
                    colored++;
                }
            }
        }
        log.info("BarTender 预览图片 {}x{}, 非白像素={}, PNG 字节数={}", w, h, colored, png.length);
        if (colored == 0) {
            log.warn("BarTender 预览图片为全白空白，请检查模板内容、字段赋值或数据库连接");
        }
        imgs.add(bi);
        return imgs;
    }

    /**
     * 从 content.params 提取 BarTender 模板字段值。
     */
    private Map<String, String> bartenderParams() {
        Map<String, String> params = new HashMap<>();
        JSONObject p = content != null ? content.getJSONObject("params") : null;
        if (p != null) {
            for (String key : p.keySet()) {
                Object v = p.get(key);
                params.put(key, v == null ? "" : String.valueOf(v));
            }
        }
        return params;
    }

    // ================= 渲染 loading 覆盖层 =================

    /**
     * 在预览画布上叠加一个居中的不确定进度条 loading 覆盖层，
     * 渲染中显示、渲染完成/失败后隐藏。
     */
    private void createLoadingOverlay() {
        loadingOverlay = new Composite(shell, SWT.NONE);
        loadingOverlay.setLayout(new GridLayout(1, false));
        loadingOverlay.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
        loadingOverlay.setVisible(false);
        // 覆盖层用 setBounds 叠在画布上，必须 exclude，否则 GridLayout 仍给它留一行空白
        GridData overlayData = new GridData();
        overlayData.exclude = true;
        loadingOverlay.setLayoutData(overlayData);

        Composite center = new Composite(loadingOverlay, SWT.NONE);
        center.setLayout(new GridLayout(1, true));
        center.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));

        loadingBar = new ProgressBar(center, SWT.INDETERMINATE);
        loadingBar.setLayoutData(new GridData(180, 14));
        Label lbl = new Label(center, SWT.NONE);
        lbl.setText("渲染中...");
        lbl.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

        // 画布尺寸变化时同步覆盖层位置
        canvas.addListener(SWT.Resize, e -> {
            if (loadingOverlay != null && !loadingOverlay.isDisposed()) {
                updateLoadingOverlay();
            }
        });
    }

    private void updateLoadingOverlay() {
        if (loadingOverlay == null || loadingOverlay.isDisposed()) {
            return;
        }
        Rectangle r = canvas.getBounds();
        loadingOverlay.setBounds(r.x, r.y, r.width, r.height);
        loadingOverlay.moveAbove(canvas);
        loadingOverlay.layout();
    }

    private void showLoading() {
        if (loadingOverlay == null || loadingOverlay.isDisposed()) {
            return;
        }
        updateLoadingOverlay();
        loadingOverlay.setVisible(true);
        loadingOverlay.moveAbove(canvas);
    }

    private void hideLoading() {
        if (loadingOverlay == null || loadingOverlay.isDisposed()) {
            return;
        }
        loadingOverlay.setVisible(false);
    }

    private void disposeRenderedDoc() {
        if (renderedDoc != null) {
            try {
                renderedDoc.close();
            } catch (IOException ignored) {
            }
            renderedDoc = null;
        }
    }

    private void disposePageImagesSwt() {
        for (Image img : pageImagesSwt) {
            if (img != null && !img.isDisposed()) {
                img.dispose();
            }
        }
        pageImagesSwt.clear();
    }

    // ================= 工具条 =================

    private void buildToolBar() {
        // 不用 SWT.WRAP：Win32 折行工具栏会按两行报高度，按钮只占第一行，下面留一条空白
        ToolBar toolbar = new ToolBar(shell, SWT.FLAT);
        GridData toolbarData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        toolbar.setLayoutData(toolbarData);

        // 打印：image_03
        addIconPush(toolbar, "打印", "image_03.png", e -> doPrint());
        // 页面设置：image_04
        addIconPush(toolbar, "页面设置", "image_04.png", e -> openPageSetup());
        // 打印机设置：image_05
        addIconPush(toolbar, "打印机设置", "image_05.png", e -> doPrinterSetup());
        // 导出PDF：image_02
        addIconPush(toolbar, "导出PDF", "image_02.png", e -> exportPdf());
        // 导出图片：image_06
        addIconPush(toolbar, "导出图片", "image_06.png", e -> exportImage());
        separator(toolbar);

        // 放大：image_10
        addIconPush(toolbar, "放大", "image_10.png", e -> zoomIn());
        // 缩小：image_11
        addIconPush(toolbar, "缩小", "image_11.png", e -> zoomOut());
        ToolItem sepZoom = separator(toolbar);
        zoomCombo = new Combo(toolbar, SWT.READ_ONLY);
        zoomCombo.setItems(new String[]{"适应窗口", "50%", "75%", "100%", "125%", "150%", "200%"});
        zoomCombo.select(3);
        zoomCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                applyZoomSelection(zoomCombo.getText());
            }
        });
        sepZoom.setWidth(120);
        sepZoom.setControl(zoomCombo);

        separator(toolbar);

        // 首页：image_08
        addIconPush(toolbar, "首页", "image_08.png", e -> goPage(0));
        // 上一页：image_12
        addIconPush(toolbar, "上一页", "image_12.png", e -> goPage(currentPage - 1));
        ToolItem sepPage = separator(toolbar);
        pageText = new Text(toolbar, SWT.BORDER | SWT.CENTER);
        pageText.setTextLimit(12);
        pageText.addListener(SWT.DefaultSelection, e -> jumpToPage(pageText.getText()));
        sepPage.setWidth(120);
        sepPage.setControl(pageText);
        // 下一页：image_16
        addIconPush(toolbar, "下一页", "image_16.png", e -> goPage(currentPage + 1));
        // 尾页：image_09
        addIconPush(toolbar, "尾页", "image_09.png", e -> goPage(pageCount() - 1));

        separator(toolbar);

        // 拖动：image_19（CHECK 开关，仅图标）
        dragItem = new ToolItem(toolbar, SWT.CHECK);
        Image dragIcon = loadImage("image_19.png", ICON_SIZE, ICON_SIZE);
        if (dragIcon != null) {
            dragItem.setImage(dragIcon);
            dragItem.setWidth(ICON_SIZE + BUTTON_GAP);
        }
        dragItem.setToolTipText("拖动");
        dragItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                dragMode = dragItem.getSelection();
            }
        });

        separator(toolbar);

        // 关闭：image_20
        addIconPush(toolbar, "关闭", "image_20.png", e -> shell.close());

        // Combo/Text 嵌入后再按单行真实高度锁定，避免 GridLayout 按控件 preferredSize 多留空
        toolbar.pack();
        toolbarData.heightHint = toolbar.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
    }

    /** 工具条动作（函数式接口，供 addPush 使用） */
    @FunctionalInterface
    private interface Action {
        void run(SelectionEvent e);
    }

    /** 工具条图标尺寸 */
    private static final int ICON_SIZE = 26;
    /** 按钮间间距（像素） */
    private static final int BUTTON_GAP = 3;
    /** 标尺条宽度（像素） */
    private static final int RULER_SIZE = 35;
    /** 滚动到边缘时内容外侧预留的空白（像素） */
    private static final int SCROLL_BLANK = 30;

    /**
     * 添加仅显示图标的工具按钮（无文字），tooltip 为功能说明
     */
    private ToolItem addIconPush(ToolBar toolbar, String tooltip, String iconName, Action action) {
        ToolItem item = new ToolItem(toolbar, SWT.PUSH);
        Image icon = loadImage(iconName, ICON_SIZE, ICON_SIZE);
        if (icon != null) {
            item.setImage(icon);
            item.setWidth(ICON_SIZE + BUTTON_GAP);
        }
        item.setToolTipText(tooltip);
        item.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                action.run(e);
            }
        });
        return item;
    }

    private ToolItem separator(ToolBar toolbar) {
        return new ToolItem(toolbar, SWT.SEPARATOR);
    }

    /**
     * 从 classpath 的 images 目录加载图标（resources/images），并按指定尺寸缩放
     */
    private Image loadImage(String name, int width, int height) {
        try (java.io.InputStream is = PreviewDialog.class.getResourceAsStream("/images/" + name)) {
            if (is == null) {
                log.warn("图标资源不存在: /images/{}", name);
                return null;
            }
            ImageData data = new ImageData(is);
            if (width > 0 && height > 0) {
                data = data.scaledTo(width, height);
            }
            Image img = new Image(display, data);
            icons.add(img);
            return img;
        } catch (Exception e) {
            log.warn("加载图标失败: {}", name, e);
            return null;
        }
    }

    // ================= 绘制 =================

    /** 是否 BarTender 类型预览 */
    private boolean isBartender() {
        return content != null && "bartender".equalsIgnoreCase(content.getString("type"));
    }

    /**
     * 实际纸张尺寸（pt）。
     * BarTender 类型：按导出图片的实际像素（DPI300）换算为物理尺寸，
     * 使预览与模板真实标签尺寸、比例一致；其他类型用标准纸张规格。
     */
    private double[] effectivePaperSize() {
        if (isBartender() && !pageImagesAwt.isEmpty()) {
            BufferedImage bi = pageImagesAwt.get(0);
            double wPt = bi.getWidth() / 300.0 * 72.0;
            double hPt = bi.getHeight() / 300.0 * 72.0;
            return new double[]{ wPt, hPt };
        }
        return PaperSizeUtil.sizeOfOrDefault(paper);
    }

    private void paintPage(GC gc, int availWidth, int availHeight) {
        double[] size = effectivePaperSize();
        // 预留标尺条空间（上、左各 RULER_SIZE）
        int wArea = availWidth - RULER_SIZE;
        int hArea = availHeight - RULER_SIZE;
        double scale = computeScale(wArea, hArea, size);
        int pw = (int) (size[0] * scale);
        int ph = (int) (size[1] * scale);
        int ox = RULER_SIZE + (wArea - pw) / 2 + offsetX;
        int oy = RULER_SIZE + (hArea - ph) / 2 + offsetY;
        drawPage(gc, ox, oy, pw, ph);
        drawRulers(gc, availWidth, availHeight, ox, oy, pw, ph, scale);
        updateScrollBars(availWidth, availHeight, pw, ph);
    }

    /**
     * 绘制整页：白底 + 纸张边框 + 当前页渲染图像（铺满纸张区域，含边距）
     */
    private void drawPage(GC gc, int ox, int oy, int pw, int ph) {
        Color white = display.getSystemColor(SWT.COLOR_WHITE);
        Color gray = display.getSystemColor(SWT.COLOR_GRAY);
        Color black = display.getSystemColor(SWT.COLOR_BLACK);

        gc.setBackground(white);
        gc.fillRectangle(ox, oy, pw, ph);
        gc.setForeground(gray);
        gc.drawRectangle(ox, oy, pw, ph);

        if (rendering) {
            // 渲染中由 loading 覆盖层提示，不绘制内容
            return;
        }
        if (renderError != null) {
            gc.setForeground(black);
            gc.drawText("渲染失败: " + renderError, ox + 10, oy + 10, true);
            return;
        }
        if (currentPage >= 0 && currentPage < pageImagesSwt.size()) {
            Image img = pageImagesSwt.get(currentPage);
            if (img != null && !img.isDisposed()) {
                Rectangle b = img.getBounds();
                gc.drawImage(img, 0, 0, b.width, b.height, ox, oy, Math.max(1, pw), Math.max(1, ph));
            }
        }
    }

    /**
     * 绘制水平（上侧）与垂直（左侧）毫米标尺。
     * 标尺以纸张左/上边缘为 0mm 基准，刻度范围仅覆盖纸张本身（宽度/高度），
     * 数字使用小号字体。
     */
    private void drawRulers(GC gc, int availWidth, int availHeight, int ox, int oy, int pw, int ph, double scale) {
        // 1 point = 1/72 inch = 25.4/72 mm
        double mmPerPoint = 25.4 / 72.0;
        double pxPerMm = mmPerPoint > 0 ? scale / mmPerPoint : 1;
        if (pxPerMm < 0.0001) {
            pxPerMm = 1;
        }

        Color bg = display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
        Color line = display.getSystemColor(SWT.COLOR_GRAY);
        Color fg = display.getSystemColor(SWT.COLOR_BLACK);
        Font small = new Font(display, display.getSystemFont().getFontData()[0].getName(), 8, SWT.NORMAL);

        // 水平标尺（上侧），刻度范围 0..纸张宽度
        gc.setBackground(bg);
        gc.fillRectangle(0, 0, availWidth, RULER_SIZE);
        gc.setForeground(line);
        gc.drawLine(0, RULER_SIZE - 1, availWidth, RULER_SIZE - 1);
        gc.setFont(small);
        gc.setForeground(fg);
        int rightEdgeX = ox + pw;
        int maxMmH = (int) (pw / pxPerMm);
        for (int mm = 0; mm <= maxMmH; mm++) {
            int x = ox + (int) (mm * pxPerMm);
            if (x > rightEdgeX) {
                break;
            }
            if (x < RULER_SIZE || x > availWidth) {
                continue;
            }
            int len = (mm % 10 == 0) ? 10 : (mm % 5 == 0) ? 7 : 4;
            gc.drawLine(x, RULER_SIZE - 1 - len, x, RULER_SIZE - 1);
            if (mm % 10 == 0) {
                // 数字靠上显示，与底部刻度线分离
                gc.drawString(String.valueOf(mm / 10), x + 2, 2, true);
            }
        }

        // 垂直标尺（左侧），刻度范围 0..纸张高度
        gc.setBackground(bg);
        gc.fillRectangle(0, 0, RULER_SIZE, availHeight);
        gc.setForeground(line);
        gc.drawLine(RULER_SIZE - 1, 0, RULER_SIZE - 1, availHeight);
        gc.setForeground(fg);
        int bottomEdgeY = oy + ph;
        int maxMmV = (int) (ph / pxPerMm);
        for (int mm = 0; mm <= maxMmV; mm++) {
            int y = oy + (int) (mm * pxPerMm);
            if (y > bottomEdgeY) {
                break;
            }
            if (y < RULER_SIZE || y > availHeight) {
                continue;
            }
            int len = (mm % 10 == 0) ? 10 : (mm % 5 == 0) ? 7 : 4;
            gc.drawLine(RULER_SIZE - 1 - len, y, RULER_SIZE - 1, y);
            if (mm % 10 == 0) {
                // 数字靠左显示，与右侧刻度线分离
                gc.drawString(String.valueOf(mm / 10), 2, y + 2, true);
            }
        }

        gc.setFont(display.getSystemFont());
        small.dispose();
    }

    /**
     * 把 AWT BufferedImage 转成 SWT Image（用于预览绘制）
     */
    private Image toSwtImage(BufferedImage bi) {
        try {
            int w = bi.getWidth(), h = bi.getHeight();
            PaletteData palette = new PaletteData(0xFF0000, 0xFF00, 0xFF);
            ImageData data = new ImageData(w, h, 32, palette);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = bi.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    data.setPixel(x, y, palette.getPixel(new RGB(r, g, b)));
                    data.setAlpha(x, y, (rgb >> 24) & 0xFF);
                }
            }
            return new Image(display, data);
        } catch (Exception e) {
            log.warn("图片转 SWT 失败", e);
            return null;
        }
    }

    // ================= 滚动条 =================

    /**
     * 内容超出可视区域时启用滚动条，并把滚动条位置映射到平移偏移
     */
    private void installScrollBars() {
        ScrollBar hBar = canvas.getHorizontalBar();
        ScrollBar vBar = canvas.getVerticalBar();
        if (hBar != null) {
            hBar.addListener(SWT.Selection, e -> {
                // selection 0..(maxSelX+2*blank) 对应左对齐→右对齐，两端各留 blank 空白
                offsetX = maxSelX / 2 + SCROLL_BLANK - hBar.getSelection();
                canvas.redraw();
            });
        }
        if (vBar != null) {
            vBar.addListener(SWT.Selection, e -> {
                offsetY = maxSelY / 2 + SCROLL_BLANK - vBar.getSelection();
                canvas.redraw();
            });
        }
    }

    private void updateScrollBars(int availWidth, int availHeight, int pw, int ph) {
        ScrollBar hBar = canvas.getHorizontalBar();
        ScrollBar vBar = canvas.getVerticalBar();
        if (hBar == null || vBar == null) {
            return;
        }
        // 可视区（扣除标尺条）
        int viewW = availWidth - RULER_SIZE;
        int viewH = availHeight - RULER_SIZE;
        maxSelX = Math.max(0, pw - viewW);
        maxSelY = Math.max(0, ph - viewH);

        // maximum 为内容总尺寸（含两端空白），thumb 为可视尺寸
        hBar.setMaximum(Math.max(pw + 2 * SCROLL_BLANK, viewW));
        hBar.setThumb(Math.max(1, viewW));
        vBar.setMaximum(Math.max(ph + 2 * SCROLL_BLANK, viewH));
        vBar.setThumb(Math.max(1, viewH));

        // 滚动条位置与平移偏移同步：offsetX = maxSelX/2 + blank - selection
        int totalRangeX = maxSelX + 2 * SCROLL_BLANK;
        int totalRangeY = maxSelY + 2 * SCROLL_BLANK;
        int selX = Math.min(Math.max(0, maxSelX / 2 + SCROLL_BLANK - offsetX), totalRangeX);
        int selY = Math.min(Math.max(0, maxSelY / 2 + SCROLL_BLANK - offsetY), totalRangeY);
        if (hBar.getSelection() != selX) {
            hBar.setSelection(selX);
        }
        if (vBar.getSelection() != selY) {
            vBar.setSelection(selY);
        }
    }

    private double computeScale(int availWidth, int availHeight, double[] size) {
        if (fitWindow) {
            double s = Math.min((availWidth - 40) / size[0], (availHeight - 40) / size[1]);
            return s <= 0 ? 1 : s;
        }
        return zoomFactor <= 0 ? 1 : zoomFactor;
    }

    // ================= 拖动 =================

    private void installDragEvents() {
        canvas.addListener(SWT.MouseDown, e -> {
            if (dragMode && e.button == 1) {
                dragging = true;
                dragStartX = e.x;
                dragStartY = e.y;
                offsetStartX = offsetX;
                offsetStartY = offsetY;
            }
        });
        canvas.addListener(SWT.MouseMove, e -> {
            if (dragging) {
                offsetX = offsetStartX + (e.x - dragStartX);
                offsetY = offsetStartY + (e.y - dragStartY);
                canvas.redraw();
            }
        });
        canvas.addListener(SWT.MouseUp, e -> dragging = false);
    }

    // ================= 缩放 =================

    /**
     * 当前实际显示比例：适应窗口时取窗口内的真实缩放比，否则为固定缩放因子
     */
    private double currentScale() {
        double[] size = effectivePaperSize();
        if (fitWindow) {
            if (canvas == null || canvas.isDisposed()) {
                return zoomFactor > 0 ? zoomFactor : 1;
            }
            Rectangle ca = canvas.getClientArea();
            int wArea = Math.max(1, ca.width - RULER_SIZE);
            int hArea = Math.max(1, ca.height - RULER_SIZE);
            return computeScale(wArea, hArea, size);
        }
        return zoomFactor > 0 ? zoomFactor : 1;
    }

    private void zoomIn() {
        if (fitWindow) {
            fitWindow = false;
            zoomFactor = currentScale();
        }
        zoomFactor *= 1.25;
        syncZoomCombo();
        canvas.redraw();
    }

    private void zoomOut() {
        if (fitWindow) {
            fitWindow = false;
            zoomFactor = currentScale();
        }
        zoomFactor *= 0.75;
        syncZoomCombo();
        canvas.redraw();
    }

    private void applyZoomSelection(String sel) {
        if ("适应窗口".equals(sel)) {
            fitWindow = true;
            canvas.redraw();
            return;
        }
        fitWindow = false;
        int pct = 100;
        try {
            pct = Integer.parseInt(sel.replace("%", ""));
        } catch (NumberFormatException ignored) {
        }
        zoomFactor = pct / 100.0;
        canvas.redraw();
    }

    private void syncZoomCombo() {
        if (zoomCombo == null || zoomCombo.isDisposed()) {
            return;
        }
        if (fitWindow) {
            zoomCombo.select(0);
            return;
        }
        int pct = (int) Math.round(zoomFactor * 100);
        String target = pct + "%";
        String[] items = zoomCombo.getItems();
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(target)) {
                zoomCombo.select(i);
                return;
            }
        }
        // 下拉无对应项时动态加入并选中，保证下拉始终反映当前比例
        zoomCombo.add(target);
        zoomCombo.select(zoomCombo.getItemCount() - 1);
    }

    // ================= 翻页 =================

    private int pageCount() {
        return pageImagesAwt.size();
    }

    private void goPage(int page) {
        if (page < 0) {
            page = 0;
        }
        if (page >= pageCount()) {
            page = Math.max(0, pageCount() - 1);
        }
        currentPage = page;
        updatePageLabel();
        canvas.redraw();
    }

    private void jumpToPage(String text) {
        String s = text.trim();
        String num = s;
        int slash = s.indexOf('/');
        if (slash >= 0) {
            num = s.substring(0, slash);
        }
        try {
            int p = Integer.parseInt(num.trim()) - 1;
            goPage(p);
        } catch (NumberFormatException ignored) {
            updatePageLabel();
        }
    }

    private void updatePageLabel() {
        if (pageText == null || pageText.isDisposed()) {
            return;
        }
        if (rendering) {
            pageText.setText("渲染中...");
            return;
        }
        int total = pageCount();
        pageText.setText((currentPage + 1) + "/" + (total <= 0 ? 1 : total));
    }

    // ================= 工具功能 =================

    private JSONObject currentStyle() {
        JSONObject styleJson = new JSONObject();
        JSONObject margin = new JSONObject();
        margin.put("top", marginTop / CM_TO_POINT);
        margin.put("right", marginRight / CM_TO_POINT);
        margin.put("bottom", marginBottom / CM_TO_POINT);
        margin.put("left", marginLeft / CM_TO_POINT);
        styleJson.put("margin", margin);
        styleJson.put("zoom", zoomValue);
        styleJson.put("direction", direction == null ? "vertical" : direction);
        styleJson.put("paperHeader", header == null ? "" : header);
        styleJson.put("paperFooter", footer == null ? "" : footer);
        styleJson.put("fontFamily", fontFamily == null ? "宋体" : fontFamily);
        styleJson.put("fontSize", fontSize);
        styleJson.put("paper", paper);
        return styleJson;
    }

    private void doPrint() {
        try {
            String printer = printerName == null || printerName.isEmpty() ? "默认打印机" : printerName;
            if (server != null) {
                // 提交到 WebSocket 服务：同步到任务列表并异步执行，完成后自动清除
                server.doPrint(printer, currentStyle(), content);
            } else {
                // 无服务引用时回退为同步打印
                PrintTask task = new PrintTask("打印预览", taskName(), printer, paper);
                PrintTaskExecutor.execute(task, currentStyle(), content);
            }
            showInfo("打印任务已提交");
        } catch (Exception ex) {
            log.error("打印失败", ex);
            showError("打印失败: " + ex.getMessage());
        }
    }

    /** 内容摘要（用于任务名） */
    private String taskName() {
        if (content == null || content.getString("value") == null) {
            return "打印任务";
        }
        String s = content.getString("value").replaceAll("(?s)<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) {
            return "打印任务";
        }
        return s.length() > 20 ? s.substring(0, 20) + "..." : s;
    }

    private void doPrinterSetup() {
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.printDialog();
        } catch (Exception ex) {
            log.error("打开打印机设置失败", ex);
        }
    }

    private void openPageSetup() {
        Shell dlg = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        dlg.setText("页面设置");
        dlg.setLayout(new GridLayout(2, false));
        dlg.setSize(360, 400);

        Label paperLabel = new Label(dlg, SWT.NONE);
        paperLabel.setText("纸张:");
        Combo paperCombo = new Combo(dlg, SWT.READ_ONLY);
        paperCombo.setItems(PAPER_OPTIONS);
        int idx = indexOf(PAPER_OPTIONS, paper);
        paperCombo.select(idx < 0 ? 0 : idx);
        paperCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label dirLabel = new Label(dlg, SWT.NONE);
        dirLabel.setText("方向:");
        Combo dirCombo = new Combo(dlg, SWT.READ_ONLY);
        dirCombo.setItems(new String[]{"纵向", "横向"});
        dirCombo.select("horizontal".equalsIgnoreCase(direction) ? 1 : 0);
        dirCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label fontLabel = new Label(dlg, SWT.NONE);
        fontLabel.setText("字体:");
        Combo fontCombo = new Combo(dlg, SWT.READ_ONLY);
        fontCombo.setItems(new String[]{"宋体", "黑体", "楷体", "仿宋", "微软雅黑"});
        fontCombo.select(indexOf(new String[]{"宋体", "黑体", "楷体", "仿宋", "微软雅黑"},
                fontFamily == null ? "宋体" : fontFamily));
        fontCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label sizeLabel = new Label(dlg, SWT.NONE);
        sizeLabel.setText("字号:");
        Spinner sizeSpinner = new Spinner(dlg, SWT.BORDER);
        sizeSpinner.setMinimum(8);
        sizeSpinner.setMaximum(72);
        sizeSpinner.setSelection(fontSize);
        sizeSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // 边距（cm）
        String[] marginNames = {"上边距(cm)", "右边距(cm)", "下边距(cm)", "左边距(cm)"};
        double[] marginVals = {marginTop, marginRight, marginBottom, marginLeft};
        Spinner[] marginSpinners = new Spinner[4];
        for (int i = 0; i < 4; i++) {
            Label l = new Label(dlg, SWT.NONE);
            l.setText(marginNames[i]);
            Spinner sp = new Spinner(dlg, SWT.BORDER);
            sp.setMinimum(0);
            sp.setMaximum(100);
            sp.setDigits(1);
            sp.setIncrement(1);
            sp.setSelection((int) Math.round(marginVals[i] / CM_TO_POINT * 10));
            sp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            marginSpinners[i] = sp;
        }

        Composite btns = new Composite(dlg, SWT.NONE);
        btns.setLayout(new GridLayout(2, true));
        btns.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        Button ok = new Button(btns, SWT.PUSH);
        ok.setText("确定");
        ok.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button cancel = new Button(btns, SWT.PUSH);
        cancel.setText("取消");
        cancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        ok.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                paper = paperCombo.getText();
                direction = dirCombo.getSelectionIndex() == 1 ? "horizontal" : "vertical";
                fontFamily = fontCombo.getText();
                fontSize = sizeSpinner.getSelection();
                marginTop = marginSpinners[0].getSelection() / 10.0 * CM_TO_POINT;
                marginRight = marginSpinners[1].getSelection() / 10.0 * CM_TO_POINT;
                marginBottom = marginSpinners[2].getSelection() / 10.0 * CM_TO_POINT;
                marginLeft = marginSpinners[3].getSelection() / 10.0 * CM_TO_POINT;
                reRender();
                dlg.close();
            }
        });
        cancel.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                dlg.close();
            }
        });

        dlg.open();
    }

    private int indexOf(String[] arr, String value) {
        if (value == null) {
            return -1;
        }
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equalsIgnoreCase(value)) {
                return i;
            }
        }
        return -1;
    }

    private void exportImage() {
        if (rendering) {
            showError("正在渲染，请稍候");
            return;
        }
        if (currentPage < 0 || currentPage >= pageImagesAwt.size()) {
            showError("没有可导出的页面");
            return;
        }
        FileDialog fd = new FileDialog(shell, SWT.SAVE);
        fd.setText("导出图片");
        fd.setFilterNames(new String[]{"PNG 图片", "JPG 图片"});
        fd.setFilterExtensions(new String[]{"*.png", "*.jpg"});
        String path = fd.open();
        if (path == null) {
            return;
        }
        try {
            BufferedImage img = pageImagesAwt.get(currentPage);
            String fmt = path.toLowerCase().endsWith(".jpg") ? "jpg" : "png";
            ImageIO.write(img, fmt, new File(path));
            log.info("预览已导出图片: {}", path);
            showInfo("图片已导出: " + path);
        } catch (Exception ex) {
            log.error("导出图片失败", ex);
            showError("导出图片失败: " + ex.getMessage());
        }
    }

    private void exportPdf() {
        if (rendering) {
            showError("正在渲染，请稍候");
            return;
        }
        if (renderedDoc == null) {
            showError("没有可导出的 PDF");
            return;
        }
        FileDialog fd = new FileDialog(shell, SWT.SAVE);
        fd.setText("导出 PDF");
        fd.setFilterNames(new String[]{"PDF 文件"});
        fd.setFilterExtensions(new String[]{"*.pdf"});
        String path = fd.open();
        if (path == null) {
            return;
        }
        if (!path.toLowerCase().endsWith(".pdf")) {
            path += ".pdf";
        }
        try {
            renderedDoc.save(path);
            log.info("预览已导出 PDF: {}", path);
            showInfo("PDF 已导出: " + path);
        } catch (Exception ex) {
            log.error("导出 PDF 失败", ex);
            showError("导出 PDF 失败: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        org.eclipse.swt.widgets.MessageBox box =
                new org.eclipse.swt.widgets.MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        box.setText("错误");
        box.setMessage(message);
        box.open();
    }

    private void showInfo(String message) {
        org.eclipse.swt.widgets.MessageBox box =
                new org.eclipse.swt.widgets.MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        box.setText("提示");
        box.setMessage(message);
        box.open();
    }
}
