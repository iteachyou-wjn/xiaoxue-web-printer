package cc.iteachyou.printservice.print.bartender;

import cn.databytes.bartender.BarTenderPrintService;
import cn.databytes.bartender.IBarTenderPrintService;
import cn.databytes.bartender.IDesignObjects;
import cn.databytes.bartender.IObjects;
import cn.databytes.bartender.IPrintSetup;
import cn.databytes.bartender.ITemplate;
import cn.databytes.bartender.constant.Colors;
import cn.databytes.bartender.constant.ExportFormat;
import cn.databytes.bartender.constant.NameType;
import cn.databytes.bartender.constant.ObjectType;
import cn.databytes.bartender.constant.Resolution;
import cn.databytes.bartender.constant.SaveOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BarTender 打印服务实例入口（基于 bartender-printer-sdk 2.1.1）。
 *
 * <p>职责：
 * <ol>
 *   <li>加载 SDK 原生库（{@link JbtCobLoader}）并以懒加载单例方式创建 {@link IBarTenderPrintService}；</li>
 *   <li>提供统一的获取 / 可用性判断 / 版本读取 / 关闭释放入口；</li>
 *   <li>本机未安装 BarTender 或原生库加载失败时优雅降级（返回 {@code null} 并记录日志），不影响打印服务主流程。</li>
 * </ol>
 */
public final class BartenderManager {

    private static final Logger log = LoggerFactory.getLogger(BartenderManager.class);

    /** 单例：BarTender 服务实例 */
    private static volatile IBarTenderPrintService service;

    private BartenderManager() {
    }

    /**
     * 获取 BarTender 服务实例（线程安全懒加载单例）。
     *
     * @return BarTender 服务实例；本机未安装 BarTender / 原生库加载失败时为 {@code null}
     */
    public static IBarTenderPrintService getService() {
        IBarTenderPrintService s = service;
        if (s == null) {
            synchronized (BartenderManager.class) {
                s = service;
                if (s == null) {
                    s = createService();
                    service = s;
                }
            }
        }
        return s;
    }

    /**
     * 当前是否已成功连接 BarTender 服务。
     */
    public static boolean isAvailable() {
        return getService() != null;
    }

