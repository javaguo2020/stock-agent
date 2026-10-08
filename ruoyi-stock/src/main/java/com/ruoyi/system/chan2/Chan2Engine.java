package com.ruoyi.system.chan2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 第二套缠论（v2）分析核心引擎。
 *
 * 基于标准缠论八步流程完整实现：
 *   第一步 数据输入与K线预处理（包含关系处理/合并）
 *   第二步 分型识别（顶分型 / 底分型）
 *   第三步 笔的划分（新笔规则，含极端分型选择与独立K线间隔约束）
 *   第四步 线段划分（特征序列法：包含处理 + 特征序列分型确认线段破坏）
 *   第五步 走势中枢识别（笔中枢/线段中枢，含中枢延伸）
 *   第六步 走势类型判定（盘整 / 上涨趋势 / 下跌趋势）
 *   第七步 背驰判断（MACD 力度对比：DIF 极值与柱面积）
 *   第八步 三类买卖点综合判定（一类=背驰、二类=次低/次高、三类=中枢突破回踩）
 *
 * 本引擎采用特征序列法进行标准线段划分，并输出完整的走势类型与三类买卖点。
 */
public final class Chan2Engine {

    private Chan2Engine() {
    }

    public static final String UP = "up";
    public static final String DOWN = "down";
    public static final String TOP = "top";
    public static final String BOTTOM = "bottom";

    public static final String LEVEL_BI = "bi";
    public static final String LEVEL_SEGMENT = "segment";

    // ------------------------------------------------------------------
    // 第一步：K线预处理（包含关系处理）
    // ------------------------------------------------------------------

    /**
     * 处理K线包含关系并合并。
     *
     * 规则：
     *  - 相邻两根K线若价格区间有重叠（相互包含），按趋势方向合并；
     *  - 上升趋势：高点取高、低点取高（保留更强势的组合）；
     *  - 下降趋势：高点取低、低点取低（保留更弱势的组合）；
     *  - 方向依据最近两根非包含K线的相对位置确定；
     *  - 记录每根合并K线覆盖的原始K线下标范围，用于 MACD 对齐。
     *
     * @param klines 按时间升序排列的原始K线
     * @return 合并后的K线序列
     */
    public static List<MergedBar> mergeKlines(List<Chan2Kline> klines) {
        List<MergedBar> merged = new ArrayList<>();
        if (klines == null || klines.isEmpty()) {
            return merged;
        }
        for (int i = 0; i < klines.size(); i++) {
            Chan2Kline k = klines.get(i);
            MergedBar bar = new MergedBar();
            bar.open = k.open;
            bar.high = k.high;
            bar.low = k.low;
            bar.close = k.close;
            bar.date = k.date != null ? k.date : String.valueOf(i);
            bar.origStart = k.origIndex;
            bar.origEnd = k.origIndex;
            bar.volume = k.volume;

            if (merged.isEmpty()) {
                merged.add(bar);
                continue;
            }

            MergedBar last = merged.get(merged.size() - 1);

            // 判断包含关系：当前K线区间被上一根包含，或包含上一根
            boolean contained = (k.high <= last.high && k.low >= last.low)
                    || (k.high >= last.high && k.low <= last.low);

            if (!contained) {
                // 非包含：直接追加，并记录相对方向
                MergedBar prev = merged.size() >= 2 ? merged.get(merged.size() - 2) : null;
                bar.direction = (prev == null)
                        ? (k.close >= last.close ? UP : DOWN)
                        : (last.high >= prev.high ? UP : DOWN);
                merged.add(bar);
                continue;
            }

            // 包含关系：按方向合并
            String direction;
            if (merged.size() >= 2) {
                MergedBar prev = merged.get(merged.size() - 2);
                direction = last.high >= prev.high ? UP : DOWN;
            } else {
                direction = k.close >= last.close ? UP : DOWN;
            }

            if (UP.equals(direction)) {
                last.high = Math.max(last.high, k.high);
                last.low = Math.max(last.low, k.low);
                last.close = Math.max(last.close, k.close);
            } else {
                last.high = Math.min(last.high, k.high);
                last.low = Math.min(last.low, k.low);
                last.close = Math.min(last.close, k.close);
            }
            last.date = k.date != null ? k.date : last.date;
            last.origEnd = k.origIndex;
            last.volume += k.volume;
        }
        return merged;
    }

    // ------------------------------------------------------------------
    // 第二步：分型识别
    // ------------------------------------------------------------------

    /**
     * 识别顶分型与底分型。
     *
     * 顶分型：三根相邻合并K线中，中间K线的高点是三者中最高的，低点也是三者中最高的。
     * 底分型：三根相邻合并K线中，中间K线的低点是三者中最低的，高点也是三者中最低的。
     *
     * @param merged 合并后的K线序列
     * @return 分型列表（按位置升序）
     */
    public static List<Fractal2> detectFractals(List<MergedBar> merged) {
        List<Fractal2> fractals = new ArrayList<>();
        if (merged == null || merged.size() < 3) {
            return fractals;
        }
        int n = merged.size();
        for (int i = 1; i < n - 1; i++) {
            MergedBar a = merged.get(i - 1);
            MergedBar b = merged.get(i);
            MergedBar c = merged.get(i + 1);
            if (b.high > a.high && b.high > c.high && b.low > a.low && b.low > c.low) {
                fractals.add(new Fractal2(i, TOP, b.high, b.low, b.date));
            } else if (b.low < a.low && b.low < c.low && b.high < a.high && b.high < c.high) {
                fractals.add(new Fractal2(i, BOTTOM, b.high, b.low, b.date));
            }
        }
        return fractals;
    }

