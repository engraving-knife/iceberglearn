# 提交 2270：Build: Use Java 17 to publish snapshot to Maven (#13369)

## 提交信息

- **序号**：2270 / 4088
- **哈希**：8601bda6d97272baf7194141720823fd6f7ecc00
- **短哈希**：8601bda6d
- **日期**：2025-06-25 12:28:44 +0200
- **作者**：Cheng Pan
- **提交说明**：Build: Use Java 17 to publish snapshot to Maven (#13369)
- **PR/Issue**：#13369

## 总体目的

本提交将发布快照（snapshot）到 Maven 中央仓库的 GitHub Actions 工作流所使用的 JDK 版本从 11 升级到 17。这是 Iceberg 项目整体向 JDK 17 迁移的一部分。

随着 Iceberg 项目逐步要求 JDK 17 作为基线（例如 Spark 4.0 支持需要 JDK 17/21，参见 2271 提交），继续使用 JDK 11 来构建和发布快照会导致构建产物无法正确包含需要 JDK 17 编译的模块，或者在发布过程中因 JDK 版本不匹配而失败。将发布快照的 JDK 升级到 17，确保发布的快照制品与项目新的最低 JDK 要求一致，且能正确编译所有模块。

## 如何达成设计目的

- 修改 `.github/workflows/publish-snapshot.yml` 中 `actions/setup-java` 步骤的 `java-version` 输入，从 `11` 改为 `17`。
- 保留 `distribution: zulu` 不变，仅调整版本号。
- 发布流程其余步骤（`printVersion`、`publishApachePublicationToMavenRepository`）不变，仅运行时 JDK 发生变化。

## 修改详情

### `.github/workflows/publish-snapshot.yml` (+1/-1 lines)

**修改目的**：将快照发布工作流的 JDK 版本从 11 升级为 17。

**工作逻辑**：在该工作流的 `setup-java` 步骤中，`java-version: 11` 被改为 `java-version: 17`。后续的 `./gradlew printVersion` 和 `./gradlew -DallModules publishApachePublicationToMavenRepository` 命令将运行在 JDK 17 环境下。这样所有模块（包括要求 JDK 17 的 Spark 4.0 模块）都能被正确编译并发布到 Maven 仓库，避免因 JDK 版本过低导致的编译失败或制品缺失。

## 总结

本提交是 Iceberg 项目 JDK 基线迁移工作的一环，将快照发布流程升级到 JDK 17，确保发布制品与项目最低 JDK 要求保持一致。改动虽小（一行），但对于持续交付流水线的正确性至关重要。
