# 提交 2927：Core: Allow overriding view location for subclasses (#14653)

## 提交信息

- **序号**：2927 / 4088
- **哈希**：fe6f78b32839bfb3ef6153fc84d836e68b447581
- **短哈希**：fe6f78b32
- **日期**：2025-11-26 15:39:16 +0100
- **作者**：Tamas Mate
- **提交说明**：Core: Allow overriding view location for subclasses (#14653)
- **PR/Issue**：#14653

## 总体目的

`ViewCatalogTests` 是 Iceberg core 模块中视图（View）相关的抽象测试基类，被各个 catalog 实现（如 REST、JDBC、Hive、 Nessie 等）的测试子类继承，用于验证视图的创建、加载、更新位置等通用行为。该基类在多处测试用例中需要构造一个"期望的视图存储位置"字符串，用来传给 `buildView(...).withLocation(location)` 并随后断言视图实际落盘位置是否符合预期。

在此次改动之前，期望位置是在测试用例内部直接用 `Paths.get(tempDir.toUri().toString(), Paths.get("ns", "view").toString()).toString()` 等硬编码方式拼接的。这带来两个问题：其一，位置构造逻辑分散在多个测试方法中，无法被子类统一调整；其二，不同 catalog 实现对 location 的规范化处理（例如 URI scheme、尾部斜杠、路径分隔符）可能不同，当某个 catalog 子类需要以不同方式计算期望位置时，无法覆盖这些硬编码逻辑，只能复制粘贴整段测试。

本提交将这些散落的位置构造逻辑抽取为一个 `protected` 方法 `viewLocation(String... paths)`，使其可被子类重写。这样当某个 catalog 实现的视图位置计算规则与默认行为不同时，子类只需重写 `viewLocation` 即可，无需改动基类测试逻辑。此外，新方法统一使用 `LocationUtil.stripTrailingSlash` 规范化基础路径，去掉了对 `java.nio.file.Paths` 的依赖，也避免了原先把 URI 字符串当普通路径传给 `Paths.get(String, String...)` 的语义混淆。

## 如何达成设计目的

改动集中在单一测试文件 `ViewCatalogTests.java`：新增一个 `protected viewLocation(String...)` 辅助方法，以 `tempDir` 的 URI（去除尾部斜杠）为前缀拼接可变路径段；然后将测试中所有原先用 `Paths.get(tempDir.toUri().toString(), ...)` 构造 location 的地方替换为对 `viewLocation(...)` 的调用。同时移除不再使用的 `java.nio.file.Paths` 导入，新增 `org.apache.iceberg.util.LocationUtil` 导入。

## 修改详情

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java` (+18/-15 lines)

**修改目的**：将散落的视图位置构造逻辑抽取为可被子类重写的 `protected` 方法。

**工作逻辑**：

1. **导入调整**：移除 `java.nio.file.Paths`，新增 `org.apache.iceberg.util.LocationUtil`。

2. **新增 `viewLocation` 方法**：

```java
protected String viewLocation(String... paths) {
    StringBuilder location =
        new StringBuilder(LocationUtil.stripTrailingSlash(tempDir.toFile().toURI().toString()));
    for (String path : paths) {
        location.append("/").append(path);
    }
    return location.toString();
}
```

该方法以 `tempDir` 转换为 `file:` URI 后去掉尾部斜杠作为基础前缀，再依次拼接传入的路径段（以 `/` 连接）。声明为 `protected`，子类可按需重写以匹配特定 catalog 的位置规范。不传参数时返回纯基础路径（如 `file:/tmp/xxx`），传 `"custom-location"` 时返回 `file:/tmp/xxx/custom-location`，传 `identifier.namespace().toString(), identifier.name()` 时按命名空间+视图名拼接。

3. **替换测试用例中的位置构造**：涉及以下几处测试方法：
   - 创建视图并指定 location 的用例：原先 `Paths.get(tempDir.toUri().toString(), Paths.get("ns", "view").toString())` → `viewLocation(identifier.namespace().toString(), identifier.name())`；
   - 自定义 location 用例：原先 `Paths.get(tempDir.toUri().toString())` 与 `Paths.get(tempDir.toUri().toString(), "custom-location")` → 分别替换为 `viewLocation()` 与 `viewLocation("custom-location")`；
   - 更新视图 location 的用例：原先 `Paths.get(tempDir.toUri().toString(), Paths.get("updated", "ns", "view").toString())` → `viewLocation("updated", "ns", "view")`。

这些替换在保持原有期望路径语义不变的前提下，把构造逻辑统一收敛到 `viewLocation` 方法，从而达成"允许子类重写视图位置"的设计目标。

## 总结

该提交通过将 `ViewCatalogTests` 中分散的视图位置构造逻辑抽取为 `protected viewLocation(String...)` 方法，使各 catalog 测试子类能够按自身位置规范重写该方法。改动同时用 `LocationUtil.stripTrailingSlash` 规范化基础路径、移除对 `Paths` 的不当使用，提升了测试基类的可扩展性与位置构造的一致性。
