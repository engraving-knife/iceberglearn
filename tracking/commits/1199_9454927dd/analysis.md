# 提交 1199：Core: Improve error handling when parsing view representations (#11236)

## 提交信息

- **序号**：1199 / 4088
- **哈希**：9454927ddb62d675d9f78843341a81ff2f3d946f
- **短哈希**：9454927dd
- **日期**：2024-09-30（Mon Sep 30 18:21:56 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Improve error handling when parsing view representations (#11236)
- **PR/Issue**：#11236

## 总体目的

Iceberg 1.4 起原生支持 View（视图）对象，`ViewVersionParser` 负责将视图版本的 JSON 元数据反序列化为 `ViewVersion` 对象。其中 `representations` 字段是视图表示（如 SQL 表示）的数组，是必填字段。原代码使用 `node.get(REPRESENTATIONS)` 直接读取：

- 当 JSON 中**缺失** `representations` 字段时，`node.get(...)` 返回 `null`，随后 `for (JsonNode serializedRepresentation : serializedRepresentations)` 在 `null` 上迭代会抛出 `NullPointerException`，错误信息晦涩、不易定位；
- 当 `representations` 字段**存在但不是数组**（如写成数字、字符串）时，Jackson 的 `for` 循环会以难以预测的方式遍历（例如对数字节点迭代会得到 0 个元素，对字符串节点迭代会逐字符返回），不会立即报错，导致静默的错误行为。

本提交改进 `ViewVersionParser.fromJson` 的错误处理：

1. 用 `JsonUtil.get(REPRESENTATIONS, node)` 替代 `node.get(REPRESENTATIONS)`，前者在字段缺失或为 null 时抛出清晰的 `IllegalArgumentException`（消息形如 `Cannot parse missing field: representations`）；
2. 在读取后立即用 `Preconditions.checkArgument(serializedRepresentations.isArray(), ...)` 校验节点类型，对非数组值抛出 `IllegalArgumentException`（消息形如 `Cannot parse representations from non-array: 23`）。

这样无论是缺失还是类型错误，都能给出语义明确的错误信息，便于上层（REST Catalog、Spark 等）诊断与上报。

## 如何达成设计目的

修改 `core/src/main/java/org/apache/iceberg/view/ViewVersionParser.java` 的 `fromJson(JsonNode node)` 方法：

- 将 `JsonNode serializedRepresentations = node.get(REPRESENTATIONS);` 改为 `JsonUtil.get(REPRESENTATIONS, node)`，借助已有的 `JsonUtil.get` 工具方法统一缺失字段校验；
- 紧接着插入 `Preconditions.checkArgument(serializedRepresentations.isArray(), "Cannot parse representations from non-array: %s", serializedRepresentations);`；
- 顺手把原 `for` 循环内两行的"先赋值再 add"合并为单行 `representations.add(ViewRepresentationParser.fromJson(serializedRepresentation));`，无行为差异，仅简化。

同时在 `TestViewVersionParser` 中新增两个测试用例：

- `invalidRepresentations`：`representations` 字段存在但值为数字 `23`，断言抛出 `IllegalArgumentException` 且消息为 `Cannot parse representations from non-array: 23`；
- `missingRepresentations`：JSON 中完全缺失 `representations` 字段，断言抛出 `IllegalArgumentException` 且消息为 `Cannot parse missing field: representations`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/view/ViewVersionParser.java`

**修改目的**：在解析视图版本时对 `representations` 字段进行缺失校验与类型校验，给出清晰错误。

**工作逻辑**：

修改前：

```java
JsonNode serializedRepresentations = node.get(REPRESENTATIONS);
ImmutableList.Builder<ViewRepresentation> representations = ImmutableList.builder();
for (JsonNode serializedRepresentation : serializedRepresentations) {
  ViewRepresentation representation =
      ViewRepresentationParser.fromJson(serializedRepresentation);
  representations.add(representation);
}
```

修改后：

```java
JsonNode serializedRepresentations = JsonUtil.get(REPRESENTATIONS, node);
Preconditions.checkArgument(
    serializedRepresentations.isArray(),
    "Cannot parse representations from non-array: %s",
    serializedRepresentations);
ImmutableList.Builder<ViewRepresentation> representations = ImmutableList.builder();
for (JsonNode serializedRepresentation : serializedRepresentations) {
  representations.add(ViewRepresentationParser.fromJson(serializedRepresentation));
}
```

关键点：

- `JsonUtil.get(property, node)` 内部调用 `Preconditions.checkArgument(node.hasNonNull(property), "Cannot parse missing field: %s", property)`，因此字段缺失或为 `null` 时抛 `IllegalArgumentException("Cannot parse missing field: representations")`，而非 `NullPointerException`；
- 新增的 `isArray()` 校验保证后续 `for` 循环一定遍历数组节点，避免对非数组节点的隐式迭代造成静默错误。

### `core/src/test/java/org/apache/iceberg/view/TestViewVersionParser.java`

**修改目的**：为新增的错误处理路径补充测试覆盖。

**工作逻辑**：

新增两个测试方法：

1. `invalidRepresentations()`：构造一个 `representations` 为数字 `23` 的非法 JSON，断言 `ViewVersionParser.fromJson(...)` 抛出 `IllegalArgumentException` 且消息为 `Cannot parse representations from non-array: 23`；
2. `missingRepresentations()`：构造一个完全缺失 `representations` 字段的 JSON，断言抛出 `IllegalArgumentException` 且消息为 `Cannot parse missing field: representations`。

两个测试均使用 AssertJ 的 `assertThatThrownBy(...)` 链式断言，与该测试类既有风格一致。

## 小结

- **成效**：`ViewVersionParser` 在遇到 `representations` 缺失或类型错误时，会抛出语义明确的 `IllegalArgumentException`，而非 `NullPointerException` 或静默错误；测试覆盖了两种非法输入路径。
- **影响范围**：`ViewVersionParser.java` 改动约 6 行（净增 2 行：一行 `JsonUtil.get` 替换 + 一行 `checkArgument`），`TestViewVersionParser.java` 新增 18 行测试。无 API 签名变更，无运行时正常路径行为变化。
- **回迁到 1.4.x 的注意事项**：1.4.x 是 View 支持的首个稳定版本，对错误处理的质量要求高。**建议回迁**此改动以改善 1.4.x 用户在视图元数据损坏时的诊断体验。回迁时需确认 1.4.x 的 `ViewVersionParser` 仍使用 `node.get(REPRESENTATIONS)` 模式（如已演进需重新评估），并确认 `JsonUtil.get` 与 `Preconditions` 的 import 路径与 1.4.x 一致。回迁后建议同步引入两个测试用例。
