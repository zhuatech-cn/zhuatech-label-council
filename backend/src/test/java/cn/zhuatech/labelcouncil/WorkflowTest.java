/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.labelcouncil;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:acceptance;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
      "app.admin-password=Integration-test-only-2026!"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("unchecked")
/** 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
class WorkflowTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  final HttpClient http = HttpClient.newHttpClient();
  String admin, operator, viewer, reviewer, other;
  Map<String, Object> batch;

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  record Reply(int code, Map<String, Object> value) {}

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  Reply req(String method, String path, String token, Object body, String key) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
            .timeout(java.time.Duration.ofSeconds(12));
    if (token != null) b.header("Authorization", "Bearer " + token);
    if (key != null) b.header("Idempotency-Key", key);
    if (body != null) b.header("Content-Type", "application/json");
    var r =
        http.send(
            b.method(
                    method,
                    body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    var data = r.body().isBlank() ? Map.of() : json.readValue(r.body(), Object.class);
    return new Reply(r.statusCode(), data instanceof Map ? (Map) data : Map.of("items", data));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  String login(String name) throws Exception {
    return (String)
        req(
                "POST",
                "/login",
                null,
                Map.of("username", name, "password", "Integration-test-only-2026!"),
                null)
            .value()
            .get("token");
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @BeforeAll
  void setup() throws Exception {
    admin = login("admin");
    assertNotNull(admin);
    for (String role : List.of("OPERATOR", "VIEWER", "REVIEWER")) {
      assertEquals(
          200,
          req(
                  "POST",
                  "/users",
                  admin,
                  Map.of(
                      "username",
                      role.toLowerCase(Locale.ROOT),
                      "password",
                      "Integration-test-only-2026!",
                      "role",
                      role),
                  null)
              .code());
    }
    operator = login("operator");
    viewer = login("viewer");
    reviewer = login("reviewer");
    db.update("INSERT INTO tenant_guard VALUES('separate')");
    db.update(
        "INSERT INTO app_user(id,tenant,username,password_hash,role,active) VALUES(?,?,?,?,?,true)",
        UUID.randomUUID().toString(),
        "separate",
        "other",
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Integration-test-only-2026!"),
        "ADMIN");
    other = login("other");
    var cat = json.readValue(getClass().getResourceAsStream("/static/catalog.json"), Map.class);
    batch = Map.of("title", "季度验收", "settings", cat.get("settings"), "rows", cat.get("rows"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void passwordRotationRevokesEverySessionAndKeepsHistorySecretFree() throws Exception {
    String initial="Integration-test-only-2026!", updated="New-integration-only-secret";
    assertEquals(200,req("POST","/users",admin,Map.of("username","rotate-test","role","VIEWER","password",initial),null).code());
    String first=login("rotate-test"), second=login("rotate-test");assertNotEquals(first,second);
    assertEquals(403,req("POST","/me/password",first,Map.of("currentPassword","wrong","newPassword",updated),null).code());
    assertEquals(400,req("POST","/me/password",first,Map.of("currentPassword",initial,"newPassword","            "),null).code());
    assertEquals(200,req("GET","/me",second,null,null).code());
    assertEquals(200,req("POST","/me/password",first,Map.of("currentPassword",initial,"newPassword",updated),null).code());
    for(String t:List.of(first,second))assertEquals(401,req("GET","/me",t,null,null).code());
    assertEquals(401,req("POST","/login",null,Map.of("username","rotate-test","password",initial),null).code());
    assertEquals(200,req("POST","/login",null,Map.of("username","rotate-test","password",updated),null).code());
    String log=req("GET","/audit",admin,null,null).value().toString();assertTrue(log.contains("PASSWORD_CHANGED"));assertFalse(log.contains(initial));assertFalse(log.contains(updated));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void scopedQueueMatchesTitleLiterallyAndHonoursIsolation() throws Exception {
    var b=new HashMap<>(batch);b.put("title","队列_%fixture");
    var r=req("POST","/runs",operator,b,"queue-filter-fixture");assertEquals(200,r.code());
    String path="/runs?q="+java.net.URLEncoder.encode("_%fixture",java.nio.charset.StandardCharsets.UTF_8);
    assertEquals(1,req("GET",path+"&scope=mine",operator,null,null).value().get("total"));
    assertEquals(0,req("GET",path+"&scope=mine",reviewer,null,null).value().get("total"));
    assertEquals(1,req("GET",path+"&scope=reviewable",reviewer,null,null).value().get("total"));
    assertEquals(403,req("GET",path+"&scope=reviewable",viewer,null,null).code());
    assertEquals(0,req("GET",path,other,null,null).value().get("total"));
    assertEquals(200,req("POST","/runs/"+r.value().get("id")+"/review",reviewer,Map.of("version",1,"state","REJECTED","note","queue test"),null).code());
    assertEquals(0,req("GET",path+"&scope=reviewable",reviewer,null,null).value().get("total"));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void withdrawalKeepsHistoryAndEnforcesOwnershipAndVersion() throws Exception {
    var original = req("POST", "/runs", operator, batch, "withdraw-fixture");
    assertEquals(200, original.code());
    String path = "/runs/" + original.value().get("id");
    var valid = Map.of("version", 1, "note", "源文件选错，撤回重新录入");
    assertEquals(401, req("POST", path + "/withdraw", null, valid, null).code());
    assertEquals(404, req("POST", path + "/withdraw", other, valid, null).code());
    assertEquals(403, req("POST", path + "/withdraw", admin, valid, null).code());
    assertEquals(403, req("POST", path + "/withdraw", viewer, valid, null).code());
    assertEquals(400, req("POST", path + "/withdraw", operator, Map.of("version",1,"note","  "), null).code());
    assertEquals(409, req("POST", path + "/withdraw", operator, Map.of("version",2,"note","冲突"), null).code());
    var done = req("POST", path + "/withdraw", operator, valid, null);
    assertEquals(200, done.code());
    assertEquals("WITHDRAWN", done.value().get("stage"));
    assertEquals(2, done.value().get("revision"));
    assertEquals(original.value().get("result"), done.value().get("result"));
    assertEquals(original.value().get("input"), done.value().get("input"));
    assertTrue(done.value().get("history").toString().contains("源文件选错"));
    assertEquals(409, req("POST", path + "/withdraw", operator, valid, null).code());
    assertEquals(409, req("POST", path + "/review", reviewer, Map.of("version",2,"state","REJECTED","note","too late"), null).code());
    var list = req("GET","/runs?state=WITHDRAWN&scope=mine",operator,null,null);
    assertEquals(200,list.code());
    assertTrue(list.value().get("items").toString().contains(original.value().get("id").toString()));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void onlyOneConcurrentWithdrawalOrReviewCanWin() throws Exception {
    var created = req("POST","/runs",operator,batch,"workflow-race-key");
    String path = "/runs/" + created.value().get("id");
    var gate = new CyclicBarrier(2);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var a = workers.submit(() -> { gate.await(); return req("POST",path+"/withdraw",operator,Map.of("version",1,"note","重新核对"),null); });
      var b = workers.submit(() -> { gate.await(); return req("POST",path+"/review",reviewer,Map.of("version",1,"state","REJECTED","note","退回"),null); });
      assertEquals(Set.of(200,409), Set.of(a.get(15,TimeUnit.SECONDS).code(), b.get(15,TimeUnit.SECONDS).code()));
    }
    var detail = req("GET",path,viewer,null,null).value();
    assertEquals(2,detail.get("revision"));
    assertEquals(2,((List<?>)detail.get("history")).size());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void bootstrapReadinessIsExplicit() {
    Auth cold = new Auth(db, "");
    assertEquals(503, assertThrows(Model.Failure.class, cold::requireReady).status);
    assertEquals(
        503, assertThrows(Model.Failure.class, () -> cold.login("admin", "unused")).status);
    cold.run(null);
    assertDoesNotThrow(cold::requireReady);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void loginAndRoles() throws Exception {
    assertEquals(401, req("GET", "/runs", null, null, null).code());
    assertEquals(403, req("POST", "/runs", viewer, batch, "viewer-test").code());
    assertEquals(403, req("GET", "/audit", operator, null, null).code());
    assertEquals(403, req("GET", "/users", viewer, null, null).code());
    assertEquals(
        401,
        req("POST", "/login", null, Map.of("username", "admin", "password", "incorrect"), null)
            .code());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void atomicReviewAndIsolation() throws Exception {
    var created = req("POST", "/runs", admin, batch, "review-fixture");
    assertEquals(200, created.code(), created.value().toString());
    String id = (String) created.value().get("id");
    var decision = Map.of("state", "APPROVED", "note", "核对样本口径", "version", 1);
    assertEquals(403, req("POST", "/runs/" + id + "/review", admin, decision, null).code());
    assertEquals(404, req("GET", "/runs/" + id, other, null, null).code());
    assertEquals(404, req("POST", "/runs/" + id + "/review", other, decision, null).code());
    assertEquals(0, req("GET", "/runs", other, null, null).value().get("total"));
    assertEquals(200, req("GET", "/runs/" + id, viewer, null, null).code());
    assertEquals(409, req("POST", "/runs/" + id + "/review", reviewer, decision, null).code());
    var bad = Map.of("version", 1, "note", "原文复核", "labels", Map.of("I2", "未出现的标签"));
    assertEquals(400, req("POST", "/runs/" + id + "/adjudicate", reviewer, bad, null).code());
    var resolve = Map.of("version", 1, "note", "原文复核", "labels", Map.of("I2", "投诉"));
    var checked = req("POST", "/runs/" + id + "/adjudicate", reviewer, resolve, null);
    assertEquals(200, checked.code());
    assertEquals("APPROVED", checked.value().get("stage"));
    assertEquals(2, ((List<?>) checked.value().get("history")).size());
    var row = ((List<Map<?, ?>>) ((Map<?, ?>) checked.value().get("result")).get("rows")).getLast();
    assertEquals("投诉", row.get("label"));
    assertEquals("ADJUDICATED", row.get("decision"));
    assertEquals(409, req("POST", "/runs/" + id + "/review", reviewer, decision, null).code());
    assertEquals(200, req("GET", "/runs?state=APPROVED", viewer, null, null).code());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void concurrentRetryAndConflict() throws Exception {
    try (var pool = Executors.newFixedThreadPool(3)) {
      var work = new ArrayList<Future<Reply>>();
      for (int i = 0; i < 3; i++)
        work.add(pool.submit(() -> req("POST", "/runs", operator, batch, "same-concurrent-key")));
      var ids = new HashSet<>();
      for (var f : work) {
        var r = f.get(15, TimeUnit.SECONDS);
        assertEquals(200, r.code(), r.value().toString());
        ids.add(r.value().get("id"));
      }
      assertEquals(1, ids.size());
    }
    var changed = new HashMap<>(batch);
    changed.put("title", "changed");
    assertEquals(409, req("POST", "/runs", operator, changed, "same-concurrent-key").code());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM work_run WHERE request_key='same-concurrent-key'",
            Integer.class));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void invalidBatchLeavesNoPartialRows() throws Exception {
    var bad = new HashMap<>(batch);
    bad.put("rows", List.of(Map.of("unexpected", 42)));
    assertEquals(400, req("POST", "/runs", operator, bad, "invalid-test").code());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM work_run WHERE request_key='invalid-test'", Integer.class));
    assertEquals(400, req("GET", "/runs?page=0", viewer, null, null).code());
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void expiredLockStartsANewFailureWindowAndAdminCanUnlock() throws Exception {
    String name="locked-person", initial="Integration-test-only-2026!";
    assertEquals(200,req("POST","/users",admin,Map.of("username",name,"role","VIEWER","password",initial),null).code());
    String id=db.queryForObject("SELECT id FROM app_user WHERE username=?",String.class,name);
    String activeSession=login(name);
    assertEquals(200,req("PATCH","/users/"+id,admin,Map.of("role","VIEWER"),null).code());
    assertEquals(200,req("GET","/me",activeSession,null,null).code(),"a no-op must not revoke sessions");
    assertEquals(400,req("PATCH","/users/"+id,admin,Map.of("password",""),null).code());
    db.update("UPDATE app_user SET failures=5,locked_until=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(5)),id);
    var wrong=Map.of("username",name,"password","incorrect");
    for(int i=0;i<5;i++) assertEquals(401,req("POST","/login",null,wrong,null).code(),"attempt "+i);
    assertEquals(429,req("POST","/login",null,Map.of("username",name,"password",initial),null).code());
    String path="/users/"+id+"/unlock";
    assertEquals(403,req("POST",path,reviewer,Map.of("note","核对身份"),null).code());
    assertEquals(404,req("POST",path,other,Map.of("note","核对身份"),null).code());
    assertEquals(400,req("POST",path,admin,Map.of("note"," "),null).code());
    assertEquals(200,req("POST",path,admin,Map.of("note","已线下核实账号本人"),null).code());
    assertEquals(409,req("POST",path,admin,Map.of("note","重复操作"),null).code());
    assertNotNull(login(name));assertEquals(401,req("GET","/me",activeSession,null,null).code());
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_audit WHERE subject=? AND action='UNLOCK_USER'",Integer.class,id));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void concurrentAdminHandoverPreservesAnActiveAdministrator() throws Exception {
    db.update("INSERT INTO tenant_guard VALUES('handover')");
    String pw="Integration-test-only-2026!", a=UUID.randomUUID().toString(), b=UUID.randomUUID().toString();
    var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    for(var entry:Map.of(a,"handover-a",b,"handover-b").entrySet())
      db.update("INSERT INTO app_user(id,tenant,username,password_hash,role,active) VALUES(?,'handover',?,?,'ADMIN',true)",entry.getKey(),entry.getValue(),encoder.encode(pw));
    String ta=login("handover-a"),tb=login("handover-b");var gate=new CyclicBarrier(2);
    try(var pool=Executors.newFixedThreadPool(2)){
      var x=pool.submit(()->{gate.await();return req("PATCH","/users/"+b,ta,Map.of("active","false"),null).code();});
      var y=pool.submit(()->{gate.await();return req("PATCH","/users/"+a,tb,Map.of("role","VIEWER"),null).code();});
      var codes=List.of(x.get(15,TimeUnit.SECONDS),y.get(15,TimeUnit.SECONDS));
      assertEquals(1,codes.stream().filter(c->c==200).count());
      assertTrue(codes.stream().allMatch(c->Set.of(200,401,403,409).contains(c)));
    }
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM app_user WHERE tenant='handover' AND role='ADMIN' AND active=true",Integer.class));
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void olderAuditPagesRemainTenantScopedAndStableDuringWrites() throws Exception {
    for(int i=0;i<105;i++)db.update("INSERT INTO work_audit(tenant,actor,subject,action,note) VALUES('separate','qa','history','TEST',?)","row-"+i);
    String path="/audit/page?limit=50";
    assertEquals(403,req("GET",path,viewer,null,null).code());
    assertEquals(400,req("GET","/audit/page?limit=101",other,null,null).code());
    var page=req("GET",path,other,null,null);assertEquals(200,page.code());
    var ids=new HashSet<Object>();int count=0;
    db.update("INSERT INTO work_audit(tenant,actor,subject,action,note) VALUES('separate','qa','history','TEST','late')");
    while(true){
      for(var row:(List<Map<String,Object>>)page.value().get("items")){
        assertTrue(ids.add(row.get("seq")));assertNotEquals("late",row.get("note"));
        assertEquals("qa",row.get("actor"));count++;
      }
      if(!Boolean.TRUE.equals(page.value().get("hasMore"))) break;
      page=req("GET",path+"&before="+page.value().get("nextBefore"),other,null,null);assertEquals(200,page.code());
    }
    assertEquals(105,count);
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @Test
  void revocationInvalidatesSessions() throws Exception {
    assertEquals(
        200,
        req(
                "POST",
                "/users",
                admin,
                Map.of(
                    "username",
                    "temp-user",
                    "password",
                    "Integration-test-only-2026!",
                    "role",
                    "VIEWER"),
                null)
            .code());
    String t = login("temp-user");
    String id =
        db.queryForObject("SELECT id FROM app_user WHERE username='temp-user'", String.class);
    assertEquals(200, req("PATCH", "/users/" + id, admin, Map.of("active", "false"), null).code());
    assertEquals(401, req("GET", "/me", t, null, null).code());
  }
}
