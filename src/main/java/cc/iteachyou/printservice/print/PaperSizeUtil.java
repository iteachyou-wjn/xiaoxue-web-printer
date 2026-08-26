package cc.iteachyou.printservice.print;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 纸张规格工具类
 *
 * 集中管理纸张名称 -> 尺寸（points，1/72 英寸）的映射，
 * 供打印执行器（PrintTaskExecutor）与打印预览（PreviewDialog）共用，
 * 保证打印与预览使用一致的纸张尺寸。
 */
public final class PaperSizeUtil {

    private static final Map<String, double[]> PAPER_SIZES = new LinkedHashMap<>();

    static {
        // ISO A 系列
        PAPER_SIZES.put("A0", new double[]{2384, 3370});
        PAPER_SIZES.put("A1", new double[]{1684, 2384});
        PAPER_SIZES.put("A2", new double[]{1191, 1684});
        PAPER_SIZES.put("A3", new double[]{842, 1191});
        PAPER_SIZES.put("A4", new double[]{595, 842});
        PAPER_SIZES.put("A5", new double[]{420, 595});
        PAPER_SIZES.put("A6", new double[]{298, 420});
        PAPER_SIZES.put("A7", new double[]{210, 298});
        PAPER_SIZES.put("A8", new double[]{147, 210});
        // ISO B 系列
        PAPER_SIZES.put("B4", new double[]{709, 1001});
        PAPER_SIZES.put("B5", new double[]{499, 709});
        PAPER_SIZES.put("B6", new double[]{354, 499});
        // 北美常用规格
        PAPER_SIZES.put("Letter", new double[]{612, 792});
        PAPER_SIZES.put("Legal", new double[]{612, 1008});
        PAPER_SIZES.put("Ledger", new double[]{792, 1224});
        PAPER_SIZES.put("Tabloid", new double[]{792, 1224});
        PAPER_SIZES.put("Executive", new double[]{522, 756});
        // 信封
        PAPER_SIZES.put("C4", new double[]{649, 918});
        PAPER_SIZES.put("C5", new double[]{459, 649});
        PAPER_SIZES.put("C6", new double[]{323, 459});
        PAPER_SIZES.put("DL", new double[]{312, 624});
    }

    private PaperSizeUtil() {
    }

    /**
     * 获取纸张尺寸（points）；未知纸张返回 null
     *
     * @param paper 纸张名称，如 "A4"、"Letter"（不区分大小写）
     * @return {width, height} 尺寸数组；未匹配返回 null
     */
    public static double[] sizeOf(String paper) {
        if (paper == null) {
            return null;
        }
        double[] size = PAPER_SIZES.get(paper.toUpperCase());
        return size != null ? size.clone() : null;
    }

    /**
     * 获取纸张尺寸，未知纸张回退到 A4
     */
    public static double[] sizeOfOrDefault(String paper) {
        double[] size = sizeOf(paper);
        return size != null ? size : PAPER_SIZES.get("A4").clone();
    }

    /**
     * 判断是否为已知纸张规格
     */
    public static boolean contains(String paper) {
        return paper != null && PAPER_SIZES.containsKey(paper.toUpperCase());
    }
}
