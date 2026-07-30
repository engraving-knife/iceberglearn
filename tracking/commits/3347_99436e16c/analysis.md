# 提交 3347：Build: Bump lz4-java 1.10.4 (#15518)

## 提交信息

- **序号**：3347 / 4088
- **哈希**：99436e16cbb3d49802ea07a87a68167ffd8f98f6
- **短哈希**：99436e16c
- **日期**：2026-03-06 10:48:06 -0800
- **作者**：Cheng Pan
- **提交说明**：Build: Bump lz4-java 1.10.4 (#15518)
- **PR/Issue**：#15518

## 总体目的

lz4-java 是 Iceberg 在 Parquet / ORC 等列式文件读写时使用的底层压缩解压库（通过 Parquet 的 lz4 压缩编解码器间接依赖），对 IO 性能有直接影响。此前 Iceberg 出于安全原因将 lz4-java 的 Maven 坐标从 `org.lz4:lz4-java` 切换到 `at.yawk.lz4:lz4-java`（yawkat 维护的 fork，修复了已知安全漏洞）。但该 fork 在 1.9+ 系列中引入了原生（JNI）路径上的性能回归，导致使用 lz4 压缩的读写吞吐明显下降。

上游 Celeborn（CELEBORN-2218）与 Spark（SPARK-55803）的基准测试均报告了这一回归。1.10.4 版本正是 yawkat 专门为修复该原生性能回归而发布的版本——按其 release 说明，本次修复"对功能与安全没有影响"，仅恢复性能。本提交把版本从 `1.10.3` 升到 `1.10.4`，目的是在保持安全修复的前提下恢复压缩解压性能。

## 如何达成设计目的

仅升级版本号即可，无需改动调用代码。版本统一定义在 Gradle 的 version catalog `gradle/libs.versions.toml` 中（键 `lz4Java`），各模块通过 catalog 引用，因此一行改动即可全局生效。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：把 lz4-java 版本从 `1.10.3` 升级到 `1.10.4`。

**工作逻辑**：将 `lz4Java = "1.10.3"` 改为 `lz4Java = "1.10.4"`。该 key 在 catalog 中被各 Spark / Flink 等模块通过 `libs.lz4-java` 形式引用，作为 Parquet 等压缩链路的传递依赖。1.10.4 是一次 patch 级（语义版本 semver-patch）升级，仅修复 1.9+ 系列引入的 JNI 原生性能回归，不改变 API 与功能行为，因此无需任何适配改动，也无破坏性风险。

## 总结

本提交把 lz4-java 从 1.10.3 升到 1.10.4，恢复此前因切换到安全 fork 而引入的原生压缩性能回归。这是一次针对性能的 patch 级依赖升级，配合 Celeborn / Spark 上游的基准验证，确保 Iceberg 在使用 lz4 压缩时既享有安全修复又不再付出性能代价。
