# 提交 1644：OpenAPI: Deprecate snapshot-id of SetStatisticsUpdate (#12010)

## 提交信息

- **序号**：1644 / 4088
- **哈希**：b7de28e72ef8134f90105b869acb5f89b16803f0
- **短哈希**：b7de28e72
- **日期**：2025-01-27（Mon Jan 27 13:03:30 2025 +0100）
- **作者**：Christian <christian@hansetag.com>
- **提交说明**：OpenAPI: Deprecate `snapshot-id` of `SetStatisticsUpdate`
- **PR/Issue**：#12010
- **共同作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>

## 总体目的

Iceberg 的 `set-statistics` 元数据更新（`SetStatisticsUpdate`）在 REST 协议与 Java API 中此前要求调用方同时提供两个有冗余关系的 `snapshot-id`：

1. 更新顶层的 `snapshot-id` 字段（`SetStatisticsUpdate.snapshot-id`）；
2. `statistics` 字段内嵌的 `StatisticsFile` 自带的 `snapshot-id`（`statistics.snapshot-id`）。

二者必须一致，否则会触发校验失败。这种冗余设计带来几个问题：

- 客户端必须重复传同一个值，容易因代码路径不一致导致两个值不匹配，触发不必要的校验错误；
- 协议层面存在两个"事实源"，语义不清；
- Java API `UpdateStatistics.setStatistics(long snapshotId, StatisticsFile)` 强制要求传入 `snapshotId`，但 `StatisticsFile` 自身已携带，调用方需要重复从 `statisticsFile.snapshotId()` 取再传回，啰嗦且易错；
- `MetadataUpdate.SetStatistics` 类内部还把 `snapshotId` 单独存一份字段，与 `statisticsFile.snapshotId()` 必须手动保持同步，是潜在 bug 源。

本提交把 `snapshot-id` 从"必填的冗余字段"降级为"已废弃的可选字段"，统一以 `statistics.snapshot-id` 作为唯一事实源：

1. OpenAPI 规范：`SetStatisticsUpdate.snapshot-id` 从 `required` 移除，标 `deprecated: true`，加描述引导客户端改用 `statistics.snapshot-id`；
2. Python 模型同步：`snapshot_id` 改为 `Optional`，描述同上；
3. Java API：`UpdateStatistics.setStatistics(long, StatisticsFile)` 标 `@Deprecated`，新增 `setStatistics(StatisticsFile)` 默认方法；
4. `SetStatistics` 实现、`TableMetadata.Builder`、`MetadataUpdate.SetStatistics` 均新增不接收 `snapshotId` 的重载，旧版标 `@Deprecated`；
5. `MetadataUpdate.SetStatistics` 内部删除独立 `snapshotId` 字段，`snapshotId()` 改为 `return statisticsFile.snapshotId()`；
6. `MetadataUpdateParser.readSetStatistics` 不再从 JSON 读 `snapshot-id`，直接用 `StatisticsFile` 构造 update（旧客户端发的 `snapshot-id` 字段被忽略）。

## 如何达成设计目的

通过"双轨过渡 + 标记废弃"的方式：

- 旧 API/构造器/字段保留并标 `@Deprecated`（计划 1.9.0 或 2.0.0 移除），保证二进制与序列化兼容；
- 新增不带 `snapshotId` 的重载，作为推荐用法；
- REST 协议层把 `snapshot-id` 改为可选 + `deprecated`，老客户端继续发该字段会被服务端忽略（parser 不再读），新客户端可直接省略；
- 内部 `SetStatistics` 类不再保存冗余 `snapshotId` 字段，统一从 `statisticsFile.snapshotId()` 取，消除不一致风险。

## 修改详情

### `api/src/main/java/org/apache/iceberg/UpdateStatistics.java`（修改，+13）

**修改目的**：API 层废弃旧重载、新增推荐重载。

**工作逻辑**：

- `setStatistics(long snapshotId, StatisticsFile statisticsFile)` 标 `@Deprecated`（"since 1.8.0, will be removed 1.9.0 or 2.0.0, use #setStatistics(StatisticsFile)"），Javadoc 同步加 `@deprecated`；
- 新增 `default UpdateStatistics setStatistics(StatisticsFile statisticsFile)`，默认抛 `UnsupportedOperationException("Setting statistics is not supported")`——这是个默认方法兜底，由 core 模块的 `SetStatistics` 实现覆写提供真实逻辑。这样 api 模块无需依赖 core 即可声明新方法。

### `core/src/main/java/org/apache/iceberg/MetadataUpdate.java`（修改，+13/-4）

