# 提交 0869：Build: Bump org.roaringbitmap:RoaringBitmap from 1.0.6 to 1.1.0 (#10552)

## 提交信息

- **序号**：0869 / 4088
- **哈希**：29fd2a0cb940fd16aa5bfb8003ec1354e678c949
- **短哈希**：29fd2a0cb
- **日期**：2024-06-24（Mon Jun 24 10:34:55 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.roaringbitmap:RoaringBitmap from 1.0.6 to 1.1.0 (#10552)
- **PR/Issue**：#10552

## 总体目的

Iceberg 在 core 模块使用 `org.roaringbitmap:RoaringBitmap` 这一高性能压缩位图库，主要服务于 `BitmapDeletionVector`（删除向量的位图实现）等场景，用于高效表示行的删除状态。该库在 1.0.6 之后发布了 1.1.0 minor 版本。本提交由 dependabot 自动生成，目的是把 `gradle/libs.versions.toml` 中 `roaringbitmap` 的版本从 1.0.6 升级到 1.1.0，跟进上游 minor 版本带来的改进与新功能。

RoaringBitmap 1.x → 1.1 是 minor 版本升级，按 semver 应保持向后兼容（API 不破坏），通常带来性能优化、新 API 或 bug 修复。由于 RoaringBitmap 是 Iceberg 删除向量存储格式的底层依赖，版本升级可能影响删除向量在存储层的字节布局（如果有 API 行为变化），需关注。

## 如何达成设计目的

实现方式非常直接：修改 `gradle/libs.versions.toml` 中 `roaringbitmap` 变量的版本钉，从 `1.0.6` 改为 `1.1.0`。该变量被 version catalog 中 RoaringBitmap 工件坐标引用。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 RoaringBitmap 库版本从 1.0.6 升级到 1.1.0。

**工作逻辑**：仅修改一行，diff 如下：

```diff
 orc = "1.9.3"
 parquet = "1.13.1"
 pig = "0.17.0"
-roaringbitmap = "1.0.6"
+roaringbitmap = "1.1.0"
 s3mock-junit5 = "2.11.0"
 scala-collection-compat = "2.12.0"
 slf4j = "1.7.36"
```

RoaringBitmap 在 Iceberg 中由 `core` 模块引用，主要供 `BitmapDeletionVector` 使用，作为行级删除向量的内存与持久化表示。

## 小结

- **成效**：把 RoaringBitmap 库从 1.0.6 升级到 1.1.0，跟进上游 minor 版本改进。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动。运行时影响：使用 `BitmapDeletionVector`（即基于位图的行级删除向量）的表会切换到新版本 RoaringBitmap。
- **回迁到 1.4.x 的注意事项**：本提交是 RoaringBitmap minor 版本升级（1.0 → 1.1），**回迁需谨慎评估**。注意事项：(1) minor 版本通常向后兼容，但 RoaringBitmap 是删除向量持久化格式的底层依赖，如果 1.1.0 在序列化/反序列化层有任何行为差异，可能影响已写入的删除向量文件的读取兼容性，回迁前需运行删除向量相关测试（`TestBitmapDeletionVector`、`TestDeletionVector` 等）；(2) 1.4.x 是否已经支持 `BitmapDeletionVector` 需要确认——如果 1.4.x 还未引入删除向量位图实现，本升级在该分支上可能没有实际作用，可不必回迁；(3) 该升级不依赖其他提交，可独立 cherry-pick；(4) 与 awssdk/nessie 升级（提交 0866/0867/0870）无冲突。
