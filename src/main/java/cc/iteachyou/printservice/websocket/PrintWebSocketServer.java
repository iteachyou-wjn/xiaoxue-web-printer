package cc.iteachyou.printservice.websocket;

import cc.iteachyou.printservice.print.PrintTaskExecutor;
import cc.iteachyou.printservice.print.bartender.BartenderManager;
import cc.iteachyou.printservice.ui.PreviewDialog;
import cc.iteachyou.printservice.util.LicenseManager;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import org.eclipse.swt.widgets.Display;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 打印 WebSocket 服务
 *
 * 仅监听 127.0.0.1，接收本机客户端的打印任务请求。
 *
 * 支持的消息类型（JSON）：
 * - list: 查询任务列表
 *   { "type": "list" }
 * - status: 查询单个任务状态
 *   { "type": "status", "taskId": "任务ID" }
 * - cancel: 取消任务
 *   { "type": "cancel", "taskId": "任务ID" }
 * - clear: 清除已完成/失败的任务
 *   { "type": "clear" }
 * - printers: 查询可用打印机列表
 *   { "type": "printers" }  → 响应 { "type": "printers_result", "printers": ["打印机A", ...] }
 * - pageSize: 查询指定打印机可用的纸张类型
 *   { "type": "pageSize", "printerName": "打印机A" }
 *     → 响应 { "type": "pageSize_result", "printerName": "打印机A", "sizes": ["A4", "A3", ...] }
 * - doPrint: 提交并执行打印（创建任务 → 渲染 PDF → 打印 → 完成后清除）
 *   { "type": "doPrint", "printer": "打印机A", "style": {...}, "content": {"type": "text|html", "value": "..."} }
 *     → 响应 { "type": "doPrint_result", "success": true, "taskId": "...", "message": "打印任务已提交" }
 * - doPreview: 打开打印预览窗体（不打印，仅展示）
 *   { "type": "doPreview", "printer": "打印机A", "style": {...}, "content": {"type": "text|html", "value": "..."} }
 *     → 响应 { "type": "doPreview_result", "success": true, "message": "打印预览已打开" }
 *
 * JSON 处理使用 Fastjson2。
 */