    /**
     * 读取 BarTender 服务版本号。
     *
     * @return 版本号；服务不可用或读取失败时为 {@code null}
     */
    public static String getVersion() {
        IBarTenderPrintService s = getService();
        if (s == null) {
            return null;
        }
        try {
            return s.getVersion();
        } catch (Throwable e) {
            log.warn("读取 BarTender 版本失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 读取 BarTender 服务完整版本信息。
     *
     * @return 完整版本信息；服务不可用或读取失败时为 {@code null}
     */
    public static String getFullVersion() {
        IBarTenderPrintService s = getService();
        if (s == null) {
            return null;
        }
        try {
            return s.getFullVersion();
        } catch (Throwable e) {
            log.warn("读取 BarTender 完整版本失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 关闭并释放 BarTender 服务（不保存更改）。
     * 幂等操作，可重复调用。
     */
    public static void shutdown() {
        IBarTenderPrintService s = service;
        service = null;
        if (s == null) {
            return;
        }
        try {
            s.quit(SaveOptions.doNotSaveChanges);
        } catch (Throwable e) {
            log.warn("BarTender quit 失败: {}", e.getMessage());
        }
        try {
            s.release();
        } catch (Throwable e) {
            log.warn("BarTender release 失败: {}", e.getMessage());
        }
    }

    // ================= 模板操作 =================

    /**
     * 预览：打开模板、应用参数并渲染为 PNG 图片（无原生预览 API，用图片导出实现）。
     *
     * @param templateFile BarTender 模板文件路径（.btw）
     * @param params       模板字段名 -> 值（可为 {@code null}）
     * @return PNG 图片字节；失败或服务不可用时返回 {@code null}
     */
    public static byte[] preview(String templateFile, Map<String, String> params) {
        return preview(templateFile, params, 150);
    }

    /**
     * 预览（指定渲染分辨率 DPI）。
     *
     * @param templateFile BarTender 模板文件路径（.btw）
     * @param params       模板字段名 -> 值（可为 {@code null}）
     * @param dpi          渲染分辨率（映射到 SDK 的 Resolution，仅支持 75/150/300/600/800/1200 等档位）
     * @return PNG 图片字节；失败或服务不可用时返回 {@code null}
     */
    public static byte[] preview(String templateFile, Map<String, String> params, int dpi) {
        return preview(templateFile, params, dpi, null);
    }

    /**
     * 预览：打开模板（带打印机）、应用参数并渲染为 PNG 图片字节。
     *
     * <p>采用经验证的调用方式：
     * {@code openTemplate(path, printer)} + {@code exportToImageOutputStream(PNG, 24bit, DPI300, saveChanges)}。
     *
     * @param templateFile BarTender 模板文件路径（.btw）
     * @param params       模板字段名 -> 值（可为 {@code null}）
     * @param dpi          渲染分辨率（保留兼容，导出固定使用 DPI300）
     * @param printerName  目标打印机（可为 {@code null}，作为 openTemplate 第二参数）
     * @return PNG 图片字节；失败或服务不可用时返回 {@code null}
     */
    public static byte[] preview(String templateFile, Map<String, String> params, int dpi, String printerName) {
        ITemplate tpl = openTemplate(templateFile, printerName);
        if (tpl == null) {
            return null;
        }
        try {
            applyData(tpl, params);
            ByteArrayOutputStream baos = tpl.exportToImageOutputStream(
                    ExportFormat.PNG, Colors.colors24Bit, Resolution.DPI300, SaveOptions.saveChanges);
            return baos != null ? baos.toByteArray() : null;
        } catch (Throwable e) {
            log.error("BarTender 预览渲染失败: {}", templateFile, e);
            return null;
        } finally {
            closeTemplate(tpl);
        }
    }

    /**
     * 打印模板（使用默认打印机）。
     *
     * @param templateFile BarTender 模板文件路径（.btw）
     * @param params       模板字段名 -> 值（可为 {@code null}）
     * @return 打印的标签数量；失败或服务不可用时返回 -1
     */
    public static int print(String templateFile, Map<String, String> params) {
        return print(templateFile, params, null, -1, -1);
    }

    /**
     * 打印模板（指定打印机）。
     *
     * @param templateFile BarTender 模板文件路径（.btw）
     * @param params       模板字段名 -> 值（可为 {@code null}）
     * @param printer      目标打印机名称（{@code null} 使用模板默认）
     * @return 打印的标签数量；失败或服务不可用时返回 -1
     */
    public static int print(String templateFile, Map<String, String> params, String printer) {
        return print(templateFile, params, printer, -1, -1);
    }

    /**
     * 打印模板（完整控制：打印机、份数、序列号）。
     *
     * @param templateFile     BarTender 模板文件路径（.btw）
     * @param params           模板字段名 -> 值（可为 {@code null}）
     * @param printer          目标打印机名称（{@code null} 使用模板默认）
     * @param copies           同一标签份数（&lt;=0 使用模板默认）
     * @param serializedLabels 序列化标签数量（&lt;=0 使用模板默认）
     * @return 打印的标签数量；失败或服务不可用时返回 -1
     */
    public static int print(String templateFile, Map<String, String> params, String printer,
                            int copies, int serializedLabels) {
        ITemplate tpl = openTemplate(templateFile);
        if (tpl == null) {
            return -1;
        }
        try {
            if (printer != null && !printer.trim().isEmpty()) {
                tpl.setPrinter(printer);
            }
            if (copies > 0 || serializedLabels > 0) {
                IPrintSetup ps = tpl.getPrintSetup();
                if (copies > 0) {
                    ps.setIdenticalCopiesOfLabel(copies);
                }
                if (serializedLabels > 0) {
                    ps.setNumberSerializedLabels(serializedLabels);
                }
            }
            applyData(tpl, params);
            return tpl.printOut();
        } catch (Throwable e) {
            log.error("BarTender 打印失败: {}", templateFile, e);
            return -1;
        } finally {
            closeTemplate(tpl);
        }
    }

    /**
     * 获取模板参数（模板字段名及控件类型）列表。
     *
     * <p>BarTender 模板变量可能是「模板字段」或「命名子串(NamedSubString)」或「数据库字段」，
     * 此处收集所有类型并去重；随后通过遍历模板对象获取每个参数对应的控件类型（ObjectType 粒度，
     * 例如 Barcode / Text / Picture / RichText 等，条码不细分 QRCode / Code128）。</p>
     *
     * @param templateFile BarTender 模板文件路径（.btw）
     * @return 参数信息列表，每项为 {name: 参数名, type: 控件类型}；失败或服务不可用时返回空列表
     */
    public static List<Map<String, String>> getTemplateParameters(String templateFile) {
        ITemplate tpl = openTemplate(templateFile);
        if (tpl == null) {
            return Collections.emptyList();
        }
        try {
            // 1. 收集参数名集合（模板字段 + 命名子串 + 数据库字段）
            Set<String> names = new LinkedHashSet<>();
            NameType[] types = {
                    NameType.UsedTemplateFields,
                    NameType.UsedNamedSubStrings,
                    NameType.UsedDatabaseFields
            };
            for (NameType type : types) {
                try {
                    String used = tpl.getUsedNames(type, ",");
                    if (used != null && !used.trim().isEmpty()) {
                        for (String name : used.split(",")) {
                            if (name != null && !name.trim().isEmpty()) {
                                names.add(name.trim());
                            }
                        }
                    }
                } catch (Throwable e) {
                    log.warn("获取模板字段类型 [{}] 失败: {}", type, e.getMessage());
                }
            }

            // 2. 遍历模板对象，尝试用对象名精确匹配参数类型
            Map<String, String> typeByName = new HashMap<>();
            try {
                IObjects objects = tpl.getObjects();
                if (objects != null) {
                    int count = objects.getCount();
                    // 用 native 的 getItem(i) 逐个枚举（getItems() 有重复返回首元素的 bug；COM 集合 1-based）
                    for (int i = 1; i <= count; i++) {
                        try {
                            IDesignObjects obj = objects.getItem(i);
                            if (obj == null) {
                                continue;
                            }
                            String name = obj.getName();
                            ObjectType ot = obj.getTypeName();
                            if (name != null && names.contains(name.trim())) {
                                typeByName.put(name.trim(), ot == null ? "Unknown" : ot.name());
                            }
                        } catch (Throwable e) {
                            log.warn("getItem({}) 失败: {}", i, e.getMessage());
                        }
                    }
                }
            } catch (Throwable e) {
                log.warn("遍历模板对象获取控件类型失败: {}", e.getMessage());
            }

            // 3. 组装结果；未精确匹配的参数按命名语义推断类型
            List<Map<String, String>> result = new ArrayList<>();
            for (String name : names) {
                Map<String, String> item = new HashMap<>();
                item.put("name", name);
                item.put("type", typeByName.getOrDefault(name, inferTypeByName(name)));
                result.add(item);
            }
            return result;
        } catch (Throwable e) {
            log.error("获取模板参数失败: {}", templateFile, e);
            return Collections.emptyList();
        } finally {
            closeTemplate(tpl);
        }
    }

    // ================= 私有辅助 =================

    /**
     * 根据变量名语义推断控件类型。
     *
     * <p>当设计对象名（如中文自动名「条形码 1」）与变量名（如 barcode1）不一致、无法精确匹配时，
     * 依据变量名关键词推断类型。注意此推断依赖命名约定，是兜底方案。
     *
     * @param name 变量名
     * @return ObjectType 名称或 Unknown
     */
    private static String inferTypeByName(String name) {
        if (name == null) {
            return "Unknown";
        }
        String lower = name.toLowerCase();
        if (lower.contains("barcode") || lower.contains("qrcode") || lower.contains("code")
                || lower.contains("条码") || lower.contains("二维码")) {
            return "Barcode";
        }
        if (lower.contains("text") || lower.contains("字符") || lower.contains("文本")) {
            return "Text";
        }
        if (lower.contains("pic") || lower.contains("image") || lower.contains("图片")) {
            return "Picture";
        }
        if (lower.contains("rich") || lower.contains("富")) {
            return "RichText";
        }
        if (lower.contains("line") || lower.contains("线条")) {
            return "Line";
        }
        if (lower.contains("box") || lower.contains("矩形")) {
            return "Box";
        }
        return "Unknown";
    }

    /** 打开模板（默认打印机），失败返回 {@code null} */
    private static ITemplate openTemplate(String templateFile) {
        return openTemplate(templateFile, null);
    }

    /** 打开模板（可指定打印机，为空时传空串使用默认），失败返回 {@code null} */
    private static ITemplate openTemplate(String templateFile, String printerName) {
        IBarTenderPrintService svc = getService();
        if (svc == null) {
            return null;
        }
        try {
            String p = (printerName == null || printerName.trim().isEmpty()) ? "" : printerName.trim();
            return svc.openTemplate(templateFile, p);
        } catch (Throwable e) {
            log.error("打开 BarTender 模板失败: {}", templateFile, e);
            return null;
        }
    }

    /** 关闭模板（容错） */
    private static void closeTemplate(ITemplate tpl) {
        if (tpl != null) {
            try {
                tpl.close();
            } catch (Throwable e) {
                log.warn("关闭 BarTender 模板失败: {}", e.getMessage());
            }
        }
    }

    /** 应用模板参数（逐字段容错：单个字段失败不影响其他字段） */
    private static void applyData(ITemplate tpl, Map<String, String> params) {
        if (tpl == null || params == null || params.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            try {
                tpl.setData(entry.getKey(), entry.getValue());
            } catch (Throwable e) {
                log.warn("设置 BarTender 模板字段 [{}] 失败（请确认该字段在模板中存在）: {}",
                        entry.getKey(), e.getMessage());
            }
        }
    }

    /** 将整数 DPI 映射到 SDK 的 Resolution 枚举 */
    private static Resolution resolveDpi(int dpi) {
        if (dpi <= 75) {
            return Resolution.DPI75;
        }
        if (dpi <= 150) {
            return Resolution.DPI150;
        }
        if (dpi <= 300) {
            return Resolution.DPI300;
        }
        if (dpi <= 600) {
            return Resolution.DPI600;
        }
        if (dpi <= 800) {
            return Resolution.DPI800;
        }
        if (dpi <= 1200) {
            return Resolution.DPI1200;
        }
        return Resolution.DPI2400;
    }

    /**
     * 创建并连接 BarTender 服务，失败时返回 {@code null}。
     */
    private static IBarTenderPrintService createService() {
        try {
            // BarTenderPrintService.create() 内部会自动加载 jbtcob 原生库，
            // 无需显式 JbtCobLoader.loadLibrary()（避免重复注册打印两次）
            IBarTenderPrintService svc = BarTenderPrintService.create();
            log.info("已连接 BarTender 服务, 版本={}, 完整版本={}", svc.getVersion(), svc.getFullVersion());
            return svc;
        } catch (Throwable e) {
            log.error("连接 BarTender 服务失败（本机可能未安装 BarTender）: {}", e.getMessage(), e);
            return null;
        }
    }
}
