# 提交 0576：Build: Bump org.roaringbitmap:RoaringBitmap from 1.0.1 to 1.0.5

## 提交信息

- **序号**：0576 / 4088
- **哈希**：a9d6310b8840e78bf3a4d95ca5c91e548b836c41
- **短哈希**：a9d6310b8
- **日期**：2024-03-11 11:55:13 +0100
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.roaringbitmap:RoaringBitmap from 1.0.1 to 1.0.5 (#9911)
- **PR/Issue**：#9911

## 总体目的

本提交由 Dependabot 自动生成，把 Iceberg 依赖的 `org.roaringbitmap:RoaringBitmap` 从 `1.0.1` 升级到 `1.0.5`，属于语义化版本中的 patch 更新（`version-update:semver-patch`），依赖类型标记为 `direct:production`（直接生产依赖）。

RoaringBitmap 是一种高性能的压缩位图（compressed bitmap）库，专门为存储和快速查询大规模整数集合而设计，在稀疏与密集分布下都能保持较低的内存占用与快速的查找/集合运算性能。在 Iceberg 中它扮演**位置删除索引（Position Delete Index）**的底层实现角色：

- `core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java` 内部持有 `Roaring64Bitmap`，通过 `add(position)` 标记被删除的数据行号（64 位 long），通过 `contains(position)` 实现 `isDeleted(long)` 判定，通过 `add(posStart, posEnd)` 支持区间删除；
- 该索引由 `Deletes.toPositionIndex(...)` 构建，被读取侧用于跳过已删除的行，是 Iceberg 行级删除（DELETE / 行级 UPDATE 产生的 position delete）语义正确性的关键。

本次 1.0.1 → 1.0.5 一次性跨越了 1.0.x 维护线上的多个 patch 版本（1.0.1 → 1.0.2 → 1.0.3 → 1.0.4 → 1.0.5），目的是一次性累积应用 1.0.x 维护分支上已有的缺陷修复与微优化。由于 RoaringBitmap 直接参与删除向量的构建与读取，其正确性直接影响查询结果（即已删除的行是否被正确过滤），因此及时跟进 patch 修复对保证数据正确性与运行时稳定性有实际意义。

## 如何达成设计目的

改动极简：仅在 Gradle 版本目录 `gradle/libs.versions.toml` 中把 `roaringbitmap = "1.0.1"` 改为 `roaringbitmap = "1.0.5"`。所有通过 `libs.roaringbitmap`（即 `org.roaringbitmap:RoaringBitmap`，version.ref = roaringbitmap）引用该依赖的模块会自动解析到新版本，无需逐个修改各模块的 `build.gradle`。Dependabot 仅做版本号这一行的替换，不触碰任何代码。

由于是 patch 版本升级，按语义化版本约定 API 保持兼容，理论上不涉及破坏性变更；但仍需维护者审阅上游 [release notes](https://github.com/RoaringBitmap/RoaringBitmap/releases) 并通过 CI 验证，以防 patch 中夹带的序列化或行为微调影响已写入的删除文件兼容性。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `org.roaringbitmap:RoaringBitmap` 的版本从 `1.0.1` 升级到 `1.0.5`。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖的版本常量与坐标。改动位于版本声明区第 77 行附近：

```
-roaringbitmap = "1.0.1"
+roaringbitmap = "1.0.5"
```

该版本常量在文件第 146 行附近以 `roaringbitmap = { module = "org.roaringbitmap:RoaringBitmap", version.ref = "roaringbitmap" }` 绑定为依赖坐标，并被以下模块引用：

- `iceberg-core`（核心模块，`implementation libs.roaringbitmap`）：`BitmapPositionDeleteIndex` 直接使用 `Roaring64Bitmap` 存储与查询被删除的行位置，这是该依赖在生产代码中的核心消费点。
- Spark 集成模块（v3.3 / v3.4 / v3.5 的 spark-extensions）：用于引擎侧删除向量处理，并在运行时 shadow jar 中通过 `relocate 'org.roaringbitmap', 'org.apache.iceberg.shaded.org.roaringbitmap'` 重命名包以避免与 Spark 自带的 RoaringBitmap 版本冲突。

升级后，构建时 core 与 spark 扩展模块解析到的 RoaringBitmap 版本即从 1.0.1 提升到 1.0.5。

## 小结

该提交由 Dependabot 将 RoaringBitmap 从 1.0.1 一次性 patch 升级到 1.0.5，累积应用 1.0.x 维护线上的多处修复，使 Iceberg 位置删除索引的底层位图库跟进上游最新 patch 版本，获取正确性与稳定性改进。由于属 patch 升级、API 兼容，风险较低，但 RoaringBitmap 直接参与删除向量读写路径，回迁到 1.4.x 时仍建议运行删除相关的测试套件（core 与 spark 模块的 deletes/delete-index 测试）确认无回归。