public class PrintWebSocketServer extends WebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(PrintWebSocketServer.class);

    /** 用于打开预览窗体的 Display（由 MainApp 注入） */
    private Display display;

    /**
     * 所有打印任务（线程安全列表，供 UI 轮询读取）
     * CopyOnWriteArrayList 保证 WebSocket 线程写入和 UI 线程读取并发安全
     */
    private final List<PrintTask> taskList = new CopyOnWriteArrayList<>();

    /** 已连接的客户端映射：connection -> 客户端标识 */
    private final Map<WebSocket, String> clients = new ConcurrentHashMap<>();

    /** 已打开的打印预览窗体：预览ID(MD5) -> 窗体实例（重复预览时激活已有窗体） */
    private final Map<String, PreviewDialog> previewWindows = new ConcurrentHashMap<>();

    public PrintWebSocketServer(int port) {
        this("127.0.0.1", port);
    }

    public PrintWebSocketServer(String host, int port) {
        super(new InetSocketAddress(host, port));
        // 设置连接丢失超时（毫秒）
        setConnectionLostTimeout(30);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String clientId = conn.getRemoteSocketAddress().toString();
        clients.put(conn, clientId);
        log.info("客户端连接: {}，当前连接数: {}", clientId, clients.size());

        // 发送欢迎消息
        JSONObject welcome = new JSONObject();
        welcome.put("type", "welcome");
        welcome.put("message", "已连接到打印控件 WebSocket 服务");
        welcome.put("port", getPort());
        conn.send(JSON.toJSONString(welcome));
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String clientId = clients.remove(conn);
        log.info("客户端断开: {}，原因: {}，当前连接数: {}", clientId, reason, clients.size());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        log.debug("收到消息: {}", message);

        try {
            JSONObject request = JSON.parseObject(message);
            if (request == null || !request.containsKey("type")) {
                sendError(conn, "无效的消息格式，缺少 type 字段");
                return;
            }

            String type = request.getString("type");
            switch (type) {
                case "list" -> handleList(conn);
                case "status" -> handleStatus(conn, request);
                case "cancel" -> handleCancel(conn, request);
                case "clear" -> handleClear(conn);
                case "printers" -> handlePrinters(conn);
                case "pageSize" -> handlePageSize(conn, request);
                case "doPrint" -> handleDoPrint(conn, request);
                case "doPreview" -> handleDoPreview(conn, request);
                case "bartenderInstance" -> handleBartenderInstance(conn);
                case "bartenderTemplateParams" -> handleBartenderTemplateParams(conn, request);
                case "bartenderTemplateImage" -> handleBartenderTemplateImage(conn, request, false);
                case "bartenderTemplateImageWithParams" -> handleBartenderTemplateImage(conn, request, true);
                case "submit" -> sendError(conn, "submit 已移除，请使用 doPrint");
                default -> sendError(conn, "未知的消息类型: " + type);
            }
        } catch (JSONException e) {
            log.error("JSON 解析失败: {}", message, e);
            sendError(conn, "JSON 格式错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("处理消息时发生异常", e);
            sendError(conn, "服务器内部错误: " + e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log.error("WebSocket 错误", ex);
        if (conn != null) {
            sendError(conn, "连接错误: " + ex.getMessage());
        }
    }

    @Override
    public void onStart() {
        log.info("打印 WebSocket 服务已启动，监听 {}:{}", getAddress().getHostString(), getPort());
    }

    // ========== 消息处理方法 ==========

    /**
     * 处理查询任务列表
     */
    private void handleList(WebSocket conn) {
        JSONObject response = new JSONObject();
        response.put("type", "task_list");
        response.put("tasks", taskList);
        conn.send(JSON.toJSONString(response));
    }

    /**
     * 处理查询单个任务状态
     */
    private void handleStatus(WebSocket conn, JSONObject request) {
        if (!request.containsKey("taskId")) {
            sendError(conn, "缺少 taskId 字段");
            return;
        }
        String taskId = request.getString("taskId");
        PrintTask task = findTaskById(taskId);

        JSONObject response = new JSONObject();
        response.put("type", "task_status");
        if (task != null) {
            response.put("task", task);
        } else {
            response.put("found", false);
            response.put("message", "未找到任务: " + taskId);
        }
        conn.send(JSON.toJSONString(response));
    }

    /**
     * 处理取消任务
     */
    private void handleCancel(WebSocket conn, JSONObject request) {
        if (!request.containsKey("taskId")) {
            sendError(conn, "缺少 taskId 字段");
            return;
        }
        String taskId = request.getString("taskId");
        PrintTask task = findTaskById(taskId);

        JSONObject response = new JSONObject();
        response.put("type", "cancel_result");

        if (task != null && task.getStatus() == PrintTask.Status.PENDING) {
            task.setStatus(PrintTask.Status.FAILED);
            task.setErrorMessage("用户取消");
            response.put("success", true);
            response.put("message", "任务已取消");
            log.info("任务已取消: {}", taskId);
        } else {
            response.put("success", false);
            response.put("message", task == null ? "未找到任务" : "当前状态不可取消");
        }
        conn.send(JSON.toJSONString(response));
        broadcastTaskList();
    }

    /**
     * 处理清除已完成/失败任务
     */
    private void handleClear(WebSocket conn) {
        taskList.removeIf(t ->
                t.getStatus() == PrintTask.Status.COMPLETED ||
                t.getStatus() == PrintTask.Status.FAILED);

        JSONObject response = new JSONObject();
        response.put("type", "clear_result");
        response.put("success", true);
        response.put("message", "已清除已完成和失败的任务");
        conn.send(JSON.toJSONString(response));
        broadcastTaskList();
    }

    /**
     * 处理 doPrint：提交并执行打印任务
     *
     * 流程：在任务列表创建任务 → 异步调用打印机执行打印 → 打印完成后清除任务
     */
    private void handleDoPrint(WebSocket conn, JSONObject request) {
        String printer = request.containsKey("printer") ? request.getString("printer") : "默认打印机";
        JSONObject content = request.getJSONObject("content");
        JSONObject style = request.containsKey("style") ? request.getJSONObject("style") : null;

        try {
            enforceLicense(style, content);
        } catch (IllegalArgumentException e) {
            sendError(conn, e.getMessage());
            return;
        }

        String taskId = doPrint(printer, style, content);

        // 返回提交结果
        JSONObject response = new JSONObject();
        response.put("type", "doPrint_result");
        response.put("success", true);
        response.put("taskId", taskId);
        response.put("message", "打印任务已提交");
        conn.send(JSON.toJSONString(response));
    }

    /**
     * 提交并执行打印（WebSocket doPrint 与预览窗体打印共用）。
     *
     * 流程：在任务列表创建任务并广播 → 异步执行打印（状态置为打印中并广播）→
     * 打印完成/失败后清除任务并广播，保证任务列表窗口与前端实时同步。
     *
     * @param printer 打印机名称
     * @param style   打印样式
     * @param content 打印内容（type: text|html, value: 内容）
     * @return 任务 ID
     */
    public String doPrint(String printer, JSONObject style, JSONObject content) {
        enforceLicense(style, content);
        String value = (content != null && content.getString("value") != null)
                ? content.getString("value") : "";
        PrintTask task = new PrintTask(makeTaskName(value), value, printer);
        task.setPageSize(style != null ? style.getString("paper") : "");

        // 1. 创建任务
        taskList.add(task);
        log.info("已创建打印任务 {}", task.getId());
        broadcastTaskList();

        // 2. 异步执行打印
        new Thread(() -> {
            try {
                task.setStatus(PrintTask.Status.PRINTING);
                broadcastTaskList();
                PrintTaskExecutor.execute(task, style, content);
                task.setStatus(PrintTask.Status.COMPLETED);
                log.info("打印完成，清除任务 {}", task.getId());
            } catch (Throwable t) {
                log.error("打印失败，清除任务 {}", task.getId(), t);
                task.setErrorMessage(t.getMessage() == null ? "打印失败" : t.getMessage());
                task.setStatus(PrintTask.Status.FAILED);
            } finally {
                // 3. 打印完成后清除任务
                taskList.remove(task);
                broadcastTaskList();
            }
        }, "do-print-" + task.getId()).start();

        return task.getId();
    }

    /**
     * 根据打印内容生成任务名称（取前 20 个字符）
     */
    private String makeTaskName(String value) {
        String s = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) {
            return "打印任务";
        }
        return s.length() > 20 ? s.substring(0, 20) + "..." : s;
    }

    /**
     * 注入 Display（用于在 UI 线程打开打印预览窗体）
     */
    public void setDisplay(Display display) {
        this.display = display;
    }

    /**
     * 处理 doPreview：打开打印预览窗体（不执行打印）
     *
     * 用当前指令中的 printer/style/content 做 MD5 生成预览 ID：
     * 相同内容的重复预览不重复创建窗体，直接激活（置前）已有窗体。
     */
    private void handleDoPreview(WebSocket conn, JSONObject request) {
        String printer = request.containsKey("printer") ? request.getString("printer") : "默认打印机";
        JSONObject content = request.getJSONObject("content");
        JSONObject style = request.containsKey("style") ? request.getJSONObject("style") : null;

        if (display == null || display.isDisposed()) {
            sendError(conn, "打印控件 UI 不可用，无法打开预览");
            return;
        }

        try {
            enforceLicense(style, content);
        } catch (IllegalArgumentException e) {
            sendError(conn, e.getMessage());
            return;
        }

        String previewId = computePreviewId(request);
        PreviewDialog existing = previewWindows.get(previewId);
        if (existing != null && !existing.isDisposed()) {
            // 已存在相同预览：激活原有窗体
            existing.activate();
            log.info("doPreview: 相同预览已存在，激活窗体 [{}]", previewId);
        } else {
            previewWindows.remove(previewId);
            // 在 Display 线程打开预览窗体（传入 server 引用，预览窗打印时同步到任务列表）
            PreviewDialog.show(display, this, previewId, printer, style, content);
            log.info("doPreview: 已创建预览窗体 [{}]，打印机 [{}]", previewId, printer);
        }

        JSONObject response = new JSONObject();
        response.put("type", "doPreview_result");
        response.put("success", true);
        response.put("previewId", previewId);
        response.put("message", "打印预览已打开");
        conn.send(JSON.toJSONString(response));
    }

    /**
     * 授权策略：
     * <ul>
     *   <li>未授权时 BarTender 功能不可用，抛异常拒绝；</li>
     *   <li>未授权时 text/html 打印强制使用试用版页头页尾；</li>
     *   <li>已授权时页头页尾保持用户设置（不设置时默认为空）。</li>
     * </ul>
     */
    private void enforceLicense(JSONObject style, JSONObject content) {
        String ctype = content != null ? content.getString("type") : null;
        if ("bartender".equalsIgnoreCase(ctype)) {
            if (!LicenseManager.isAuthorized()) {
                throw new IllegalArgumentException("未授权，BarTender 功能不可用，请联系作者购买授权");
            }
            return;
        }
        // text / html：未授权时强制试用版页头页尾
        if (!LicenseManager.isAuthorized() && style != null) {
            style.put("paperHeader", "梦想家WEB打印控件试用版");
            style.put("paperFooter", "梦想家WEB打印控件试用版");
        }
    }

    /**
     * BarTender 功能授权检查；未授权时发送错误并返回 false
     */
    private boolean requireBartender(WebSocket conn) {
        if (!LicenseManager.isAuthorized()) {
            sendError(conn, "未授权，BarTender 功能不可用，请联系作者购买授权");
            return false;
        }
        return true;
    }

    /**
     * 用指令中的预览内容字段（printer / style / content）做 MD5 生成预览 ID。
     * 固定字段插入顺序，相同内容重复点击得到相同 ID。
     */
    private String computePreviewId(JSONObject request) {
        JSONObject payload = new JSONObject();
        payload.put("printer", request.getString("printer"));
        payload.put("style", request.getJSONObject("style"));
        payload.put("content", request.getJSONObject("content"));
        String text = payload.toJSONString();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    /**
     * 注册预览窗体（PreviewDialog 打开时调用）
     */
    public void registerPreview(String previewId, PreviewDialog dialog) {
        if (previewId != null && dialog != null) {
            previewWindows.put(previewId, dialog);
        }
    }

    /**
     * 注销预览窗体（PreviewDialog 关闭时调用）
     */
    public void unregisterPreview(String previewId, PreviewDialog dialog) {
        if (previewId != null && dialog != null) {
            previewWindows.remove(previewId, dialog);
        }
    }

    /**
     * 处理查询可用打印机列表
     */
    private void handlePrinters(WebSocket conn) {
        List<String> printers = getAvailablePrinters();

        JSONObject response = new JSONObject();
        response.put("type", "printers_result");
        response.put("printers", printers);
        conn.send(JSON.toJSONString(response));

        log.info("返回打印机列表: {}", printers);
    }

    /**
     * 枚举系统已安装的可用打印机
     * 使用 JDK 内置的 javax.print.PrintServiceLookup，无需额外依赖。
     */
    private List<String> getAvailablePrinters() {
        List<String> printers = new ArrayList<>();
        try {
            javax.print.PrintService[] services =
                    javax.print.PrintServiceLookup.lookupPrintServices(null, null);
            if (services != null) {
                for (javax.print.PrintService service : services) {
                    String name = service.getName();
                    if (name != null && !name.trim().isEmpty()) {
                        printers.add(name.trim());
                    }
                }
            }
        } catch (Throwable t) {
            log.warn("获取打印机列表失败，使用默认打印机兜底", t);
        }

        // 兜底：没有检测到打印机时返回默认打印机
        if (printers.isEmpty()) {
            printers.add("默认打印机");
        }
        return printers;
    }

    /**
     * 处理查询指定打印机可用的纸张类型
     */
    private void handlePageSize(WebSocket conn, JSONObject request) {
        String printerName = request.containsKey("printerName")
                ? request.getString("printerName")
                : "";
        if (printerName == null || printerName.trim().isEmpty()) {
            sendError(conn, "缺少 printerName 字段");
            return;
        }

        List<String> sizes = getSupportedPaperSizes(printerName.trim());

        JSONObject response = new JSONObject();
        response.put("type", "pageSize_result");
        response.put("printerName", printerName.trim());
        response.put("sizes", sizes);
        conn.send(JSON.toJSONString(response));

        log.info("返回打印机 [{}] 可用纸张: {}", printerName, sizes);
    }

    /**
     * 枚举指定打印机支持的纸张类型
     */
    private List<String> getSupportedPaperSizes(String printerName) {
        List<String> sizes = new ArrayList<>();
        try {
            javax.print.PrintService[] services =
                    javax.print.PrintServiceLookup.lookupPrintServices(null, null);
            javax.print.PrintService target = null;
            if (services != null) {
                for (javax.print.PrintService service : services) {
                    if (service.getName().equalsIgnoreCase(printerName)) {
                        target = service;
                        break;
                    }
                }
            }
            if (target == null) {
                return sizes;
            }

            javax.print.attribute.standard.Media[] medias =
                    (javax.print.attribute.standard.Media[]) target.getSupportedAttributeValues(
                            javax.print.attribute.standard.Media.class,
                            javax.print.DocFlavor.SERVICE_FORMATTED.PRINTABLE,
                            null);
            if (medias != null) {
                for (javax.print.attribute.standard.Media media : medias) {
                    if (media instanceof javax.print.attribute.standard.MediaSizeName msn) {
                        String friendly = toFriendlySizeName(msn.toString());
                        if (friendly != null && !friendly.isEmpty() && !sizes.contains(friendly)) {
                            sizes.add(friendly);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            log.warn("获取打印机 [{}] 纸张列表失败", printerName, t);
        }
        return sizes;
    }

    /**
     * 将 javax.print 内部纸张名称转换为用户友好的名称（如 iso-a4 → A4）
     * 兼容连字符与下划线两种内部命名风格
     */
    private String toFriendlySizeName(String name) {
        if (name == null) {
            return null;
        }
        String key = name.replace('-', '_');
        switch (key) {
            case "iso_a0": return "A0";
            case "iso_a1": return "A1";
            case "iso_a2": return "A2";
            case "iso_a3": return "A3";
            case "iso_a4": return "A4";
            case "iso_a5": return "A5";
            case "iso_a6": return "A6";
            case "iso_a7": return "A7";
            case "iso_a8": return "A8";
            case "iso_b4": return "B4";
            case "iso_b5": return "B5";
            case "iso_b6": return "B6";
            case "iso_c4": return "C4";
            case "iso_c5": return "C5";
            case "jis_b4": return "B4 (JIS)";
            case "jis_b5": return "B5 (JIS)";
            case "na_letter": return "Letter";
            case "na_legal": return "Legal";
            case "na_ledger": return "Ledger";
            case "na_executive": return "Executive";
            case "na_index_3x5": return "Index 3x5";
            case "na_index_4x6": return "Index 4x6";
            case "na_number_10_envelope": return "Envelope #10";
            case "om_dl_envelope": return "DL Envelope";
            case "iso_dl_envelope": return "DL Envelope";
            case "iso_c6_envelope": return "C6 Envelope";
            case "iso_b5_envelope": return "B5 Envelope";
            default: return name;
        }
    }

    // ========== BarTender 相关 ==========

    /**
     * 处理获取 BarTender 实例信息（版本号等）
     * { "type": "bartenderInstance" }
     *   → { "type": "bartenderInstance_result", success, available, version, fullVersion, message }
     */
    private void handleBartenderInstance(WebSocket conn) {
        if (!requireBartender(conn)) {
            return;
        }
        JSONObject response = new JSONObject();
        response.put("type", "bartenderInstance_result");
        boolean available = BartenderManager.isAvailable();
        response.put("success", available);
        response.put("available", available);
        response.put("version", BartenderManager.getVersion());
        response.put("fullVersion", BartenderManager.getFullVersion());
        if (!available) {
            response.put("message", "BarTender 未安装或不可用");
        }
        conn.send(JSON.toJSONString(response));
    }

    /**
     * 处理获取 BarTender 模板参数
     * { "type": "bartenderTemplateParams", "templatePath": "..." }
     *   → { "type": "bartenderTemplateParams_result", success, templatePath, params: [...] }
     */
    private void handleBartenderTemplateParams(WebSocket conn, JSONObject request) {
        if (!requireBartender(conn)) {
            return;
        }
        String templatePath = request.getString("templatePath");
        JSONObject response = new JSONObject();
        response.put("type", "bartenderTemplateParams_result");
        response.put("templatePath", templatePath);
        if (templatePath == null || templatePath.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "缺少 templatePath 字段");
        } else {
            List<Map<String, String>> params = BartenderManager.getTemplateParameters(templatePath.trim());
            response.put("success", true);
            response.put("params", params);
        }
        conn.send(JSON.toJSONString(response));
    }

    /**
     * 处理获取 BarTender 模板图像（可选带参数），图片以 base64 返回
     * { "type": "bartenderTemplateImage", "templatePath": "...", "dpi": 300 }
     * { "type": "bartenderTemplateImageWithParams", "templatePath": "...", "params": {...}, "dpi": 300 }
     */
    private void handleBartenderTemplateImage(WebSocket conn, JSONObject request, boolean withParams) {
        if (!requireBartender(conn)) {
            return;
        }
        String respType = withParams
                ? "bartenderTemplateImageWithParams_result"
                : "bartenderTemplateImage_result";
        String templatePath = request.getString("templatePath");
        JSONObject response = new JSONObject();
        response.put("type", respType);
        response.put("templatePath", templatePath);
        if (templatePath == null || templatePath.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "缺少 templatePath 字段");
            conn.send(JSON.toJSONString(response));
            return;
        }
        int dpi = request.getIntValue("dpi");
        if (dpi <= 0) {
            dpi = 300;
        }
        Map<String, String> params = null;
        if (withParams) {
            params = new HashMap<>();
            JSONObject p = request.getJSONObject("params");
            if (p != null) {
                for (String key : p.keySet()) {
                    Object v = p.get(key);
                    params.put(key, v == null ? "" : String.valueOf(v));
                }
            }
        }
        byte[] png = BartenderManager.preview(templatePath.trim(), params, dpi, null);
        if (png == null) {
            response.put("success", false);
            response.put("message", "BarTender 预览渲染失败（请确认已安装 BarTender 且模板有效）");
        } else {
            response.put("success", true);
            response.put("image", Base64.getEncoder().encodeToString(png));
        }
        conn.send(JSON.toJSONString(response));
    }

    // ========== 辅助方法 ==========

    /**
     * 根据 ID 查找任务
     */
    private PrintTask findTaskById(String taskId) {
        for (PrintTask task : taskList) {
            if (task.getId().equals(taskId)) {
                return task;
            }
        }
        return null;
    }

    /**
     * 广播任务列表给所有已连接客户端
     */
    private void broadcastTaskList() {
        JSONObject broadcast = new JSONObject();
        broadcast.put("type", "task_list_update");
        broadcast.put("tasks", taskList);
        String message = JSON.toJSONString(broadcast);

        for (WebSocket client : clients.keySet()) {
            if (client.isOpen()) {
                client.send(message);
            }
        }
    }

    /**
     * 发送错误消息
     */
    private void sendError(WebSocket conn, String errorMsg) {
        JSONObject error = new JSONObject();
        error.put("type", "error");
        error.put("message", errorMsg);
        conn.send(JSON.toJSONString(error));
    }

    /**
     * 获取任务列表（线程安全，供 UI 轮询读取）
     */
    public List<PrintTask> getTaskList() {
        return taskList;
    }

    /**
     * 获取当前连接的客户端数量
     */
    public int getClientCount() {
        return clients.size();
    }
}
