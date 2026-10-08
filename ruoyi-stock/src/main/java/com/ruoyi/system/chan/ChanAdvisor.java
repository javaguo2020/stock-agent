package com.ruoyi.system.chan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 操作建议生成器（代码模板版，确定性）。对应 Python advisor.py。
 *
 * 输入：多级别分析结果列表（ChanLevelResult），输出结构化建议（持有者/空仓者/综合评估）。
 * 所有价格位（中枢ZG/ZD、止盈区、止损位、突破位）均来自代码计算，不编造。
 */
public final class ChanAdvisor {

    private ChanAdvisor() {
    }

    // ------------------------------------------------------------------
    // 价格格式化与区间
    // ------------------------------------------------------------------

    private static String fmt(Double p) {
        if (p == null) {
            return "None";
        }
        double a = Math.abs(p);
        int scale = a >= 100 ? 1 : 2;
        return BigDecimal.valueOf(p).setScale(scale, RoundingMode.HALF_EVEN)
                .stripTrailingZeros().toPlainString();
    }

    private static String band(Double center, double pct) {
        if (center == null) {
            return null;
        }
        double lo = center * (1 - pct);
        double hi = center * (1 + pct);
        return fmt(lo) + "-" + fmt(hi);
    }

    private static String tpZone(Double close, Double recentHigh) {
        if (close == null) {
            return null;
        }
        double hi = recentHigh != null ? recentHigh : close;
        double hi2 = Math.max(hi, close);
        double lo = Math.min(close, hi2);
        return fmt(lo) + "-" + fmt(hi2 * 1.02);
    }

    private static Double gainPct(Double close, Double recentLow) {
        if (close == null || recentLow == null || recentLow <= 0) {
            return null;
        }
        return (close / recentLow - 1.0) * 100.0;
    }

    // ------------------------------------------------------------------
    // 信号提取
    // ------------------------------------------------------------------

    private static boolean up(ChanLevelResult r) {
        return r != null && "趋势".equals(r.getState()) && "向上".equals(r.getTrendDirection());
    }

    private static boolean divTop(ChanLevelResult r) {
        return r != null && Boolean.TRUE.equals(r.getDivergenceTop());
    }

    private static boolean divBot(ChanLevelResult r) {
        return r != null && Boolean.TRUE.equals(r.getDivergenceBottom());
    }

    private static Double zg(ChanLevelResult r) {
        return r == null ? null : r.getPivotTopZg();
    }

    private static Double zd(ChanLevelResult r) {
        return r == null ? null : r.getPivotBottomZd();
    }

    private static final class Support {
        final double price;
        final String label;

        Support(double price, String label) {
            this.price = price;
            this.label = label;
        }
    }

