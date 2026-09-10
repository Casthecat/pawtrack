# PawTrack 当前实现架构

更新：2026-09-10。项目面向求职作品集，当前范围为 P1 领养、P2 护理及 P2.4a/b 冻结验证，等待审阅后冻结。实现背景与明确边界见 [项目梳理](../docs/PROJECT_REVIEW.md)。

## 运行结构

React + TypeScript + Vite 通过 REST API 调用 Java 21 / Spring Boot 3.3.6。后端是按业务模块组织的单体应用，持久开发环境使用 PostgreSQL 与 Flyway V1–V6，JPA 使用 validate。测试及 demo profile 使用独立 H2 内存库，并允许 create-drop，避免依赖开发数据。

模块：cat、healthdata、alert、adoption、care。各模块按 api（controller、DTO、mapper）、service、domain 和 repo 组织；这是包级组织约定。Controller 不直接访问 Repository，也不向客户端返回实体。领养、护理时间线、预警队列和处理响应在服务事务内组装 DTO；部分旧猫咪/健康接口在 controller 映射已读取的标量或关联 ID。全局关闭 open-in-view。

## 领养事务

提交、批准和拒绝都先锁定对应的 Cat 行。审核接口先查询 catId，再取得猫行的悲观写锁，最后读取申请当前状态，避免锁等待前读入旧状态。

提交校验猫状态为 NORMAL 或 ADOPTABLE、没有 OPEN 预警，并检查同猫同邮箱是否存在 PENDING 申请。邮箱去掉首尾空白并统一小写。

批准只允许 PENDING。单个事务内执行：申请变为 APPROVED，猫变为 ADOPTED，其他 PENDING 申请变为 REJECTED。拒绝只改变当前 PENDING 申请。重复或逆转终态返回 409。不存在的记录返回 404；输入格式/字段错误返回 400。

应用服务使用相同的猫行锁约定协调领养、健康上报、状态更新与图片写入。这是应用路径上的一致性保证，尚未添加数据库唯一约束来覆盖外部脚本或直接 SQL 写入。

## 健康与领养联动

HealthDataService 保存数据后调用 HealthMonitorService。当前规则检查最新温度是否高于 39.5；这只是沿用已有的演示规则。

HealthData 最新值与历史查询统一按 ts DESC、id DESC；同时间取更大 ID。可空温度的 HTTP 输入使用 @Digits(integer=3, fraction=2) 对齐 NUMERIC(5,2)，多余精度/超容量返回 400，不静默舍入。前端时间线及详情均显示两位小数。新的正常观察不会自动关闭已有 OPEN 预警。

同猫同类型已有 OPEN 预警时不重复创建。关闭后再次发现异常可以新建预警。健康监测不会覆盖 ADOPTED 或 SICK 状态，普通猫状态接口也不能直接设置或撤销 ADOPTED。

cat.status 仍混合健康与领养含义，是后续模型债务。P2.2 只在工作人员处理最后一个 OPEN 预警时，将恰好为 UNDER_OBSERVATION 的猫恢复为 NORMAL。自动关闭预警、历史窗口规则、通知和机器学习目前未实现。

## 护理时间线（P2.1）

CareRecord 表示工作人员人工输入的一次护理动作或备注。P2.1 的追加式基础字段是 `id`、`cat`、`type`、`note`、`createdAt`；类型刻意限制为 CHECKUP、MEDICATION、FEEDING、OTHER。P2.2 增加可空的 Alert 关联，普通护理记录仍可不关联预警。

`GET /api/cats/{catId}/health-timeline` 返回 `catId`、`catName`、固定值 `order: NEWEST_FIRST` 和 `events`。每个事件都有 `eventKind`、`sourceId`、`occurredAt`、`eventType`、`description`，并按来源选填 `temperatureC`、`activityLevel`、`alertStatus`、`alertSeverity`。`eventKind` 明确区分 HEALTH_OBSERVATION、ALERT、CARE_RECORD，Controller 不暴露 JPA 实体。

时间线以各领域事件的发生时间降序排列：健康观察使用 `HealthData.ts`，预警和护理记录使用各自的 `createdAt`。相同时间时依次按 ALERT、CARE_RECORD、HEALTH_OBSERVATION 排列，最后按来源 ID 降序，确保结果确定。服务在只读事务内加载并映射三种实体，因此不依赖 open-in-view。

当前实现执行三次模块查询后在内存中合并，尚未分页；数据量增长后应改为有界查询或数据库级合并。`createdAt` 暂时同时代表护理记录的录入时间，未来若需要补录历史护理动作，应增加独立的发生时间字段。

