/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.labelcouncil;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@SuppressWarnings("unchecked")
class AlgorithmTest {
  Map<String, Object> cfg;
  List<Map<String, Object>> data;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @BeforeEach
  void load() throws Exception {
    var fixture =
        new ObjectMapper()
            .readValue(getClass().getResourceAsStream("/static/catalog.json"), Map.class);
    cfg = (Map<String, Object>) fixture.get("settings");
    data = (List<Map<String, Object>>) fixture.get("rows");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void boundaryCases() {
    var x =
        Algorithm.compute(
            cfg,
            List.of(
                Map.of("id", "s", "annotator", "A", "label", "x"),
                Map.of("id", "s", "annotator", "B", "label", "x")));
    assertNull(((Map<?, ?>) x.get("metrics")).get("fleissKappa"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void enterpriseEvidenceAndBoundaries() {
    var result=Algorithm.compute(Map.of("minVotes",2,"agreement",0.8),List.of(
        Map.of("id","a","annotator","1","label","X"),Map.of("id","b","annotator","1","label","X"),
        Map.of("id","b","annotator","2","label","Y"),Map.of("id","c","annotator","1","label","X"),
        Map.of("id","c","annotator","2","label","X"),Map.of("id","c","annotator","3","label","Y")));
    var rows=(List<Map<String,Object>>)result.get("rows");
    assertEquals(List.of("TOO_FEW","TIE","BELOW_THRESHOLD"),rows.stream().map(r->r.get("initialReason")).toList());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void knownResults() {
    var result = Algorithm.compute(cfg, data);
    var m = (Map<String, Object>) result.get("metrics");
    var out = (List<Map<String, Object>>) result.get("rows");
    var first = out.getFirst();
    assertEquals(1, ((Number) m.get("needsReview")).intValue());
    assertEquals(-1.0 / 3, ((Number) m.get("fleissKappa")).doubleValue(), 1e-9);
    assertEquals("", out.get(1).get("label"));
    assertThrows(
        Model.Failure.class,
        () -> Algorithm.compute(cfg, List.of(data.getFirst(), data.getFirst())));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void rejectsInvalidInput() {
    cfg.put("minVotes", 1);
    assertThrows(Model.Failure.class, () -> Algorithm.compute(cfg, data));
  }
}
