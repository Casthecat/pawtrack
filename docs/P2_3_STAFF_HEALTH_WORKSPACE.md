# P2.3：工作人员健康工作台

完成日期：2026-09-10。现在可以在页面中演示 OPEN 预警 → 查看猫咪历史 → 输入护理动作 → 处理 → 查看 CLOSED 预警与新护理记录 → 公开详情恢复申请。范围止于 P2.3。

## 文件变更

路径相对于 pawtrack 项目根目录。

| 操作 | 文件 |
| --- | --- |
| 新增 | backend/src/main/java/com/pawtrack/backend/alert/api/AlertQueueController.java |
| 修改 | backend/src/main/java/com/pawtrack/backend/alert/api/dto/AlertResponse.java |
| 修改 | backend/src/main/java/com/pawtrack/backend/alert/api/mapper/AlertMapper.java |
| 修改 | backend/src/main/java/com/pawtrack/backend/alert/repo/AlertRepository.java |
| 修改 | backend/src/main/java/com/pawtrack/backend/alert/service/AlertService.java |
| 新增 | backend/src/test/java/com/pawtrack/backend/alert/api/AlertQueueIntegrationTest.java |
| 修改 | frontend/src/App.tsx |
| 修改 | frontend/src/api/types.ts |
| 修改 | frontend/src/api/pawtrack.ts |
| 修改 | frontend/src/api/axiosInstance.ts |
| 新增 | frontend/src/api/careQueryKeys.ts |
| 新增 | frontend/src/components/StaffLayout.tsx |
| 新增 | frontend/src/components/HealthTimeline.tsx |
| 新增 | frontend/src/pages/CarePage.tsx |
| 新增 | frontend/src/lib/careLabel.ts |
| 修改 | frontend/src/styles/globals.css |
| 修改 | README.md、backend/ARCHITECTURE.md、backend/TASK.md |
| 新增 | docs/P2_3_STAFF_HEALTH_WORKSPACE.md |

本地验证辅助文件位于已忽略的 `.local/p2-3-browser.cjs` 和 `.local/p2-3-browser/` 截图目录；后端日志为 `backend/p2-3-test.log`。没有增加前端测试框架或项目依赖。P1 页面、P2.1/P2.2 领域、处理事务、时间线 DTO、demo seed 和 V1–V6 未修改。

## 后端读取契约

`GET /api/alerts?status=OPEN`

省略 status 时默认 OPEN，也接受 CLOSED。无分页、搜索或组合过滤。200 返回数组，字段为 `id、catId、catName、type、severity、status、message、createdAt、resolvedAt`。message/resolvedAt 可以为 null；空队列返回 `[]`；非法状态沿用项目 400/message 格式。

顺序为 createdAt DESC、id DESC。Repository fetch join Cat，AlertService 在只读事务内组装 DTO，不依赖 open-in-view。对既有 AlertResponse 只补充 resolvedAt，使列表与当前预警元数据一致。新增 4 个集成测试覆盖 OPEN 排除 CLOSED、猫信息/OSIV、同时间确定性排序、空队列、CLOSED 过滤及非法状态。

## 路由与组件

App 的现有站点 shell 保留。StaffLayout 仅提供两个导航链接和 Outlet：`/staff` index 渲染原 ReviewPage，`/staff/care` 渲染 CarePage。

CarePage 管理队列查询、选中预警上下文、处理 mutation 和失效操作；内部 CareDetail 根据选中 alertId 重置本地表单，并查询该猫的时间线。HealthTimeline 按服务端原顺序渲染观察、预警和护理记录，未重排或绘制图表。所有请求通过 api/pawtrack.ts，组件没有直接调用 Axios。

桌面为左队列、右猫历史/处理；900px 以下堆叠。使用既有暖色背景、绿色按钮和卡片。类型/严重度/状态均有文字；表单使用关联 label、required、maxLength 和键盘可访问控件。反馈使用 role=status/alert。

## Query key 与刷新

| 数据 | Key | 处理成功 / 409 |
| --- | --- | --- |
| OPEN 队列 | `['alerts', 'OPEN']` | invalidate |
| 选中猫时间线 | `['health-timeline', catId]` | invalidate |
| 猫详情 | `['cat', String(catId)]` | invalidate，与原详情页字符串 ID 一致 |
| 猫画廊 | `['cats']` | invalidate |