    // ------------------------------------------------------------------
    // 第三步：笔的划分
    // ------------------------------------------------------------------

    /** 判断分型 f 是否比 ref 更极端（同类型分型比较） */
    private static boolean isMoreExtreme(Fractal2 f, Fractal2 ref) {
        if (TOP.equals(f.kind)) {
            return f.high > ref.high;
        }
        return f.low < ref.low;
    }

    /** 判断笔的价格条件：起止分型必须同向运动 */
    private static boolean priceCondition(Fractal2 start, Fractal2 end) {
        if (BOTTOM.equals(start.kind) && TOP.equals(end.kind)) {
            return end.high > start.high && end.low > start.low;
        }
        if (TOP.equals(start.kind) && BOTTOM.equals(end.kind)) {
            return end.low < start.low && end.high < start.high;
        }
        return false;
    }

    /**
     * 笔的划分（新笔规则）。
     *
     * 规则：
     *  - 上升笔 = 底分型 + 上升K线 + 顶分型；下降笔 = 顶分型 + 下降K线 + 底分型；
     *  - 顶底分型之间至少有一根独立K线：两分型中心下标差 >= minGap；
     *  - 顶分型与底分型不能共用K线；
     *  - 连续出现的同向分型，只保留最极端的一个（顶分型取最高、底分型取最低）。
     *
     * @param merged    合并后的K线序列
     * @param fractals  分型列表
     * @param minGap    最小分型间隔（默认 4）
     * @return 笔序列（方向交替）
     */
    public static List<Bi2> findBis(List<MergedBar> merged, List<Fractal2> fractals, int minGap) {
        List<Bi2> bis = new ArrayList<>();
        if (fractals == null || fractals.size() < 2) {
            return bis;
        }
        int n = fractals.size();
        int i = 0;
        while (i < n - 1) {
            Fractal2 start = fractals.get(i);

            // 1) 跳过连续同向分型，保留最极端者
            int j = i + 1;
            Fractal2 best = start;
            int bestIdx = i;
            while (j < n && fractals.get(j).kind.equals(start.kind)) {
                if (isMoreExtreme(fractals.get(j), best)) {
                    best = fractals.get(j);
                    bestIdx = j;
                }
                j++;
            }
            if (j >= n) {
                break;
            }

            // 2) 在反向分型中寻找第一个满足间隔与价格条件的分型
            Fractal2 found = null;
            int k = j;
            while (k < n && !fractals.get(k).kind.equals(best.kind)) {
                Fractal2 f = fractals.get(k);
                if (f.index - best.index >= minGap && priceCondition(best, f)) {
                    found = f;
                    break;
                }
                k++;
            }

            if (found != null) {
                String direction = BOTTOM.equals(best.kind) ? UP : DOWN;
                bis.add(new Bi2(direction, best, found));
                i = k;
            } else {
                i = bestIdx + 1;
            }
        }
        return bis;
    }

    // ------------------------------------------------------------------
    // 第四步：线段划分（特征序列法）
    // ------------------------------------------------------------------

    /**
     * 特征序列元素（内部结构）。
     *
     * 对上升线段，特征元素为线段内的向下笔；对下降线段，特征元素为线段内的向上笔。
     * 特征元素可被包含合并，合并方向与线段方向一致。
     */
    private static final class FeatureElement {
        /** 特征元素对应反向笔在笔序列中的起始下标 */
        int startBi;
        /** 特征元素对应反向笔在笔序列中的结束下标 */
        int endBi;
        /** 特征元素的高点（顶分型） */
        Fractal2 topFx;
        /** 特征元素的低点（底分型） */
        Fractal2 bottomFx;
        /** 特征元素的高点价格 */
        double high;
        /** 特征元素的低点价格 */
        double low;
    }

    /**
     * 从一笔构建特征序列元素。
     *
     * @param b      反向笔
     * @param segDir 线段方向
     * @param biIdx  该笔在笔序列中的下标
     */
    private static FeatureElement toFeature(Bi2 b, String segDir, int biIdx) {
        FeatureElement fe = new FeatureElement();
        fe.startBi = biIdx;
        fe.endBi = biIdx;
        if (UP.equals(segDir)) {
            // 上升线段的特征元素 = 向下笔：起点为顶分型，终点为底分型
            fe.topFx = b.start;
            fe.bottomFx = b.end;
            fe.high = b.start.high;
            fe.low = b.end.low;
        } else {
            // 下降线段的特征元素 = 向上笔：起点为底分型，终点为顶分型
            fe.topFx = b.end;
            fe.bottomFx = b.start;
            fe.high = b.end.high;
            fe.low = b.start.low;
        }
        return fe;
    }

    /** 判断两个特征元素是否相互包含 */
    private static boolean isFeatureContained(FeatureElement a, FeatureElement b) {
        return (a.high <= b.high && a.low >= b.low) || (a.high >= b.high && a.low <= b.low);
    }

