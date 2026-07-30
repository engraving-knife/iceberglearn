# 提交 3531：API, Core: Move stats classes to core as package-private (#15971)

## 提交信息

- **序号**：3531 / 4088
- **哈希**：ffb095db0c5f0bf9962a89a13f0a1953c740d90a
- **短哈希**：ffb095db0
- **日期**：2026-04-14 13:05:11 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：API, Core: Move stats classes to core as package-private (#15971)
- **PR/Issue**：#15971

## 总体目的

Iceberg 正在开发 v4 规范相关的字段级统计（stats）特性，包括 `ContentStats`、`FieldStats`、`FieldStatistic`、`StatsUtil`、`BaseContentStats`、`BaseFieldStats` 等类。此前这些类分布在两个模块：
- `api` 模块（`org.apache.iceberg.stats` 包）：`ContentStats`、`FieldStatistic`、`FieldStats`、`StatsUtil` 及其测试
- `core` 模块（`org.apache.iceberg.stats` 包）：`BaseContentStats`、`BaseFieldStats` 及其测试

由于 v4 spec 尚未定稿，这些类的 API 还在演进。如果它们留在 `api` 模块且为 `public`，就意味着对外暴露了未稳定的 API，一旦后续改动就会破坏二进制兼容性。为避免「在 spec 定稿前过早固化公共 API」，本提交将所有 stats 相关类统一移到 `iceberg-core` 模块的 `org.apache.iceberg` 包下，并将访问修饰符从 `public` 降级为 package-private（`class`/`interface`/`enum` 无修饰符，方法去掉 `public`）。

同时把包从 `org.apache.iceberg.stats` 改为 `org.apache.iceberg`，目的是让这些类与 core 模块中其他 v4 相关类处于同一包，便于互相访问（package-private 可见性需要同包）。

## 如何达成设计目的

通过 git rename 移动文件，并做以下统一处理：
1. **包声明**：所有类从 `package org.apache.iceberg.stats` 改为 `package org.apache.iceberg`
2. **访问修饰符降级**：`public class` → `class`，`public interface` → `interface`，`public enum` → `enum`，构造函数和方法去掉 `public`
3. **清理冗余 import**：由于类现在与 `Schema`、`StructLike`、`TestHelpers` 同在 `org.apache.iceberg` 包，不再需要显式 import 这些同包类
4. **更新引用方**：`MetricsUtil`、`TrackedFile`、`TestMetrics`、`TestTrackedFile` 等引用 stats 类的地方更新 import 或去掉 import
5. **测试文件同步移动**：`TestContentStats`、`TestFieldStats`、`TestStatsUtil` 从 `api`/`core` 的 `stats` 包移到 `core` 的 `org.apache.iceberg` 包，并更新 import 和断言消息中的类全限定名

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseContentStats.java` (rename from `core/.../stats/BaseContentStats.java`) (+3/-4 lines)

**修改目的**：迁移包并降级为 package-private。

**工作逻辑**：
- `package org.apache.iceberg.stats` → `package org.apache.iceberg`
- `public class BaseContentStats` → `class BaseContentStats`
- `public BaseContentStats(Types.StructType projection)` → `BaseContentStats(Types.StructType projection)`
- 去掉 `import org.apache.iceberg.Schema`（同包无需 import）

### `core/src/main/java/org/apache/iceberg/BaseFieldStats.java` (rename) (+2/-2 lines)

**修改目的**：迁移包并降级。

**工作逻辑**：包声明改为 `org.apache.iceberg`，`public class BaseFieldStats<T>` → `class BaseFieldStats<T>`。

### `core/src/main/java/org/apache/iceberg/ContentStats.java` (rename from `api/.../stats/ContentStats.java`) (+2/-3 lines)

**修改目的**：从 api 模块迁移到 core，并降级为 package-private。

**工作逻辑**：
- `public interface ContentStats extends StructLike` → `interface ContentStats extends StructLike`
- 去掉 `import org.apache.iceberg.StructLike`

### `core/src/main/java/org/apache/iceberg/FieldStatistic.java` (rename from `api/.../stats/FieldStatistic.java`) (+2/-2 lines)

**修改目的**：迁移到 core 并降级。

**工作逻辑**：`public enum FieldStatistic` → `enum FieldStatistic`，包声明改为 `org.apache.iceberg`。注意此前的 #15939（提交 3519）修改的 `avg_value_size_in_bytes` 等字段名随之带入新位置。

### `core/src/main/java/org/apache/iceberg/FieldStats.java` (rename from `api/.../stats/FieldStats.java`) (+2/-3 lines)

**修改目的**：迁移到 core 并降级。

**工作逻辑**：`public interface FieldStats<T> extends StructLike` → `interface FieldStats<T> extends StructLike`，去掉 `import org.apache.iceberg.StructLike`。

### `core/src/main/java/org/apache/iceberg/StatsUtil.java` (rename from `api/.../stats/StatsUtil.java`) (+2/-3 lines)

**修改目的**：迁移到 core 并降级。

**工作逻辑**：`public class StatsUtil` → `class StatsUtil`，包改为 `org.apache.iceberg`，去掉 `import org.apache.iceberg.Schema`。

### `core/src/main/java/org/apache/iceberg/MetricsUtil.java` (+1/-4 lines)

**修改目的**：更新对 stats 类的引用。

**工作逻辑**：
- 去掉三个 import：`BaseContentStats`、`BaseFieldStats`、`ContentStats`（现在同包无需 import）
- `public static ContentStats fromMetrics(...)` → `static ContentStats fromMetrics(...)`（降级为 package-private，因为 `ContentStats` 已是 package-private，public 方法返回 package-private 类型不合理）

### `core/src/main/java/org/apache/iceberg/TrackedFile.java` (+0/-1 lines)

**修改目的**：去掉 `import org.apache.iceberg.stats.ContentStats`（同包）。

### `core/src/test/java/org/apache/iceberg/TestContentStats.java` (rename) (+12/-11 lines)

**修改目的**：测试迁移包并更新 import。

**工作逻辑**：包改为 `org.apache.iceberg`，8 个 `FieldStatistic` 常量的 static import 从 `org.apache.iceberg.stats.FieldStatistic.*` 改为 `org.apache.iceberg.FieldStatistic.*`，去掉 `import org.apache.iceberg.Schema`。同时更新一处断言消息中的类全限定名：
```java
-            "Wrong class, expected java.lang.Long but was org.apache.iceberg.stats.BaseFieldStats for object:");
+            "Wrong class, expected java.lang.Long but was org.apache.iceberg.BaseFieldStats for object:");
```

### `core/src/test/java/org/apache/iceberg/TestFieldStats.java` (rename) (+10/-11 lines)

**修改目的**：测试迁移包并更新 import。

**工作逻辑**：包改为 `org.apache.iceberg`，8 个 `FieldStatistic` 常量 static import 路径更新，去掉 `import org.apache.iceberg.TestHelpers`。

### `core/src/test/java/org/apache/iceberg/TestMetrics.java` (+0/-2 lines)

**修改目的**：去掉 `ContentStats` 和 `FieldStats` 的 import（同包）。

### `core/src/test/java/org/apache/iceberg/TestStatsUtil.java` (rename from `api/.../stats/TestStatsUtil.java`) (+10/-9 lines)

**修改目的**：测试从 api 模块迁移到 core，更新包和 import。

**工作逻辑**：包改为 `org.apache.iceberg`，8 个 `FieldStatistic` 常量 static import 路径更新，去掉 `import org.apache.iceberg.Schema`。

### `core/src/test/java/org/apache/iceberg/TestTrackedFile.java` (+0/-1 lines)

**修改目的**：去掉 `import org.apache.iceberg.stats.StatsUtil`（同包）。

## 总结

本提交将 v4 规范相关的字段级统计（stats）类从 `api` 模块集中迁移到 `iceberg-core` 模块的 `org.apache.iceberg` 包下，并将访问修饰符从 `public` 统一降级为 package-private。这是在 spec 未定稿前避免过早固化公共 API 的预防性重构，保证后续 stats API 的演进不会破坏二进制兼容性。同时统一包名便于 v4 相关类的互相访问。改动涉及 6 个主代码文件迁移、3 个测试文件迁移、4 个引用方更新 import，是一次较为系统的内部 API 收敛。