React Query 自动重取活跃查询；未挂载查询变为 stale，后续访问时重取。没有整页 reload。护理查询设 staleTime=0，在重新挂载或窗口重新聚焦时刷新；手动 Refresh 同时刷新选中猫的相关查询。无轮询、实时推送或后台模拟。

仅把选中 alert 的标识与展示上下文留在本地 state，远端列表/事件保留在 React Query。选择不会因为处理后队列移除该项而自动切到下一只猫。点击其他预警才切换右侧并重置草稿。

## 处理成功与失败

成功：显示具体预警处理成功与护理记录提示，刷新相关查询，保留该猫时间线，显示 CLOSED 与新 CARE_RECORD。队列中的其他预警继续显示。表单 pending 时禁用输入、重复提交和队列切换。

409：显示后端刷新提示并刷新同一组查询，不显示成功消息。如果最新历史已为 CLOSED，隐藏处理表单但保留错误说明与历史。

网络/服务器失败：显示可理解的错误，不清空 careType 或 note，允许重试。后端字段校验错误沿用 apiError 转为字段/原因文字。空白 note 无法提交，前端最长 2000 字符。队列与历史各自有加载、失败重试、空状态。

## 实际验证命令与结果

本机 Java 21；下面命令分别在 backend/frontend 目录执行。初次前端构建因沙箱目录访问被拒，在获得执行许可后通过；首次 lint 的 Fast Refresh 规则问题已通过拆分 careLabel 工具修正。

```powershell
mvn clean test
```

**51 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS**。包括全部原后端默认测试与新增队列测试。既有 PostgreSQL MigrationIT 仍按原方式单独运行；本阶段无 schema 改动，未重复执行迁移测试。

```powershell
npm.cmd run build
npm.cmd run lint
```

build：TypeScript + Vite 生产构建通过。lint：通过，0 错误、0 警告。

演示后端命令为 `mvn spring-boot:run '-Dspring-boot.run.profiles=demo'`，Vite 本地页面为 `http://127.0.0.1:5173`，代理后端 9090。此处命令已去掉历史验证使用的个人缓存路径。

```powershell
# 当前可复现的浏览器检查（历史 P2.3 脚本位于忽略的 .local，不能从干净克隆运行）
npm run test:care
npm run test:live
```

使用环境已有 Playwright 驱动本机 Edge 无头浏览器，桌面 1440×1100 与窄屏 390×844。通过以下检查，并查看实际截图：

- 公开 Nori 页面先显示申请暂停，再从共享工作人员导航进入护理。
- 全局队列及时间线 503 错误和重试，错误不误显示为空队列。
- 网络请求中断后备注保持不变，重试成功。
- 处理 Nori 后队列清空，右侧保持 Nori、新 CARE_RECORD、CLOSED 预警及关联引用。
- 再进入缓存过的公开详情，申请表恢复；画廊重新访问时刷新。
- P1 Mochi 提交、申请回执与 `/staff` 审批仍可完成。
- 窄屏多个预警选择、类型下拉、备注与处理；处理 Nori 后 Cleo 仍留在队列。
- 新选预警存在于更新的时间线中；修复了全局 5 秒 staleTime 对护理短间隔导航造成的暂时旧历史。
- 另一请求真实处理 Cleo 后，原页面提交得到 409，刷新后显示另一条护理记录；数据库只有一条关联记录。
- 初始空队列、键盘从 Care type Tab 到 Care note、两种宽度均无横向溢出和页面脚本错误。

浏览器验证额外上报的少量观察只写入临时 demo 内存库，未改 seed 或引入模拟器。验证后重启 demo 恢复初始 Nori OPEN 场景。

## 假设与技术债

日期/时间按浏览器本地格式显示；活动数值原样展示，不推断医学意义。护理查询依赖挂载、聚焦与手动刷新，没有实时推送。草稿为选中预警的本地状态，切换预警或离开页面会丢弃；网络失败时保留。

混合 Cat.status、无身份认证、未分页、事务并发回归主要基于 H2 等已有边界保持不变。39.5℃ 仍为继承的演示规则，不能称为临床验证。未实施 P2.4 硬化、过期观察保护、状态拆分或部署。
