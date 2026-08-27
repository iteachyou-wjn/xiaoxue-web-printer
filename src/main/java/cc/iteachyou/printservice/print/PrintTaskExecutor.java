package cc.iteachyou.printservice.print;

import cc.iteachyou.printservice.print.bartender.BartenderManager;
import cc.iteachyou.printservice.websocket.PrintTask;
import com.alibaba.fastjson2.JSONObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.print.PrinterJob;
import java.util.HashMap;
import java.util.Map;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;

/**
 * 打印任务执行器（方案 B：OpenHTMLtoPDF 渲染 + PDFPageable 打印）
 *
 * 流程：
 * 1. 调用 {@link HtmlRenderService#render} 把 text/html 内容按打印样式
 *    （纸张/方向/边距/字体/字号/缩放/页眉页脚）渲染为 PDF（原生 CSS 分页）；
 * 2. 使用 JDK 内置 AWT 打印 API 以 {@link PDFPageable} 打印该 PDF，
 *    页面尺寸、方向、边距由 PDF 页面决定，打印与预览所见即所得；
 * 3. 支持指定打印机（未指定或"默认打印机"时使用系统默认）。
 *
 * text / html 两种内容类型统一走渲染链路：
 * - text：HTML 转义后包裹，应用 body 字体/字号/缩放；
 * - html：保留内联 style，非 base64 图片预下载内嵌。
 */
public class PrintTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(PrintTaskExecutor.class);

    private PrintTaskExecutor() {
    }

    /**
     * 执行打印
     *
     * @param task    打印任务（含打印机名称、内容）
     * @param style   打印样式（margin / zoom / direction / paperHeader / paperFooter / fontFamily / fontSize / paper）
     * @param content 内容对象（type: text|html, value: 打印内容）
     * @throws Exception 打印失败时抛出
     */
    public static void execute(PrintTask task, JSONObject style, JSONObject content) throws Exception {
        String contentType = content != null ? content.getString("type") : null;
        if ("bartender".equalsIgnoreCase(contentType)) {
            executeBartender(task, content);
            return;
        }
        try (PDDocument doc = HtmlRenderService.render(style, content)) {
            PrinterJob job = PrinterJob.getPrinterJob();

            String printerName = task.getPrinterName();
            PrintService service = findPrintService(printerName);
            if (service != null) {
                job.setPrintService(service);
                log.info("使用打印机: {}", service.getName());
            }

            job.setPageable(new PDFPageable(doc));
            job.print();

            log.info("打印完成，任务: {}", task.getId());
        }
    }

    /**
     * BarTender 类型打印：使用 {@link BartenderManager#print} 直接调用 BarTender 打印模板。
     *
     * @param task    打印任务（含打印机名称）
     * @param content 内容对象（type: bartender, value: 模板路径, params: 模板字段值）
     */
    private static void executeBartender(PrintTask task, JSONObject content) throws Exception {
        String template = content.getString("value");
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalArgumentException("BarTender 模板路径不能为空");
        }
        Map<String, String> params = extractParams(content);
        int result = BartenderManager.print(template, params, task.getPrinterName());
        if (result < 0) {
            throw new RuntimeException("BarTender 打印失败（请确认本机已安装 BarTender 且模板有效）");
        }
        log.info("BarTender 打印完成，任务: {}", task.getId());
    }

    /** 从 content.params 提取模板字段值 */
    private static Map<String, String> extractParams(JSONObject content) {
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

    private static PrintService findPrintService(String name) {
        if (name == null || name.trim().isEmpty() || "默认打印机".equals(name)) {
            return null; // 使用系统默认打印机
        }
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        if (services != null) {
            for (PrintService s : services) {
                if (s.getName().equalsIgnoreCase(name.trim())) {
                    return s;
                }
            }
        }
        return null;
    }
}
