# 提交 0036：Build: Bump org.roaringbitmap:RoaringBitmap from 0.9.47 to 1.0.0 (#8792)

## 提交信息

- **序号**：0036 / 4088
- **哈希**：9cb22b93ada3bb89040f6efc59b4f7b714035a3b
- **短哈希**：9cb22b93a
- **日期**：2023-10-11 09:34:06 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.roaringbitmap:RoaringBitmap from 0.9.47 to 1.0.0 (#8792)
- **PR/Issue**：#8792

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 依赖的 [RoaringBitmap](https://github.com/RoaringBitmap/RoaringBitmap) 库从 `0.9.47` 升级到 `1.0.0`，属于 `version-update:semver-major`（主版本号升级），是本批依赖升级中风险等级最高的一笔。

RoaringBitmap 是一种高性能的压缩位图（compressed bitmap）库，专门为存储和快速查询大规模整数集合而设计，在稀疏与密集分布下都能保持较低的内存占用。在 Iceberg 中它被用作**位置删除索引（Position Delete Index）**的底层实现：当通过 `DELETE` 或行级更新产生删除向量时，Iceberg 用 `Roaring64Bitmap` 存储被删除的数据行号（64 位长整型），读取侧据此跳过已删除的行。核心实现位于 [`core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java`](../../../../core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java)，由 `Deletes#toPositionIndex` 构建。

本次升级跨越了 RoaringBitmap 的 1.0 里程碑——0.x 到 1.0.0 通常意味着库作者认为 API 已趋于稳定。1.0 版本通常带来 API 清理、潜在的破坏性变更（如方法签名调整、包结构调整）、性能优化与 bug 修复。由于 RoaringBitmap 直接参与 Iceberg 的删除向量读写路径，任何序列化格式或 API 行为的不兼容都可能影响已写入的删除文件兼容性，因此这类 major 升级需要维护者仔细审阅 [release notes](https://github.com/RoaringBitmap/RoaringBitmap/releases) 并通过测试验证。

## 如何达成设计目的

改动极简：仅在 Gradle 版本目录 [`gradle/libs.versions.toml`](../../../../gradle/libs.versions.toml) 中把 `roaringbitmap = "0.9.47"` 改为 `roaringbitmap = "1.0.0"`。所有引用 `libs.roaringbitmap` 的模块会自动解析到新版本，无需逐个修改各模块的 `build.gradle`。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `org.roaringbitmap:RoaringBitmap` 的版本从 0.9.47 升级到 1.0.0。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区（约第 57 行），原行 `roaringbitmap = "0.9.47"` 被改为 `roaringbitmap = "1.0.0"`。该版本常量被以下模块引用：

- `iceberg-core`（[`build.gradle:351`](../../../../build.gradle) `implementation libs.roaringbitmap`）：核心删除索引实现 `BitmapPositionDeleteIndex` 直接使用 `Roaring64Bitmap`。
- `iceberg-spark` v3.3 / v3.4 / v3.5 的 spark-extensions 模块（如 [`spark/v3.3/build.gradle:138`](../../../../spark/v3.3/build.gradle) `implementation libs.roaringbitmap`）：用于 Spark 引擎侧的删除向量处理，并在运行时 shadow jar 中通过 `relocate 'org.roaringbitmap', 'org.apache.iceberg.shaded.org.roaringbitmap'`（[`spark/v3.3/build.gradle:285`](../../../../spark/v3.3/build.gradle)）重命名包以避免与 Spark 自带的 RoaringBitmap 冲突。

由于是 major 版本跨越（0.x → 1.0.0），潜在风险包括：API 签名变更、`Roaring64Bitmap` 的序列化格式调整、依赖的 JDK 基线提升等。Iceberg 在构建时排除了 Spark 自带的 `org.roaringbitmap`（见 spark 模块 `compileOnly` 依赖中的 `exclude group: 'org.roaringbitmap'`），并通过 relocate 机制隔离，这降低了与 Spark 内置版本的冲突风险。

## 小结

该提交由 Dependabot 将 RoaringBitmap 从 0.9.47 major 升级到 1.0.0 里程碑版本，使 Iceberg 的位置删除索引底层位图库跟进上游稳定版，获取 API 稳定性承诺与潜在的性能/正确性改进，但因属 major 升级需维护者重点验证删除向量读写兼容性。
