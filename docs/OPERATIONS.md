# 部署、使用与验收
上海如静知华信息科技有限公司 · https://www.zhuatech.cn/

## 本地与生产的区别

本地启动脚本在工程根目录运行 JAR，H2 数据保存在 `var/workspace.mv.db`。只需一个进程，默认仅监听环回地址。账号和业务记录不会随刷新、退出或普通重启丢失。首次须自设管理员密码，随后把管理员、业务员、复核员、只读人员分开；没有硬编码演示账号。示例是页面内的虚构输入，只有点击「运行并保存」才入库。

## MySQL 8.4

使用 Docker Compose 时，将 `.env.example` 复制成 `.env`，分别填写独立的 `ADMIN_PASSWORD`、`DB_ROOT_PASSWORD`、`DB_PASSWORD`；运行 `docker compose up -d --build`。数据库不映射主机端口。Compose 使用项目独立数据卷，**不要使用 down -v 清理生产数据**。首次下载镜像和 Maven 依赖需联网；本地运行已打包的 JAR 不需要联网或模型密钥。

已有数据库可用 `DB_URL`、`DB_USER`、`DB_PASSWORD` 指向本项目专用的空 schema，字符集 utf8mb4。不要让十个项目共用同一 schema。Flyway 在首次运行时建表并追踪迁移，禁止改写已经执行的 V1；新增迁移按版本追加。在正式环境为客户端启用验证证书的 MySQL TLS，不要把 Compose 内网样例连接串直接用于跨网段部署。为每企业部署独立 schema 和实例；无在线自助开租户入口。

## 日常操作

1. 管理员登录「账号与审计」，建立业务员与复核员，个人账号不共享。
2. 业务端打开「新建分析」。用表格逐行编辑或导入 UTF-8 JSON 批次；可先「载入示例」查看字段格式。数组字段必须是 JSON 数组。全批提交失败不落半笔。
3. 检查结果和限制说明。从台账查看历史输入、明细和操作记录。点「下载输入批次」可重新导入；点「导出完整结果」只导出这一个已选批次，不会下载不相关记录。
4. 第二个账号进详情填写意见，通过或退回；原作者不能自审。完成后历史结果不可覆盖，需更正时复制为新批次。
5. 账号停用或权限变更会使旧会话失效。管理员不能停用或降级自己。管理密码重置用 API；初始环境变量不会覆盖现有密码。

## 接口与权限

除静态页面、健康及登录以外，业务和管理接口均需 `Authorization: Bearer <token>`。同源 H5 页面使用相对路径，不需要跨域白名单。POST/PATCH 用 JSON 和明确的 Content-Length，上限 512 KiB，批次序列化后上限 250,000 字符；每批 1–200 行。

| 路径 | 行为 |
|---|---|
| GET /api/health | 数据库可读且账号初始化完成后才返回 UP |
| POST /api/login | 输入 username、password；返回 token 和 user。连续失败受限 |
| GET /api/me、POST /api/logout | 读取当前身份、使会话失效 |
| POST /api/runs | body: title、settings、rows；请求头 Idempotency-Key（8–80 位）。同人同键同内容返回原记录；同键异内容 409 |
| GET /api/runs?page=1&state=READY | 每页 20 条，本企业可见；可选 READY、APPROVED、REJECTED |
| GET /api/runs/{id} | 查看该企业的输入、结果、版本和审计；其他企业记录返回 404 |
| POST /api/runs/{id}/review | 独立复核人，传 version、state、note；状态/版本冲突 409 |
| GET/POST /api/users、PATCH /api/users/{id} | 管理员列账号、创建账号、修改 role/active/password；PATCH 采用字符串值 |
| GET /api/audit | 管理员查看本企业最近 100 条操作 |

计算人和复核人须为不同账号。服务端校验角色；VIEWER 不能发起计算，OPERATOR 不能审批，REVIEWER 可计算及复核，ADMIN 可管理。初始工作区为 default，用户输入不能指定租户。401 先重新登录，403 核对角色与制单人，409 刷新版本，503 等初始化完成。不要在日志、问题单或 URL 中记录密码和令牌。

<a id="local-recovery"></a>

## 1.5 本地库：停机备份与恢复演练

**先确认使用的是哪个库。** 下列操作只适用于本项目内置 H2 文件库，`workspace.mv.db` 是实际数据。默认路径为项目根目录下的 `var/`。不同项目不要共用数据目录。直接启动的环境变量须在终端或服务管理器中设置；把值写入 Compose 的 `.env` 并不会自动导入当前终端。使用自定义 `DB_URL` 或 MySQL 时，本节的维护命令不适用，使用数据库原生备份，在独立库做恢复演练。

