# 提交 3800：Core: Validate non-string elements in JsonUtil.getStringArray (#16586)

## 提交信息

- **序号**：3800 / 4088
- **哈希**：f6d281122cf901baa8ea60a379eedb8fda2e4136
- **短哈希**：f6d281122
- **日期**：2026-05-30 15:35:58 -0700
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Core: Validate non-string elements in JsonUtil.getStringArray (#16586)
- **PR/Issue**：#16586
- **协作者**：Claude Opus 4.7 (1M context)

## 总体目的

本提交修复 `JsonUtil.getStringArray` 方法在解析 JSON 数组时缺乏对元素类型的校验问题。原实现直接对每个数组元素调用 `asText()`，如果数组中混入了非字符串元素（如数字、布尔、对象），`asText()` 会进行隐式转换（例如把数字 `45` 转为字符串 `"45"`），从而悄悄地接受非法输入而不报错。这与项目内其他类似方法（`getStringList`、`getStringSet`、`getStringListOrNull`，它们都通过 `JsonStringArrayIterator` 做了 `isTextual()` 校验）行为不一致，可能导致脏数据被静默接受，掩盖上游协议错误。

此外，原 `getStringArray(JsonNode node)` 重载在报错时不携带字段名，错误信息不够友好。本提交同时新增一个 `getStringArray(String property, JsonNode node)` 重载，让错误消息中包含字段名（如 `default-namespace`），与 `getStringList`/`getStringSet` 的报错约定一致，便于排查 JSON 解析失败的具体位置。

## 如何达成设计目的

设计上分两步：第一步在 `getStringArray(JsonNode)` 的循环中增加 `element.isTextual()` 校验，遇到非文本元素立即抛 `IllegalArgumentException` 并带上元素值，实现快速失败；第二步新增带 `property` 参数的重载，内部委托给 `getStringList(property, node)` 再转数组，复用其已有的字段名感知错误消息。然后将持有字段名上下文的两处调用方（`ViewVersionParser`、`RemoteSignRequestParser`）迁移到新重载，而对于反序列化顶层 `Namespace`、无字段名上下文的 `RESTSerializers.NamespaceDeserializer` 保留旧的 1 参数重载。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/JsonUtil.java` (+8/-1 lines)

**修改目的**：为 `getStringArray` 增加元素类型校验，并新增字段名感知的重载。

**工作逻辑**：
- 在 1 参数 `getStringArray(JsonNode node)` 的循环中，先取出元素并校验是否为文本：
```java
JsonNode element = arrayNode.get(i);
Preconditions.checkArgument(
    element.isTextual(), "Cannot parse string from non-text value: %s", element);
arr[i] = element.asText();
```
- 新增带 property 的重载，委托给 `getStringList`：
```java
public static String[] getStringArray(String property, JsonNode node) {
  return getStringList(property, node).toArray(new String[0]);
}
```
这样错误消息会变为 `Cannot parse string from non-text value in <property>: <value>`，包含字段名信息。

### `core/src/main/java/org/apache/iceberg/view/ViewVersionParser.java` (+1/-2 lines)

**修改目的**：迁移到字段名感知的 `getStringArray` 重载，使错误消息包含 `default-namespace` 字段名。

**工作逻辑**：
将原本手动 `get(DEFAULT_NAMESPACE, node)` 后再调用 1 参数 `getStringArray` 的写法，替换为直接传入字段名：
```java
Namespace defaultNamespace = Namespace.of(JsonUtil.getStringArray(DEFAULT_NAMESPACE, node));
```

### `core/src/main/java/org/apache/iceberg/rest/requests/RemoteSignRequestParser.java` (+1/-1 lines)

**修改目的**：在解析签名请求的 headers 时也使用字段名感知重载，提升错误消息可读性。

**工作逻辑**：
将 `JsonUtil.getStringArray(entry.getValue())` 改为 `JsonUtil.getStringArray(key, headersNode)`，其中 `key` 是 header 名，作为 property 传入。

### `core/src/test/java/org/apache/iceberg/util/TestJsonUtil.java` (+47/-0 lines)

**修改目的**：为两个重载的 `getStringArray` 添加测试，覆盖 null、非数组、含非字符串元素、合法数组、空数组等场景。

**工作逻辑**：
- `getStringArray` 测试 1 参数版本：`null`/`23` 抛 "Cannot parse string array from non-array"；`["23", 45]` 抛 "Cannot parse string from non-text value: 45"；合法字符串数组与空数组正常返回。
- `getStringArrayWithProperty` 测试带 property 版本：缺失字段抛 "Cannot parse missing list: items"；值为 null 抛 "Cannot parse JSON array from non-array value: items: null"；含非字符串元素抛 "Cannot parse string from non-text value in items: 45"；合法数组与空数组正常返回。

### `core/src/test/java/org/apache/iceberg/view/TestViewVersionParser.java` (+1/-1 lines)

**修改目的**：更新因迁移到新重载而变化的错误消息断言。

**工作逻辑**：
原本缺失 `default-namespace` 字段时报 "Cannot parse missing field: default-namespace"，迁移后由 `getStringList` 路径报错，消息改为 "Cannot parse missing list: default-namespace"：
```java
.hasMessage("Cannot parse missing list: default-namespace");
```

## 总结

本提交通过为 `JsonUtil.getStringArray` 增加元素类型校验和字段名感知重载，统一了 JSON 字符串数组解析的校验与报错约定，避免脏数据被静默接受，并使错误消息更具可诊断性。改动虽小，但提升了 Iceberg JSON 序列化层的健壮性和可维护性。值得一提的是，本提交由人与 AI（Claude Opus 4.7）协作完成，体现了 AI 辅助编码在项目中的实践。