**修改目的**：`SetStatistics` 内部去掉冗余 `snapshotId` 字段，新增单参构造器，旧双参构造器废弃。

**工作逻辑**：

```
class SetStatistics implements MetadataUpdate {
  // 删除: private final long snapshotId;
  private final StatisticsFile statisticsFile;

  @Deprecated
  public SetStatistics(long snapshotId, StatisticsFile statisticsFile) {
    this.statisticsFile = statisticsFile;  // 不再赋值 this.snapshotId
  }

  public SetStatistics(StatisticsFile statisticsFile) {
    this.statisticsFile = statisticsFile;
  }

  public long snapshotId() {
    return statisticsFile.snapshotId();  // 改为从 statisticsFile 取
  }

  public StatisticsFile statisticsFile() { return statisticsFile; }

  @Override
  public void applyTo(TableMetadata.Builder metadataBuilder) {
    metadataBuilder.setStatistics(statisticsFile);  // 调用新重载
  }
}
```

要点：

- 旧双参构造器保留（标 `@Deprecated`）但内部不再保存 `snapshotId`，等价于单参构造器——保证旧调用方二进制兼容，但语义上 `snapshotId` 参数被忽略（仅做冗余校验已在更上层 `SetStatistics` / `TableMetadata.Builder` 的旧重载里完成）；
- `snapshotId()` 改为 `statisticsFile.snapshotId()`，确保任何时刻返回值与 statisticsFile 一致；
- `applyTo` 改为调用 `metadataBuilder.setStatistics(statisticsFile)`（新重载）。

### `core/src/main/java/org/apache/iceberg/MetadataUpdateParser.java`（修改，+1/-2）

**修改目的**：反序列化 `set-statistics` update 时不再读 `snapshot-id`。

**工作逻辑**：

```
private static MetadataUpdate readSetStatistics(JsonNode node) {
  // 删除: long snapshotId = JsonUtil.getLong(SNAPSHOT_ID, node);
  JsonNode statisticsFileNode = JsonUtil.get(STATISTICS, node);
  StatisticsFile statisticsFile = StatisticsFileParser.fromJson(statisticsFileNode);
  return new MetadataUpdate.SetStatistics(statisticsFile);  // 单参构造
}
```

老客户端发来的 JSON 中若仍含 `snapshot-id` 字段，会被忽略（不再读取）。`statistics.snapshot-id` 是唯一事实源。

### `core/src/main/java/org/apache/iceberg/SetStatistics.java`（修改，+14/-1）

**修改目的**：core 模块的 `UpdateStatistics` 实现新增推荐重载，旧重载废弃。

**工作逻辑**：

```
@Deprecated
@Override
public UpdateStatistics setStatistics(long snapshotId, StatisticsFile statisticsFile) {
  Preconditions.checkArgument(snapshotId == statisticsFile.snapshotId());
  statisticsToSet.put(statisticsFile.snapshotId(), Optional.of(statisticsFile));
  return this;
}

@Override
public UpdateStatistics setStatistics(StatisticsFile statisticsFile) {
  statisticsToSet.put(statisticsFile.snapshotId(), Optional.of(statisticsFile));
  return this;
}
```

旧重载保留 `Preconditions.checkArgument(snapshotId == statisticsFile.snapshotId())` 校验（保护老调用方传错），但内部 `put` 用 `statisticsFile.snapshotId()` 而非传入的 `snapshotId`（虽二者已校验相等，但语义上以 statisticsFile 为准）。新重载直接用 `statisticsFile.snapshotId()` 作 key。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`（修改，+15/-1）

**修改目的**：`TableMetadata.Builder` 新增推荐重载，旧重载废弃。

**工作逻辑**：

```
@Deprecated
public Builder setStatistics(long snapshotId, StatisticsFile statisticsFile) {
  Preconditions.checkNotNull(statisticsFile, "statisticsFile is null");
  Preconditions.checkArgument(snapshotId == statisticsFile.snapshotId(), ...);
  statisticsFiles.put(statisticsFile.snapshotId(), ImmutableList.of(statisticsFile));
  changes.add(new MetadataUpdate.SetStatistics(statisticsFile));  // 改为单参构造
  return this;
}