1. **从 1.4 首次升级：** 先停掉原服务，将整个原数据目录复制到受控位置，保留一份不再打开的原版副本。旧库尚无项目标识，先核对原工程和业务批次，再让**本项目** 1.5 程序打开工作副本完成 Flyway V3。不要用另一项目初始化这份副本。回退请使用原版程序和保留的原版副本，不要让旧程序操作升级后的库。
2. **生成和校验备份：** 先正常停止运行中的服务。以下命令在工程根目录执行，示例的 `./var` 要替换为真正的数据目录；备份文件要位于数据目录外。使用自定义 H2 凭据时，提供原 `DB_USER` 和 `DB_PASSWORD`，不要把密码写入命令、日志或工单。
3. **恢复到新位置：** 目标的父目录须已存在，目标数据目录须不存在（即使空目录也不覆盖）。校验不通过或项目不符会拒绝。备份和恢复都需要支持硬链接的本地文件系统及足够临时磁盘空间，不支持超过 8 GiB 的单个数据库文件。命令失败返回非零，不要跳过报错启动新库。

```bash
java -jar backend/target/app.jar maintenance backup ./var ./before-upgrade.zhbk
java -jar backend/target/app.jar maintenance verify ./before-upgrade.zhbk
java -jar backend/target/app.jar maintenance restore ./before-upgrade.zhbk ./restored-data
export WORKSPACE_DATA_DIR="$PWD/restored-data"
export WORKSPACE_REQUIRE_EXISTING=TRUE
bash scripts/start.sh --no-build
```

Windows PowerShell 中，上述三个 Java 命令相同；随后执行：

```powershell
$env:WORKSPACE_DATA_DIR = (Resolve-Path .\restored-data).Path
$env:WORKSPACE_REQUIRE_EXISTING = 'TRUE'
.\scripts\start.cmd --no-build
```

4. **业务验收：** 用原账号重新登录，按备份前的编号核对业务结果、审批与历史；管理员在审计中核对 `RESTORE_LOCAL`，再用可识别的测试批次验证写入。恢复会撤销副本中的全部旧会话，不改密码，不改原库。确认无误后再切换日常启动配置。仅打开已有库的开关不用于全新安装；目录名请勿包含分号。
5. **保管：** 备份含业务明细及账号密码哈希，**没有加密或数字签名**。SHA-256 用于发现意外损坏，不能证明发送者身份。只恢复来自可信来源的备份；按企业策略限制访问、加密离机保存、设置保留期并定期演练。备份不能代替 MySQL 高可用、异地灾备或容量规划。

## 运维与故障处理

- 生产入口使用 HTTPS 反向代理；转发同源的 / 和 /api，不要缓存 /api，不要直接暴露应用端口。仅在可信内网按需设置 BIND_ADDRESS。默认数据库为空，不要把测试数据库或 .env 加入仓库。
- 数据模型：app_user / auth_session 保存哈希密码、会话摘要和有效期；tenant_guard 在同企业内协调提交；work_run 保存不可覆盖的输入与计算快照；work_audit 保存审批和管理事件。按企业、状态、时间和业务标识建索引。不包含硬删除 API。
- 本地 H2：停机后复制整个 var 目录到受控存储；恢复到独立测试目录，再启动同版本核对批次 ID、总数及历史。MySQL：定期一致性备份（例如 mysqldump --single-transaction），用全新隔离 schema 演练恢复，禁止直接覆盖活动库。账号密码及数据库备份纳入权限和加密管理。
- 源码修改后重新运行 `./mvnw -f backend/pom.xml package`，并重启旧进程。前端在 frontend/，构建时打入 JAR，无须 Node、外部 CDN 或跨域配置。Windows 可在 PowerShell 设置环境变量后运行 `java -jar backend/target/app.jar`。
- 性能边界：同步小批计算，单企业写入串行，同实例可服务多个用户；不是海量离线训练集群。未认证生产 SLA、异地容灾、SSO、峰值并发或针对客户数据的模型精度。生产上线前须按实际负载、数据保留、备份恢复、HTTPS 和账号制度验收。

## 1.4 管理员交接与账号恢复

- 交接前先建立第二位实名管理员，让对方独立登录，再由其调整原账号。本企业的账号管理操作串行提交，获得锁后重新验证当前会话；禁止自行停用或降级。未改变角色或状态时不撤销会话，不接受空密码重置。
- 连续 5 次失败锁定 15 分钟。到期后重新计数；也可由另一名管理员线下核实身份后，在「账号与审计」的锁定账号行填原因并解锁。解锁只适用于启用账号，不改密码，撤销该账号的旧会话。只有一个管理员时可等待锁定到期。不要在解锁依据中填写密码、验证码或证件号。
- POST /api/users/{id}/unlock：JSON 示例 {"note":"已与业务负责人核实"}。空依据 400，跨企业目标 404，已解锁或停用 409。GET /api/users 增加 login_locked（1/0）。
- GET /api/audit/page?limit=50：按序号倒序，hasMore 为真时使用 nextBefore 作为下一页的 before 参数，每页 1–100 条；向历史翻页不会因新事件重复读到旧事件。兼容旧 GET /api/audit（最近 100 条）。变更日志记录角色及启用状态前后值，密码重置仅记动作。
- 升级前备份。Flyway V2 仅新增 work_audit(tenant,seq) 索引，不改旧记录。大库请安排维护窗口；确认运行账号有建索引权限，迁移失败不要清库。
- 启动前用 scripts/doctor.py，启动后用 scripts/check-service.py。后者仅访问 127.0.0.1 和本项目端口（可用 PORT 覆盖），不登录、不写库、不跟随重定向。不能替代业务流程测试。

