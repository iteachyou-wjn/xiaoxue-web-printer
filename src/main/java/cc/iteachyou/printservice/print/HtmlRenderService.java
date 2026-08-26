package cc.iteachyou.printservice.print;

import com.alibaba.fastjson2.JSONObject;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 渲染服务（方案 B：OpenHTMLtoPDF 渲染引擎）
 *
 * 职责：
 * 1. 把 doPrint / doPreview 传入的 content（type: text|html）+ style 转换为完整 XHTML：
 *    - 非 base64 图片（http(s)/file/本地路径）预下载并内嵌为 data URI，渲染不依赖网络；
 *    - 注入 {@code @page}（纸张尺寸/边距/方向/页眉页脚）与 {@code body}（字体/字号/缩放）样式；
 *    - 保留 html 内容中已有的内联 style 与 &lt;style&gt; 块，保证 div 样式完整。
 * 2. 用 OpenHTMLtoPDF 渲染 XHTML 为 PDF（原生 CSS 分页、中文字体嵌入）。
 * 3. 提供页面转图像、字体注册等工具，供预览与打印共用。
 */
public final class HtmlRenderService {

    private static final Logger log = LoggerFactory.getLogger(HtmlRenderService.class);

    /** 厘米 -> 磅 */
    private static final double CM_TO_POINT = 72.0 / 2.54;

    /** 预览页渲染 DPI（2 倍，保证放大后清晰） */
    public static final int PREVIEW_DPI = 144;

    private static final Pattern STYLE_BLOCK = Pattern.compile("(?is)<style[^>]*>(.*?)</style>");

    private HtmlRenderService() {
    }

    // ================= 对外 API =================

    /**
     * 渲染打印内容为 PDF 文档（调用方负责关闭返回的 PDDocument）
     *
     * @param style   打印样式（paper / direction / margin / fontFamily / fontSize / zoom / paperHeader / paperFooter）
     * @param content 内容对象（type: text|html, value: 内容）
     */
    public static PDDocument render(JSONObject style, JSONObject content) throws Exception {
        String type = content != null ? content.getString("type") : "text";
        String raw = (content != null && content.getString("value") != null) ? content.getString("value") : "";
        String xhtml = buildXhtml(raw, type, style);
        return renderXhtml(xhtml);
    }

    /**
     * 渲染 PDF 的第 pageIndex 页为图像
     */
    public static BufferedImage renderPageImage(PDDocument doc, int pageIndex, int dpi) throws IOException {
        return new PDFRenderer(doc).renderImageWithDPI(pageIndex, dpi);
    }

    // ================= XHTML 构造 =================

    /**
     * 构建完整 XHTML（含 @page 页面样式、body 字体样式、内嵌图片、保留原 style 块）
     */
    static String buildXhtml(String raw, String type, JSONObject style) throws IOException {
        String css = buildPageCss(style);

        StringBuilder extraCss = new StringBuilder();
        Matcher m = STYLE_BLOCK.matcher(raw == null ? "" : raw);
        while (m.find()) {
            extraCss.append(m.group(1)).append('\n');
        }

        String bodyHtml;
        if ("html".equalsIgnoreCase(type)) {
            bodyHtml = sanitizeHtml(raw);
        } else {
            bodyHtml = "<div style=\"white-space:pre-wrap;word-wrap:break-word;\">"
                    + escapeHtml(raw == null ? "" : raw) + "</div>";
        }

        return "<!DOCTYPE html><html><head><meta charset='utf-8'/>"
                + "<style>" + css + extraCss + "</style>"
                + "</head><body>" + bodyHtml + "</body></html>";
    }

    /** 页面级 CSS：纸张尺寸/边距/方向、页眉页脚 margin boxes、body 字体 */
    private static String buildPageCss(JSONObject style) {
        String paperName = style != null ? style.getString("paper") : null;
        double[] size = PaperSizeUtil.sizeOfOrDefault(paperName);
        boolean landscape = style != null && "horizontal".equalsIgnoreCase(style.getString("direction"));
        double pageW = landscape ? Math.max(size[0], size[1]) : size[0];
        double pageH = landscape ? Math.min(size[0], size[1]) : size[1];

        double mt = 0, mr = 0, mb = 0, ml = 0;
        JSONObject margin = style != null ? style.getJSONObject("margin") : null;
        if (margin != null) {
            mt = margin.getDoubleValue("top") * CM_TO_POINT;
            mr = margin.getDoubleValue("right") * CM_TO_POINT;
            mb = margin.getDoubleValue("bottom") * CM_TO_POINT;
            ml = margin.getDoubleValue("left") * CM_TO_POINT;
        }

        String family = mapFontFamily(style != null ? style.getString("fontFamily") : null);
        double zoom = style != null ? style.getDoubleValue("zoom") : 1;
        if (zoom <= 0) {
            zoom = 1;
        }
        int fontSize = style != null ? style.getIntValue("fontSize") : 12;
        if (fontSize <= 0) {
            fontSize = 12;
        }
        double bodySize = fontSize * zoom;

        StringBuilder css = new StringBuilder();
        css.append("@page {\n")
                .append("  size: ").append(fmt(pageW)).append("pt ").append(fmt(pageH)).append("pt;\n")
                .append("  margin: ").append(fmt(mt)).append("pt ").append(fmt(mr)).append("pt ")
                .append(fmt(mb)).append("pt ").append(fmt(ml)).append("pt;\n");

        String header = style != null ? style.getString("paperHeader") : null;
        String footer = style != null ? style.getString("paperFooter") : null;
        if (header != null && !header.isEmpty()) {
            css.append("  @top-center { content: \"").append(cssQuote(header)).append("\"; }\n");
        }
        if (footer != null && !footer.isEmpty()) {
            css.append("  @bottom-center { content: \"").append(cssQuote(footer)).append("\"; }\n");
        }
        css.append("}\n");

        css.append("body {\n")
                .append("  font-family: '").append(family)
                .append("', 'SimSun', 'Microsoft YaHei', 'SimHei', 'KaiTi', 'FangSong', sans-serif;\n")
                .append("  font-size: ").append(fmt(bodySize)).append("pt;\n")
                .append("}\n");
        return css.toString();
    }

