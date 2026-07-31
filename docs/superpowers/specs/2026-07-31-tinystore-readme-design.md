# Tiny Store GitHub README — 设计文档

- 创建于：2026-07-31
- 状态：已获用户批准
- 目标仓库：`github.com/jamespud/tiny-store`（remote: `git@github.com:jamespud/tiny-store.git`）

## 背景

仓库目前没有可用的 README：根目录 `README.MD` 是被 git 跟踪的**空文件**（0 字节）。仓库将在 GitHub 上展示，需要一套完整的 README。

## 需求（经用户确认）

| 项 | 决定 |
|----|------|
| 语言 | 中英双语 |
| 组织形式 | 双文件：`README.md`（英文完整版）+ `README.zh-CN.md`（中文完整版），顶部互放语言切换链接 |
| 主要用途 | 架构展示为主（DDD、六边形、Outbox、Saga、幂等、库存状态机等模式是仓库核心资产） |
| 架构图 | Mermaid 图（GitHub 原生渲染） |

## 方案选择

**方案 A：标准完整版**（选定）—— 徽章 + 架构图 + 模块表 + 模式详解 + 快速开始 + 测试策略 + 文档索引。仓库的核心价值是架构设计与 docs/rfcs、docs/architecture 文档，README 负责索引这些资产。

（备选方案 B 极简版被否决：篇幅短但浪费架构资产展示机会。）

## 文件操作

1. `git mv README.MD README.md` — 改名为 GitHub 惯例小写形式（README.MD 已被 git 跟踪且为空，git mv 保留历史）
2. 写入 `README.md`（英文）
3. 写入 `README.zh-CN.md`（中文）

范围外：根目录 `BOOT-INF/`、`perf/` 等散落构建残留**不处理**，避免无关改动。

## README.md 结构（英文完整版）

1. **标题 + 简介** — Tiny Store：DDD 风格微服务电商平台，一句话定位 + 核心亮点列表
2. **徽章** — Java 17 / Spring Boot 3.5 / Spring Cloud 2025.0.0 / MIT License
3. **语言切换** — `English | 简体中文`（链到 README.zh-CN.md）
4. **架构总览** — Mermaid flow 图：Gateway → 7 个领域服务（Auth/Account/Inventory/Order/Payment/Product/Promotion）→ PostgreSQL / Redis / Kafka / Nacos；标注 outbox 事件流与 ACL Feign 调用
5. **服务模块表** — 9 个 Maven 模块（8 个可部署服务 + 共享基础设施库 `tinystore-library-infrastructure`）：端口 / schema / 职责（数据以 Makefile 与 pom.xml 核实为准；gateway 无 schema，列 "—"）
6. **核心架构模式** — 六边形架构（每模块 domain/application/infrastructure/interfaces 分层）、Outbox 模式（order → Kafka，含 DLT）、支付 Saga 状态机、Redis 幂等（网关 + order 双实现）、ACL（Feign）、特性开关、库存预扣规范状态机（RFC-001：PRE_DEDUCTED → CONFIRMED/RELEASED/EXPIRED）
7. **快速开始** — 前置条件（Docker、JDK 17）→ `make debug`（一键起 8 服务 + 基础设施）→ 端口表 → 常用 make 目标（build/unit/it/e2e/test）
8. **测试策略** — Unit（mock）→ IT（Testcontainers）→ E2E（compose 黑盒，经网关）→ 性能（200 并发一致性、库存超卖边界、幂等、锁竞争、k6）
9. **文档** — 链接 docs/rfcs/RFC-001-canonical-inventory-reservation.md、docs/architecture/、docs/performance/
10. **License** — MIT（链接 LICENSE）

## README.zh-CN.md 结构（中文完整版）

与 README.md 完全对应的中文版本，顶部语言切换链接指向 README.md。

## 事实来源（防止 CLAUDE.md 冲突标记污染）

根目录 `CLAUDE.md` 存在未解决的合并冲突标记（Updated upstream / Stashed changes），**不作为事实来源**。所有事实从以下核实：

- `pom.xml`：Java 17、Spring Boot 3.5.0、Spring Cloud 2025.0.0、Spring Statemachine 4.0.1、Testcontainers 2.0.2、模块列表
- `Makefile`：构建/测试命令、服务端口（Gateway 8080 / Auth 9000 / Account 8000 / Inventory 13000 / Order 28080 / Payment 8083 / Product 8090 / Promotion 1200）、Nacos 8848 / PG 5432 / Redis 6379 / Kafka 9092
- `LICENSE`：MIT，Copyright (c) 2025 spud

## 验收标准

1. `README.md` 与 `README.zh-CN.md` 均在仓库根目录
2. `README.MD` 被 `git mv` 改名（历史保留），不残留空文件
3. 两个文件内容完整对应，无占位符、无 TBD
4. 模块表端口/schema 与 Makefile 核实数据一致
5. Mermaid 图语法正确（GitHub 渲染友好：flowchart LR，节点文本不含引号冲突）
6. 所有文档链接指向实际存在的文件
