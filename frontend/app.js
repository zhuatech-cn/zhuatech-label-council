// 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
import { readBatch } from "./batch-import.mjs";
const $ = (id) => document.getElementById(id),
  cat = await fetch("/catalog.json").then((r) => r.json());
let token = sessionStorage.getItem("workspace-token") || "",
  user = null,
  view = "",
  page = 1,
  state = "",
  query = "", scope = "all",
  busy = false,
  currentKey = "",
  lastBody = "";
const statusNames = { READY: "待复核", APPROVED: "已通过", REJECTED: "已退回", WITHDRAWN: "已撤回" },
  roles = {
    ADMIN: "管理员",
    OPERATOR: "业务员",
    REVIEWER: "复核员",
    VIEWER: "只读",
  };
const labels = {
  ...cat.labels,
  stage: "状态",
  created_at: "时间",
  actor: "操作人",
  action: "操作",
  note: "说明",
  subject: "对象",
  username: "账号",
  role: "角色",
  active: "启用",
  confidence: "置信度",
  predicted: "预测类别",
  review: "需人工复核",
  errorRate: "错误率",
  substitutions: "替换",
  deletions: "删除",
  insertions: "插入",
  referenceUnits: "参考单元",
  start: "开始",
  end: "结束",
  decision: "判定",
  split: "集合",
  title: "批次名称",
  selfMs: "独占毫秒",
  requiredGiB: "所需 GiB",
  marginGiB: "余量 GiB",
  minSimilarity: "最低相似度",
  representative: "代表样本",
  selected: "选入",
  needsReview: "待复核数",
  totalValue: "总价值",
  free: "剩余",
  used: "已用",
  regressions: "退步查询",
  context: "编排正文",
};
const resultLabels = {
  initialReason: "初判依据",
  ...labels,
  ...{
    knownTokens: "词表命中数",
    training: "训练样本",
    predictions: "预测工单",
    classes: "类别数",
    vocabulary: "词表大小",
    documents: "文档数",
    chunks: "切片数",
    sourceUnits: "源文码点",
    part: "片段序号",
    sha256: "内容摘要",
    configurations: "候选配置",
    fits: "满足容量",
    exceeds: "超出容量",
    weightGiB: "权重 GiB",
    kvPerSequenceGiB: "单序列缓存 GiB",
    maxConcurrency: "最大并发",
    components: "关联组数",
    component: "关联组代表",
    test: "测试集",
    emptySplit: "存在空集合",
    vectors: "向量数",
    dimensions: "维度",
    groups: "保留组数",
    candidates: "疑似重复",
    baseRecall: "基线召回率",
    candidateRecall: "候选召回率",
    baseMRR: "基线 MRR",
    candidateMRR: "候选 MRR",
    baseRR: "基线倒数排名",
    candidateRR: "候选倒数排名",
    baseNDCG: "基线 nDCG",
    candidateNDCG: "候选 nDCG",
    regression: "排序退步",
    consensus: "达成一致",
    adjudicated: "已裁决",
    fleissKappa: "Fleiss κ",
    tally: "标签票数",
    votes: "票数",
    elapsedMs: "总耗时（毫秒）",
    spans: "调用数",
    hottest: "最大独占耗时节点",
    durationMs: "持续毫秒",
    childrenUnionMs: "子调用占用毫秒",
    reason: "选择依据",
    reserved: "回复预留",
    revision: "版本",
    train: "训练集",
    validation: "验证集",
    agreement: "实际一致率",
    start: "起点",
    end: "终点",
  },
};
const choices = {
  TOO_FEW: "票数不足",
  TIE: "最高票并列",
  BELOW_THRESHOLD: "未达一致率",
  AGREED: "一致通过",
  KEEP: "保留",
  REVIEW_DUPLICATE: "待查重",
  CONSENSUS: "一致",
  REVIEW: "待裁决",
  ADJUDICATED: "已裁决",
  REQUIRED: "必选",
  OPTIMAL: "预算内优选",
  NOT_SELECTED: "未入选",
  train: "训练集",
  validation: "验证集",
  test: "测试集",
  RUN: "运行并保存",
  "READY -> WITHDRAWN": "提交人撤回",
};
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function el(tag, text, cls) {
  const n = document.createElement(tag);
  if (text != null) n.textContent = text;
  if (cls) n.className = cls;
  return n;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function info(msg) {
  $("notice").textContent = msg || "";
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
async function api(path, method = "GET", body, key) {
  const headers = {};
  if (token) headers.Authorization = "Bearer " + token;
  if (body != null) headers["Content-Type"] = "application/json";
  if (key) headers["Idempotency-Key"] = key;
  const r = await fetch("/api" + path, {
    method,
    headers,
    body: body == null ? undefined : JSON.stringify(body),
  });
  if (!r.ok) {
    let m = "请求失败 (" + r.status + ")";
    try {
      m = (await r.json()).message || m;
    } catch {}
    if (r.status === 401) off();
    throw Error(m);
  }
  const t = await r.text();
  return t ? JSON.parse(t) : null;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
async function act(fn) {
  if (busy) return;
  busy = true;
  info("");
  try {
    await fn();
  } catch (e) {
    info(e.message);
  } finally {
    busy = false;
  }
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function button(text, fn, primary) {
  const b = el("button", text, primary ? "primary" : "");
  b.type = "button";
  b.onclick = () => act(fn);
  return b;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function off() {
  page = 1; state = ""; scope = "all"; query = ""; currentKey = ""; lastBody = "";
  user = null;
  token = "";
  sessionStorage.removeItem("workspace-token");
  $("account").textContent = "";
  $("shell").hidden = true;
  $("login").hidden = false;
  $("body").replaceChildren();
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
async function enter() {
  user = await api("/me");
  $("login").hidden = true;
  $("shell").hidden = false;
  $("account").textContent = user.username + " · " + roles[user.role];
  $("entry").hidden = user.role === "VIEWER";
  $("accounts").hidden = $("adminCaption").hidden = user.role !== "ADMIN";
  await (user.role === "VIEWER" ? ledger() : entry());
}
$("signin").onsubmit = (e) => {
  e.preventDefault();
  act(async () => {
    const f = e.target;
    const x = await api("/login", "POST", Object.fromEntries(new FormData(f)));
    token = x.token;
    sessionStorage.setItem("workspace-token", token);
    f.reset();
    await enter();
  });
};
$("logout").onclick = () =>
  act(async () => {
    await api("/logout", "POST");
    off();
  });
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function head(v, t, d) {
  view = v;
  $("crumb").textContent =
    cat.title + " / " + (v === "accounts" ? "系统管理" : "业务工作区");
  $("heading").textContent = t;
  $("intro").textContent = d;
  $("body").replaceChildren();
  for (const k of ["entry", "ledger", "accounts", "security"])
    $(k).classList.toggle("active", k === v);
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function box(t) {
  const n = el("div", null, "box");
  if (t) n.append(el("h2", t));
  $("body").append(n);
  return n;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function input(type, val = "") {
  const n = el("input");
  n.type = type;
  n.value = val;
  return n;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function field(parent, k, n) {
  const l = el("label", labels[k] || k);
  l.append(n);
  parent.append(l);
  return n;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function fmt(v) {
  if (v == null) return "—";
  if (typeof v === "boolean") return v ? "是" : "否";
  if (typeof v === "object") return JSON.stringify(v);
  return typeof v === "number" && !Number.isInteger(v)
    ? Number(v.toFixed(5)).toString()
    : statusNames[v] || roles[v] || String(v);
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function table(parent, keys, rows, action) {
  const wrap = el("div", null, "scroll"),
    tb = el("table"),
    tr = tb.createTHead().insertRow();
  for (const k of keys) tr.append(el("th", resultLabels[k] || k));
  if (action) tr.append(el("th", "操作"));
  const tbody = tb.createTBody();
  for (const row of rows) {
    const line = tbody.insertRow();
    for (const k of keys)
      line.insertCell().textContent =
        typeof row[k] === "string" &&
        ["decision", "action", "reason", "split","reviewReason","capacityStatus","initialReason"].includes(k)
          ? choices[row[k]] || row[k]
          : fmt(row[k]);
    if (action) line.insertCell().append(action(row));
  }
  wrap.append(tb);
  parent.append(wrap);
  if (!rows.length) parent.append(el("p", "没有符合条件的记录。", "muted"));
  return tbody;
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function entry(seed) {
  const draft = structuredClone(
    seed || {
      title: "",
      settings: cat.settings,
      rows: cat.rows.slice(0, 1).map(blank),
    },
  );
  head("entry", "新建分析", cat.description);
  const top = box("批次与参数"),
    bar = el("div", null, "settings");
  top.append(bar);
  const title = field(bar, "title", input("text", draft.title));
  title.placeholder = "例如：本周验收批次";
  title.maxLength = 100;
  const config = {};
  for (const [k, v] of Object.entries(cat.settings)) {
    config[k] = field(
      bar,
      k,
      input(typeof v === "number" ? "number" : "text", draft.settings[k]),
    );
    if (typeof v === "number") config[k].step = "any";
  }
  const area = box("输入明细"),
    tools = el("div", null, "bar");
  area.append(tools);
  const spec = cat.rows[0],
    keys = Object.keys(spec),
    wrap = el("div", null, "scroll"),
    tb = el("table");
  wrap.append(tb);
  area.append(wrap);
  const h = tb.createTHead().insertRow();
  for (const k of [...keys, "操作"]) h.append(el("th", labels[k] || k));
  const tbody = tb.createTBody();
  let editors = [];
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  function append(row) {
    if (editors.length >= 200) throw Error("单批最多 200 条");
    const tr = tbody.insertRow(),
      values = {};
    for (const k of keys) {
      const base = spec[k],
        n =
          typeof base === "boolean"
            ? input("checkbox")
            : typeof base === "number"
              ? input("number")
              : el("textarea");
      n.setAttribute("aria-label", labels[k] || k);
      if (n.type === "checkbox") n.checked = row[k] === true;
      else
        n.value = Array.isArray(base)
          ? JSON.stringify(row[k] ?? [])
          : (row[k] ?? "");
      if (n.type === "number") n.step = "any";
      if (n.tagName === "TEXTAREA") n.rows = 1;
      tr.insertCell().append(n);
      values[k] = n;
    }
    editors.push(values);
    tr.insertCell().append(
      button("移除", () => {
        tr.remove();
        editors = editors.filter((a) => a !== values);
      }),
    );
  }
  for (const r of draft.rows) append(r);
  tools.append(
    button("增加一行", () => append(blank(spec))),
    button("载入示例", () => {
      if (editors.some((r) => r.id.value.trim()) && !confirm("替换当前明细？"))
        return;
      entry({
        title: title.value || cat.scene + "样例",
        settings: cat.settings,
        rows: cat.rows,
      });
    }),
  );
  const picker = input("file");
  picker.accept = ".json";
  field(tools, "导入 JSON 批次", picker);
  picker.onchange = () =>
    act(async () => {
      const f = picker.files[0];
      if (!f) return;
      if (f.size > 524288) throw Error("文件上限 512 KiB");
      try {
        const batch = readBatch(await f.text(), cat);
        entry(batch);
        info("已载入 " + batch.rows.length + " 行，尚未提交。");
      } finally { picker.value = ""; }
    });
  const end = el("div", null, "bar");
  area.append(end);
  end.append(
    button(
      "运行并保存",
      async () => {
        const settings = {};
        for (const [k, n] of Object.entries(config))
          settings[k] =
            typeof cat.settings[k] === "number" ? number(n.value, k) : n.value;
        const rows = editors.map((r, i) =>
          Object.fromEntries(
            keys.map((k) => {
              const n = r[k],
                base = spec[k];
              let v = n.value;
              if (Array.isArray(base)) {
                try {
                  v = JSON.parse(v);
                } catch {
                  throw Error("第 " + (i + 1) + " 行 " + k + " 不是 JSON 数组");
                }
                if (!Array.isArray(v)) throw Error(k + " 需要数组");
              } else if (typeof base === "number") v = number(v, k);
              else if (typeof base === "boolean") v = n.checked;
              return [k, v];
            }),
          ),
        );
        const b = { title: title.value, settings, rows },
          s = JSON.stringify(b);
        if (!currentKey || lastBody !== s) {
          currentKey = crypto.randomUUID();
          lastBody = s;
        }
        const result = await api("/runs", "POST", b, currentKey);
        currentKey = "";
        show(result);
      },
      true,
    ),
  );
  area.append(el("p", cat.limit, "muted"));
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function blank(spec) {
  return Object.fromEntries(
    Object.entries(spec).map(([k, v]) => [
      k,
      Array.isArray(v) ? [] : typeof v === "boolean" ? false : "",
    ]),
  );
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function number(v, k) {
  if (!v.trim() || !Number.isFinite(Number(v)))
    throw Error((labels[k] || k) + " 需要数值");
  return Number(v);
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function download(data, name) {
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
  );
  const a = el("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
async function ledger() {
  const r = await api("/runs?" + new URLSearchParams({ page, state, q:query, scope }));
  head(
    "ledger",
    "结果台账",
    "按标题、状态或办理范围查找批次；待我复核不包含自己提交的记录。",
  );
  const n = box(),
    bar = el("form", null, "bar");
  n.append(bar);
  const s = el("select");
  for (const [k, v] of Object.entries({ "": "全部状态", ...statusNames }))
    s.append(new Option(v, k));
  s.value = state;
  field(bar, "状态", s);
  const range = el("select");range.add(new Option("全部批次","all"));range.add(new Option("我提交的","mine"));
  if(["ADMIN","REVIEWER"].includes(user.role))range.add(new Option("待我复核","reviewable"));
  range.value=scope;field(bar,"业务范围",range);
  const keyword=field(bar,"批次标题",input("search",query));keyword.maxLength=100;
  bar.append(el("button","查询","primary"));
  bar.onsubmit=e=>{e.preventDefault();act(async()=>{state=s.value;scope=range.value;query=keyword.value.trim();page=1;await ledger();});};
  s.onchange=range.onchange=()=>bar.requestSubmit();
  table(n, ["title", "stage", "revision", "created_at"], r.items, (v) =>
    button("查看", async () => show(await api("/runs/" + v.id))),
  );
  const p = el("div", null, "bar");
  n.append(p);
  const prev = button("上一页", async () => {
      page--;
      await ledger();
    }),
    next = button("下一页", async () => {
      page++;
      await ledger();
    });
  prev.disabled = page === 1;
  next.disabled = page * 20 >= r.total;
  p.append(
    prev,
    el("span", "第 " + page + " 页 · 共 " + r.total + " 条"),
    next,
  );
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
function show(r) {
  head(
    "ledger",
    r.title,
    "批次结果 · " + statusNames[r.stage] + " · 版本 " + r.revision,
  );
  const n = box("分析结果"),
    m = el("div", null, "metrics");
  n.append(m);
  for (const [k, v] of Object.entries(r.result.metrics).sort(([a], [b]) =>
    a.localeCompare(b),
  )) {
    const block = el("div");
    block.append(el("small", resultLabels[k] || k), el("strong", fmt(v)));
    m.append(block);
  }
  const details = box("明细");
  table(
    details,
    Object.keys(r.result.rows[0] || {}).sort((a, b) =>
      a === b ? 0 : a === "id" ? -1 : b === "id" ? 1 : a.localeCompare(b),
    ),
    r.result.rows,
  );
  if (r.result.context != null) details.append(el("pre", r.result.context));
  const bar = el("div", null, "bar");
  details.append(bar);
  bar.append(
    button("导出完整结果", () => download(r, r.id + ".json")),
    button("下载输入批次", () => download(r.input, "batch.json")),
  );
  if (user.role !== "VIEWER")
    bar.append(button("复制为新批次", () => entry(r.input)));
  if (r.stage === "READY" && r.actor_id === user.id && user.role !== "VIEWER") {
    const panel = box("撤回待复核批次");
    const form = el("form", null, "bar");
    panel.append(form);
    const reason = field(form, "撤回原因", input("text"));
    reason.required = true;
    reason.maxLength = 1000;
    form.append(el("button", "确认撤回"));
    form.onsubmit = (event) => {
      event.preventDefault();
      act(async () => {
        if (!reason.value.trim()) throw Error("请填写撤回原因");
        show(await api("/runs/" + r.id + "/withdraw", "POST", {version: r.revision, note: reason.value}));
        info("批次已撤回，原记录保留。需要更正时请复制为新批次。");
      });
    };
  }
  const hist = box("操作记录");
  table(hist, ["actor", "action", "note", "created_at"], r.history);
  if (
    ["ADMIN", "REVIEWER"].includes(user.role) &&
    r.stage === "READY" &&
    r.actor_id !== user.id
  ) {
    const b = box("独立复核"),
      note = field(b, "裁决或退回依据", input("text")),
      selectors = new Map();
    for (const row of r.result.rows.filter((x) => x.decision === "REVIEW")) {
      const select = el("select");
      select.add(new Option("请选择", ""));
      for (const label of Object.keys(row.tally))
        select.add(new Option(label, label));
      field(b, row.id, select);
      selectors.set(row.id, select);
    }
    const actions = el("div", null, "bar");
    b.append(actions);
    actions.append(
      button(
        "确认裁决并通过",
        async () => {
          const labels = Object.fromEntries(
            [...selectors].map(([id, s]) => [id, s.value]),
          );
          const adjudicate = selectors.size > 0,
            body = adjudicate
              ? { version: r.revision, note: note.value, labels }
              : { version: r.revision, note: note.value, state: "APPROVED" };
          show(
            await api(
              "/runs/" + r.id + (adjudicate ? "/adjudicate" : "/review"),
              "POST",
              body,
            ),
          );
        },
        true,
      ),
      button("退回", async () =>
        show(
          await api("/runs/" + r.id + "/review", "POST", {
            version: r.revision,
            note: note.value,
            state: "REJECTED",
          }),
        ),
      ),
    );
  }
}
/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
async function accounts() {
  const [users, audit] = await Promise.all([api("/users"), api("/audit")]);
  head(
    "accounts",
    "账号与审计",
    "按岗位授予最小权限；停用、改密或变更角色会撤销旧会话。",
  );
  const b = box("新增账号"),
    f = el("form", null, "bar");
  b.append(f);
  const name = field(f, "username", input("text")),
    pw = field(f, "初始密码", input("password")),
    role = el("select");
  for (const [k, v] of Object.entries(roles)) role.append(new Option(v, k));
  role.value = "VIEWER";
  field(f, "role", role);
  f.append(el("button", "新增账号", "primary"));
  f.onsubmit = (e) => {
    e.preventDefault();
    act(async () => {
      await api("/users", "POST", {
        username: name.value,
        password: pw.value,
        role: role.value,
      });
      await accounts();
    });
  };
  table(box("企业账号"), ["username", "role", "active"], users, (row) => {
    const g = el("div", null, "bar");
    const sw = button(row.active ? "停用" : "启用", async () => {
      await api("/users/" + row.id, "PATCH", { active: String(!row.active) });
      await accounts();
    });
    sw.disabled = row.id === user.id;
    const access=el("select");access.setAttribute("aria-label",row.username+" 的角色");
    for (const [k,v] of Object.entries(roles))access.add(new Option(v,k));access.value=row.role;
    const save=button("保存角色",async()=>{if(access.value===row.role)return;
      if(!confirm("变更 "+row.username+" 的角色并撤销旧会话？"))return;
      await api("/users/"+row.id,"PATCH",{role:access.value});await accounts();});
    access.disabled=save.disabled=row.id===user.id;
    g.append(access,save,sw);
    return g;
  });
  table(
    box("最近 100 条操作"),
    ["actor", "action", "subject", "note", "created_at"],
    audit,
  );
}
$("entry").onclick = () => act(() => entry());
$("ledger").onclick = () =>
  act(() => {
    page = 1;
    return ledger();
  });
$("accounts").onclick = () => act(accounts);
$("security").onclick = () => act(() => {
  head("security","修改我的密码","修改成功后，所有设备上的旧会话均会退出。");
  const panel=box("账号安全"), f=el("form",null,"settings"); panel.append(f);
  const old=field(f,"原密码",input("password")), next=field(f,"新密码",input("password")), confirmValue=field(f,"确认新密码",input("password"));
  old.autocomplete="current-password";next.autocomplete=confirmValue.autocomplete="new-password";
  old.required=next.required=confirmValue.required=true; next.minLength=12;
  f.append(el("button","保存并重新登录","primary"));
  f.onsubmit=e=>{e.preventDefault();act(async()=>{
    if(next.value!==confirmValue.value)throw Error("两次输入的新密码不一致");
    await api("/me/password","POST",{currentPassword:old.value,newPassword:next.value});
    f.reset();off();info("密码已更新，请用新密码重新登录。");
  });};
});
if (token) await act(enter);