    /**
     * 按线段方向合并两个包含的特征元素。
     *
     * 上升线段按"向上"处理：高点取高、低点取高；
     * 下降线段按"向下"处理：高点取低、低点取低。
     * 同时保留更极端的分型作为合并后元素的参考分型（决定线段结束点/新线段起点）。
     */
    private static void mergeFeature(FeatureElement target, FeatureElement incoming, String segDir) {
        if (UP.equals(segDir)) {
            if (incoming.high > target.high) {
                target.topFx = incoming.topFx;
                target.startBi = incoming.startBi;
            }
            target.high = Math.max(target.high, incoming.high);
            target.low = Math.max(target.low, incoming.low);
        } else {
            if (incoming.low < target.low) {
                target.bottomFx = incoming.bottomFx;
                target.startBi = incoming.startBi;
            }
            target.high = Math.min(target.high, incoming.high);
            target.low = Math.min(target.low, incoming.low);
        }
        target.endBi = Math.max(target.endBi, incoming.endBi);
    }

    /**
     * 判断特征序列是否形成分型（线段结束信号）。
     *
     * 上升线段看顶分型：中间特征元素的高点最高、低点最高；
     * 下降线段看底分型：中间特征元素的低点最低、高点最低。
     */
    private static boolean isFeatureFractal(FeatureElement e0, FeatureElement e1, FeatureElement e2, String segDir) {
        if (UP.equals(segDir)) {
            return e1.high > e0.high && e1.high > e2.high && e1.low > e0.low && e1.low > e2.low;
        }
        return e1.low < e0.low && e1.low < e2.low && e1.high < e0.high && e1.high < e2.high;
    }

    /**
     * 线段划分（特征序列法）。
     *
     * 规则：
     *  - 线段至少由三笔组成；
     *  - 线段方向由组成线段的笔的方向决定；
     *  - 对线段构建特征序列（反向笔），并进行包含处理；
     *  - 特征序列形成分型（上升线段顶分型 / 下降线段底分型）时，线段结束；
     *  - 线段结束点 = 分型中间特征元素的参考分型，破坏笔被新旧两个线段共享；
     *  - 最后一段若未形成结束分型，作为未完成线段延伸到最后一笔。
     *
     * @param bis 笔序列（方向交替）
     * @return 线段序列
     */
    public static List<Segment2> findSegments(List<Bi2> bis) {
        List<Segment2> segs = new ArrayList<>();
        if (bis == null || bis.size() < 3) {
            return segs;
        }
        int n = bis.size();
        int i = 0;
        while (i < n) {
            Bi2 first = bis.get(i);
            String dir = first.direction;
            Fractal2 segStartFx = first.start;
            int segStartBi = i;
            List<FeatureElement> features = new ArrayList<>();
            int j = i + 1;
            boolean ended = false;
            while (j < n) {
                Bi2 b = bis.get(j);
                if (!b.direction.equals(dir)) {
                    // 反向笔 -> 特征元素
                    FeatureElement fe = toFeature(b, dir, j);
                    if (!features.isEmpty()) {
                        FeatureElement lastFx = features.get(features.size() - 1);
                        if (isFeatureContained(lastFx, fe)) {
                            mergeFeature(lastFx, fe, dir);
                        } else {
                            features.add(fe);
                        }
                    } else {
                        features.add(fe);
                    }

                    // 特征序列分型检测
                    if (features.size() >= 3) {
                        FeatureElement e0 = features.get(features.size() - 3);
                        FeatureElement e1 = features.get(features.size() - 2);
                        FeatureElement e2 = features.get(features.size() - 1);
                        if (isFeatureFractal(e0, e1, e2, dir)) {
                            // 线段结束：结束分型 = 中间特征元素的参考分型
                            Fractal2 endFx = UP.equals(dir) ? e1.topFx : e1.bottomFx;
                            int breakBi = e1.startBi;
                            int biCount = breakBi - segStartBi + 1;
                            if (biCount >= 3) {
                                segs.add(new Segment2(dir, segStartFx, endFx, biCount,
                                        new ArrayList<>(bis.subList(segStartBi, breakBi + 1)), false));
                            }
                            // 新线段从破坏笔开始（破坏笔被两段共享）
                            i = breakBi;
                            ended = true;
                            break;
                        }
                    }
                }
                j++;
            }
            if (!ended) {
                // 未结束：作为未完成线段延伸到最后一笔
                int endBi = n - 1;
                int biCount = endBi - segStartBi + 1;
                if (biCount >= 3) {
                    segs.add(new Segment2(dir, segStartFx, bis.get(endBi).end, biCount,
                            new ArrayList<>(bis.subList(segStartBi, endBi + 1)), true));
                }
                break;
            }
        }
        return segs;
    }

    // ------------------------------------------------------------------
    // 第五步：走势中枢识别
    // ------------------------------------------------------------------

