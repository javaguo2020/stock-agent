package com.ruoyi.system.agent.util;

import com.ruoyi.system.agent.domain.IndicatorSummary;
import com.ruoyi.system.agent.domain.StockDataContext;
import com.ruoyi.system.chan2.Chan2Engine;
import com.ruoyi.system.chan2.Macd2;
import com.ruoyi.system.domain.StockKlineDTO;
import com.ruoyi.system.domain.StockRealTimePriceDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 技术指标本地计算工具：MA / MACD / KDJ / BOLL / ATR / 量比 / 涨跌停统计 / K线压缩文本。
 *
 * <p>MACD 复用缠论引擎 {@link Chan2Engine#computeMacd}，保证与项目现有计算口径一致。</p>
 */
public final class IndicatorCalculator {

    private static final java.util.Map<String, String> LEVEL_NAMES = java.util.Map.of(
            "5", "5分钟", "15", "15分钟", "30", "30分钟", "60", "60分钟",
            "d", "日线", "w", "周线", "m", "月线");

    private IndicatorCalculator() {
    }

    /**
     * 计算单级别 K 线的技术指标摘要。
     */
    public static IndicatorSummary summarize(List<StockKlineDTO> klines, String level) {
        IndicatorSummary s = new IndicatorSummary();
        s.setLevel(level);
        s.setLevelName(LEVEL_NAMES.getOrDefault(level, level));
        if (klines == null || klines.isEmpty()) {
            return s;
        }
        int n = klines.size();
        StockKlineDTO last = klines.get(n - 1);
        s.setBars(n);
        s.setLastDate(last.getT());
        double close = val(last.getC());
        s.setLastClose(round2(close));
        s.setChangePct(round2(last.getIncreaseRate() == null ? 0 : last.getIncreaseRate()));

        double[] c = closes(klines);

        // 均线
        double ma5 = ma(c, 5), ma10 = ma(c, 10), ma20 = ma(c, 20), ma60 = ma(c, 60);
        s.setMa5(round2(ma5));
        s.setMa10(round2(ma10));
        s.setMa20(round2(ma20));
        s.setMa60(round2(ma60));
        s.setMaPattern(maPattern(ma5, ma10, ma20, ma60));

        // MACD（复用缠论引擎）
        if (n >= 35) {
            Macd2 macd = Chan2Engine.computeMacd(c, 12, 26, 9);
            int i = n - 1;
            double dif = macd.dif[i], dea = macd.dea[i], hist = macd.hist[i];
            s.setDif(round4(dif));
            s.setDea(round4(dea));
            s.setHist(round4(hist));
            s.setMacdSignal(macdSignal(macd));
        } else {
            s.setMacdSignal("数据不足");
        }

        // KDJ(9,3,3)
        double[] k = new double[n], d = new double[n], j = new double[n];
        computeKdj(klines, 9, k, d, j);
        s.setKdjK(round2(k[n - 1]));
        s.setKdjD(round2(d[n - 1]));
        s.setKdjJ(round2(j[n - 1]));
        s.setKdjSignal(kdjSignal(k, d));

        // BOLL(20,2)
        if (n >= 20) {
            double mid = ma20;
            double sd = stdDev(c, n - 20, 20);
            double up = mid + 2 * sd, low = mid - 2 * sd;
            s.setBollUp(round2(up));
            s.setBollMid(round2(mid));
            s.setBollLow(round2(low));
            double band = Math.max(up - low, 1e-9);
            s.setBollPosPct(round2((close - low) / band * 100));
            s.setBollBandwidthPct(round2(band / Math.max(mid, 1e-9) * 100));
            s.setBollSignal(bollSignal(close, up, mid, low, c, n));
        } else {
            s.setBollSignal("数据不足");
        }

        // 量比与量能信号
        if (n >= 6 && last.getV() != null) {
            double sum = 0;
            for (int i = n - 6; i < n - 1; i++) {
                sum += val(klines.get(i).getV());
            }
            double ratio = val(last.getV()) / Math.max(sum / 5, 1e-9);
            s.setVolRatio(round2(ratio));
            s.setVolumeSignal(ratio >= 1.8 ? "显著放量" : ratio >= 1.2 ? "放量" : ratio <= 0.7 ? "缩量" : "平量");
        }

        // ATR(14)
        double atr = atr(klines, 14);
        s.setAtr(round2(atr));
        s.setAtrPct(round2(atr / Math.max(close, 1e-9) * 100));
        return s;
    }

    /**
     * 日线 K 线压缩文本（形态分析 Agent 输入），格式：日期 O/H/L/C 量比。
     */
    public static String dailyDigest(List<StockKlineDTO> klines, int maxBars) {
        if (klines == null || klines.isEmpty()) {
            return "";
        }
        int from = Math.max(0, klines.size() - maxBars);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < klines.size(); i++) {
            StockKlineDTO k = klines.get(i);
            sb.append(compactDate(k.getT()))
                    .append(' ').append(num2(k.getO()))
                    .append('/').append(num2(k.getH()))
                    .append('/').append(num2(k.getL()))
                    .append('/').append(num2(k.getC()))
                    .append(" 量").append(num0(k.getV()))
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * 统计涨跌停表现（基于日线 K 线与当日实时行情）。
     */
    public static void fillLimitUpStats(StockDataContext.LimitUpStats stats, List<StockKlineDTO> daily,
                                        double limitPct, StockRealTimePriceDTO realtime) {
        if (daily == null || daily.isEmpty()) {
            return;
        }
        int n = daily.size();
        double threshold = limitPct - 0.2; // 涨幅达幅度-0.2%视为涨停（四舍五入容差）
        int from = Math.max(0, n - 120);
        int up120 = 0, up20 = 0, down120 = 0, streak = 0;
        String lastUp = null, lastDown = null;
        for (int i = from; i < n; i++) {
            StockKlineDTO k = daily.get(i);
            Float rate = k.getIncreaseRate();
            double r = rate == null ? 0 : rate;
            if (r >= threshold) {
                up120++;
                if (i >= n - 20) {
                    up20++;
                }
                lastUp = k.getT();
            } else if (r <= -threshold) {
                down120++;
                lastDown = k.getT();
            }
        }
        // 连板数：从最后一根往前数连续涨停
        for (int i = n - 1; i >= from; i--) {
            Float rate = daily.get(i).getIncreaseRate();
            double r = rate == null ? 0 : rate;
            if (r >= threshold) {
                streak++;
            } else {
                break;
            }
        }
        stats.setLimitUpCount120(up120);
        stats.setLimitUpCount20(up20);
        stats.setLimitDownCount120(down120);
        stats.setLastLimitUpDate(lastUp);
        stats.setLastLimitDownDate(lastDown);
        stats.setCurrentStreak(streak);
        if (realtime != null && realtime.getPc() != null) {
            if (realtime.getPc() >= threshold) {
                stats.setTodayStatus("涨停");
            } else if (realtime.getPc() <= -threshold) {
                stats.setTodayStatus("跌停");
            } else {
                stats.setTodayStatus("正常");
            }
        } else {
            stats.setTodayStatus("未知");
        }
    }

    // ---------------- 内部计算 ----------------

    private static double[] closes(List<StockKlineDTO> klines) {
        double[] c = new double[klines.size()];
        for (int i = 0; i < c.length; i++) {
            c[i] = val(klines.get(i).getC());
        }
        return c;
    }

    private static double ma(double[] c, int period) {
        int n = c.length;
        if (n < period) {
            return Double.NaN;
        }
        double sum = 0;
        for (int i = n - period; i < n; i++) {
            sum += c[i];
        }
        return sum / period;
    }

    /** 总体标准差（与通达信等主流行情软件口径一致） */
    private static double stdDev(double[] c, int start, int period) {
        double mean = 0;
        for (int i = start; i < start + period; i++) {
            mean += c[i];
        }
        mean /= period;
        double sq = 0;
        for (int i = start; i < start + period; i++) {
            sq += (c[i] - mean) * (c[i] - mean);
        }
        return Math.sqrt(sq / period);
    }

    private static void computeKdj(List<StockKlineDTO> klines, int period,
                                   double[] k, double[] d, double[] j) {
        int len = klines.size();
        double prevK = 50, prevD = 50;
        for (int i = 0; i < len; i++) {
            int from = Math.max(0, i - period + 1);
            double hi = -Double.MAX_VALUE, lo = Double.MAX_VALUE;
            for (int p = from; p <= i; p++) {
                hi = Math.max(hi, val(klines.get(p).getH()));
                lo = Math.min(lo, val(klines.get(p).getL()));
            }
            double rsv = hi > lo ? (val(klines.get(i).getC()) - lo) / (hi - lo) * 100 : 50;
            double kk = 2.0 / 3 * prevK + 1.0 / 3 * rsv;
            double dd = 2.0 / 3 * prevD + 1.0 / 3 * kk;
            k[i] = kk;
            d[i] = dd;
            j[i] = 3 * kk - 2 * dd;
            prevK = kk;
            prevD = dd;
        }
    }

    private static String kdjSignal(double[] k, double[] d) {
        int n = k.length;
        if (n < 2) {
            return "数据不足";
        }
        double kNow = k[n - 1], dNow = d[n - 1];
        double kPrev = k[n - 2], dPrev = d[n - 2];
        String cross = null;
        if (kPrev <= dPrev && kNow > dNow) {
            cross = "金叉";
        } else if (kPrev >= dPrev && kNow < dNow) {
            cross = "死叉";
        }
        if (kNow > 80) {
            return cross != null ? "超买区" + cross : "超买区";
        }
        if (kNow < 20) {
            return cross != null ? "超卖区" + cross : "超卖区";
        }
        return cross != null ? cross : "中性区";
    }

    private static String macdSignal(Macd2 macd) {
        int n = macd.dif.length;
        for (int i = n - 1; i > 0; i--) {
            double diffNow = macd.dif[i] - macd.dea[i];
            double diffPrev = macd.dif[i - 1] - macd.dea[i - 1];
            if (diffNow > 0 && diffPrev <= 0) {
                return "金叉(" + (n - 1 - i) + "根前)";
            }
            if (diffNow < 0 && diffPrev >= 0) {
                return "死叉(" + (n - 1 - i) + "根前)";
            }
        }
        return macd.dif[n - 1] > macd.dea[n - 1] ? "DIF在DEA上方运行" : "DIF在DEA下方运行";
    }

    private static String bollSignal(double close, double up, double mid, double low, double[] c, int n) {
        if (close > up) {
            return "突破上轨";
        }
        if (close < low) {
            return "跌破下轨";
        }
        // 收口判断：当前带宽与近60根内最宽带宽比较
        int from = Math.max(0, n - 60);
        double maxBand = 0;
        for (int end = 20; end <= n; end++) {
            double m = 0;
            for (int i = end - 20; i < end; i++) {
                m += c[i];
            }
            m /= 20;
            double sd = 0;
            for (int i = end - 20; i < end; i++) {
                sd += (c[i] - m) * (c[i] - m);
            }
            sd = Math.sqrt(sd / 20);
            maxBand = Math.max(maxBand, 4 * sd / Math.max(m, 1e-9));
        }
        double curBand = (up - low) / Math.max(mid, 1e-9);
        if (maxBand > 0 && curBand < maxBand * 0.4 && from < n) {
            return "轨道收口(带宽" + String.format("%.1f", curBand * 100) + "%)";
        }
        return close >= mid ? "中轨上方" : "中轨下方";
    }

    private static String maPattern(double ma5, double ma10, double ma20, double ma60) {
        boolean has60 = !Double.isNaN(ma60);
        if (ma5 > ma10 && ma10 > ma20 && (!has60 || ma20 > ma60)) {
            return "多头排列";
        }
        if (ma5 < ma10 && ma10 < ma20 && (!has60 || ma20 < ma60)) {
            return "空头排列";
        }
        double base = Math.max(ma20, 1e-9);
        double disperse = (max(ma5, ma10, ma20, has60 ? ma60 : ma20) - min(ma5, ma10, ma20, has60 ? ma60 : ma20)) / base;
        if (disperse < 0.02) {
            return "均线粘合(离散度" + String.format("%.1f", disperse * 100) + "%)";
        }
        return "多空交织";
    }

    private static double max(double... v) {
        double m = -Double.MAX_VALUE;
        for (double x : v) {
            m = Math.max(m, x);
        }
        return m;
    }

    private static double min(double... v) {
        double m = Double.MAX_VALUE;
        for (double x : v) {
            m = Math.min(m, x);
        }
        return m;
    }

    private static double atr(List<StockKlineDTO> klines, int period) {
        int n = klines.size();
        if (n < 2) {
            return 0;
        }
        List<Double> trs = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            double h = val(klines.get(i).getH());
            double l = val(klines.get(i).getL());
            double pc = val(klines.get(i - 1).getC());
            trs.add(Math.max(h - l, Math.max(Math.abs(h - pc), Math.abs(l - pc))));
        }
        int from = Math.max(0, trs.size() - period);
        double sum = 0;
        for (int i = from; i < trs.size(); i++) {
            sum += trs.get(i);
        }
        return sum / (trs.size() - from);
    }

    private static double val(Float f) {
        return f == null ? 0 : f;
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
    }

    private static double round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_EVEN).doubleValue();
    }

    private static String num2(Float f) {
        return f == null ? "-" : BigDecimal.valueOf(f).setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    private static String num0(Float f) {
        return f == null ? "-" : BigDecimal.valueOf(f).setScale(0, RoundingMode.HALF_EVEN).toPlainString();
    }

    /** 日期压缩：2025-08-27 / 20250827 -> 0827 */
    private static String compactDate(String t) {
        if (t == null) {
            return "-";
        }
        String digits = t.replace("-", "").replace("/", "").replace(":", "").replace(" ", "");
        if (digits.length() >= 8) {
            return digits.substring(4, 8);
        }
        return digits;
    }
}
