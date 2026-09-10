# PawTrack 当前实现架构

更新：2026-09-10。项目面向求职作品集，P1/P2 已冻结为 `v1.0-portfolio-core`；P3 独立状态迁移已完成，当前增量 P4.1 建立身份与服务端 session 认证基础。实现背景与明确边界见 [项目梳理](../docs/PROJECT_REVIEW.md)。

## 运行结构

React + TypeScript + Vite 通过 REST API 调用 Java 21 / Spring Boot 3.3.6。后端是按业务模块组织的单体应用，持久开发环境使用 PostgreSQL 与 Flyway V1–V9，JPA 使用 validate。测试及 demo profile 使用独立 H2 内存库，并允许 create-drop，避免依赖开发数据。

模块：cat、healthdata、alert、adoption、care、identity。各模块按 api（controller、DTO、mapper）、service、domain 和 repo 组织；这是包级组织约定。Controller 不直接访问 Repository，也不向客户端返回实体。领养、护理时间线、预警队列和处理响应在服务事务内组装 DTO；部分旧猫咪/健康接口在 controller 映射已读取的标量或关联 ID。全局关闭 open-in-view。

## 身份与 session 认证（P4.1，过渡阶段）

identity 模块新增 UserAccount（规范化 email、displayName、passwordHash、STAFF/ADOPTER、时间戳）及 V9 user_accounts 表。邮箱在内部创建服务和登录查询中使用 trim + Locale.ROOT 小写，数据库唯一约束防止重复，V9 另校验规范化存储和角色值。PasswordEncoder 使用 Spring Security DelegatingPasswordEncoder，当前写入 BCrypt；没有公开注册接口。

使用标准表单认证过滤器、数据库 UserDetailsService 和服务端 HTTP session；不引入 JWT/refresh token，因为当前单 SPA、单体后端不需要另一套 token 生命周期。principal 是独立 AccountPrincipal 快照，不是 JPA 实体；认证后密码凭据被擦除。POST /api/auth/login 接受 form-urlencoded email/password，成功返回安全的用户 DTO；GET /api/auth/me 未登录返回 401；POST /api/auth/logout 使用框架退出流程使 session 失效。

GET /api/auth/csrf 返回 headerName 和经过框架 XOR 编码的 token，并写入 HttpOnly、SameSite=Lax 的 XSRF-TOKEN cookie。未来 SPA 使用响应中的 token 作为 X-XSRF-TOKEN 请求头，保留 cookie；登录和退出后重新获取。所有认证路径的非安全方法，以及任何已认证 session 的非安全请求，都检查 CSRF。匿名业务写请求为兼容现有 demo 暂不要求 token；这不是完整授权方案。

当前仅 /api/auth/me 要求登录；其余路径的授权规则仍为 permitAll，未设置 STAFF/ADOPTER 业务权限或申请归属检查。前端保持原样，匿名领养和护理流程继续运行。登录后的 API 调用必须遵循 CSRF 契约，P4.2/P4.3 再接入前端并实施权限与所有权。

session 使用框架 fixation 防护、30 分钟空闲超时、仅 cookie 跟踪，JSESSIONID 为 HttpOnly/SameSite=Lax。localhost HTTP 验证不代表 HTTPS 安全；Secure cookie、TLS、代理信任配置属于后续部署。demo profile 单独创建两个带编码密码的虚构账户，非 demo profile 不创建；凭据、准确契约和验证边界见 [P4.1 报告](../docs/P4_1_IDENTITY_SESSION.md)。

## 猫的独立状态（P3.1 / P3.2）

Cat 的唯一业务状态来源是两个字符串持久化的枚举：`CatHealthStatus`（NORMAL、UNDER_OBSERVATION、SICK）和 `CatAdoptionStatus`（AVAILABLE、ADOPTED）。两者独立，新猫默认 NORMAL + AVAILABLE；已领养猫仍可生病或需要观察。申请使用另一个 `AdoptionStatus`（PENDING、APPROVED、REJECTED），描述审核流程，不能与猫的领养状态共用。

V7 新增非空 `health_status`、`adoption_status`，不修改 V1–V6。旧 NORMAL/ADOPTABLE 回填 NORMAL + AVAILABLE，UNDER_OBSERVATION/SICK 保留健康值并回填 AVAILABLE；ADOPTED 回填 NORMAL + ADOPTED。最后一种健康 NORMAL 是历史兼容假设：旧单字段已覆盖独立健康信息，无法重建。未知旧值使迁移失败，必须先明确处理。

P3.1 曾保留数据库旧列和单字段 API 投影，完整过渡记录见 [P3.1 报告](../docs/P3_1_TYPED_CAT_STATUS.md)。P3.2 的 V8 在确认两个 typed 列均有 NOT NULL 约束且无空值后删除 `cats.status`，不再读取旧值或重复回填；V1–V7 保持不变。Hibernate validate 在真实 PostgreSQL V8 schema 上验证通过。

