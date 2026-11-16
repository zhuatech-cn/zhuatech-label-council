/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.labelcouncil;

import static cn.zhuatech.labelcouncil.Checks.*;

import java.util.*;

/**
 * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 按样本汇集独立投票，输出一致率、多数标签、分歧队列以及适用条件下的 Fleiss κ。
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class Algorithm {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static Map<String, Object> compute(Map<String, Object> cfg, List<Map<String, Object>> rows) {
    int minVotes = integer(cfg, "minVotes", 2, 100);
    double threshold = num(cfg, "agreement", 0.5, 1);
    var votes = new TreeMap<String, Map<String, String>>();
    var totals = new HashMap<String, Integer>();
    for (var r : rows) {
      String id = str(r, "id", false),
          person = str(r, "annotator", false),
          label = str(r, "label", false);
      var item = votes.computeIfAbsent(id, k -> new HashMap<>());
      must(item.putIfAbsent(person, label) == null, "同一样本不能重复计票：" + id);
      totals.merge(label, 1, Integer::sum);
    }
    int agreed = 0, n = votes.firstEntry().getValue().size();
    boolean fixed = n > 1;
    double obs = 0;
    var out = new ArrayList<>();
    for (var e : votes.entrySet()) {
      var tally = new TreeMap<String, Integer>();
      e.getValue().values().forEach(v -> tally.merge(v, 1, Integer::sum));
      int count = e.getValue().size(), high = Collections.max(tally.values());
      boolean tie = tally.values().stream().filter(v -> v == high).count() > 1;
      double ratio = (double) high / count;
      fixed &= count == n;
      if (count > 1)
        for (int c : tally.values()) obs += (double) c * (c - 1) / (count * (count - 1));
      boolean pass = !tie && count >= minVotes && ratio >= threshold;
      if (pass) agreed++;
      String winning =
          pass
              ? tally.entrySet().stream()
                  .filter(v -> v.getValue() == high)
                  .findFirst()
                  .orElseThrow()
                  .getKey()
              : "";
      out.add(
          Map.of(
              "id",
              e.getKey(),
              "votes",
              count,
              "tally",
              tally,
              "agreement",
              ratio,
              "label",
              winning,
              "decision",
              pass ? "CONSENSUS" : "REVIEW",
              "initialReason", count < minVotes ? "TOO_FEW" : tie ? "TIE" : !pass ? "BELOW_THRESHOLD" : "AGREED"));
    }
    double pe =
        totals.values().stream().mapToDouble(v -> Math.pow((double) v / rows.size(), 2)).sum();
    var m = new LinkedHashMap<String, Object>();
    m.put("consensus", agreed);
    m.put("needsReview", votes.size() - agreed);
    m.put("fleissKappa", fixed && pe < 1 ? (obs / votes.size() - pe) / (1 - pe) : null);
    return result(m, out);
  }
}
