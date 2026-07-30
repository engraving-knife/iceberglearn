# 提交 0916：Dell, Hive3: Convert remaining tests to JUnit5 (#10670)

## 提交信息

- **序号**：0916 / 4088
- **哈希**：4714bbfa20642516037abac8545c24b326b53787
- **短哈希**：4714bbfa2
- **日期**：2024-07-10（Wed Jul 10 09:25:12 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Dell, Hive3: Convert remaining tests to JUnit5 (#10670)
- **PR/Issue**：#10670

## 总体目的

本提交是 Iceberg 项目"JUnit 4 → JUnit 5 + AssertJ"测试栈统一迁移工作的延续，覆盖之前迁移尚未触及的两个模块：`dell`（Dell ECS catalog 集成）和 `hive3`（Hive 3 集成）。这两个模块下仍残留使用 JUnit 4 `org.junit.Assert` 与 `org.junit.Test` 的测试类。本提交把它们切换到 JUnit 5（`org.junit.jupiter.api.Test`）+ AssertJ 流式断言（`assertThat(...).isEqualTo/isNull/isFalse/containsExactly` 等），完成全仓库测试栈的统一。

迁移动机与之前几个迁移提交一致：统一测试栈、利用 AssertJ 更丰富的断言（链式、可读性好、失败信息更友好）、消除对 JUnit 4 的依赖（为后续清理 JUnit 4 依赖做准备，见 #10672）。

## 如何达成设计目的

机械式替换：
1. `import org.junit.Assert` / `import org.junit.Test` → `import static org.assertj.core.api.Assertions.assertThat` + `import org.junit.jupiter.api.Test`。
2. `Assert.assertEquals(expected, actual)` → `assertThat(actual).isEqualTo(expected)`；`Assert.assertNull(x)` → `assertThat(x).isNull()`；`Assert.assertFalse(x)` → `assertThat(x).isFalse()`；`Assert.assertNotSame(a, b)` → 与 `isEqualTo` 链式合并为 `assertThat(copy).isEqualTo(date).isNotSameAs(date)`。
3. 对于列表断言，原本 `Assert.assertEquals(msg, ImmutableList.of(...), actual)` 改为 `assertThat(actual).containsExactly(...)`，省去 `ImmutableList` 包装，并自然校验顺序与元素。
4. 对于 map 断言，原本 `Assert.assertEquals(msg, ImmutableMap.of(...), actual)` 改为 `assertThat(actual).isEqualTo(ImmutableMap.of(...))`。
5. 顺带删除不再使用的 `import`（如 `ImmutableList` 在 `TestEcsCatalog` 中已不再使用）。

## 修改详情

### `dell/src/test/java/org/apache/iceberg/dell/ecs/TestEcsCatalog.java`

**修改目的**：把 Dell ECS Catalog 测试中残留的 JUnit 4 `Assert.*` 断言切换为 AssertJ 风格。

**工作逻辑**：该测试类此前已经使用 JUnit 5 的 `@BeforeEach`/`@AfterEach`/`@Test`（import 已是 `org.junit.jupiter.api.*`），但断言仍混用 `org.junit.Assert`。本提交把所有 `Assert.assertEquals(msg, expected, actual)` 改写：

```diff
-    Assert.assertEquals(
-        "List namespaces with empty namespace",
-        ImmutableList.of(Namespace.of("a")),
-        ecsCatalog.listNamespaces());
+    assertThat(ecsCatalog.listNamespaces()).containsExactly(Namespace.of("a"));
```

- 列表类断言改用 `containsExactly(...)`，比 `assertEquals(ImmutableList.of(...), actual)` 更直接，且不依赖 `ImmutableList`（删除对应 import）。
- Map 类断言改用 `isEqualTo(ImmutableMap.of(...))`。
- 由于 AssertJ 风格自带更好的失败描述，原 `Assert.assertEquals` 中的描述字符串（如 `"List namespaces with empty namespace"`）被省略；如需要可在 `assertThat(...).as(...)` 中补充，但作者认为测试方法名+实际值已足够清晰。

### `hive3/src/test/java/org/apache/iceberg/mr/hive/serde/objectinspector/TestIcebergDateObjectInspectorHive3.java`

**修改目的**：把 Hive3 `IcebergDateObjectInspectorHive3` 测试从 JUnit 4 迁移到 JUnit 5 + AssertJ。

**工作逻辑**：
- import 替换：`org.junit.Assert` + `org.junit.Test` → `org.assertj.core.api.Assertions.assertThat`（static）+ `org.junit.jupiter.api.Test`。
- `Assert.assertEquals(Date.class, oi.getJavaPrimitiveClass())` → `assertThat(oi.getJavaPrimitiveClass()).isEqualTo(Date.class)`。注意参数顺序对调：JUnit4 是 `(expected, actual)`，AssertJ 是 `assertThat(actual).isEqualTo(expected)`。
- null 检查：`Assert.assertNull(oi.copyObject(null))` → `assertThat(oi.copyObject(null)).isNull()`。
- 拷贝对象相等且非同一引用的两步断言合并为链式：`assertThat(copy).isEqualTo(date).isNotSameAs(date)`，比原本两行 `assertEquals` + `assertNotSame` 更紧凑。
- `Assert.assertFalse(oi.preferWritable())` → `assertThat(oi.preferWritable()).isFalse()`。

### `hive3/src/test/java/org/apache/iceberg/mr/hive/serde/objectinspector/TestIcebergTimestampObjectInspectorHive3.java`

**修改目的**：把 Hive3 `IcebergTimestampObjectInspectorHive3` 测试从 JUnit 4 迁移到 JUnit 5 + AssertJ。

**工作逻辑**：与 `TestIcebergDateObjectInspectorHive3` 完全同构。`assertEquals`/`assertNull`/`assertFalse`/`assertNotSame` 全部替换为 AssertJ 风格。`oi.convert(null)` 也用 `assertThat(...).isNull()` 校验。最终 `assertEquals(local, oi.convert(ts))` 改为 `assertThat(oi.convert(ts)).isEqualTo(local)`，调整参数顺序。

### `hive3/src/test/java/org/apache/iceberg/mr/hive/serde/objectinspector/TestIcebergTimestampWithZoneObjectInspectorHive3.java`

**修改目的**：把 Hive3 `IcebergTimestampWithZoneObjectInspectorHive3` 测试从 JUnit 4 迁移到 JUnit 5 + AssertJ。

**工作逻辑**：与前两个 Hive3 测试同构。所有 `Assert.*` 替换为 AssertJ。涉及 `TimestampTZ`、`TimestampLocalTZWritable`、`OffsetDateTime` 之间的转换断言同样改写为 `assertThat(actual).isEqualTo(expected)` 风格。该文件改动量较大（48 行），主要因为断言数量多且每个 `Assert.assertEquals` 都需要拆成两行 `assertThat(...).isEqualTo(...)`。

## 小结

- **成效**：完成 `dell` 与 `hive3` 两个模块下 4 个测试类从 JUnit 4 到 JUnit 5 + AssertJ 的迁移，使全仓库测试栈风格统一。共修改 4 个文件，72 行新增 / 93 行删除（删除多于新增是因为 AssertJ 链式风格更紧凑、可合并多步断言）。
- **影响范围**：仅测试代码，无生产代码变更。涉及 `dell/src/test/.../ecs/TestEcsCatalog.java`、`hive3/src/test/.../objectinspector/` 下 3 个 ObjectInspector 测试类。
- **回迁到 1.4.x 的注意事项**：测试基础设施迁移，不影响生产功能。
  - 单独回迁本提交会让 1.4.x 的 `dell` 与 `hive3` 测试栈与分支其余部分（若仍以 JUnit 4 为主）不一致，需确保 1.4.x 的 `dell`/`hive3` 模块 build.gradle 已声明 AssertJ 与 JUnit 5 依赖。
  - 改动量小、风险低，若 1.4.x 整体在做 JUnit 5 迁移则可顺带回迁；否则建议不主动回迁，避免引入不必要的依赖差异。
  - 注意 `TestEcsCatalog.java` 此前部分已使用 JUnit 5 注解（`@BeforeEach` 等），仅断言仍用 JUnit 4，回迁时需确认 1.4.x 该文件状态与本提交起点一致，否则会有冲突。