## 1.2 业务纠错与重复提交

- 导入 UTF-8 JSON（可带 BOM）时先校验整个文件；顶层仅允许 title/settings/rows，字段、数组元素和数值类型按本工程样本校验。格式不符时显示行或参数名，不替换已有编辑内容。文件载入并不意味着入库，仍需点击「运行并保存」；业务约束由后端再次检查。
- 提交人在自己待复核的批次详情填写原因（1–1000 字），点「确认撤回」。其他人（包括管理员）不能代撤回；只读人员不可操作。撤回不删除、修改原输入或计算结果；需要改正时点「复制为新批次」。新批次独立编号，不会覆盖历史。
- 接口：POST /api/runs/{id}/withdraw，JSON 示例：{"version":1,"note":"原始样本选错"}。成功后状态 WITHDRAWN、版本 +1，记录操作人、时间和原因。GET /api/runs?state=WITHDRAWN 可查询；不再出现在待复核队列。撤回与复核使用同一版本检查，如遇 409 请重新打开详情确认实际状态。
- 调用方网络超时可沿用原 Idempotency-Key 和完全相同的请求体重试。多人并发时相同键以当前提交人和企业为范围返回同一笔；更换内容而不换键返回 409。不得把同一键用于另一笔业务。
- 从 1.1 升级无需改表；先停机备份，保留数据目录或原 MySQL 连接，替换应用包再启动。1.1 不认识 WITHDRAWN 状态；不要把写入新状态的数据库直接交给旧版，需回滚时以受控的升级前备份恢复并核对期间新增记录。
- 从源码 ZIP 解压时也可用 bash scripts/start.sh，初始化脚本不依赖 mvnw 文件拥有可执行权限。本地启动不需要 Node；维护者可选用 Node 18+ 运行 node --test scripts/import.test.mjs。

## 1.1 日常使用与升级

- macOS/Linux 在根目录运行 `bash scripts/start.sh`；Windows 在 cmd 或 PowerShell 运行 `.\scripts\start.cmd`。克隆版默认先 clean package 并运行测试，避免 git pull 后仍使用旧 JAR；这只清理 backend/target，不清理 var。已测试构建后可显式加 `--no-build` 快速启动，需重新构建时用 `--build`。
- 源码需要完整 JDK 21，Maven Wrapper 固定 3.9.11 并校验分发包 SHA-256，首次需能访问 Maven Central。运行包已有 JAR，可用 Java 21 离线运行。`.env` 只供 Compose；直接运行 JAR 请用系统环境变量。管理员初始化根据数据库账号记录判定，而非根据文件是否存在。没有交互终端时务必为首次启动设置 ADMIN_PASSWORD；密码无效时修正并重启，**不要删除数据库**。
- 改密：登录后点「修改我的密码」。新密码至少 12 个 Unicode 字符、最多 72 个 UTF-8 字节，不能全空格或与原密码相同。成功后包括当前窗口在内的所有会话都失效。改密接口 `POST /api/me/password` 仅接受 currentPassword 和 newPassword；审计不记录密码。登录及改密的错误原密码共享账号锁定策略。
- 台账：标题支持字面量子串查询（% 和 _ 不是通配符）；按「我提交的」过滤，复核人可选「待我复核」。接口 `GET /api/runs?q=标题&scope=all|mine|reviewable&state=READY&page=1`；reviewable 只包含本企业 READY 且非本人提交的批次。
- 管理员在「账号与审计」中可保存他人角色；变更会撤销旧会话。修改初始环境变量不会重置已有账号。升级前备份数据库、保留旧版本包，在同一工作目录/同一数据库连接上启动新版本。1.1 未变更数据库结构。
- 性能边界：同步小批计算，单企业写入串行，同实例可服务多个用户；不是海量离线训练集群。未认证生产 SLA、异地容灾、SSO、峰值并发或针对客户数据的模型精度。生产上线前须按实际负载、数据保留、备份恢复、HTTPS 和账号制度验收。

## 分歧仲裁

有待裁决样本时，普通 APPROVED 请求返回 409。独立复核人提交 `POST /api/runs/{id}/adjudicate`，传 `version`、`note` 及以分歧样本 ID 为键的 `labels` 对象。必须覆盖所有分歧项，每项从原始候选标签中选择。原始输入和票数不变；结果保留裁决、版本和复核人。

## 标注一致性仲裁的输入口径

票数、唯一多数和一致率同时达标才采纳；平票进入人工队列。Fleiss κ 只在各样本票数一致且分母非零时返回，否则显示“—”。不同审核人需为每个分歧样本指定一个实际候选标签并填写依据。 仅处理单标签分类；未达票数/一致率的样本保留待人工复核，不冒充金标准。

输入明细字段：`id`（编号）、`annotator`（标注员）、`label`（类别）。批次参数：`minVotes`（最少票数）、`agreement`（一致率下限）。

[可直接导入的样例](../examples/batch.json)。核心实现见 [Algorithm.java](../backend/src/main/java/cn/zhuatech/labelcouncil/Algorithm.java)。