    /**
     * 中枢识别。
     *
     * 定义：连续三个次级别走势类型的重叠部分。
     *  - 中枢顶 ZG = 每段走势高点中的最低点
     *  - 中枢底 ZD = 每段走势低点中的最高点
     *  - 有效中枢要求 ZG > ZD
     *  - 中枢延伸：在已构成中枢后，后续走势若仍在 ZG/ZD 区间内震荡，则纳入中枢
     *
     * @param items 笔或线段序列（实现 Chan2Item）
     * @param level "bi" 笔中枢(次级别) / "segment" 线段中枢(本级别)
     * @return 中枢列表
     */
    public static List<Pivot2> findPivots(List<? extends Chan2Item> items, String level) {
        List<Pivot2> pivots = new ArrayList<>();
        if (items == null || items.size() < 3) {
            return pivots;
        }
        int n = items.size();
        int i = 0;
        while (i <= n - 3) {
            Chan2Item a = items.get(i);
            Chan2Item b = items.get(i + 1);
            Chan2Item c = items.get(i + 2);
            double zg = Math.min(Math.min(a.high(), b.high()), c.high());
            double zd = Math.max(Math.max(a.low(), b.low()), c.low());
            if (zg > zd) {
                // 中枢延伸：后续段与中枢区间重叠则纳入
                int j = i + 3;
                while (j < n) {
                    Chan2Item it = items.get(j);
                    if (it.high() > zd && it.low() < zg) {
                        j++;
                    } else {
                        break;
                    }
                }
                Chan2Item last = items.get(j - 1);
                pivots.add(new Pivot2(zg, zd, level,
                        items.get(i).startDate(), last.endDate(),
                        j - i, items.get(i).startIndex(), last.endIndex()));
                i = j;
            } else {
                i++;
            }
        }
        return pivots;
    }

    /** 笔中枢（次级别） */
    public static List<Pivot2> findPivotsFromBi(List<Bi2> bis) {
        return findPivots(bis, LEVEL_BI);
    }

    /** 线段中枢（本级别） */
    public static List<Pivot2> findPivotsFromSegments(List<Segment2> segments) {
        return findPivots(segments, LEVEL_SEGMENT);
    }

    // ------------------------------------------------------------------
    // 第六步：走势类型判定
    // ------------------------------------------------------------------

    /**
     * 走势类型判定。
     *
     *  - 盘整：只包含一个走势中枢；
     *  - 上涨/下跌：至少包含两个及以上依次同向的走势中枢
     *    （依次同向 = 后一中枢整体位于前一中枢上方/下方，即互不重叠）；
     *  - 未形成：尚无有效中枢。
     *
     * @param segmentPivots 本级别（线段中枢）序列
     * @param lastClose     最新收盘价
     * @return 走势类型
     */
    public static TrendType judgeTrend(List<Pivot2> segmentPivots, double lastClose) {
        if (segmentPivots == null || segmentPivots.isEmpty()) {
            return TrendType.UNDEFINED;
        }
        // 从最后一个中枢向前统计连续同向中枢的个数
        String runDir = null;
        int runLen = 1;
        for (int idx = segmentPivots.size() - 1; idx >= 1; idx--) {
            Pivot2 a = segmentPivots.get(idx - 1);
            Pivot2 b = segmentPivots.get(idx);
            if (b.getZd() > a.getZg()) {
                // 后一中枢整体位于前一中枢上方：中枢依次上行
                if (runDir == null) {
                    runDir = "up";
                } else if ("up".equals(runDir)) {
                    runLen++;
                } else {
                    break;
                }
            } else if (b.getZg() < a.getZd()) {
                // 后一中枢整体位于前一中枢下方：中枢依次下行
                if (runDir == null) {
                    runDir = "down";
                } else if ("down".equals(runDir)) {
                    runLen++;
                } else {
                    break;
                }
            } else {
                // 两中枢重叠 = 中枢延伸/扩展，不构成趋势
                break;
            }
        }
        if (runLen >= 2) {
            return "up".equals(runDir) ? TrendType.UP : TrendType.DOWN;
        }
        // 仅一个中枢或无法形成趋势：严格定义为盘整
        return TrendType.CONSOLIDATION;
    }

    // ------------------------------------------------------------------
    // 第七步：背驰判断（动力学）
    // ------------------------------------------------------------------

    /**
     * 计算 MACD 指标。
     *
     * @param close  收盘价序列（按时间升序）
     * @param fast   快线周期（默认12）
     * @param slow   慢线周期（默认26）
     * @param signal 信号周期（默认9）
     * @return MACD 结果
     */
    public static Macd2 computeMacd(double[] close, int fast, int slow, int signal) {
        double[] emaFast = ema(close, fast);
        double[] emaSlow = ema(close, slow);
        double[] dif = new double[close.length];
        for (int i = 0; i < close.length; i++) {
            dif[i] = emaFast[i] - emaSlow[i];
        }
        double[] dea = ema(dif, signal);
        double[] hist = new double[close.length];
        for (int i = 0; i < close.length; i++) {
            hist[i] = 2.0 * (dif[i] - dea[i]);
        }
        return new Macd2(dif, dea, hist);
    }

    /** EMA 递推计算（与 pandas ewm(adjust=false) 一致） */
    private static double[] ema(double[] x, int span) {
        double alpha = 2.0 / (span + 1.0);
        double[] out = new double[x.length];
        if (x.length == 0) {
            return out;
        }
        out[0] = x[0];
        for (int i = 1; i < x.length; i++) {
            out[i] = alpha * x[i] + (1.0 - alpha) * out[i - 1];
        }
        return out;
    }

