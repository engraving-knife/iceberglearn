# 提交 0030：Build: increase open-pull-requests-limit to 50 (#8768)

## 提交信息

- **序号**：0030 / 4088
- **哈希**：b3ebccccfb7af6ee99a37b95ef15e655b55f5590
- **短哈希**：b3ebccccf
- **日期**：2023-10-11
- **作者**：JB Onofré
- **提交说明**：Build: increase open-pull-requests-limit to 50 (#8768)
- **PR/Issue**：#8768

## 总体目的

这个提交调整了 Dependabot 配置中 Gradle 依赖生态的 `open-pull-requests-limit` 参数，从 5 提升到 50。Dependabot 是 GitHub 内置的自动化依赖更新服务，会按配置的周期（本仓库是每周日）扫描依赖项是否有新版本，并为可升级的依赖自动创建 Pull Request。`open-pull-requests-limit` 控制 Dependabot 同时维持的未合并 PR 上限：达到上限后，Dependabot 不会再创建新的更新 PR，直到已有 PR 被合并或关闭。

Iceberg 是一个多引擎、多版本的大型项目，Gradle 依赖众多（涵盖 Spark、Flink、Hive、Parquet、Avro、ORC、Arrow 等多个计算引擎与文件格式，且 Spark/Flink 各有多个版本分支）。原来的 5 个 PR 上限严重制约了依赖更新的吞吐——一旦同时积累 5 个未合并的依赖升级 PR，Dependabot 就会停摆，新的安全补丁或版本升级无法及时以 PR 形式提出。把上限提到 50，可以让 Dependabot 在一个周期内为更多依赖同时开 PR，加快依赖更新节奏、缩短安全漏洞的暴露窗口。

注意：本次提交时该配置文件只声明了两个生态——`github-actions`（无显式 limit，用默认值 5）和 `gradle`（本次从 5 改为 50）。本次修改只影响 `gradle` 生态，`github-actions` 生态保持不变。

## 如何达成设计目的

整体思路是单行配置修改：把 `.github/dependabot.yml` 中 `gradle` 生态下的 `open-pull-requests-limit` 从 `5` 改为 `50`。Dependabot 在下次按计划扫描时即会读取新配置，允许同时维持最多 50 个 Gradle 依赖更新 PR。这是纯配置改动，不涉及任何源代码，不影响构建产物或运行时行为，仅影响 CI/CD 流程中 Dependabot 的行为。

## 修改详情

### [.github/dependabot.yml](file:///Users/fengxiaohang/trae/iceberglearn/.github/dependabot.yml)

**修改目的**：提升 Gradle 生态的 Dependabot 同时打开 PR 数上限，加快依赖更新吞吐。

**工作逻辑**：本次提交时该文件结构如下（两个生态）：

```yaml
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "sunday"
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "sunday"
    open-pull-requests-limit: 5   # <- 本次改为 50
```

修改只涉及 `gradle` 生态下的一行：

```diff
-    open-pull-requests-limit: 5
+    open-pull-requests-limit: 50
```

- `package-ecosystem: "gradle"` 告诉 Dependabot 扫描 Gradle 依赖（Iceberg 的 `build.gradle` 中声明的依赖）。
- `directory: "/"` 表示从仓库根目录开始扫描。
- `schedule: weekly / sunday` 表示每周日执行扫描。
- `open-pull-requests-limit: 50`（修改后）表示同时最多可有 50 个未合并的 Gradle 依赖更新 PR。提到 10 倍后，Dependabot 在一个周期内可以为多达 50 个不同依赖各自开 PR，避免因 PR 排队等待合并而阻塞其他依赖的更新。

`github-actions` 生态未设置该字段，Dependabot 默认值为 5（GitHub 官方文档默认值），本次未改动。

## 小结

通过将 Dependabot 中 Gradle 生态的 `open-pull-requests-limit` 从 5 提升到 50，解除了原配置对依赖更新 PR 数量的瓶颈，让 Iceberg 庞大的依赖树能更高效地通过自动化 PR 完成版本升级与安全补丁，是 CI/CD 流程效率方面的一项基础设施改进。
