# P2.2：预警处理与护理状态协调

范围：后端原子处理流程。P1 领养业务与 P2.1 时间线排序保持不变；本阶段不添加工作人员页面。

## 事务与锁顺序

1. `AlertRepository.findCatIdByAlertId` 只查询 catId，不加载 Alert 实体。
2. `CatRepository.findByIdForUpdate` 获取同一猫行的悲观写锁。
3. 重新查询 Alert，检查当前状态是否 OPEN；CLOSED 返回 409。
4. 设置 CLOSED 与服务端 resolvedAt；创建同猫、同预警的 CareRecord，createdAt 使用同一时间值。
5. 刷新事务中的写入，查询该猫剩余 OPEN 预警。
6. 仅在零个 OPEN 且状态恰好为 UNDER_OBSERVATION 时，将 Cat.status 恢复为 NORMAL。
7. 提交全部变更；失败或回滚会撤销预警、护理记录和猫状态的变更。

编排位于 `care.service.AlertResolutionService`。care 可以组合 alert/cat 的仓储；alert 不依赖 care，因此没有新增双向模块依赖。所有持久化操作和 DTO 组装均位于服务事务中，open-in-view 保持关闭。

先提交处理、后提交高温观察时，可以生成新的 OPEN FEVER 并重新阻止领养。先提交高温观察时，既有 OPEN FEVER 去重逻辑保留，随后处理看到当前状态。没有额外锁类型、异步任务或重试。

## 表结构

只添加 `V6__alert_resolution.sql`，V1–V5 保持字节不变。

| 变更 | 决策 |
| --- | --- |
| alerts.resolved_at | nullable timestamptz；新处理写服务端时间，历史 CLOSED 保持 NULL |
| care_records.alert_id | nullable bigint FK → alerts.id，ON DELETE SET NULL |
| idx_care_records_alert | alert_id 索引 |

保留 OPEN/CLOSED 持久化枚举和四项 CareRecordType。普通护理记录仍可没有预警关联。未增加操作者身份，也不猜测历史处理时间。V1 中已有但当前实体未使用的 acknowledged_at 不复用为处理时间，因为确认和处理是不同含义。

## HTTP 契约

`PATCH /api/alerts/{alertId}/resolve`

```json
{
  "careType": "CHECKUP",
  "note": "Temperature rechecked; resting comfortably."
}
```

careType 必填，值为 CHECKUP、MEDICATION、FEEDING 或 OTHER。note 必填、非空白，最长 2000 字符；服务保存时去掉首尾空白。

200 响应示例（ID 与时间由服务端产生）：

```json
{
  "alertId": 12,
  "catId": 4,
  "status": "CLOSED",
  "resolvedAt": "2026-09-10T12:00:00Z",
  "careRecordId": 8
}
```

| 情况 | HTTP / 行为 |
| --- | --- |
| 无效类型、缺字段、空白/过长备注 | 400，沿用项目校验错误格式，无数据变更 |
| 预警不存在 | 404，`message: Alert not found: <id>` |
| 预警已 CLOSED | 409，`This alert is already closed. Please refresh the cat's health timeline.`，不新增记录 |
| 原 body-less /close | 已移除，404，不能绕过护理事务 |

兼容性决策：本地项目没有生产客户端，移除旧 `/close`，不提供无备注的兼容转发；调用者应改用 `/resolve`。已更新唯一使用旧接口的领养回归测试。

`GET /api/cats/{catId}/health-timeline` 保持既有结构、三种事件和排序，只新增可空 `relatedAlertId`。该字段用于关联预警的 CARE_RECORD；普通记录及其他事件为 null。Alert.occurredAt 继续使用原 createdAt，护理记录使用处理时间。预警事件展示当前状态，不是不可变的状态变化审计日志。

## 临时状态规则与限制

没有剩余 OPEN 且恰好是 UNDER_OBSERVATION → NORMAL；其他情况不改变状态，包括 ADOPTED、SICK、ADOPTABLE 和任何人工状态。领养服务继续使用原有状态和 OPEN 检查，无需修改领养业务代码。

混合状态模型无法区分人工设置的 UNDER_OBSERVATION 与发烧触发的同名状态，因此本阶段严格执行精确匹配的桥接规则。它不恢复先前的 ADOPTABLE，也不重新运行温度判断；工作人员可以根据检查结果处理预警，之后的新高温观察仍可重新阻止领养。

