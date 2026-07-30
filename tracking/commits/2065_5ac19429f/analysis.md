# 提交 2065：Flink: change Preconditions import from flink util to guava (#12939)

## 提交信息

- **序号**：2065 / 4088
- **哈希**：5ac19429fa3f63b35a455b695f3c066371afe525
- **短哈希**：5ac19429f
- **日期**：2025-04-30 17:12:54 +0200
- **作者**：GuoYu
- **提交说明**：Flink: change Preconditions import from flink util to guava (#12939)
- **PR/Issue**：#12939

## 总体目的

Iceberg Flink 集成模块中大量代码使用了 `org.apache.flink.util.Preconditions` 来做参数校验。这与 Iceberg 的依赖最小化原则不符——Iceberg 各模块统一使用自身 relocated guava 的 `org.apache.iceberg.relocated.com.google.common.base.Preconditions`，避免对宿主引擎（Flink/Spark）的工具类产生直接依赖，便于在其他环境（如独立服务、测试）中复用，也避免 Flink 版本升级时 `Preconditions` API 变化带来的兼容性问题。

本提交把 Flink 1.19、1.20、2.0 三个模块下共 33 个源文件中 `org.apache.flink.util.Preconditions` 的 import 替换为 `org.apache.iceberg.relocated.com.google.common.base.Preconditions`，并在 checkstyle 配置中加入 `BanFlinkPreconditions` 规则，禁止后续代码再次引入 Flink 的 Preconditions，从而守护这一约定。

## 如何达成设计目的

1. **批量替换 import**：对 3 个 Flink 版本模块下 11 个文件（每个版本同样的 11 个文件，共 33 个）做相同的 import 替换：移除 `import org.apache.flink.util.Preconditions;`，新增 `import org.apache.iceberg.relocated.com.google.common.base.Preconditions;`。`Preconditions.checkArgument/checkNotNull/checkState` 等方法签名在两者间兼容，因此调用处无需改动。
2. **checkstyle 守护**：在 `.baseline/checkstyle/checkstyle.xml` 中新增 `IllegalImport` 模块，id 为 `BanFlinkPreconditions`，禁止 `org.apache.flink.util.Preconditions`，违反时提示"Use org.apache.iceberg.relocated.com.google.common.base.Preconditions instead."

## 修改详情

### `.baseline/checkstyle/checkstyle.xml` (修改, +5/-0 lines)

**修改目的**：新增禁止 Flink Preconditions 的 checkstyle 规则。

**工作逻辑**：
新增一个 `IllegalImport` 模块，`id="BanFlinkPreconditions"`，`illegalClasses="org.apache.flink.util.Preconditions"`，违规消息提示使用 relocated guava 的 Preconditions。该规则会随 checkstyle 检查在所有模块生效，防止后续代码重新引入 Flink Preconditions。

### 33 个 Flink 源文件 (修改, 每文件 +1/-1 lines)

**修改目的**：把 Flink Preconditions 替换为 relocated guava Preconditions。

**工作逻辑**：
每个文件做相同的两行 import 变更：删除 `import org.apache.flink.util.Preconditions;`，添加 `import org.apache.iceberg.relocated.com.google.common.base.Preconditions;`。涉及的 11 个文件（每个 Flink 版本一份）：
- `FlinkDynamicTableFactory.java`
- `IcebergTableSink.java`
- `sink/CachingTableSupplier.java`
- `sink/IcebergSink.java`
- `sink/SinkUtil.java`
- `sink/shuffle/DataStatisticsCoordinator.java`
- `sink/shuffle/SortKeySerializer.java`
- `source/IcebergSource.java`
- `source/ScanContext.java`
- `source/assigner/GetSplitResult.java`
- `source/enumerator/ContinuousSplitPlannerImpl.java`

覆盖 Flink 1.19、1.20、2.0 三个版本目录。

## 总结

本提交把 Iceberg Flink 集成中 3 个版本模块共 33 个源文件的 `Preconditions` import 从 Flink 的 `org.apache.flink.util.Preconditions` 切换到 Iceberg relocated guava 的 `org.apache.iceberg.relocated.com.google.common.base.Preconditions`，消除对 Flink 工具类的直接依赖；并新增 checkstyle 规则 `BanFlinkPreconditions` 防止回退。调用处因 API 兼容无需修改。
