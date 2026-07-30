# 提交 1549 dbfefb073 分析

## 提交信息
- 哈希：dbfefb07312be8554438c1f16f1037ab22bf153b
- 日期：2025-01-04（Sat Jan 4 04:00:33 2025 +0800）
- 作者：Cheng Pan <pan3793@gmail.com>
- 消息：Bump Apache Spark to 3.5.4 (#11731)

## 总体目的

将 Iceberg 的 Spark 3.5 模块所依赖的 Apache Spark 版本从 3.5.2 升级到 3.5.4，并同步适配 Spark 3.5.4 中引入的 `ColumnVector` API 变更。

Spark 3.5.4 是 Spark 3.5 系列的维护版本，包含多项 bug 修复和改进。其中 SPARK-50235 和 SPARK-50463 对 `ColumnVector` 的资源管理进行了调整，新增了 `closeIfFreeable()` 方法。该方法用于在向量不再需要时尝试释放资源，但对于 writable 或 constant 类型的向量，应当覆写该方法并什么都不做（因为这些向量的生命周期不由读取器管理）。

Iceberg 的 `IcebergArrowColumnVector` 继承自 Spark 的 `ColumnVector`，用于在 Spark 向量化读取 Iceberg 表时包装 Arrow 列向量。升级到 Spark 3.5.4 后，需要提供 `closeIfFreeable()` 的实现以确保 API 兼容。根据 Iceberg 向量的使用方式，该实现为空操作（no-op），因为 Iceberg 的向量资源清理已由现有的 `close()` 方法（调用 `accessor.close()`）管理。

## 如何达成设计目的

分两步完成：首先在 `gradle/libs.versions.toml` 中将 `spark-hive35` 版本号从 `3.5.2` 改为 `3.5.4`；然后在 `IcebergArrowColumnVector` 中新增 `closeIfFreeable()` 空方法实现以适配 Spark 3.5.4 的新 API。

### 修改详情

#### `gradle/libs.versions.toml`

**修改目的**：升级 Spark 3.5 依赖版本。

**工作逻辑**：将
```toml
spark-hive35 = "3.5.2"
```
改为
```toml
spark-hive35 = "3.5.4"
```
该版本号被 Spark 3.5 模块的构建配置引用，升级后 Gradle 会解析并拉取 Spark 3.5.4 的全部依赖。

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/IcebergArrowColumnVector.java`

**修改目的**：适配 Spark 3.5.4 中 `ColumnVector` 新增的 `closeIfFreeable()` 方法。

**工作逻辑**：在 `close()` 方法之后新增：
```java
public void closeIfFreeable() {
    // If a column vector is writable or constant, it should override this method and do nothing.
    // See more details at SPARK-50235, SPARK-50463 (Fixed in Spark 3.5.4)
}
```

该方法为空实现（no-op）。注释说明了设计原因：如果列向量是 writable 或 constant 的，应当覆写此方法并什么都不做。Iceberg 的 `IcebergArrowColumnVector` 属于这类情况——其底层 Arrow 向量的生命周期由 `close()` 方法中的 `accessor.close()` 管理，`closeIfFreeable()` 不需要额外操作。注释中引用的 SPARK-50235 和 SPARK-50463 是 Spark 3.5.4 中引入此 API 变更的两个 JIRA issue。

## 小结

- **成效**：将 Spark 3.5 模块的依赖升级到 3.5.4，获得上游 bug 修复；并通过新增 `closeIfFreeable()` 空方法确保与 Spark 3.5.4 的 `ColumnVector` API 兼容。
- **影响范围**：涉及 2 个文件——`gradle/libs.versions.toml`（版本号变更）和 `IcebergArrowColumnVector.java`（新增 5 行方法）。改动范围小且明确，但依赖版本升级可能间接影响所有 Spark 3.5 集成测试的行为。
- **回迁到 1.4.x 的注意事项**：依赖版本升级和 API 适配。如果 1.4.x 的 Spark 3.5 模块仍使用 Spark 3.5.2，**可选回迁**——回迁可获得 3.5.4 的 bug 修复，但需确保 1.4.x 的构建和测试在 3.5.4 下通过。若 1.4.x 已锁定 Spark 3.5.2 且无升级需求，可不回迁，但需注意 3.5.2 可能存在 3.5.4 已修复的问题。
