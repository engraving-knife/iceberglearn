# 提交 0201：AWS, Core, Dell, Spark: Use Strings to verify null and empty string (#9090)

## 提交信息

- **序号**：0201 / 4088
- **哈希**：4e62b58f04aba4ccc2a2a846494b478d3b03f58f
- **短哈希**：4e62b58f0
- **日期**：2023-11-27 16:41:32 -0800
- **作者**：Andre Luis Anastacio
- **提交说明**：AWS, Core, Dell, Spark: Use Strings to verify null and empty string (#9090)
- **PR/Issue**：#9090

## 总体目的

本提交是一次横跨多个模块（AWS、Core、Dell、Spark 3.2/3.3）的代码清理与规范化提交，目的是把代码库里大量分散的"手动判空 + 长度判断"惯用法（`x != null && x.length() > 0`）统一替换为更简洁、更语义化的 Guava 工具方法 `Strings.isNullOrEmpty(...)`，把字符串判空逻辑收敛到一个标准实现上。

在 Java 代码中，判断一个字符串既不是 `null` 又不是空串，最常见的反模式是写 `str != null && str.length() > 0`（或 `str.length() != 0`）。这种写法虽然正确，但存在几个问题：一是冗长，每次需要两段判断；二是语义不直观，读代码的人要先在脑中拆解"非空且长度大于零"才能确认意图；三是项目已经 relocation 了 Guava（`org.apache.iceberg.relocated.com.google.common.base.Strings`），有现成的 `Strings.isNullOrEmpty` 可直接使用，再保留手写判断属于不必要的重复造轮子。

提交作者 Andre Luis Anastacio 在 PR 中按模块分阶段推进：AWS、Core、Dell 模块的 catalog 类用 `!Strings.isNullOrEmpty(...)` 替换 `path != null && path.length() > 0`；Spark 3.2 和 3.3 测试基类中则用 `!str.trim().isEmpty()` 替换 `str.trim().length() > 0`。对 Iceberg 演进而言，这类清理单个看是小事，但累积起来能显著降低代码噪音、提升一致性，也让后续的静态分析（如 SpotBugs、Error Prone）能更容易发现真正的潜在 NPE 风险。

## 如何达成设计目的

整体思路很简单：在每个涉及的源文件顶部新增 `Strings` 的 import，然后把每处 `x != null && x.length() > 0` 替换为 `!Strings.isNullOrEmpty(x)`，把 `x.trim().length() > 0` 替换为 `!x.trim().isEmpty()`。改动只动判断表达式，不改变任何控制流、错误信息或行为语义——即原表达式为真的场景，替换后依然为真；为假的场景依然为假。共改动 8 个文件、15 行新增、11 行删除。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/dynamodb/DynamoDbCatalog.java`

**修改目的**：把 `DynamoDbCatalog.initialize` 中对 `path`（warehousePath）的非空校验改用 `Strings.isNullOrEmpty`。

**工作逻辑**：新增 `import org.apache.iceberg.relocated.com.google.common.base.Strings;`，然后把 [`DynamoDbCatalog.initialize`](aws/src/main/java/org/apache/iceberg/aws/dynamodb/DynamoDbCatalog.java) 中的 `Preconditions.checkArgument(path != null && path.length() > 0, ...)` 改为 `Preconditions.checkArgument(!Strings.isNullOrEmpty(path), ...)`。错误信息 `"Cannot initialize DynamoDbCatalog because warehousePath must not be null or empty"` 保持不变。

### `aws/src/main/java/org/apache/iceberg/aws/glue/GlueCatalog.java`

**修改目的**：统一 GlueCatalog 中两处对 `path` / `warehousePath` 的判空写法。

**工作逻辑**：在构造器中，原本是
```java
this.warehousePath =
    (path != null && path.length() > 0) ? LocationUtil.stripTrailingSlash(path) : null;
```
改为更紧凑的三元：
```java
this.warehousePath = Strings.isNullOrEmpty(path) ? null : LocationUtil.stripTrailingSlash(path);
```
注意条件取反——把"非空"分支放在前变成把"为空"分支放在前，从而把"取 null"作为短路结果，可读性更好。另外在 `defaultWarehouseLocation` 中，`ValidationException.check(warehousePath != null && warehousePath.length() > 0, ...)` 同样改为 `!Strings.isNullOrEmpty(warehousePath)`，错误信息保持不变。

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopCatalog.java`

**修改目的**：HadoopCatalog 初始化时对 `WAREHOUSE_LOCATION` 配置项的校验改用 `Strings.isNullOrEmpty`。

**工作逻辑**：新增 `Strings` import，把 `Preconditions.checkArgument(inputWarehouseLocation != null && inputWarehouseLocation.length() > 0, ...)` 改为 `!Strings.isNullOrEmpty(inputWarehouseLocation)`，错误信息保持不变。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java`

**修改目的**：与 HadoopCatalog 同类替换，统一 JdbcCatalog 的 warehouse 路径校验。

**工作逻辑**：把 `inputWarehouseLocation != null && inputWarehouseLocation.length() > 0` 改为 `!Strings.isNullOrEmpty(inputWarehouseLocation)`，错误信息不变。注意此分支用的是 `iceberglearn` 仓库的 `core` 模块路径。

### `core/src/main/java/org/apache/iceberg/util/LocationUtil.java`

**修改目的**：把工具类 `LocationUtil.stripTrailingSlash` 的入参校验也改为 `Strings.isNullOrEmpty`，使工具类自身也是规范化的。

**工作逻辑**：原代码
```java
Preconditions.checkArgument(
    path != null && path.length() > 0, "path must not be null or empty");
```
改为
```java
Preconditions.checkArgument(!Strings.isNullOrEmpty(path), "path must not be null or empty");
```
由于 `LocationUtil` 是被各 catalog 调用的公共工具，这处修改也消除了一个潜在不一致点（catalog 自己用 `Strings` 判空，调到的工具却手写判空）。

### `dell/src/main/java/org/apache/iceberg/dell/ecs/EcsCatalog.java`

**修改目的**：Dell ECS 模块的 catalog 入口校验同样规范化。

**工作逻辑**：与 HadoopCatalog/JdbcCatalog 完全同型，把 `inputWarehouseLocation != null && inputWarehouseLocation.length() > 0` 改为 `!Strings.isNullOrEmpty(inputWarehouseLocation)`，错误信息不变。

### `spark/v3.2/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkRowLevelOperationsTestBase.java`

**修改目的**：测试基类 `toDS` 方法里过滤空行的 lambda 改用更直观的 `isEmpty()`。

**工作逻辑**：原 `Arrays.stream(jsonData.split("\n")).filter(str -> str.trim().length() > 0)` 改为 `.filter(str -> !str.trim().isEmpty())`。这里没有用 `Strings.isNullOrEmpty`，因为流里的元素已经是 lambda 参数 `str`（非 null 已由 stream 语义保证），且 `trim()` 后只需判空串，用 JDK 原生 `String.isEmpty()` 即可，无需引入 Guava。语义完全等价。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkRowLevelOperationsTestBase.java`

**修改目的**：与 Spark 3.2 测试基类完全相同的替换，保持两条 Spark 分支测试代码同步。

**工作逻辑**：同样把 `.filter(str -> str.trim().length() > 0)` 改为 `.filter(str -> !str.trim().isEmpty())`，无行为变化。

## 小结

这是一次纯规范化清理提交，把 catalog 与工具类中散布的手写"非空且非空串"判断统一收敛到 Guava `Strings.isNullOrEmpty` / JDK `String.isEmpty()`，在不改变任何运行时行为的前提下提升了代码一致性、可读性和静态可分析性。
