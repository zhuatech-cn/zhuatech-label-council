/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.labelcouncil;

import static cn.zhuatech.labelcouncil.Model.*;

import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@RestController
@RequestMapping("/api")
/** 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
public class Api {
  private final Auth auth;
  private final JdbcTemplate db;
  private final ObjectMapper json;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public Api(Auth auth, JdbcTemplate db, ObjectMapper json) {
    this.auth = auth;
    this.db = db;
    this.json = json;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Login(String username, String password) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Batch(String title, Map<String, Object> settings, List<Map<String, Object>> rows) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Review(int version, String state, String note) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/health")
  public Map<String, Object> health() {
    auth.requireReady();
    db.queryForObject("SELECT 1", Integer.class);
    return Map.of("status", "UP", "project", "label-council");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/login")
  public Object login(@RequestBody Login login) {
    return auth.login(login.username(), login.password());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/me")
  public Object me(@RequestHeader(value = "Authorization", required = false) String h) {
    return auth.current(h);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/logout")
  public void logout(@RequestHeader(value = "Authorization", required = false) String h) {
    auth.current(h);
    auth.logout(h);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/me/password")
  @Transactional(noRollbackFor = Failure.class)
  public void rotate(@RequestHeader(value="Authorization",required=false) String h,@RequestBody Map<String,String> data){
    var u=auth.current(h);
    if (!data.keySet().equals(Set.of("currentPassword","newPassword"))) throw new Failure(400,"请提供原密码和新密码");
    auth.rotatePassword(u,data.get("currentPassword"),data.get("newPassword"));
    audit(u,u.id(),"PASSWORD_CHANGED","自助改密，撤销全部会话");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/users")
  public Object users(@RequestHeader(value = "Authorization", required = false) String h) {
    return auth.users(auth.current(h));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/users")
  @Transactional
  public void add(
      @RequestHeader(value = "Authorization", required = false) String h,
      @RequestBody Map<String, String> p) {
    var u = auth.current(h);
    auth.createUser(u, p);
    audit(u, p.get("username"), "CREATE_USER", p.get("role"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PatchMapping("/users/{id}")
  @Transactional
  public void userEdit(
      @RequestHeader(value = "Authorization", required = false) String h,
      @PathVariable String id,
      @RequestBody Map<String, String> p) {
    var u = auth.current(h);
    auth.changeUser(u, id, p);
    audit(u, id, "CHANGE_USER", "fields=" + p.keySet());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/audit")
  public Object auditLog(@RequestHeader(value = "Authorization", required = false) String h) {
    var u = auth.current(h);
    Auth.role(u, "ADMIN");
    return db.queryForList(
        "SELECT actor,subject,action,note,created_at FROM work_audit WHERE tenant=? ORDER BY seq"
            + " DESC LIMIT 100",
        u.tenant());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  void audit(User u, String subject, String action, String note) {
    db.update(
        "INSERT INTO work_audit(tenant,actor,subject,action,note) VALUES(?,?,?,?,?)",
        u.tenant(),
        u.username(),
        subject,
        action,
        note);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/runs")
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Map<String, Object> run(
      @RequestHeader(value = "Authorization", required = false) String h,
      @RequestHeader(value = "Idempotency-Key", defaultValue = "") String key,
      @RequestBody Batch batch) {
    var u = auth.current(h);
    Auth.role(u, "OPERATOR");
    Checks.must(key.matches("[a-zA-Z0-9_-]{8,80}"), "需要 8–80 位 Idempotency-Key");
    Checks.must(
        batch.title() != null && !batch.title().isBlank() && batch.title().length() <= 100,
        "标题须为 1–100 字");
    Checks.must(
        batch.settings() != null
            && batch.rows() != null
            && !batch.rows().isEmpty()
            && batch.rows().size() <= 200
            && !batch.rows().contains(null),
        "每批 1–200 条完整记录");
    Checks.must(batch.settings().keySet().equals(Set.of("minVotes", "agreement")), "参数缺失或包含未知字段");
    int line = 0;
    for (var row : batch.rows()) {
      line++;
      Checks.must(
          row.keySet().equals(Set.of("id", "annotator", "label")), "第 " + line + " 行：字段缺失或包含未知列");
    }
    String input = json.writeValueAsString(batch);
    Checks.must(input.length() <= 250000, "输入过大，请拆分批次");
    String fingerprint = Checks.hash(input);
    // Serialize writes for this workspace so a concurrent retry cannot compute or post twice.
    db.queryForObject(
        "SELECT tenant FROM tenant_guard WHERE tenant=? FOR UPDATE", String.class, u.tenant());
    var prior =
        db.queryForList(
            "SELECT id,fingerprint FROM work_run WHERE tenant=? AND actor_id=? AND request_key=?",
            u.tenant(),
            u.id(),
            key);
    if (!prior.isEmpty()) {
      var r = prior.getFirst();
      require(fingerprint.equals(r.get("fingerprint")), "同一提交键的输入已改变");
      return detail(u, (String) r.get("id"));
    }
    var output = Algorithm.compute(batch.settings(), batch.rows());
    String encoded = json.writeValueAsString(output);
    Checks.must(encoded.length() <= 2000000, "结果过大，请拆分批次");
    String id = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO"
            + " work_run(id,tenant,actor_id,request_key,fingerprint,title,stage,revision,input_json,result_json)"
            + " VALUES(?,?,?,?,?,?,'READY',1,?,?)",
        id,
        u.tenant(),
        u.id(),
        key,
        fingerprint,
        batch.title().strip(),
        input,
        encoded);
    audit(u, id, "RUN", "input sha256=" + fingerprint);
    return detail(u, id);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/runs")
  public Object list(
      @RequestHeader(value = "Authorization", required = false) String h,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "") String state,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "all") String scope) {
    var u = auth.current(h);
    Checks.must(
        page >= 1 && page <= 100000 && Set.of("", "READY", "APPROVED", "REJECTED", "WITHDRAWN").contains(state),
        "筛选或页码错误");
    Checks.must(q.length()<=100 && Set.of("all","mine","reviewable").contains(scope),"标题或业务范围错误");
    if ("reviewable".equals(scope)) Auth.role(u,"REVIEWER");
    String where = " FROM work_run WHERE tenant=? AND (?='' OR stage=?) AND LOCATE(?,title)>0"
        + ("mine".equals(scope) ? " AND actor_id=?" : "reviewable".equals(scope) ? " AND stage='READY' AND actor_id<>?" : "");
    var args=new ArrayList<Object>(List.of(u.tenant(),state,state,q.strip()));
    if(!"all".equals(scope))args.add(u.id());
    long total=db.queryForObject("SELECT COUNT(*)"+where,Long.class,args.toArray());
    args.add((page-1)*20);
    return Map.of(
        "total",
        total,
        "items",
        db.queryForList(
            "SELECT id,title,stage,revision,created_at"
                + where
                + " ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",
            args.toArray()));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @GetMapping("/runs/{id}")
  public Object get(
      @RequestHeader(value = "Authorization", required = false) String h, @PathVariable String id) {
    return detail(auth.current(h), id);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Map<String, Object> detail(User u, String id) {
    var records =
        db.queryForList(
            "SELECT id,title,actor_id,stage,revision,input_json,result_json,created_at FROM"
                + " work_run WHERE tenant=? AND id=?",
            u.tenant(),
            id);
    if (records.isEmpty()) throw new Failure(404, "记录不存在");
    var r = new LinkedHashMap<>(records.getFirst());
    r.put("input", json.readValue((String) r.remove("input_json"), Map.class));
    r.put("result", json.readValue((String) r.remove("result_json"), Map.class));
    r.put(
        "history",
        db.queryForList(
            "SELECT actor,action,note,created_at FROM work_audit WHERE tenant=? AND subject=? ORDER"
                + " BY seq",
            u.tenant(),
            id));
    return r;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/runs/{id}/withdraw")
  @Transactional
  public Map<String, Object> withdraw(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable String id, @RequestBody Withdrawal request) {
    var user = auth.current(authorization);
    Auth.role(user, "OPERATOR");
    var run = detail(user, id);
    if (!user.id().equals(run.get("actor_id"))) throw new Failure(403, "仅提交人可撤回自己的批次");
    Checks.must(request.note() != null && !request.note().isBlank() && request.note().length() <= 1000,
        "请填写撤回原因（最多 1000 字）");
    require(db.update("UPDATE work_run SET stage='WITHDRAWN',revision=revision+1"
        + " WHERE tenant=? AND id=? AND actor_id=? AND stage='READY' AND revision=?",
        user.tenant(), id, user.id(), request.version()) == 1, "批次已办理或版本变化，请刷新");
    audit(user, id, "READY -> WITHDRAWN", request.note().strip());
    return detail(user, id);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Withdrawal(int version, String note) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @PostMapping("/runs/{id}/review")
  @Transactional
  public Object review(
      @RequestHeader(value = "Authorization", required = false) String h,
      @PathVariable String id,
      @RequestBody Review req) {
    var u = auth.current(h);
    Auth.role(u, "REVIEWER");
    var r = detail(u, id);
    Checks.must(
        req.state() != null
            && Set.of("APPROVED", "REJECTED").contains(req.state())
            && req.note() != null
            && !req.note().isBlank()
            && req.note().length() <= 1000,
        "需要有效决定与复核意见");
    if (u.id().equals(r.get("actor_id"))) throw new Failure(403, "禁止自审");
    if (req.state().equals("APPROVED") && unresolved(r) > 0)
      throw new Failure(409, "请先对所有分歧样本逐条裁决");
    require(
        db.update(
                "UPDATE work_run SET stage=?,revision=revision+1 WHERE tenant=? AND id=? AND"
                    + " stage='READY' AND revision=?",
                req.state(),
                u.tenant(),
                id,
                req.version())
            == 1,
        "状态或版本已变化");
    audit(u, id, "READY -> " + req.state(), req.note());
    return detail(u, id);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public record Decisions(int version, String note, Map<String, String> labels) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private int unresolved(Map<String, Object> r) {
    return ((Number) ((Map<?, ?>) ((Map<?, ?>) r.get("result")).get("metrics")).get("needsReview"))
        .intValue();
  }

  /**
   * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ An independent reviewer resolves every disputed item
   * in a single transaction. 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  @SuppressWarnings("unchecked")
  @Transactional
  @PostMapping("/runs/{id}/adjudicate")
  public Object adjudicate(
      @RequestHeader(value = "Authorization", required = false) String h,
      @PathVariable String id,
      @RequestBody Decisions d) {
    var u = auth.current(h);
    Auth.role(u, "REVIEWER");
    var work = detail(u, id);
    if (u.id().equals(work.get("actor_id"))) throw new Failure(403, "禁止自审");
    require("READY".equals(work.get("stage")) && unresolved(work) > 0, "无待裁决样本或批次已完成");
    Checks.must(
        d.note() != null && !d.note().isBlank() && d.note().length() <= 1000 && d.labels() != null,
        "请填写裁决标签及依据");
    var result = (Map<String, Object>) work.get("result");
    var rows = (List<Map<String, Object>>) result.get("rows");
    var pending = new HashSet<String>();
    for (var row : rows)
      if ("REVIEW".equals(row.get("decision"))) pending.add((String) row.get("id"));
    Checks.must(pending.equals(d.labels().keySet()), "必须且只能裁决所有待复核样本");
    for (var row : rows)
      if (pending.contains(row.get("id"))) {
        String label = d.labels().get(row.get("id"));
        Checks.must(
            label != null && ((Map<?, ?>) row.get("tally")).containsKey(label), "裁决标签须在该样本的候选标签中");
        row.put("label", label);
        row.put("decision", "ADJUDICATED");
      }
    var metrics = (Map<String, Object>) result.get("metrics");
    metrics.put("adjudicated", pending.size());
    metrics.put("needsReview", 0);
    require(
        db.update(
                "UPDATE work_run SET result_json=?,stage='APPROVED',revision=revision+1 WHERE"
                    + " tenant=? AND id=? AND stage='READY' AND revision=?",
                json.writeValueAsString(result),
                u.tenant(),
                id,
                d.version())
            == 1,
        "状态或版本已变化");
    audit(u, id, "ADJUDICATED -> APPROVED", d.note());
    return detail(u, id);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @ExceptionHandler(Failure.class)
  public ResponseEntity<?> domainError(Failure e) {
    return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<?> badInput() {
    return ResponseEntity.badRequest().body(Map.of("message", "请求 JSON 格式或字段类型错误"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<?> conflict() {
    return ResponseEntity.status(409).body(Map.of("message", "记录冲突，请刷新后重试"));
  }
}