CatResponse/CatDetailResponse 现在直接返回 `healthStatus` 和 `adoptionStatus` 枚举名，旧 `status` 输出和 CatMapper 兼容投影已删除。列表、单猫、创建、图片上传响应使用 CatResponse；dashboard 额外返回 `temperatureC` 和 `hasActiveAlert`。Alert 和领养申请的 `status` 不变。

旧 `PATCH /api/cats/{id}/status`、UpdateCatStatusRequest 和 CatService.updateStatus 已删除。它没有前端调用，当前演示不需要手工状态编辑，因此不添加替代 health-status PATCH。健康观察与预警处理更新健康状态，批准流程更新领养状态，不提供直接设置领养状态的接口。API 与前端应一同更新；旧客户端不再兼容。

## 领养事务

提交、批准和拒绝都先锁定对应的 Cat 行。审核接口先查询 catId，再取得猫行的悲观写锁，最后读取申请当前状态，避免锁等待前读入旧状态。

提交校验猫 adoptionStatus 为 AVAILABLE、healthStatus 为 NORMAL、没有 OPEN 预警，并检查同猫同邮箱是否存在 PENDING 申请。邮箱去掉首尾空白并统一小写。

批准只允许 PENDING，并重新校验同一领养资格。单个事务内执行：申请变为 APPROVED，猫 adoptionStatus 变为 ADOPTED、healthStatus 保持不变，其他 PENDING 申请变为 REJECTED。拒绝只改变当前 PENDING 申请。重复或逆转终态返回 409。不存在的记录返回 404；输入格式/字段错误返回 400。

应用服务使用相同的猫行锁约定协调领养、健康上报、预警处理与图片写入。这是应用路径上的一致性保证，尚未添加数据库唯一约束来覆盖外部脚本或直接 SQL 写入。

## 健康与领养联动

HealthDataService 保存数据后调用 HealthMonitorService。当前规则检查最新温度是否高于 39.5；这只是沿用已有的演示规则。

HealthData 最新值与历史查询统一按 ts DESC、id DESC；同时间取更大 ID。可空温度的 HTTP 输入使用 @Digits(integer=3, fraction=2) 对齐 NUMERIC(5,2)，多余精度/超容量返回 400，不静默舍入。前端时间线及详情均显示两位小数。新的正常观察不会自动关闭已有 OPEN 预警。

同猫同类型已有 OPEN 预警时不重复创建。关闭后再次发现异常可以新建预警。发烧将非 SICK 的 healthStatus 设为 UNDER_OBSERVATION，保留 SICK；无论猫是否已领养，都不改变 adoptionStatus。

工作人员处理最后一个 OPEN 预警时，只将恰好为 UNDER_OBSERVATION 的 healthStatus 恢复为 NORMAL，领养状态保持不变。自动关闭预警、历史窗口规则、通知和机器学习目前未实现。

## 护理时间线（P2.1）

CareRecord 表示工作人员人工输入的一次护理动作或备注。P2.1 的追加式基础字段是 `id`、`cat`、`type`、`note`、`createdAt`；类型刻意限制为 CHECKUP、MEDICATION、FEEDING、OTHER。P2.2 增加可空的 Alert 关联，普通护理记录仍可不关联预警。

`GET /api/cats/{catId}/health-timeline` 返回 `catId`、`catName`、固定值 `order: NEWEST_FIRST` 和 `events`。每个事件都有 `eventKind`、`sourceId`、`occurredAt`、`eventType`、`description`，并按来源选填 `temperatureC`、`activityLevel`、`alertStatus`、`alertSeverity`。`eventKind` 明确区分 HEALTH_OBSERVATION、ALERT、CARE_RECORD，Controller 不暴露 JPA 实体。

时间线以各领域事件的发生时间降序排列：健康观察使用 `HealthData.ts`，预警和护理记录使用各自的 `createdAt`。相同时间时依次按 ALERT、CARE_RECORD、HEALTH_OBSERVATION 排列，最后按来源 ID 降序，确保结果确定。服务在只读事务内加载并映射三种实体，因此不依赖 open-in-view。

当前实现执行三次模块查询后在内存中合并，尚未分页；数据量增长后应改为有界查询或数据库级合并。`createdAt` 暂时同时代表护理记录的录入时间，未来若需要补录历史护理动作，应增加独立的发生时间字段。

只读事务沿用默认隔离级别；READ_COMMITTED 下多次查询不保证同一快照，跨查询可能看到处理前后的不同状态。当前没有宣称时间线是强一致快照。

## 预警处理事务（P2.2）

`care.service.AlertResolutionService` 编排跨模块操作，alert 模块不反向依赖 care。`PATCH /api/alerts/{alertId}/resolve` 接受护理类型与备注，使用服务层事务，先以标量查询获得 catId，再通过 `CatRepository.findByIdForUpdate` 获取猫行悲观写锁，之后才读取 Alert 可变状态。全局顺序是 Cat → Alert/CareRecord/领养相关状态，与健康上报和领养事务一致。