    private static Support support(ChanLevelResult r, Double price) {
        if (r == null || price == null) {
            return null;
        }
        Double zg = zg(r);
        Double zd = zd(r);
        if (zg == null || zd == null) {
            return null;
        }
        double p = price;
        if (p > zg) {
            return new Support(zg, r.getName() + "中枢ZG");
        }
        if (p >= zd) {
            return new Support(zd, r.getName() + "中枢ZD");
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 建议生成
    // ------------------------------------------------------------------

    public static ChanAdvice generateAdvice(List<ChanLevelResult> results, String name) {
        Map<String, ChanLevelResult> rMap = new HashMap<>();
        for (ChanLevelResult r : results) {
            if (r != null && r.getError() == null) {
                rMap.put(r.getLevel(), r);
            }
        }
        ChanLevelResult r5 = rMap.get("5");
        ChanLevelResult r15 = rMap.get("15");
        ChanLevelResult r30 = rMap.get("30");
        ChanLevelResult r60 = rMap.get("60");
        ChanLevelResult rd = rMap.get("d");
        ChanLevelResult rm = rMap.get("m");
        ChanLevelResult ry = rMap.get("y");

        int midUp = 0;
        for (ChanLevelResult r : new ChanLevelResult[]{r15, r30, r60}) {
            if (up(r)) {
                midUp++;
            }
        }
        int shortUp = 0;
        for (ChanLevelResult r : new ChanLevelResult[]{r5, r15}) {
            if (up(r)) {
                shortUp++;
            }
        }
        int longUp = 0;
        for (ChanLevelResult r : new ChanLevelResult[]{rm, ry}) {
            if (up(r)) {
                longUp++;
            }
        }

        boolean top5 = divTop(r5);
        boolean bot5 = divBot(r5);

        Double dClose = rd == null ? null : rd.getLastClose();
        Double dRh20 = rd == null ? null : rd.getRecentHigh20();
        Double dRl20 = rd == null ? null : rd.getRecentLow20();

        Double s5Close = r5 == null ? null : r5.getLastClose();
        Double s5Rh = r5 == null ? null : r5.getRecentHigh20();
        Double s5Rl = r5 == null ? null : r5.getRecentLow20();

        Double price = dClose != null ? dClose : s5Close;

        List<String> holder = new ArrayList<>();
        List<String> buyer = new ArrayList<>();

        // 1) 底仓（中线仓位）
        if (midUp >= 2) {
            StringBuilder line = new StringBuilder("底仓（中线仓位）可继续持有：15分钟、30分钟、60分钟共 "
                    + midUp + "/3 周期向上共振，中期趋势未破坏，底仓应保持不动。");
            if (up(rd)) {
                line.append("日线亦处多头，可维持当前仓位。");
            } else {
                line.append("注意日线已转弱，仓位不宜再增。");
            }
            holder.add(line.toString());
        } else {
            holder.add("底仓（中线仓位）需谨慎：15/30/60 分钟中期共振不足（" + midUp + "/3 向上），"
                    + "若日线走弱应适度降低仓位，等企稳再回补。");
        }

        // 2) 短线仓位
        if (top5) {
            Double gain = gainPct(s5Close, s5Rl);
            String tp = tpZone(s5Close, s5Rh);
            StringBuilder base = new StringBuilder("短线仓位应考虑减仓：5分钟顶背驰信号明确，短期涨幅已较充分");
            if (gain != null) {
                base.append("（自低点 ").append(fmt(s5Rl)).append(" 反弹约 ")
                        .append(String.format(Locale.ROOT, "%.0f", gain)).append("%）");
            }
            if (tp != null) {
                base.append("，短线仓位可在 ").append(tp).append(" 区间分批止盈");
            }
            holder.add(base + "。");
        } else if (bot5) {
            holder.add("短线仓位可持有：5分钟出现底背驰，短线止跌信号，可等待反弹后再减。");
        } else {
            holder.add("短线仓位可持有：5分钟未见明显背驰，跟随日线方向即可。");
        }

        // 3) 止损纪律
        Support supportRef = null;
        for (ChanLevelResult rr : new ChanLevelResult[]{r15, r30, r60, r5}) {
            Support s = support(rr, price);
            if (s != null) {
                supportRef = s;
                break;
            }
        }
        if (supportRef != null) {
            holder.add("止损纪律：若股价有效跌破 " + fmt(supportRef.price) + "（" + supportRef.label
                    + "），应考虑进一步降低仓位。");
        } else if (dRl20 != null) {
            holder.add("止损纪律：跌破最近低点 " + fmt(dRl20) + " 应无条件止损。");
        } else {
            holder.add("止损纪律：跌破最近低点应无条件止损。");
        }

        // 4) 切勿追高
        Double cur = dClose != null ? dClose : s5Close;
        Double hi = dRh20 != null ? dRh20 : s5Rh;
        boolean nearHigh = cur != null && hi != null && cur >= hi * 0.995;
        if (top5) {
            if (cur != null && hi != null) {
                buyer.add("切勿追高：5分钟顶背驰，且当前价 " + fmt(cur) + " 已接近近期高点 " + fmt(hi)
                        + "，追入性价比极低，回调风险远大于上行空间。");
            } else {
                buyer.add("切勿追高：5分钟顶背驰，短期回调风险大于上行空间，追入性价比低。");
            }
        } else if (nearHigh) {
            buyer.add("切勿追高：当前价 " + fmt(cur) + " 已接近近期高点 " + fmt(hi) + "，追入性价比不高，等待回调更稳妥。");
        } else {
            buyer.add("可逢回调分批介入，不必急于追价。");
        }

        // 5) 等待回踩确认
        if (supportRef != null) {
            buyer.add("等待回踩确认：理想的介入时机是股价回踩 " + band(supportRef.price, 0.005)
                    + " 区域（" + supportRef.label + "附近）且 5分钟级别出现底背驰信号后再考虑介入。");
        } else {
            buyer.add("等待企稳确认：当前价格已远离近期所有中枢支撑，处于调整中，"
                    + "需等待 5 分钟级别出现底背驰企稳信号后再考虑介入，勿左侧抄底。");
        }

        // 6) 分批建仓
        Double deepZg = null;
        Double deepZd = null;
        String deepName = null;
        for (ChanLevelResult rr : new ChanLevelResult[]{r60, r30, r15, r5}) {
            Double z = zg(rr);
            Double d = zd(rr);
            if (z != null && price != null && z < price) {
                deepZg = z;
                deepZd = d;
                deepName = rr.getName();
                break;
            }
        }
        if (deepZg != null) {
            String stop = (deepZd != null && deepZd != 0.0) ? fmt(deepZd) : fmt(deepZg * 0.99);
            buyer.add("分批建仓：若回踩至 " + band(deepZg, 0.005) + " 区间（" + deepName
                    + "中枢ZG附近），可考虑轻仓试多，止损设在 " + stop + " 下方。");
        } else {
            buyer.add("分批建仓：暂不急于建仓，等待价格形成新的低位中枢或企稳信号后再分批介入。");
        }

        // 7) 趋势跟踪
        Double resistance = null;
        if (dRh20 != null) {
            resistance = Math.max(resistance == null ? 0.0 : resistance, dRh20);
        }
        if (s5Rh != null) {
            resistance = Math.max(resistance == null ? 0.0 : resistance, s5Rh);
        }
        if (resistance != null) {
            buyer.add("趋势跟踪：若股价放量突破 " + fmt(resistance * 1.02) + " 并站稳，则短期回调风险解除，可考虑右侧追入。");
        } else {
            buyer.add("趋势跟踪：等待放量突破近期高点后再右侧介入。");
        }

        // ---------- 综合评估 ----------
        String shortTxt;
        if (top5) {
            shortTxt = "短期（1-3天）：5分钟顶背驰压制，回调概率较大，需警惕获利盘涌出。";
        } else if (shortUp >= 1) {
            shortTxt = "短期（1-3天）：短周期向上（" + shortUp + "/2），短线动能尚可，留意 5 分钟是否出现顶背驰。";
        } else {
            shortTxt = "短期（1-3天）：短周期偏弱，以观望或逢反弹减仓为主。";
        }

        String midTxt;
        if (midUp >= 2) {
            midTxt = "中期（1-4周）：15-60分钟 " + midUp + "/3 周期向上，中期趋势向好，回调后仍是机会。";
        } else if (midUp == 1) {
            midTxt = "中期（1-4周）：15-60分钟仅 " + midUp + "/3 周期向上，多空分歧，以轻仓为主。";
        } else {
            midTxt = "中期（1-4周）：15-60分钟中期信号偏空，反弹以减仓为主。";
        }

        String longTxt;
        if (longUp == 2 && !(divTop(rm) || divTop(ry))) {
            longTxt = "长期（月线以上）：月线、年线均为多头无背驰，大方向向上。";
        } else if (longUp == 2) {
            longTxt = "长期（月线以上）：月线、年线多头，但需留意高位背驰风险。";
        } else if (longUp == 1) {
            longTxt = "长期（月线以上）：月线、年线方向分化，长期趋势有待确认。";
        } else {
            longTxt = "长期（月线以上）：月线、年线偏空，大级别风险需重视。";
        }

        String sTag = (top5 || !up(r5)) ? "空" : "多";
        String lTag = longUp == 2 ? "多" : (longUp == 0 ? "空" : "分歧");
        String mTag = midUp >= 2 ? "多" : (midUp == 0 ? "空" : "分歧");
        String tag = "短" + sTag;
        if (!mTag.equals(lTag)) {
            tag += "中" + mTag;
        }
        tag += "长" + lTag;

        String stock = (name == null || name.isEmpty()) ? "该股" : name;
        String overall;
        if ("空".equals(lTag)) {
            overall = "当前" + stock + "处于“" + tag + "”格局：大方向偏空，任何反弹都以减仓/回避为主，严格控制仓位。";
        } else if ("空".equals(mTag) && "空".equals(sTag)) {
            overall = "当前" + stock + "处于“" + tag + "”格局：短期与中期信号均偏空，以防守为主，反弹减仓，等待企稳信号。";
        } else if ("空".equals(mTag)) {
            overall = "当前" + stock + "处于“" + tag + "”格局：中期信号偏弱，反弹以减仓为主，谨防冲高回落。";
        } else if ("空".equals(sTag) && !"空".equals(mTag)) {
            overall = "当前" + stock + "处于“" + tag + "”的典型技术格局：短期回调压力与中期上行并存，操作上以中枢为锚、高抛低吸，严格执行止损。";
        } else if ("多".equals(sTag) && "多".equals(mTag) && "多".equals(lTag)) {
            overall = "当前" + stock + "处于“" + tag + "”的强势格局：多周期共振向上，可持股待涨、回踩分批介入。";
        } else {
            overall = "当前" + stock + "处于“" + tag + "”格局：多空信号分化，建议轻仓观望、以中枢为锚等待方向明朗。";
        }

        ChanAdvice advice = new ChanAdvice();
        advice.setTag(tag);
        advice.setHolder(holder);
        advice.setBuyer(buyer);
        ChanAdvice.Assessment asm = new ChanAdvice.Assessment();
        asm.setShortTerm(shortTxt);
        asm.setMidTerm(midTxt);
        asm.setLongTerm(longTxt);
        asm.setOverall(overall);
        advice.setAssessment(asm);
        return advice;
    }

    public static String formatAdvice(ChanAdvice advice) {
        StringBuilder sb = new StringBuilder();
        sb.append("===== 操作建议 =====\n\n");
        sb.append("🟢 持有者\n");
        int i = 1;
        for (String t : advice.getHolder()) {
            sb.append(i++).append(". ").append(t).append('\n');
        }
        sb.append('\n');
        sb.append("🔴 空仓/想买入者\n");
        i = 1;
        for (String t : advice.getBuyer()) {
            sb.append(i++).append(". ").append(t).append('\n');
        }
        sb.append('\n');
        sb.append("综合评估\n");
        ChanAdvice.Assessment asm = advice.getAssessment();
        sb.append("- ").append(asm.getShortTerm()).append('\n');
        sb.append("- ").append(asm.getMidTerm()).append('\n');
        sb.append("- ").append(asm.getLongTerm()).append('\n');
        sb.append("- ").append(asm.getOverall());
        return sb.toString();
    }
}
