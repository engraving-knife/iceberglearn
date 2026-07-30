# 提交 0947：Core: Make new TableMetadata.Builder constructor private (#10714)

## 提交信息

- **序号**：0947 / 4088
- **哈希**：3c46178855ecec864e1ea9ff67392c0c96d49e0d
- **短哈希**：3c4617885
- **日期**：2024-07-17 14:14:43 -0600
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Core: Make new TableMetadata.Builder constructor private (#10714)
- **PR/Issue**：#10714

## 总体目的

`TableMetadata` 是 Iceberg core 模块中描述表元数据的核心数据结构，其内部嵌套类 `TableMetadata.Builder` 负责以建造者模式构建或修改表元数据。该 Builder 原本提供两个构造函数：
- `private Builder()` —— 委托给下面的构造函数，使用默认格式版本；
- `public Builder(int formatVersion)` —— 接受指定格式版本，对外公开。

问题在于：将"带格式版本参数的构造函数"设为 `public` 等于鼓励外部直接 `new TableMetadata.Builder(formatVersion)` 来创建全新表元数据，这与 Iceberg 既定的"通过 `TableMetadata.newTable()` / `TableMetadata.buildFrom()` 等静态工厂入口创建 Builder"的推荐用法不一致，绕过了这些工厂方法所封装的初始化约束。同时，Builder 暴露公共构造函数也会增加未来对 Builder 初始化逻辑做不兼容调整时的 API 表面积。

本提交将该 `public Builder(int formatVersion)` 构造函数降级为 `private`，强制所有调用方走静态工厂入口（`TableMetadata.newTable(...)`、`TableMetadata.buildFrom(...)` 等），收敛 Builder 的创建路径，提升封装性并为后续重构留出空间。

## 如何达成设计目的

实现方式极其简单：把 `Builder(int formatVersion)` 构造函数的访问修饰符从 `public` 改为 `private`。已有的 `private Builder()` 仍通过 `this(DEFAULT_TABLE_FORMAT_VERSION)` 委托给它，调用链不变。所有外部调用方原本就应通过静态工厂方法获取 Builder 实例，因此该改动对正常使用路径无影响；只有少数绕过工厂直接 `new Builder(version)` 的代码（如有）才需要改写。从仓库当前状态看，此类直接调用在 main 分支上已不存在，因此可以安全收紧可见性。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：将 `TableMetadata.Builder(int formatVersion)` 构造函数由 `public` 收紧为 `private`，统一 Builder 的创建入口。

**工作逻辑**：`Builder` 类内有两个构造函数：

```java
private Builder() {
  this(DEFAULT_TABLE_FORMAT_VERSION);
}

private Builder(int formatVersion) {   // 原为 public
  this.base = null;
  this.formatVersion = formatVersion;
  ...
}
```

改动仅将第二个构造函数的修饰符从 `public` 改为 `private`：

```diff
-    public Builder(int formatVersion) {
+    private Builder(int formatVersion) {
```

这样外部模块无法再直接 `new TableMetadata.Builder(formatVersion)`，必须改用 `TableMetadata.newTable(Schema, PartitionSpec, Map<String,String>)` 或 `TableMetadata.buildFrom(TableMetadata)` 等工厂方法，这些方法内部会调用对应构造函数完成初始化。运行时行为完全不变，仅是编译期可见性收紧。

## 小结

- **成效**：收敛 `TableMetadata.Builder` 的创建入口，强制调用方使用静态工厂方法，提升封装性并减少公共 API 表面积。
- **影响范围**：仅 `core/src/main/java/org/apache/iceberg/TableMetadata.java` 一个文件，1 行修改，运行时行为无变化。
- **回迁到 1.4.x 的注意事项**：这是一次二进制不兼容的 API 收紧（公共构造函数变为私有）。回迁到 1.4.x 前，必须确认 1.4.x 分支自身代码以及 1.4.x 兼容性契约下下游可能依赖的代码中没有直接 `new TableMetadata.Builder(int)` 的调用；如有外部插件/集成依赖该公共构造函数，回迁会破坏其编译。建议在 1.4.x 上先做一次全量代码搜索确认无内部调用，并评估对下游兼容性影响后再决定是否回迁。如果 1.4.x 仍处于活跃的下游兼容维护期，可考虑推迟到下一个 minor 版本再收紧。
