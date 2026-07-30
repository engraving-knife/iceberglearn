# 提交 0858：Run Hive3 tests on Java 11 and 17 too (#10482)

## 提交信息
- **序号**：0858 / 4088
- **哈希**：10d7ab11c90f7f0f6b60919e3bca6db251ec697e
- **短哈希**：10d7ab11c
- **日期**：2024-06-19
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Run Hive3 tests on Java 11 and 17 too (#10482)
- **PR/Issue**：#10482

## 总体目的

Iceberg 项目声明支持在 Java 8、11、17 三个版本下构建，仓库里大多数 CI 流水线已经在三个 JDK 上分别运行作业。但 Hive3 子模块的 CI（`.github/workflows/hive-ci.yml` 中的 `hive3-tests` job）此前仅在 `java-version: 8` 一个固定版本下执行，缺少对 Java 11 与 17 的覆盖；同时 `settings.gradle` 里对 `hive3` / `hive3-orc-bundle` 这两个子工程的 `include` 还做了「只在 Java 8 下生效」的限定——这意味着本地用 Java 11 或 Java 17 构建时根本无法构建 hive3 子模块，更不可能跑它的测试。

这两处限制与项目声明的多 JDK 构建支持策略不一致，也意味着「本地用 Java 11/17 构建 + 运行 Hive3 测试」是否能通过的信号缺失。该提交把 Hive3 测试扩展到 Java 8/11/17 全部三个支持的 JDK 上，从而保证 hive3 子模块在三个 JDK 上都能持续构建并跑通测试，避免悄悄引入只对 Java 8 友好的依赖或代码。

## 如何达成设计目的

修改分两个文件：

**1. `.github/workflows/hive-ci.yml` 的 `hive3-tests` job**：原本是一个固定 JDK 8 的单 job。改为引入 `strategy.matrix`，将 `jvm: [8, 11, 17]` 作为矩阵，把 `actions/setup-java` 的 `java-version` 从写死的 `8` 改为 `${{ matrix.jvm }}`。这样 CI 会自动为 8、11、17 三个 JDK 各起一个 job 跑 hive3 测试，无需额外复制步骤。这里和 Flink 的 0857 提交不同——Hive3 没有像 Flink 1.17 那样需要排除的组合，所以不需要 `exclude` 段。

**2. `settings.gradle` 的 `hive3` 子工程 include 条件**：原逻辑是 `if (JavaVersion.current() == JavaVersion.VERSION_1_8) { if (hiveVersions.contains("3")) { include 'hive3' ... } }`，即「只有当构建用 JDK 8 时，才把 hive3 和 hive3-orc-bundle 这两个工程纳入构建」。这是 Java 11/17 下无法构建 hive3 子模块的直接原因。修改后简化为 `if (hiveVersions.contains("3")) { include 'hive3' ... }`，把外层对 `JavaVersion.VERSION_1_8` 的判断去掉，让 hive3 子工程在任意 JDK 下都会被纳入构建。内层对 `hiveVersions.contains("3")` 的判断保留——这是 Iceberg 的版本目录机制：通过 `-PhiveVersions=2,3` 之类的 Gradle 属性控制要构建哪些 Hive 版本对应的子工程，hive3 工程只在用户主动选择 Hive 3 版本时才参与构建，这是与 JDK 选择正交的另一维度的开关。

两者配合：settings.gradle 解除了「JDK 8 才构建 hive3」的人为限制，hive-ci.yml 给 hive3 测试加了 11/17 的 JDK 矩阵覆盖，从此 Hive3 子模块能在三个 JDK 上同时被构建和测试。

## 修改详情

### `.github/workflows/hive-ci.yml`
**修改目的**：将 `hive3-tests` job 从单一 JDK 8 扩展到 JDK 8/11/17 矩阵。
**工作逻辑**：在 `hive3-tests:` 下新增 `strategy.matrix`，`jvm: [8, 11, 17]`；把 `actions/setup-java@v4` 的 `java-version: 8` 改为 `java-version: ${{ matrix.jvm }}`。其余步骤保持不变，CI 框架自动为三个 JDK 各拉起一个 hive3 测试 job。

### `settings.gradle`
**修改目的**：解除「只有 JDK 8 才 include hive3 子工程」的限制，使其在所有 JDK 下都能被构建。
**工作逻辑**：把原来的 `if (JavaVersion.current() == JavaVersion.VERSION_1_8) { if (hiveVersions.contains("3")) { include 'hive3'; include 'hive3-orc-bundle'; ... } }` 改为直接 `if (hiveVersions.contains("3")) { include 'hive3'; include 'hive3-orc-bundle'; ... }`，去掉外层 `JavaVersion.VERSION_1_8` 判断，保留内层 `hiveVersions.contains("3")` 的版本目录机制开关。这是 Java 11/17 下 hive3 子模块能被 Gradle 纳入构建的关键。

## 小结
- **成效**：Hive3 子模块 CI 从「仅 JDK 8」升级为「8 + 11 + 17」三个 JDK 全覆盖；settings.gradle 同时放开了 hive3 子工程对 JDK 8 的硬绑定，让本地用 Java 11/17 也能构建与测试 hive3。与项目声明的三版本 JDK 支持策略对齐，能够更早发现仅在高 JDK 下暴露的问题（默认字符集变更、JDK 内部 API 移除、Hive3 第三方依赖对高 JDK 的兼容性等）。
- **影响范围**：仅影响 CI 配置与 Gradle 工程组织，不改动任何业务源代码或运行时行为；新增的 Java 11、Java 17 hive3 测试 job 会消耗额外 CI 资源。
- **回迁注意事项**：1.4.x 分支若保留了对应 `.github/workflows/hive-ci.yml` 与 `settings.gradle`，可直接 cherry-pick。但需重点验证：(a) hive3 子模块及其第三方依赖在 Java 11/17 下能否真正构建通过（Hive 3 自身对高 JDK 兼容性历史上有坑，可能需要额外 JVM 参数如 `--add-opens`）；(b) 1.4.x 分支的 hive-ci.yml 里 `hive3-tests` 是否还包含其他写死 JDK 8 的步骤（例如 Spark / Parquet 等版本绑定）需一并梳理；(c) settings.gradle 改动会影响所有 JDK 下的本地 Gradle 构建，回迁后需要在 8/11/17 上分别跑一次 `./gradlew :iceberg-hive3:build` 确认无回退。
