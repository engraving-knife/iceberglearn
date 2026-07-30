# 提交 1723：Core: Adjust Jackson settings to handle large metadata json (#12224)

## 提交信息

- **序号**：1723 / 4088
- **哈希**：80a009a45dca9932c1e2d50616e4f20f32139fc2
- **短哈希**：80a009a45
- **日期**：2025-02-13 13:33:15 +0100
- **作者**：Bryan Keller
- **提交说明**：Core: Adjust Jackson settings to handle large metadata json (#12224)
- **PR/Issue**：#12224

## 总体目的

调整 Jackson JSON 库的配置，使其能够正确处理大型元数据 JSON 文件。当 Iceberg 表的元数据 JSON 文件非常大时（例如包含大量快照、清单文件或分区数据的表），Jackson 默认的 `JsonFactory` 配置可能导致性能问题或内存溢出错误。

具体来说，Jackson 默认启用了两个特性需要禁用：
1. `INTERN_FIELD_NAMES`（默认 true）：将 JSON 字段名内部化（intern）到 JVM 的字符串池中。对于包含大量不同字段名的超大 JSON 文件，这会导致 JVM 字符串池表溢出，产生性能问题。
2. `FAIL_ON_SYMBOL_HASH_OVERFLOW`（默认 true）：当 Jackson 内部的符号哈希表溢出时直接失败报错。对于超大 JSON 文件，符号表可能溢出导致解析失败。禁用此特性后，Jackson 会在符号表溢出时回退到更慢但不溢出的方式继续解析。

此外，mr 模块的构建配置也需要调整，排除 Calcite Avatica 依赖以避免与 Jackson 相关的依赖冲突。

## 如何达成设计目的

通过以下三个层面的修改来达成目标：

1. **REST Object Mapper 配置**：修改 `RESTObjectMapper.java` 中的 `JsonFactory` 创建方式，使用 `JsonFactoryBuilder` 禁用两个有问题的特性。
2. **通用 JSON 工具配置**：修改 `JsonUtil.java` 中的 `JsonFactory` 创建方式，应用相同的配置。
3. **MR 模块构建配置**：修改 `mr/build.gradle`，排除 Calcite 的 Avatica 依赖，解决测试中的依赖冲突。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTObjectMapper.java`（修改, +6/-1 lines）

**修改目的**：调整 REST API 使用的 JSON 映射器配置，支持大型 JSON 处理。

**工作逻辑**：将 `JsonFactory` 的创建从 `new JsonFactory()` 改为使用 `JsonFactoryBuilder` 链式配置：
```java
new JsonFactoryBuilder()
    .configure(JsonFactory.Feature.INTERN_FIELD_NAMES, false)
    .configure(JsonFactory.Feature.FAIL_ON_SYMBOL_HASH_OVERFLOW, false)
    .build();
```
禁用字段名内部化（`INTERN_FIELD_NAMES`）避免 JVM 字符串池溢出，禁用符号哈希表溢出失败（`FAIL_ON_SYMBOL_HASH_OVERFLOW`）允许 Jackson 在表溢出时回退而非报错。

### `core/src/main/java/org/apache/iceberg/util/JsonUtil.java`（修改, +6/-1 lines）

**修改目的**：调整通用 JSON 工具的配置，与 REST Object Mapper 保持一致。

**工作逻辑**：与 `RESTObjectMapper.java` 相同的修改，将 `JsonFactory` 创建方式改为 `JsonFactoryBuilder` 并禁用相同的两个特性。`JsonUtil` 是 Core 模块中广泛使用的 JSON 工具类，用于解析表元数据 JSON 文件等场景。

### `mr/build.gradle`（修改, +7/-3 lines）

**修改目的**：排除 Calcite Avatica 依赖，解决测试中的依赖冲突。

**工作逻辑**：
1. 将 `testImplementation libs.calcite.core` 和 `testImplementation libs.calcite.druid` 改为带排除配置的形式，排除 `org.apache.calcite.avatica:avatica` 模块。Avatica 是 Calcite 的 JDBC 驱动模块，引入了不兼容的 Jackson 依赖版本。
2. 删除了重复的 `testImplementation libs.calcite.core` 声明（原来在第 60 行附近有一行重复声明）。

## 小结

- **成效**：Iceberg 能够正确处理大型元数据 JSON 文件，避免了 Jackson 符号表溢出导致的解析失败和性能问题。同时解决了 MR 模块测试中 Calcite Avatica 依赖冲突。
- **影响范围**：影响 Core 模块的 JSON 处理（RESTObjectMapper 和 JsonUtil），以及 MR 模块的测试依赖配置。所有通过 Jackson 解析 JSON 的场景都受到影响。
- **回迁到 1.4.x 的注意事项**：建议回迁。大型元数据 JSON 的处理问题可能在 1.4.x 分支中也存在，此修复对处理大表的元数据文件非常重要。回迁时需确认 1.4.x 分支中 `RESTObjectMapper.java` 和 `JsonUtil.java` 的代码结构与 main 分支一致，以及 `JsonFactoryBuilder` 在 1.4.x 使用的 Jackson 版本中是否可用（Jackson 2.x 从 2.10 开始支持 `JsonFactoryBuilder`）。mr/build.gradle 的修改需确认 1.4.x 的 Calcite 依赖配置。
