# 提交 0478：Build: Bump org.roaringbitmap:RoaringBitmap from 1.0.0 to 1.0.1 (#9317)

## 提交信息

- **序号**：0478
- **哈希**：af46487419d9b5beebc2b8b78642987970379073
- **短哈希**：af4648741
- **日期**：2024-02-06 20:11:50 +0100
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.roaringbitmap:RoaringBitmap from 1.0.0 to 1.0.1
- **PR/Issue**：#9317

## 总体目的

本提交由 Dependabot 自动生成，将 `org.roaringbitmap:RoaringBitmap` 从 `1.0.0` 升级到 `1.0.1`，属于一次语义化版本中的 patch 版本更新（`version-update:semver-patch`）。RoaringBitmap 是一种高性能的压缩位图（compressed bitmap）数据结构库，专为处理大规模整数集合的存储与集合运算（并、交、差、判断成员存在等）而设计，在海量数据场景下相比传统 `HashSet` 能显著降低内存占用并提升运算速度。

在 Iceberg 中，RoaringBitmap 是核心模块（`:iceberg-core`）的正式生产依赖（`build.gradle` 第 351 行以 `implementation libs.roaringbitmap` 引入），被用于行级删除（position delete）的追踪机制。具体而言，`BitmapPositionDeleteIndex`（`core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java`）使用 RoaringBitmap 来记录被删除的数据行位置（position），而 `SortingPositionOnlyDeleteWriter`（`core/src/main/java/org/apache/iceberg/deletes/SortingPositionOnlyDeleteWriter.java`）在写入删除文件时也涉及位图操作。当读取数据文件时，读取器会根据这些位图索引快速跳过已删除的行，从而实现 MVCC 式的行级删除语义。此外，在 Spark 集成模块（`:iceberg-spark`）中，RoaringBitmap 被显式 exclude（第 595 行），以避免与 Spark 自身引入的版本产生冲突，确保 core 模块使用的 RoaringBitmap 版本不被传递依赖覆盖。

将版本从 1.0.0 提升到 1.0.1，目的在于应用 RoaringBitmap 1.0.x 维护分支上的缺陷修复。1.0.0 是 RoaringBitmap 进入 1.0 稳定线后的首个正式版本，1.0.1 作为 patch 版本修复了序列化、位图运算或内存管理方面的若干问题。由于 RoaringBitmap 直接参与 Iceberg 删除索引的构建与读取，其正确性直接影响查询结果（即已删除的行是否被正确过滤），因此及时跟进 patch 修复对保证数据正确性有实际意义。

## 如何达成设计目的

Dependabot 扫描 `gradle/libs.versions.toml` 中的版本声明，识别出 `roaringbitmap = "1.0.0"` 这一可升级项，并自动生成单行版本号替换，将引用值改为 `1.0.1`。该版本号通过 `version.ref` 机制集中声明，所有引用 `libs.roaringbitmap` 的模块配置（core 模块的 `implementation`）会自动解析到新版本，无需逐模块改动。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 RoaringBitmap 版本引用从 `1.0.0` 提升到 `1.0.1`。

**工作逻辑**：

文件第 74 行附近（在 `parquet`、`pig`、`s3mock-junit5` 等版本声明相邻处）将：

```
roaringbitmap = "1.0.0"
```

改为：

```
roaringbitmap = "1.0.1"
```

该声明对应的库坐标定义在 `libs.versions.toml` 第 146 行 `roaringbitmap = { module = "org.roaringbitmap:RoaringBitmap", version.ref = "roaringbitmap" }`，`version.ref` 指向被修改的常量。`build.gradle` 第 351 行通过 `implementation libs.roaringbitmap` 将其引入 core 模块的生产 classpath，直接影响 `BitmapPositionDeleteIndex` 与 `SortingPositionOnlyDeleteWriter` 等删除追踪组件的运行时行为。同时，第 595 行的 `exclude group: 'org.roaringbitmap'` 配置确保 Spark 集成模块不会因传递依赖引入不同版本的 RoaringBitmap 而造成类加载冲突，core 模块的 1.0.1 版本将作为权威版本被使用。

## 小结

本提交是一次由 Dependabot 驱动的核心生产依赖 patch 升级，仅修改 `gradle/libs.versions.toml` 中 `roaringbitmap` 的版本号（1.0.0 → 1.0.1），文件改动量为 1 行增、1 行删。RoaringBitmap 在 Iceberg 中作为行级删除索引（position delete）的底层数据结构，直接参与查询时已删除行的过滤逻辑。本次 patch 升级旨在应用上游 1.0.x 维护分支的缺陷修复，保障删除索引的正确性与内存效率。由于版本号采用集中式 `version.ref` 声明，单点修改即可让 core 模块生效，同时 Spark 模块的 exclude 配置保证了版本一致性。