    /** 求数组区间极值 */
    private static double rangeExtreme(double[] arr, int s, int e, boolean isMax) {
        int lo = Math.max(0, s);
        int hi = Math.min(arr.length - 1, e);
        if (lo > hi) {
            return Double.NaN;
        }
        double result = arr[lo];
        for (int i = lo + 1; i <= hi; i++) {
            if (isMax) {
                result = Math.max(result, arr[i]);
            } else {
                result = Math.min(result, arr[i]);
            }
        }
        return result;
    }

    /** 计算区间内 MACD 红柱面积（hist > 0 的累加） */
    private static double areaAboveZero(double[] hist, int s, int e) {
        int lo = Math.max(0, s);
        int hi = Math.min(hist.length - 1, e);
        double sum = 0.0;
        for (int i = lo; i <= hi; i++) {
            if (hist[i] > 0) {
                sum += hist[i];
            }
        }
        return sum;
    }

    /** 计算区间内 MACD 绿柱面积（hist < 0 的绝对值累加） */
    private static double areaBelowZero(double[] hist, int s, int e) {
        int lo = Math.max(0, s);
        int hi = Math.min(hist.length - 1, e);
        double sum = 0.0;
        for (int i = lo; i <= hi; i++) {
            if (hist[i] < 0) {
                sum += Math.abs(hist[i]);
            }
        }
        return sum;
    }

    /** 将笔映射到原始K线区间 [origStart, origEnd] */
    private static int[] biOrigRange(List<MergedBar> merged, Bi2 b) {
        MergedBar s = merged.get(b.start.index);
        MergedBar e = merged.get(b.end.index);
        return new int[]{s.origStart, e.origEnd};
    }

    /**
     * 背驰判断（扫描全部相邻同向笔）。
     *
     * 对每对相邻同向笔：
     *  - 顶背驰：后一笔创新高（b2.high > b1.high），但 DIF 峰值减弱 或 红柱面积缩小；
     *  - 底背驰：后一笔创新低（b2.low < b1.low），但 DIF 谷值抬高 或 绿柱面积缩小。
     *
     * @param merged 合并K线序列
     * @param bis    笔序列
     * @param macd   MACD 指标（基于原始K线计算）
     * @return 全部背驰信号 + 汇总标志
     */
    public static DivergenceResult detectDivergence(List<MergedBar> merged, List<Bi2> bis, Macd2 macd) {
        DivergenceResult result = new DivergenceResult();
        if (bis == null || bis.size() < 2) {
            return result;
        }
        List<Bi2> ups = new ArrayList<>();
        List<Bi2> downs = new ArrayList<>();
        for (Bi2 b : bis) {
            if (UP.equals(b.direction)) {
                ups.add(b);
            } else {
                downs.add(b);
            }
        }

        // 顶背驰：比较相邻两段上升笔
        for (int i = 1; i < ups.size(); i++) {
            Bi2 b1 = ups.get(i - 1);
            Bi2 b2 = ups.get(i);
            int[] r1 = biOrigRange(merged, b1);
            int[] r2 = biOrigRange(merged, b2);
            double dif1 = rangeExtreme(macd.dif, r1[0], r1[1], true);
            double dif2 = rangeExtreme(macd.dif, r2[0], r2[1], true);
            double a1 = areaAboveZero(macd.hist, r1[0], r1[1]);
            double a2 = areaAboveZero(macd.hist, r2[0], r2[1]);
            if (b2.high > b1.high && (dif2 < dif1 || a2 < a1)) {
                List<String> reasons = new ArrayList<>();
                if (dif2 < dif1) {
                    reasons.add(String.format(Locale.ROOT, "DIF 峰值 %.3f < %.3f", dif2, dif1));
                }
                if (a2 < a1) {
                    reasons.add(String.format(Locale.ROOT, "红柱面积 %.2f < %.2f", a2, a1));
                }
                String detail = String.format(Locale.ROOT,
                        "顶背驰：上升笔创新高(%.2f > %.2f)，但 %s", b2.high, b1.high, String.join("；", reasons));
                result.top = true;
                result.detail.add(detail);
                result.divergences.add(new Divergence2(TOP, b2.end, detail));
            }
        }

        // 底背驰：比较相邻两段下降笔
        for (int i = 1; i < downs.size(); i++) {
            Bi2 b1 = downs.get(i - 1);
            Bi2 b2 = downs.get(i);
            int[] r1 = biOrigRange(merged, b1);
            int[] r2 = biOrigRange(merged, b2);
            double dif1 = rangeExtreme(macd.dif, r1[0], r1[1], false);
            double dif2 = rangeExtreme(macd.dif, r2[0], r2[1], false);
            double a1 = areaBelowZero(macd.hist, r1[0], r1[1]);
            double a2 = areaBelowZero(macd.hist, r2[0], r2[1]);
            if (b2.low < b1.low && (dif2 > dif1 || a2 < a1)) {
                List<String> reasons = new ArrayList<>();
                if (dif2 > dif1) {
                    reasons.add(String.format(Locale.ROOT, "DIF 谷值 %.3f > %.3f", dif2, dif1));
                }
                if (a2 < a1) {
                    reasons.add(String.format(Locale.ROOT, "绿柱面积 %.2f < %.2f", a2, a1));
                }
                String detail = String.format(Locale.ROOT,
                        "底背驰：下降笔创新低(%.2f < %.2f)，但 %s", b2.low, b1.low, String.join("；", reasons));
                result.bottom = true;
                result.detail.add(detail);
                result.divergences.add(new Divergence2(BOTTOM, b2.end, detail));
            }
        }
        return result;
    }