当前没有认证/处理人字段，普通护理写接口、分页、完整审计与状态拆分均未实现。数据库外键只验证预警存在，同猫关联由工作流赋值保证；直接 SQL 仍可绕过应用约束。并发回归使用 H2，不代表 PostgreSQL 生产并发或负载验证。

## 文件清单

下列路径相对于 pawtrack 项目目录：

| 操作 | 文件 |
| --- | --- |
| 新增 | backend/src/main/java/com/pawtrack/backend/care/api/AlertResolutionController.java |
| 新增 | backend/src/main/java/com/pawtrack/backend/care/api/dto/AlertResolutionRequest.java |
| 新增 | backend/src/main/java/com/pawtrack/backend/care/api/dto/AlertResolutionResponse.java |
| 新增 | backend/src/main/java/com/pawtrack/backend/care/service/AlertResolutionService.java |
| 新增 | backend/src/main/resources/db/migration/V6__alert_resolution.sql |
| 修改 | backend/src/main/java/com/pawtrack/backend/alert/domain/Alert.java |
| 修改 | backend/src/main/java/com/pawtrack/backend/alert/repo/AlertRepository.java |
| 修改 | backend/src/main/java/com/pawtrack/backend/alert/service/AlertService.java |
| 删除 | backend/src/main/java/com/pawtrack/backend/alert/api/AlertController.java |
| 修改 | backend/src/main/java/com/pawtrack/backend/care/domain/CareRecord.java |
| 修改 | backend/src/main/java/com/pawtrack/backend/care/api/dto/HealthTimelineEventResponse.java |
| 修改 | backend/src/main/java/com/pawtrack/backend/care/api/mapper/HealthTimelineMapper.java |
| 新增 | backend/src/test/java/com/pawtrack/backend/care/api/AlertResolutionIntegrationTest.java |
| 新增 | backend/src/test/java/com/pawtrack/backend/care/api/AlertResolutionPostgresMigrationIT.java |
| 修改 | backend/src/test/java/com/pawtrack/backend/adoption/api/AdoptionReviewIntegrationTest.java |
| 修改 | backend/src/test/java/com/pawtrack/backend/care/api/CareRecordPostgresMigrationIT.java |
| 修改 | README.md、backend/ARCHITECTURE.md、backend/TASK.md |
| 新增 | docs/P2_2_ALERT_RESOLUTION.md |

V4→V5 的旧迁移测试显式固定 target=5，保留其原有升级边界。demo/bootstrap、领养业务、39.5℃ 阈值、时间线服务和前端未修改。

## 验证

新集成测试覆盖：成功关闭与时间、唯一关联记录、时间线/OSIV、重复处理及历史 CLOSED、单个/多个预警、ADOPTED/SICK/其他状态保护、后续高温重新阻止领养、领养恢复与继续阻止、非法输入、404、旧入口移除、整体回滚、并发处理及 Cat 锁等待后的当前状态读取。

新 PostgreSQL 迁移测试覆盖 V5→V6、历史 CLOSED 时间未知与通用护理记录保留、关联记录时间、非法外键拒绝、删除预警时保留护理备注并置空外键、索引存在与重复迁移无变更。测试只创建并清理自己的随机 schema。

测试数据库使用独立 `alert-resolution` H2 名称，避免既有测试的 create-drop 上下文关闭时清空另一个缓存上下文正在使用的同名数据库。

2026-09-10 最终验证：

| 命令 / 环境 | 结果 |
| --- | --- |
| Java 21，Maven `clean test`，完整默认后端套件 | 47 tests，0 failures，0 errors，0 skipped；BUILD SUCCESS |
| PostgreSQL 16.11，单独指定三个 MigrationIT | 3 tests，0 failures，0 errors，0 skipped；BUILD SUCCESS |
| V1–V5 SHA-256 与本轮修改前对比 | 全部相同 |

新增 AlertResolutionIntegrationTest 包含 22 个执行用例（含参数化）；P1 的重复关闭回归更新为重复处理返回 409。迁移测试按既有项目惯例单独运行，不计入默认 `test` 的 47 个用例；各自随机 schema 已清理，未迁移开发 public schema。

本次验证日志保存在 `backend/p2-2-test.log` 与 `backend/p2-2-migration-test.log`。P2.2 到此停止，后续阶段等待 review。
