# 提交 0676：Spark: Test initialization improvements

## 提交信息
- **序号**：0676 / 4088
- **哈希**：2025e79905488c724a180e25b91b487aeb848811
- **短哈希**：2025e7990
- **日期**：2024-04-12 19:20:22 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Test initialization improvements (#10131)
- **PR/Issue**：#10131

## 总体目的

本提交对 Spark 3.5 测试基础设施中的两处问题进行修复与清理，目的是消除测试基类层次中 `@TempDir` 字段类型不一致所带来的隐患，并补全 `ExtensionsTestBase` 初始化流程中遗漏的 `sparkContext` 赋值。这些问题虽然不直接导致生产代码故障，但会干扰测试的可靠性与可维护性，在某些测试用例组合下可能引发编译错误或空指针异常。

第一处问题是 `@TempDir` 临时目录字段在父子类之间类型不一致。在原代码中，`TestBaseWithCatalog` 声明了 `@TempDir protected File temp;`（`java.io.File`），而其子类 `CatalogTestBase` 又重复声明了 `@TempDir protected Path temp;`（`java.nio.file.Path`）。JUnit 5 的 `@TempDir` 同时支持 `File` 与 `Path` 两种类型，但子类用 `Path` 遮蔽（shadow）了父类的 `File` 字段，导致在同一继承层次中存在两个不同类型的 `temp` 字段。这不仅让代码语义模糊，还使依赖 `temp` 类型的下游测试（如 `TestSparkExecutorCache` 中 `new File(temp, ...)`）在面对实际注入的是哪个字段时产生歧义，当通过子类引用访问时得到 `Path`，通过父类引用访问时得到 `File`，极易引发类型不匹配。

第二处问题是 `ExtensionsTestBase` 在创建 `SparkSession` 后没有设置 `TestBase.sparkContext` 静态字段。`TestBase` 持有一个静态 `sparkContext`（`JavaSparkContext`）供测试体系使用，但 `ExtensionsTestBase` 的 `@BeforeAll` 初始化逻辑只创建了 `SparkSession` 与 `catalog`，漏掉了 `sparkContext` 的赋值。这意味着任何在 extensions 测试体系内、依赖 `TestBase.sparkContext` 的用例都会遇到该字段为 `null`，从而可能抛出 `NullPointerException`。

## 如何达成设计目的

整体策略是"字段上提 + 类型统一 + 补全初始化"：

1. **字段上提**：将 `temp` 字段的声明从子类 `CatalogTestBase` 中移除，使其只在父类 `TestBaseWithCatalog` 中声明一次，消除字段遮蔽。
2. **类型统一**：将 `TestBaseWithCatalog.temp` 的类型从 `File` 改为 `java.nio.file.Path`。选择 `Path` 是因为 JUnit 5 官方推荐使用 `Path`（NIO API），且原子类 `CatalogTestBase` 已使用 `Path`，说明项目倾向使用 `Path` 类型。
3. **下游适配**：由于 `temp` 类型从 `File` 变为 `Path`，原本直接使用 `new File(temp, ...)` 的 `TestSparkExecutorCache` 需要改为 `new File(temp.toFile(), ...)`，通过 `Path.toFile()` 桥接回 `File`。
4. **补全初始化**：在 `ExtensionsTestBase` 创建 `SparkSession` 之后，通过 `JavaSparkContext.fromSparkContext(spark.sparkContext())` 将底层 `SparkContext` 包装为 `JavaSparkContext` 并赋值给 `TestBase.sparkContext`，使继承链上的所有测试都能访问到非空的 `sparkContext`。

## 修改详情

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/ExtensionsTestBase.java`
**修改目的**：补全 `TestBase.sparkContext` 的初始化。
**工作逻辑**：在 `@BeforeAll` 方法中，调用 `spark.getOrCreate()` 获得 `SparkSession` 后，新增一行 `TestBase.sparkContext = JavaSparkContext.fromSparkContext(spark.sparkContext());`。`JavaSparkContext.fromSparkContext()` 是 Spark 提供的工厂方法，用于从 Scala 的 `SparkContext` 构造 Java 友好的 `JavaSparkContext` 包装器。赋值给 `TestBase` 的静态字段后，extensions 测试体系中所有依赖 `sparkContext` 的用例都能获取到有效实例。同时新增了 `import org.apache.spark.api.java.JavaSparkContext;`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/CatalogTestBase.java`
**修改目的**：移除子类中重复声明的 `@TempDir Path temp` 字段，消除与父类 `TestBaseWithCatalog` 中 `File temp` 的类型遮蔽。
**工作逻辑**：删除了 `import java.nio.file.Path;`、`import org.junit.jupiter.api.io.TempDir;` 以及类体末尾的 `@TempDir protected Path temp;` 字段声明。删除后，`CatalogTestBase` 及其子类统一继承父类 `TestBaseWithCatalog` 中的 `temp` 字段，类型由父类决定（`Path`）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestBaseWithCatalog.java`
**修改目的**：将 `temp` 字段类型从 `File` 改为 `java.nio.file.Path`，与子类原声明保持一致，并遵循 JUnit 5 对 `@TempDir` 推荐使用 `Path` 的最佳实践。
**工作逻辑**：将 `@TempDir protected File temp;` 改为 `@TempDir protected java.nio.file.Path temp;`（使用全限定名避免额外 import）。这是字段类型统一的根节点修改，配合 `CatalogTestBase` 中删除重复声明，整个继承层次现在只有一个 `Path` 类型的 `temp` 字段。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkExecutorCache.java`
**修改目的**：适配 `temp` 字段类型变更（`File` → `Path`），修正因此导致的编译问题。
**工作逻辑**：该测试在两处使用 `temp` 构造输出文件路径：`writeEqDeletes` 中的 `new File(temp, "eq-deletes-" + UUID.randomUUID())` 和 `writePosDeletes` 中的 `new File(temp, "pos-deletes-" + UUID.randomUUID())`。由于 `temp` 现在是 `Path`，不能直接传给 `java.io.File` 的构造函数（`File(File, String)` 重载），需改为 `new File(temp.toFile(), ...)`，先用 `Path.toFile()` 转回 `File` 再拼接子路径。

## 小结
- **成效**：成功达成目的。统一了 `temp` 字段的声明位置与类型，消除了字段遮蔽隐患；补全了 `sparkContext` 初始化，避免 extensions 测试中的潜在空指针。
- **影响范围**：仅影响 Spark 3.5 模块的测试基础设施（`spark-extensions` 与 `spark` 测试基类），不涉及生产代码，对运行时行为无影响。
- **回迁到 1.4.x 的注意事项**：本提交只修改测试代码，回迁风险低。需确认 1.4.x 分支的 Spark 3.5 测试基类层次结构与 main 一致；若 1.4.x 已有其他对 `temp` 字段类型的本地修改（如额外依赖 `File` 类型），需一并适配 `toFile()` 调用。
