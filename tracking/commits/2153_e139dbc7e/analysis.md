# 提交 2153：Build: increase gradle jvm heap size from 1 GB to 1.5 GB.

## 提交信息

- **序号**：2153 / 4088
- **哈希**：e139dbc7eea9ff7248ba0d504d498cf205ff3a35
- **短哈希**：e139dbc7e
- **日期**：2025-05-20 16:34:45 -0700
- **作者**：Steven Wu
- **提交说明**：Build: increase gradle jvm heap size from 1 GB to 1.5 GB.
- **PR/Issue**：无（无 PR 号，直接提交）

## 总体目的

该提交将 Gradle 构建守护进程（daemon）的最大 JVM 堆内存从 1 GB（1024m）增加到 1.5 GB（1536m）。这是一个构建基础设施层面的调整。随着 Iceberg 项目规模的增长、模块数量增加以及构建任务的复杂化，1 GB 的堆内存可能不足以支撑完整的构建过程，容易出现 OutOfMemoryError 或频繁的 GC 导致构建变慢。将堆内存提升到 1.5 GB 可以为构建进程提供更充裕的内存空间，避免因内存不足导致的构建失败，并提升构建的稳定性。

## 如何达成设计目的

- 修改 `gradle.properties` 文件中的 `org.gradle.jvmargs` 参数，将 `-Xmx1024m` 改为 `-Xmx1536m`，直接调高 Gradle 守护进程的最大堆内存上限。

## 修改详情

### `gradle.properties` (修改, +1/-1 lines)

**修改目的**：调整 Gradle 守护进程的 JVM 堆内存配置，从 1 GB 提升到 1.5 GB。

**工作逻辑**：`org.gradle.jvmargs` 参数控制 Gradle 构建时 JVM 的启动参数。将 `-Xmx1024m` 修改为 `-Xmx1536m`，使构建守护进程拥有更大的可用堆内存，减少因内存压力导致的构建中断风险。

## 总结

这是一个简单的构建配置调整，通过增加 Gradle 守护进程的堆内存上限来提升构建稳定性和可靠性，适应项目日益增长的构建需求。