    /** 背驰判断汇总结果（内部使用） */
    public static final class DivergenceResult {
        public boolean top;
        public boolean bottom;
        public List<String> detail = new ArrayList<>();
        public List<Divergence2> divergences = new ArrayList<>();
    }

    // ------------------------------------------------------------------
    // 第八步：三类买卖点综合判定
    // ------------------------------------------------------------------

    /**
     * 三类买卖点综合判定。
     *
     *  - 第一类买卖点：由背驰确定，是趋势转折的精确位置；
     *  - 第二类买卖点：第一类买卖点之后的次低点/次高点；
     *  - 第三类买点：向上突破中枢后，回踩不跌破中枢上轨(ZG)；
     *  - 第三类卖点：向下突破中枢后，反弹无法回到中枢内部(低于ZD)。
     *
     * @param merged      合并K线序列
     * @param fractals    分型列表
     * @param bis         笔序列
     * @param divergences 背驰信号列表
     * @param biPivots    笔中枢（作为第三类买卖点的参考中枢）
     * @param level       级别键（如 d / 60）
     * @param name        级别中文名（如 日线）
     * @return 买卖点列表（按时间升序，去重）
     */
    public static List<BuySellPoint> findBuySellPoints(List<MergedBar> merged, List<Fractal2> fractals,
                                                       List<Bi2> bis, List<Divergence2> divergences,
                                                       List<Pivot2> biPivots, String level, String name) {
        List<BuySellPoint> points = new ArrayList<>();

        // ---- 第一类买卖点：背驰确认 ----
        for (Divergence2 d : divergences) {
            if (d == null || d.fractal == null) {
                continue;
            }
            if (TOP.equals(d.kind)) {
                points.add(new BuySellPoint("SELL1", d.fractal, level, name,
                        "顶背驰确认，趋势动能衰竭，第一类卖点（趋势转折点）"));
            } else if (BOTTOM.equals(d.kind)) {
                points.add(new BuySellPoint("BUY1", d.fractal, level, name,
                        "底背驰确认，下跌动能衰竭，第一类买点（趋势转折点）"));
            }
        }

        // ---- 第二类买卖点：第一类点之后的次低点/次高点 ----
        // 先按时间排序第一类点，再为每个第一类点找对应的第二类点
        List<BuySellPoint> first = new ArrayList<>();
        for (BuySellPoint p : points) {
            if ("BUY1".equals(p.getType()) || "SELL1".equals(p.getType())) {
                first.add(p);
            }
        }
        first.sort(Comparator.comparingInt(BuySellPoint::getFractalIndex));
        for (BuySellPoint p1 : first) {
            Fractal2 ref = null;
            // 通过分型列表找到对应分型对象（含index）
            for (Fractal2 f : fractals) {
                if (f.index == p1.getFractalIndex()) {
                    ref = f;
                    break;
                }
            }
            if (ref == null) {
                continue;
            }
            if ("BUY1".equals(p1.getType())) {
                // 第一类买点后第一个更高的底分型 = 次低点
                Fractal2 found = null;
                for (Fractal2 f : fractals) {
                    if (f.index <= ref.index) {
                        continue;
                    }
                    if (BOTTOM.equals(f.kind) && f.low > ref.low) {
                        found = f;
                        break;
                    }
                }
                if (found != null) {
                    points.add(new BuySellPoint("BUY2", found, level, name,
                            "第一类买点后首次回踩形成次低点（不创新低），第二类买点"));
                }
            } else {
                // 第一类卖点后第一个更低的顶分型 = 次高点
                Fractal2 found = null;
                for (Fractal2 f : fractals) {
                    if (f.index <= ref.index) {
                        continue;
                    }
                    if (TOP.equals(f.kind) && f.high < ref.high) {
                        found = f;
                        break;
                    }
                }
                if (found != null) {
                    points.add(new BuySellPoint("SELL2", found, level, name,
                            "第一类卖点后首次反弹形成次高点（不创新高），第二类卖点"));
                }
            }
        }

        // ---- 第三类买卖点：中枢突破后回踩/反弹 ----
        if (biPivots != null) {
            for (Pivot2 p : biPivots) {
                if (p.getZg() <= p.getZd()) {
                    continue;
                }
                findType3Points(bis, p, level, name, points);
            }
        }

        // ---- 去重并按时间排序 ----
        Map<String, BuySellPoint> dedup = new LinkedHashMap<>();
        for (BuySellPoint p : points) {
            String key = p.getType() + "@" + p.getFractalIndex();
            dedup.putIfAbsent(key, p);
        }
        List<BuySellPoint> sorted = new ArrayList<>(dedup.values());
        sorted.sort(Comparator.comparingInt(BuySellPoint::getFractalIndex));
        return sorted;
    }

