# 提交 2673：Build: Bump guava from 33.4.8-jre to 33.5.0-jre (#14128)

## 提交信息

- **序号**：2673 / 4088
- **哈希**：6b80e5c42beb856be5c84c00b9f96d7ff268a7d7
- **短哈希**：6b80e5c42
- **日期**：2025-09-23 10:11:20 +0200
- **作者**：dependabot[bot]（与 Russell Spitzer 共同提交）
- **提交说明**：Build: Bump guava from 33.4.8-jre to 33.5.0-jre (#14128)
- **PR/Issue**：#14128（含子 PR #14145）

## 总体目的

本提交由 Dependabot 发起，将 Iceberg 项目使用的 Google Guava 库从 33.4.8-jre 升级到 33.5.0-jre。这是一次 minor 版本升级（33.4 → 33.5），按照语义化版本规范，可能包含新功能、改进和 bug 修复，但不引入破坏性变更。

Guava 是 Iceberg 核心依赖之一，被广泛用于集合操作、缓存、哈希、IO 等场景。Iceberg 还维护了一个 `bundled-guava` 模块，将 Guava 的部分类重定位（relocate）后打包，以避免与用户环境的 Guava 版本冲突。因此升级 Guava 时，除了更新版本号，还需要同步更新 bundled-guava 模块中显式列出的"保留类"清单——因为新版本可能引入了 Iceberg 需要使用的新类。

本提交实际上合并了两个 PR 的工作：Dependabot 的版本升级（#14128）和 Russell Spitzer 提交的"Guava: Add UnsignedBytes"（#14145）。后者在升级 Guava 的同时，将新需要的 `UnsignedBytes` 类加入 bundled-guava 的保留清单，确保该类在 shaded 打包后仍然可用。

## 如何达成设计目的

通过两处修改完成：一是更新 `gradle/libs.versions.toml` 中的 Guava 版本声明；二是在 `bundled-guava/src/main/java/org/apache/iceberg/GuavaClasses.java` 中增加 `UnsignedBytes` 类的 import 和引用，使其被纳入 shaded guava 的保留类集合。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Guava 版本。

**工作逻辑**：将 `guava = "33.4.8-jre"` 改为 `guava = "33.5.0-jre"`。由于 Iceberg 使用 Gradle 版本目录集中管理依赖，guava 和 guava-testlib 都引用此版本，一处修改即可同步全项目。升级类型为 `version-update:semver-minor`。

### `bundled-guava/src/main/java/org/apache/iceberg/GuavaClasses.java` (+2/-0 lines)

**修改目的**：将 `UnsignedBytes` 类加入 bundled-guava 的保留清单。

**工作逻辑**：`GuavaClasses.java` 是 Iceberg shaded guava 模块的关键文件，通过引用（import 和在静态代码块中 `Class.getName()`）的方式声明哪些 Guava 类需要被保留在打包后的 jar 中（避免被 shaded 时移除）。本提交新增了 `import com.google.common.primitives.UnsignedBytes;`，并在静态代码块中添加 `UnsignedBytes.class.getName();`，确保 `UnsignedBytes` 工具类在重定位后仍可用。这表明 Iceberg 在 Guava 33.5.0 升级后开始使用（或需要确保可用）`UnsignedBytes` 类提供的无符号字节操作能力。

## 总结

这是一次常规的依赖升级提交，将 Guava 从 33.4.8-jre 升级到 33.5.0-jre（minor 版本），同时将 `UnsignedBytes` 类加入 bundled-guava 的保留清单以支持 shaded 打包。改动小且聚焦，体现了 Iceberg 在升级 shaded 依赖时需要同步维护保留类清单的特点。升级有助于获取 Guava 上游的新功能和修复，同时为 Iceberg 使用 `UnsignedBytes` 扫清了障碍。