public Builder setStatistics(StatisticsFile statisticsFile) {
  Preconditions.checkNotNull(statisticsFile, "statisticsFile is null");
  statisticsFiles.put(statisticsFile.snapshotId(), ImmutableList.of(statisticsFile));
  changes.add(new MetadataUpdate.SetStatistics(statisticsFile));
  return this;
}
```

旧重载保留校验，但 `changes.add` 改为调单参构造器（与 `MetadataUpdate.SetStatistics` 内部去冗余字段保持一致）。新重载无 `snapshotId` 校验，直接以 `statisticsFile.snapshotId()` 为 key 写入 `statisticsFiles` map 与 `changes` 列表。

### `core/src/test/java/org/apache/iceberg/TestMetadataUpdateParser.java`（修改，+0/-1）

**修改目的**：测试同步改用单参构造器。

**工作逻辑**：

```
MetadataUpdate expected =
    new MetadataUpdate.SetStatistics(
-       snapshotId,
        new GenericStatisticsFile(snapshotId, "s3://...", ...));
```

测试不再传冗余 `snapshotId` 给 `SetStatistics` 构造器。`snapshotId` 变量仍用于构造 `GenericStatisticsFile`（statisticsFile 自身的 snapshot-id）。

### `open-api/rest-catalog-open-api.yaml`（修改，+4/-1）

**修改目的**：REST 协议规范把 `snapshot-id` 改为可选并标记废弃。

**工作逻辑**：

- `SetStatisticsUpdate` 的 `required` 列表删除 `snapshot-id`（保留 `statistics`）；
- `snapshot-id` 字段加 `deprecated: true` 与描述：
  ```
  description:
    This optional field is **DEPRECATED for REMOVAL** since it contains redundant information.
    Clients should use the `statistics.snapshot-id` field instead.
  ```

### `open-api/rest-catalog-open-api.py`（修改，+5/-1）

**修改目的**：Python 模型同步把 `snapshot_id` 改为可选并加描述。

**工作逻辑**：

```
class SetStatisticsUpdate(BaseUpdate):
    action: str = Field('set-statistics', const=True)
    snapshot_id: Optional[int] = Field(
        None,
        alias='snapshot-id',
        description='This optional field is **DEPRECATED for REMOVAL** since it contains redundant information. Clients should use the `statistics.snapshot-id` field instead.',
    )
    statistics: StatisticsFile
```

`snapshot_id` 从必填 `int` 改为 `Optional[int]` 默认 `None`，描述与 yaml 一致。

## 小结

- **成效**：消除了 `SetStatisticsUpdate` 中 `snapshot-id` 与 `statistics.snapshot-id` 的冗余双源问题，统一以 `statistics.snapshot-id` 为唯一事实源；Java API、`MetadataUpdate`、`TableMetadata.Builder`、REST 协议、Python 模型同步提供新接口并标记旧接口废弃；旧客户端与服务端仍兼容（旧字段被忽略，旧 API 保留）。
- **影响范围**：
  - 公共 API：`UpdateStatistics.setStatistics(long, StatisticsFile)` 标 `@Deprecated`，新增 `setStatistics(StatisticsFile)`；
  - 内部类：`MetadataUpdate.SetStatistics` 双参构造器标 `@Deprecated`，新增单参构造器，删除 `snapshotId` 字段；
  - `TableMetadata.Builder.setStatistics` 同样双轨；
  - REST 协议：`snapshot-id` 从必填变可选 + deprecated，向后兼容；
  - 行为变化：服务端解析 `set-statistics` update 时不再读 `snapshot-id`，老客户端发的值被忽略；`SetStatistics.snapshotId()` 始终返回 `statisticsFile.snapshotId()`，避免不一致。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支若 `UpdateStatistics` / `MetadataUpdate.SetStatistics` / `TableMetadata.Builder.setStatistics` 仍是双参 + 必填 `snapshot-id`，可回迁此修复，但需注意：
    - 这是 API 弃用（deprecation）而非移除，1.4.x 回迁后老调用方仍可编译运行，只是收到 deprecation 警告；
    - REST 协议层把 `snapshot-id` 改为可选是"协议向前兼容"的变化（老客户端发的字段被忽略，新客户端可省略），1.4.x 上的 REST 服务端若回迁此 PR，需确认所有内部调用方（如 `SetStatistics` action）都改为走新重载；
    - `MetadataUpdate.SetStatistics` 删除 `snapshotId` 字段会改变序列化字段集——若 1.4.x 上有自定义 Kryo/Java 序列化路径依赖该字段，回迁后需重新验证；默认 Java 序列化因 `serialVersionUID` 未变且字段类型兼容（去掉一个 long 字段），通常仍能反序列化旧数据（字段被忽略），但建议测试；
    - 测试 `TestMetadataUpdateParser` 同步改一行，回迁时一并 cherry-pick；
    - OpenAPI yaml/py 是协议文档，1.4.x 上的 REST 客户端实现（pydantic 模型）回迁后老字段仍能解析（变为可选），新客户端可省略，兼容性良好。