    /** 在一个中枢上寻找第三类买卖点 */
    private static void findType3Points(List<Bi2> bis, Pivot2 pivot, String level, String name,
                                        List<BuySellPoint> points) {
        double zg = pivot.getZg();
        double zd = pivot.getZd();
        int n = bis.size();
        for (int i = 0; i < n; i++) {
            Bi2 b = bis.get(i);
            if (UP.equals(b.direction)) {
                // 向上突破中枢上轨 ZG，随后回踩不跌破 ZG -> 第三类买点
                if (b.end.high > zg && i + 1 < n) {
                    Bi2 pullback = bis.get(i + 1);
                    if (DOWN.equals(pullback.direction) && pullback.end.low > zg) {
                        points.add(new BuySellPoint("BUY3", pullback.end, level, name,
                                String.format(Locale.ROOT,
                                        "向上突破中枢ZG=%.2f后回踩不破（低点%.2f>ZG），第三类买点",
                                        zg, pullback.end.low)));
                    }
                }
            } else {
                // 向下突破中枢下轨 ZD，随后反弹无法回到中枢（高点低于ZD）-> 第三类卖点
                if (b.end.low < zd && i + 1 < n) {
                    Bi2 rebound = bis.get(i + 1);
                    if (UP.equals(rebound.direction) && rebound.end.high < zd) {
                        points.add(new BuySellPoint("SELL3", rebound.end, level, name,
                                String.format(Locale.ROOT,
                                        "向下突破中枢ZD=%.2f后反弹不回中枢（高点%.2f<ZD），第三类卖点",
                                        zd, rebound.end.high)));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 综合分析入口
    // ------------------------------------------------------------------

    /**
     * 第二套缠论综合分析入口。
     *
     * @param klines  原始K线（按时间升序）
     * @param period  K线周期（如 daily / 60min）
     * @param symbol  标的代码
     * @param minGap  笔的最小分型间隔（默认4）
     * @return 完整分析结果
     */
    public static Chan2Result analyze(List<Chan2Kline> klines, String period, String symbol, int minGap) {
        Chan2Result r = new Chan2Result();
        r.setSymbol(symbol);
        r.setPeriod(period);

        if (klines == null || klines.isEmpty()) {
            r.setState("数据不足");
            r.setTrendType(TrendType.UNDEFINED.getLabel());
            r.setBiCount(0);
            r.setSegmentCount(0);
            r.setMergedKlines(0);
            r.setFractalsCount(0);
            r.setBiPivotsCount(0);
            r.setSegmentPivotsCount(0);
            return r;
        }

        // ---- 第一步：K线预处理 ----
        List<MergedBar> merged = mergeKlines(klines);

        // ---- 第二步：分型识别 ----
        List<Fractal2> fractals = detectFractals(merged);

        // ---- 第三步：笔的划分 ----
        List<Bi2> bis = findBis(merged, fractals, minGap);

        // ---- 第四步：线段划分 ----
        List<Segment2> segments = findSegments(bis);

        // ---- 第五步：中枢识别 ----
        List<Pivot2> pivotsBi = findPivotsFromBi(bis);
        List<Pivot2> pivotsSeg = findPivotsFromSegments(segments);

        // ---- 第七步前置：MACD 计算（基于原始K线） ----
        double[] close = new double[klines.size()];
        for (int i = 0; i < klines.size(); i++) {
            close[i] = klines.get(i).close;
        }
        Macd2 macd = computeMacd(close, 12, 26, 9);

        // ---- 第七步：背驰判断 ----
        DivergenceResult divRes = detectDivergence(merged, bis, macd);

        // ---- 第八步：三类买卖点 ----
        List<BuySellPoint> buySellPoints = findBuySellPoints(merged, fractals, bis,
                divRes.divergences, pivotsBi, period, period);

        double lastClose = klines.get(klines.size() - 1).close;
        String lastDate = klines.get(klines.size() - 1).date != null
                ? klines.get(klines.size() - 1).date
                : String.valueOf(klines.size() - 1);

        // ---- 第六步：走势类型判定 ----
        TrendType trendType = judgeTrend(pivotsSeg, lastClose);

        // ---- 当前中枢选择（优先靠近当前价格的本级别中枢） ----
        Pivot2 pivot = null;
        String pivotLevel = null;
        List<Pivot2> nearSeg = nearPivots(pivotsSeg, lastClose);
        List<Pivot2> nearBi = nearPivots(pivotsBi, lastClose);
        if (!nearSeg.isEmpty()) {
            pivot = nearSeg.get(nearSeg.size() - 1);
            pivotLevel = "本级别(线段中枢)";
        } else if (!nearBi.isEmpty()) {
            pivot = nearBi.get(nearBi.size() - 1);
            pivotLevel = "次级别(笔中枢)";
        } else if (!pivotsSeg.isEmpty()) {
            pivot = pivotsSeg.get(pivotsSeg.size() - 1);
            pivotLevel = "本级别(线段中枢)";
        } else if (!pivotsBi.isEmpty()) {
            pivot = pivotsBi.get(pivotsBi.size() - 1);
            pivotLevel = "次级别(笔中枢)";
        }

        // ---- 状态与方向 ----
        String lastBiDir = bis.isEmpty() ? UP : bis.get(bis.size() - 1).direction;
        String state;
        String trendDirection;
        if (trendType == TrendType.UP || trendType == TrendType.DOWN) {
            state = "趋势";
            trendDirection = trendType == TrendType.UP ? "向上" : "向下";
        } else if (pivot != null && lastClose >= pivot.getZd() && lastClose <= pivot.getZg()) {
            state = "中枢震荡";
            trendDirection = UP.equals(lastBiDir) ? "向上" : "向下";
        } else {
            state = "中枢震荡";
            trendDirection = UP.equals(lastBiDir) ? "向上" : "向下";
        }

        // 回填结果
        r.setLastDate(lastDate);
        r.setLastClose(round4(lastClose));
        r.setState(state);
        r.setTrendType(trendType.getLabel());
        r.setTrendDirection(trendDirection);
        r.setPivotLevel(pivotLevel);
        r.setPivotTopZg(pivot != null ? round4(pivot.getZg()) : null);
        r.setPivotBottomZd(pivot != null ? round4(pivot.getZd()) : null);
        r.setPivotSpanStart(pivot != null ? pivot.getStartDate() : null);
        r.setPivotSpanEnd(pivot != null ? pivot.getEndDate() : null);
        r.setPivotSegments(pivot != null ? pivot.getSegments() : 0);
        r.setDivergenceTop(divRes.top);
        r.setDivergenceBottom(divRes.bottom);
        r.setDivergenceDetail(divRes.detail);
        r.setDivergences(divRes.divergences);
        r.setBuySellPoints(buySellPoints);
        r.setBiPivots(pivotsBi);
        r.setSegmentPivots(pivotsSeg);
        r.setSegments(segments);
        r.setBis(bis);
        r.setFractals(fractals);
        r.setMergedBars(merged);
        r.setDif(round4(macd.dif[macd.dif.length - 1]));
        r.setDea(round4(macd.dea[macd.dea.length - 1]));
        r.setHist(round4(macd.hist[macd.hist.length - 1]));
        r.setMergedKlines(merged.size());
        r.setFractalsCount(fractals.size());
        r.setBiCount(bis.size());
        r.setSegmentCount(segments.size());
        r.setBiPivotsCount(pivotsBi.size());
        r.setSegmentPivotsCount(pivotsSeg.size());
        return r;
    }

    /** 过滤出价格位于中枢区间附近（±15%）的中枢 */
    private static List<Pivot2> nearPivots(List<Pivot2> pivots, double lastClose) {
        List<Pivot2> out = new ArrayList<>();
        if (pivots == null) {
            return out;
        }
        for (Pivot2 p : pivots) {
            if (p.getZd() * 0.85 <= lastClose && lastClose <= p.getZg() * 1.15) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    /** 四舍五入到4位小数（与 Python round() 一致的银行家舍入） */
    public static double round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** 格式化价格为字符串（去掉多余0） */
    public static String fmt(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------
    // 报告格式化
    // ------------------------------------------------------------------

    /**
     * 将单级别完整分析结果格式化为可读文本（用于调试/导出）。
     */
    public static String formatReport(Chan2Result r) {
        StringBuilder sb = new StringBuilder();
        String symbol = r.getSymbol() == null || r.getSymbol().isEmpty() ? "(未指定)" : r.getSymbol();
        sb.append("股票/标的: ").append(symbol).append("   周期: ").append(r.getPeriod()).append('\n');
        sb.append("最新: ").append(r.getLastDate()).append("  收盘 ").append(r.getLastClose()).append('\n');
        sb.append("走势类型: ").append(r.getTrendType()).append("   状态: ").append(r.getState())
                .append("   方向: ").append(r.getTrendDirection()).append('\n');
        sb.append("MACD: DIF=").append(r.getDif()).append("  DEA=").append(r.getDea())
                .append("  柱=").append(r.getHist()).append('\n');
        if (r.getPivotTopZg() != null) {
            sb.append("当前中枢(").append(r.getPivotLevel()).append("): ZG=").append(r.getPivotTopZg())
                    .append("  ZD=").append(r.getPivotBottomZd())
                    .append("  区间[").append(r.getPivotSpanStart()).append(", ")
                    .append(r.getPivotSpanEnd()).append("]  由").append(r.getPivotSegments()).append("段构成\n");
        }
        if (r.getDivergenceTop() != null && r.getDivergenceTop()) {
            sb.append("背驰信号: 顶背驰\n");
        }
        if (r.getDivergenceBottom() != null && r.getDivergenceBottom()) {
            sb.append("背驰信号: 底背驰\n");
        }
        for (String d : r.getDivergenceDetail()) {
            sb.append("  - ").append(d).append('\n');
        }
        if (r.getBuySellPoints() != null && !r.getBuySellPoints().isEmpty()) {
            sb.append("买卖点:\n");
            for (BuySellPoint p : r.getBuySellPoints()) {
                sb.append("  - ").append(p.getTypeLabel()).append(" @ ").append(p.getDate())
                        .append("  价格 ").append(fmt(p.getPrice())).append('\n');
            }
        }
        sb.append("结构统计: 合并K线 ").append(r.getMergedKlines())
                .append(" | 分型 ").append(r.getFractalsCount())
                .append(" | 笔 ").append(r.getBiCount())
                .append(" | 线段 ").append(r.getSegmentCount())
                .append(" | 笔中枢 ").append(r.getBiPivotsCount())
                .append(" | 线段中枢 ").append(r.getSegmentPivotsCount());
        return sb.toString();
    }
}
