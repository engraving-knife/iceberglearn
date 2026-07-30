# 提交 0704：升级 RoaringBitmap 至 1.0.6

## 提交信息
- **序号**：0704 / 4088
- **哈希**：9664940ae4f241a70bd4759fbe1a9564f7400f52
- **短哈希**：9664940ae
- **日期**：2024-04-21
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.roaringbitmap:RoaringBitmap from 1.0.5 to 1.0.6 (#10190)
- **PR/Issue**：#10190

## 总体目的

本提交由 Dependabot 自动生成，将 RoaringBitmap 库从 `1.0.5` 升级到 `1.0.6`，属于 patch 版本级别的依赖升级。

RoaringBitmap 是一种高性能的压缩位图数据结构库，在 Iceberg 中被用于高效的整数集合表示和集合运算，主要场景包括：删除文件（delete files）的位图索引、扫描任务规划时文件集合的快速操作、以及字段 ID 与文件位置的映射等。位图操作在 Iceberg 的元数据管理和扫描规划中是热点路径，对性能敏感。

Dependabot 例行升级目的在于获取上游 bug 修复和性能优化。从 `1.0.5` 到 `1.0.6` 是单个 patch 版本升级，API 兼容，风险低。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `roaringbitmap` 版本属性即可。Gradle 版本目录机制会自动将新版本应用到所有引用该属性的依赖声明中。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：将 `roaringbitmap` 版本从 `1.0.5` 升级到 `1.0.6`。
**工作逻辑**：`roaringbitmap = "1.0.5"` → `roaringbitmap = "1.0.6"`。该属性在版本目录中被 `org.roaringbitmap:RoaringBitmap:${roaringbitmap}` 引用，被 `core` 等模块依赖。

## 小结
- **成效**：成功达成目的，完成 RoaringBitmap 的 patch 版本升级。
- **影响范围**：影响 `core` 模块及所有依赖 core 的下游模块中涉及位图操作的功能（如删除文件索引、扫描规划等）。patch 升级 API 兼容，运行时行为变化极小，可能获得小幅性能改进。
- **回迁到 1.4.x 的注意事项**：可直接回迁。RoaringBitmap 1.0.x 系列 API 稳定，无兼容性问题。若 1.4.x 分支对位图序列化格式有跨版本持久化要求（如写入磁盘的位图格式），需确认 1.0.6 未改变序列化格式（一般 patch 版本不会改变）。
