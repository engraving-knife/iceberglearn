# 提交 0840：Core, Flink, Spark: Import the right assertThatThrownBy method from AssertJ (#10512)

## 提交信息
- **序号**：0840 / 4088
- **哈希**：5f3809c5159694618e0619cffd563077f1b56c12
- **短哈希**：5f3809c51
- **日期**：2024-06-17
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Core, Flink, Spark: Import the right assertThatThrownBy method from AssertJ (#10512)
- **PR/Issue**：#10512

## 总体目的

本提交解决一个隐蔽的测试导入错误：多个测试文件错误地从 `org.assertj.core.api.AssertionsForClassTypes` 静态导入 `assertThatThrownBy`，而应当从 `org.assertj.core.api.Assertions` 导入。

AssertJ 在两个类中都提供了 `assertThatThrownBy` 方法，但二者能力不同：
- `org.assertj.core.api.Assertions.assertThatThrownBy` —— 主断言类中的标准方法，提供完整的重载集（如接受 `ThrowingCallable`、`ThrowingCallable`+描述等），并返回 `ThrowableAssert`，可继续链式调用 AssertJ 丰富的 Throwable 断言 API。
- `org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy` —— 这是一个面向「具体类型」的辅助类，其 `assertThatThrownBy` 重载较少，主要服务于历史兼容场景。

错误导入 `AssertionsForClassTypes.assertThatThrownBy` 不会立即编译报错（因为方法签名兼容），但会：1) 丢失主 `Assertions` 类提供的额外重载与扩展能力；2) 在调用方混合使用 `Assertions.assertThat` 与 `AssertionsForClassTypes.assertThatThrownBy` 时显得风格不一致；3) 误导后续开发者复制粘贴错误导入。这是一类「能编译但不对」的代码异味。

本提交一次性修正所有错误导入，并新增 Checkstyle 规则防止再次引入错误导入。

## 如何达成设计目的

分两步：

**第一步：修正现有错误导入**。将 8 个测试文件中的静态导入从 `AssertionsForClassTypes.assertThatThrownBy` 改为 `Assertions.assertThatThrownBy`。涉及 core 模块 2 个文件、flink v1.17/v1.18/v1.19 各 1 个文件、spark v3.3/v3.4/v3.5 各 1 个文件。修改是纯机械的导入语句替换，不涉及任何测试逻辑改动——因为两个类的 `assertThatThrownBy` 方法签名在测试使用场景下行为一致。

**第二步：新增 Checkstyle 规则防止回退**。在 `.baseline/checkstyle/checkstyle.xml` 中新增一个 `RegexpMultiline` 模块，匹配任何不是来自 `org.assertj.core.api.Assertions.` 的 `assertThatThrownBy` 静态导入，并报错。该规则使用「负向先行断言」`(?!\Qorg.assertj.core.api.Assertions.\E)` 来排除正确来源。这样未来若有开发者误用 IDE 自动补全导入到 `AssertionsForClassTypes`，Checkstyle 会在构建期立即拦截。

值得注意的是，main 分支的 checkstyle.xml 结构与 1.4.x 不同（main 用 `<module name="RegexpMultiline">` 平铺在 TreeWalker 外层的某个位置），而 1.4.x 分支的 checkstyle.xml 使用缩进格式且规则集合略有差异（1.4.x 已有禁止 Hamcrest 和 `ExpectedException` 的规则，但没有针对 `assertThatThrownBy` 来源的规则）。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml`
**修改目的**：新增 RegexpMultiline 规则，禁止从 `org.assertj.core.api.Assertions` 以外的包静态导入 `assertThatThrownBy`。
**工作逻辑**：
```xml
<module name="RegexpMultiline">
    <property name="format" value="^\s*import\s+static\s+(?!\Qorg.assertj.core.api.Assertions.\E).*\.assertThatThrownBy;"/>
    <property name="message" value="assertThatThrownBy() should be statically imported from org.assertj.core.api.Assertions"/>
</module>
```
正则解释：匹配以 `import static` 开头、其后不是 `org.assertj.core.api.Assertions.`、但以 `.assertThatThrownBy;` 结尾的导入行。`\Q...\E` 是 Java 正则的「字面量引用」语法，避免其中的 `.` 被当作通配符。该规则放在 checkstyle 配置的合适层级，对所有测试源码生效。

### `core/src/test/java/org/apache/iceberg/TestClientPoolImpl.java`
**修改目的**：将 assertThatThrownBy 的静态导入改为来自主 Assertions 类。
**工作逻辑**：
```java
// 修改前
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
// 修改后
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```
该文件同时已导入 `org.assertj.core.api.Assertions.assertThat`，修正后两个断言方法来自同一个类，风格统一。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`
**修改目的**：同上，修正 assertThatThrownBy 静态导入来源。
**工作逻辑**：与上一文件相同的一行替换。

### `flink/v1.17|v1.18|v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java`（3 份）
**修改目的**：3 个 Flink 版本的 TestFlinkCatalogTable 各修正一处 assertThatThrownBy 静态导入。
**工作逻辑**：每个文件的一行 `import static` 从 `AssertionsForClassTypes` 改为 `Assertions`。3 个 Flink 版本目录各维护一份源码副本，故有 3 处独立但相同的修改。

### `spark/v3.3|v3.4|v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java`（3 份）
**修改目的**：3 个 Spark 版本的 TestStructuredStreamingRead3 各修正一处 assertThatThrownBy 静态导入。
**工作逻辑**：与上述相同的导入来源替换。3 个 Spark 版本目录各一份副本。

## 小结
- **成效**：统一了 8 个测试文件的 `assertThatThrownBy` 导入来源，消除了「能编译但非最佳」的代码异味；通过 Checkstyle 规则固化约定，防止后续回退。属于测试代码质量与可维护性改进，不改变任何运行时或测试断言行为。
- **影响范围**：仅影响测试代码与构建期 Checkstyle 规则。Checkstyle 规则对所有模块生效，未来任何新增的 `assertThatThrownBy` 静态导入都必须来自 `org.assertj.core.api.Assertions`，否则构建失败。
- **回迁注意事项**：本提交可部分回迁到 1.4.x，但需注意差异：1) 1.4.x 分支的 checkstyle.xml 结构与 main 不同（缩进、规则组织方式），新增 RegexpMultiline 模块时需按 1.4.x 现有风格放置到正确位置（1.4.x 已有禁止 Hamcrest/ExpectedException 的同类 IllegalImport 规则可作参照）。2) 经核查，1.4.x 分支上仍存在错误导入的文件包括 `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`、`spark/v3.2|v3.3|v3.4|v3.5/.../TestStructuredStreamingRead3.java`（注意比 main 多了 v3.2）；而 `core/.../TestClientPoolImpl.java` 与 `flink/.../TestFlinkCatalogTable.java` 在 1.4.x 上要么不存在要么已无错误导入，cherry-pick 时需按实际文件清单调整。3) 1.4.x 的 flink 版本目录为 v1.15/v1.16/v1.17/v2.1，与 main 的 v1.17/v1.18/v1.19 路径不同，flink 相关改动需重新定位目标文件。建议回迁时以「grep 找出所有 `AssertionsForClassTypes.assertThatThrownBy`」为准，对命中文件逐一修正，并同步加入 Checkstyle 规则。
