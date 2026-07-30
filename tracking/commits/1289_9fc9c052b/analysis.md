# 提交 1289：Build: Bump junit-platform from 1.11.2 to 1.11.3 (#11402)

## 提交信息

- **序号**：1289 / 4088
- **哈希**：9fc9c052b592d7e79b2900eccf0459e1951d9e60
- **短哈希**：9fc9c052b
- **日期**：2024-10-28（Mon Oct 28 11:23:45 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump junit-platform from 1.11.2 to 1.11.3 (#11402)
- **PR/Issue**：#11402

## 总体目的

JUnit Platform 是 JUnit 5 的平台层（提供测试引擎 SPI、`@Suite` 套件 API 与套件引擎等），其版本号在 `gradle/libs.versions.toml` 中以 `junit-platform` 声明，被 `junit-platform-suite-api`、`junit-platform-suite-engine` 引用。dependabot 检测到 Platform 从 1.11.2 升级到 1.11.3（patch 发布），本提交把该版本号对齐，使测试平台层与同批升级的 Jupiter 5.11.3（见 #11401）版本对应，获得上游修复。

## 如何达成设计目的

在版本目录把 `junit-platform = "1.11.2"` 改为 `junit-platform = "1.11.3"`。引用 `libs.junit.platform` 的依赖（suite-api、suite-engine）随之整体升级。这是 dependabot 自动生成的单行 patch 升级，无代码逻辑变更。本提交与 #11401（`junit` 5.11.1 → 5.11.3）配套，保持 Jupiter 与 Platform 版本对应关系一致。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 JUnit Platform 从 1.11.2 升到 1.11.3。

**工作逻辑**：

```toml
-junit-platform = "1.11.2"
+junit-platform = "1.11.3"
```

此时相邻的 `junit = "5.11.3"`（已由 #11401 升级），二者配套。

## 小结

- **成效**：JUnit Platform 升级到 1.11.3 patch，与 Jupiter 5.11.3 版本对应，获得上游修复。
- **影响范围**：改动 1 个文件、1 行，仅测试依赖版本变更，无源代码变更。影响使用 `@Suite` 套件机制的测试 classpath。
- **回迁到 1.4.x 的注意事项**：
  - **可选回迁**：纯测试平台 patch 升级，不影响产物功能。
  - **配套性**：建议与 #11401（`junit` 5.11.1 → 5.11.3）配套回迁，保持 Jupiter 与 Platform 版本对应，避免版本错配告警。
  - **风险**：低。patch 版本向后兼容，回迁后跑测试套件确认无回归即可。