只读事务沿用默认隔离级别；READ_COMMITTED 下多次查询不保证同一快照，跨查询可能看到处理前后的不同状态。当前没有宣称时间线是强一致快照。

## 预警处理事务（P2.2）

`care.service.AlertResolutionService` 编排跨模块操作，alert 模块不反向依赖 care。`PATCH /api/alerts/{alertId}/resolve` 接受护理类型与备注，使用服务层事务，先以标量查询获得 catId，再通过 `CatRepository.findByIdForUpdate` 获取猫行悲观写锁，之后才读取 Alert 可变状态。全局顺序是 Cat → Alert/CareRecord/领养相关状态，与健康上报和领养事务一致。

只有 OPEN 可以处理。事务将预警设置为 CLOSED，记录服务端 `resolvedAt`，创建同时间且关联预警的 CareRecord，刷新后检查该猫剩余 OPEN 预警。只有零个 OPEN 且 Cat.status 恰好为 UNDER_OBSERVATION 时，才恢复 NORMAL。ADOPTED、SICK 及其他显式状态全部保留。这是混合状态模型下的临时兼容规则，不是新的状态框架。

旧 body-less `/close` 接口和 `AlertService.closeAlert` 已删除；本地项目没有生产 API 客户端，仅更新其回归测试。重复 `/resolve` 返回带刷新提示的 409，不重写时间也不创建第二条记录。V6 新增可空 `alerts.resolved_at` 和 `care_records.alert_id`（ON DELETE SET NULL），保留历史已关闭预警的未知处理时间为 NULL。

时间线的原 Alert 事件继续按原始 createdAt 排序，处理动作以 CareRecord.createdAt 出现。只新增可选 `relatedAlertId` 供护理事件追溯来源，DTO 在服务事务内组装。更完整的契约和验证记录见 [P2.2 说明](../docs/P2_2_ALERT_RESOLUTION.md)。

## 前端状态

React Router 提供猫咪列表、猫咪详情、申请回执和工作人员审核页面。React Query 缓存 API 数据；提交与审核后使相关查询失效，回执和审核队列定期刷新。前端负责输入体验，后端始终重新检查业务资格。

P2.3 用 StaffLayout 共享工作人员导航：`/staff` 保留领养审核，`/staff/care` 提供 OPEN 队列、三种事件的时间线和护理处理表单。新增 `GET /api/alerts?status=OPEN` 只读接口，默认 OPEN，也可指定 CLOSED；按 createdAt DESC、id DESC 返回 DTO，服务在只读事务内组装猫信息，没有迁移或领域改动。

护理处理成功及 409 冲突会使 `['alerts','OPEN']`、`['health-timeline',catId]`、`['cat',String(catId)]`、`['cats']` 失效。活跃查询即时刷新，未挂载的详情/画廊下次进入时重取；护理查询 staleTime=0，重新挂载时刷新。选中上下文独立于 OPEN 队列成员资格，处理后保留同猫历史。失败与空队列分开显示；网络/服务错误保留表单，409 显示后端消息，不当作成功。

首次自动选择保存为实际选择，后续队列重排或移除不改变编辑对象。外部处理后保留未提交备注；时间线未成功加载或加载失败时禁用处理。该规则消除了初次 GET 与处理后刷新竞争的已知路径，没有引入观察版本或过期决策保护。重启 demo 后应重载浏览器页面，清除上一轮复用 ID 的本地状态。

Vite 的 /api 和 /uploads 代理连接本地后端。错误页面区分网络失败与空数据；表单防重复点击，审核先显示具体影响再提交。

## 当前边界

这是本地 demo，没有身份认证、角色授权或申请所有权验证；“工作人员页面”是演示入口。不得直接承载真实申请人数据。相机 URL 存在于历史模型中，但前端未提供私有直播能力。

默认 H2 套件验证 63 个用例；三个独立 PostgreSQL 迁移测试验证升级链；CatLockPostgresRuntimeIT 以 Flyway 初始化的 UUID schema、Hibernate validate 和真实 Spring 服务事务验证三种 Cat 锁执行顺序，并通过 pg_blocking_pids 确认等待。独立浏览器命令分别覆盖模拟 API 的边界回归与真实 demo 的两条业务流程，命令及结果见 README 和 [冻结报告](../docs/P2_4B_MILESTONE_FREEZE.md)。

这些证据不代表所有线程交错、生产并发吞吐或分布式写入保证。混合 Cat.status、未分页、人工决策新鲜度、时间线快照、直接 SQL 绕过应用约束、文件上传与权限边界仍是明确债务。认证、真实 IoT、AI/ML、通知、微服务、Kafka、实时预警流与部署不在此里程碑内。
