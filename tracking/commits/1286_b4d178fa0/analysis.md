# 提交 1286：Build: Bump junit from 5.11.1 to 5.11.3 (#11401)

## 提交信息

- **序号**：1286 / 4088
- **哈希**：b4d178fa0502159ec087b1fced1747ee181fd4e3
- **短哈希**：b4d178fa0
- **日期**：2024-10-28（Mon Oct 28 10:05:32 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump junit from 5.11.1 to 5.11.3 (#11401)
- **PR/Issue**：#11401

## 总体目的

JUnit 5（Jupiter）是 Iceberg 全仓测试的基础框架，版本号在 `gradle/libs.versions.toml` 中以 `junit` 集中声明，并被 `junit-jupiter`、`junit-jupiter-engine`、`junit-vintage-engine` 等模块引用。dependabot 检测到 JUnit 5 从 5.11.1 升级到 5.11.3（patch 发布），本提交把该版本号对齐，获得上游的 bug 修复与稳定性改进。三类 JUnit 组件（Jupiter API/引擎、Vintage 兼容引擎）统一由 `junit` 版本变量驱动，故一行改动覆盖全部。

## 如何达成设计目的

在版本目录把 `junit = "5.11.1"` 改为 `junit = "5.11.3"`。所有引用 `libs.junit` 的依赖（`junit-jupiter`、`junit-jupiter-engine`、`junit-vintage-engine`）随之整体升级到 5.11.3。这是 dependabot 自动生成的单行 patch 升级，无代码逻辑变更。注意本提交未同步升级 `junit-platform`（仍为 1.11.2，由后续提交 #11402 单独处理）。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 JUnit 5 基线从 5.11.1 升到 5.11.3。

**工作逻辑**：

```toml
-junit = "5.11.1"
+junit = "5.11.3"
```

相邻的 `junit-platform = "1.11.2"` 本提交不动。JUnit 5.11.x 与 Platform 1.11.x 的版本对应关系遵循 JUnit 上游约定（Jupiter 5.11.3 对应 Platform 1.11.3），故本提交与 #11402（platform 升 1.11.3）配套。

## 小结

- **成效**：JUnit 5（Jupiter + Vintage 引擎）升级到 5.11.3 patch，获得上游修复，使测试框架基线保持最新。
- **影响范围**：改动 1 个文件、1 行，仅测试依赖版本变更，无源代码变更。影响全仓测试 classpath（不含产物依赖）。
- **回迁到 1.4.x 的注意事项**：
  - **可选回迁**：纯测试框架 patch 升级，不影响 Iceberg 产物功能。1.4.x 回迁收益主要是让测试基线与 main 一致；若 1.4.x 测试在 5.11.1 下正常，可不回迁。
  - **配套性**：建议与 #11402（`junit-platform` 1.11.2 → 1.11.3）配套回迁，保持 Jupiter 与 Platform 版本对应关系一致，避免版本错配告警。
  - **风险**：低。JUnit patch 版本向后兼容，回迁后跑一遍测试套件确认无回归即可。