    /** 供 CSS content 字符串使用的转义 */
    private static String cssQuote(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * jsoup 净化 html 片段：
     * - 丢弃原 <html>/<head> 骨架，只保留 body 内容；
     * - 将非 base64 图片（http/file/本地路径）下载内嵌为 data URI；
     * - 输出 XHTML 语法（自闭合标签、实体），供 OpenHTMLtoPDF 解析。
     */
    static String sanitizeHtml(String raw) throws IOException {
        Document doc = Jsoup.parse(raw == null ? "" : raw);
        Elements imgs = doc.select("img[src]");
        for (Element img : imgs) {
            String src = img.attr("src");
            String uri = toDataUri(src);
            if (uri != null) {
                img.attr("src", uri);
            } else {
                // 下载失败：移除 src，避免渲染器报错
                log.warn("图片下载失败，已移除: {}", src);
                img.removeAttr("src");
            }
        }
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        doc.outputSettings().escapeMode(Entities.EscapeMode.xhtml);
        doc.outputSettings().charset(java.nio.charset.StandardCharsets.UTF_8);
        return doc.body().html();
    }

    // ================= 图片处理 =================

    /**
     * 把图片 src 转为内嵌 data URI。
     * 支持：data:（原样返回）、http(s)://（下载）、file:// 与本地路径（读文件）。
     * 无法处理时返回 null。
     */
    static String toDataUri(String src) throws IOException {
        if (src == null) {
            return null;
        }
        String s = src.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("data:")) {
            return s;
        }
        try {
            byte[] bytes;
            String mime;
            if (s.startsWith("http://") || s.startsWith("https://")) {
                bytes = downloadUrl(s);
                mime = guessMime(s);
            } else if (s.startsWith("file:")) {
                File f = new File(new URL(s).toURI());
                bytes = java.nio.file.Files.readAllBytes(f.toPath());
                mime = guessMime(f.getName());
            } else {
                File f = new File(s);
                if (!f.exists()) {
                    return null;
                }
                bytes = java.nio.file.Files.readAllBytes(f.toPath());
                mime = guessMime(f.getName());
            }
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("图片转 data URI 失败: {}", src, e);
            return null;
        }
    }

    private static byte[] downloadUrl(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36");
        conn.setRequestProperty("Accept", "image/*,*/*;q=0.8");
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(20000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    /** 依据文件名后缀猜测 MIME 类型 */
    private static String guessMime(String name) {
        String lower = name.toLowerCase();
        if (lower.contains(".png")) {
            return "image/png";
        }
        if (lower.contains(".gif")) {
            return "image/gif";
        }
        if (lower.contains(".webp")) {
            return "image/webp";
        }
        if (lower.contains(".bmp")) {
            return "image/bmp";
        }
        if (lower.contains(".jpg") || lower.contains(".jpeg")) {
            return "image/jpeg";
        }
        return "image/jpeg";
    }

    // ================= 字体 =================

    /**
     * 注册 Windows 常用中文字体（存在的才注册）。
     * OpenHTMLtoPDF 默认字体不含中文字形，必须注册并提供 font-family 匹配。
     */
    private static void registerFonts(PdfRendererBuilder builder) {
        String[][] fonts = {
                {"SimSun", "C:\\Windows\\Fonts\\simsun.ttc"},
                {"Microsoft YaHei", "C:\\Windows\\Fonts\\msyh.ttc"},
                {"SimHei", "C:\\Windows\\Fonts\\simhei.ttf"},
                {"KaiTi", "C:\\Windows\\Fonts\\simkai.ttf"},
                {"FangSong", "C:\\Windows\\Fonts\\simfang.ttf"},
        };
        for (String[] f : fonts) {
            try {
                if (new File(f[1]).exists()) {
                    builder.useFont(new File(f[1]), f[0]);
                }
            } catch (Exception e) {
                log.warn("注册字体失败: {}", f[1], e);
            }
        }
    }

    /** 中文/逻辑字体名 -> OpenHTMLtoPDF 注册的 family 名 */
    public static String mapFontFamily(String family) {
        if (family == null || family.trim().isEmpty()) {
            return "SimSun";
        }
        switch (family.trim()) {
            case "宋体": return "SimSun";
            case "黑体": return "SimHei";
            case "楷体": case "楷体_GB2312": return "KaiTi";
            case "仿宋": return "FangSong";
            case "微软雅黑": return "Microsoft YaHei";
            default: return family;
        }
    }

    // ================= 渲染 =================

    private static PDDocument renderXhtml(String xhtml) throws Exception {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        registerFonts(builder);
        // 渲染到内存输出流后重新加载为 PDDocument。
        // 注意：不能用 usePDDocument(doc)——OpenHTMLtoPDF 1.0.10 在渲染结束时
        // 会对 doc 调用 save()，而 usePDDocument 不绑定输出流，导致 this.out 为 null 抛 NPE。
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        builder.withHtmlContent(xhtml, null);
        builder.toStream(bos);
        builder.run();
        return PDDocument.load(bos.toByteArray());
    }

    // ================= 工具 =================

    static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String fmt(double d) {
        if (d == Math.rint(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(Math.round(d * 100.0) / 100.0);
    }
}