只有 OPEN 可以处理。事务将预警设置为 CLOSED，记录服务端 `resolvedAt`，创建同时间且关联预警的 CareRecord，刷新后检查该猫剩余 OPEN 预警。只有零个 OPEN 且 healthStatus 恰好为 UNDER_OBSERVATION 时，才恢复健康 NORMAL；SICK 保持不变，adoptionStatus 始终保持不变。已领养且需要观察的猫可以恢复为已领养且健康 NORMAL。

旧 body-less `/close` 接口和 `AlertService.closeAlert` 已删除；本地项目没有生产 API 客户端，仅更新其回归测试。重复 `/resolve` 返回带刷新提示的 409，不重写时间也不创建第二条记录。V6 新增可空 `alerts.resolved_at` 和 `care_records.alert_id`（ON DELETE SET NULL），保留历史已关闭预警的未知处理时间为 NULL。

时间线的原 Alert 事件继续按原始 createdAt 排序，处理动作以 CareRecord.createdAt 出现。只新增可选 `relatedAlertId` 供护理事件追溯来源，DTO 在服务事务内组装。更完整的契约和验证记录见 [P2.2 说明](../docs/P2_2_ALERT_RESOLUTION.md)。

## 前端状态

前端 Cat 使用 CatHealthStatus 和 CatAdoptionStatus 两个独立类型。卡片和详情同时显示领养标签（Awaiting a home / Found a home）和健康标签（Health: normal / Under observation / Sick · receiving care），不会用一个维度覆盖另一个，也不保存派生状态。领养申请标签仍单独使用 AdoptionStatus。

画廊 Ready to meet 数量和筛选要求 AVAILABLE + NORMAL，Found a home 只要求 ADOPTED。列表契约没有 hasActiveAlert，因此不推测预警信息；画廊的 Ready to meet 不保证最终可申请。详情使用 AVAILABLE + NORMAL + 无活跃预警控制申请入口，后端仍重新校验并返回 409。已领养猫出现预警时继续显示已找到家及健康状态，提示护理评估，不暗示领养被撤销。

React Router 提供猫咪列表、猫咪详情、申请回执和工作人员审核页面。React Query 缓存 API 数据；提交与审核后使相关查询失效，回执和审核队列定期刷新。前端负责输入体验，后端始终重新检查业务资格。

P2.3 用 StaffLayout 共享工作人员导航：`/staff` 保留领养审核，`/staff/care` 提供 OPEN 队列、三种事件的时间线和护理处理表单。新增 `GET /api/alerts?status=OPEN` 只读接口，默认 OPEN，也可指定 CLOSED；按 createdAt DESC、id DESC 返回 DTO，服务在只读事务内组装猫信息，没有迁移或领域改动。

护理处理成功及 409 冲突会使 `['alerts','OPEN']`、`['health-timeline',catId]`、`['cat',String(catId)]`、`['cats']` 失效。活跃查询即时刷新，未挂载的详情/画廊下次进入时重取；护理查询 staleTime=0，重新挂载时刷新。选中上下文独立于 OPEN 队列成员资格，处理后保留同猫历史。失败与空队列分开显示；网络/服务错误保留表单，409 显示后端消息，不当作成功。

首次自动选择保存为实际选择，后续队列重排或移除不改变编辑对象。外部处理后保留未提交备注；时间线未成功加载或加载失败时禁用处理。该规则消除了初次 GET 与处理后刷新竞争的已知路径，没有引入观察版本或过期决策保护。重启 demo 后应重载浏览器页面，清除上一轮复用 ID 的本地状态。

Vite 的 /api 和 /uploads 代理连接本地后端。错误页面区分网络失败与空数据；表单防重复点击，审核先显示具体影响再提交。

## 当前边界

这是本地 demo，已有 session 身份认证，但没有业务角色授权或申请所有权验证；“工作人员页面”是演示入口。不得直接承载真实申请人数据。相机 URL 存在于历史模型中，但前端未提供私有直播能力。

P4.1 完整后端套件通过 96 个用例，PostgreSQL 迁移验证通过 11 个用例，CatLockPostgresRuntimeIT 通过 3 个用例。前端源码不变，build/lint、16 个 mocked 浏览器用例及真实 demo 结果见 [P4.1 报告](../docs/P4_1_IDENTITY_SESSION.md)。历史记录保留在 [P3.2 报告](../docs/P3_2_COMPLETE_TYPED_STATUS.md)、[P3.1 报告](../docs/P3_1_TYPED_CAT_STATUS.md) 和 [冻结报告](../docs/P2_4B_MILESTONE_FREEZE.md)。

这些证据不代表所有线程交错、生产并发吞吐或分布式写入保证。列表缺少活跃预警信息、未分页、人工决策新鲜度、时间线快照、直接 SQL 绕过应用约束、文件上传与权限边界仍是明确债务。业务授权、申请所有权、注册、认证 UI、真实 IoT、AI/ML、通知、微服务、Kafka、实时预警流与部署不在 P4.1 内。
