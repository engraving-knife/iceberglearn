# 提交 1439：Kafka Connect: Add config to prefix the control consumer group (#11599)

## 提交信息

- **序号**：1439 / 4088
- **哈希**：9288d987f6a62b54dbe3cb7bdf61f896e7a8b0b5
- **短哈希**：9288d987f
- **日期**：2024-11-27（Wed Nov 27 15:47:51 2024 +0100）
- **作者**：Hugo Friant <hugofriant@gmail.com>
- **提交说明**：Kafka Connect: Add config to prefix the control consumer group (#11599)
- **PR/Issue**：#11599
- **作用模块**：`kafka-connect/kafka-connect`（主代码）+ `docs/docs/kafka-connect.md`（文档）

## 总体目的

Iceberg 的 Kafka Connect sink connector 内部维护一个「control channel」（控制通道），用于在多个 connector worker 之间协调提交（commit）等控制逻辑。该控制通道使用一个临时的 Kafka consumer group 来消费控制 topic（默认 `control-iceberg`），其 group ID 由代码中硬编码的常量前缀 `DEFAULT_CONTROL_GROUP_PREFIX = "cg-control-"` 加上一个随机 UUID 拼接而成（例如 `cg-control-<uuid>`），且明确「不提交 offset」（pass transient consumer group ID to which we never commit offsets）。

这种硬编码前缀在多租户或多 connector 共享同一 Kafka 集群的场景下会带来问题：

- 不同租户或不同部署的 connector 共享同一个 `cg-control-` 前缀，无法从 group ID 上区分分属哪个部署，不利于监控、审计与隔离；
- 当多个 connector 实例指向同一 Kafka 集群但分属不同团队/环境时，难以通过 group ID 前缀做命名空间隔离或 ACL 控制；
- 用户无法按自身命名规范定制 group ID 前缀。

本提交新增一个配置项 `iceberg.control.group-id-prefix`，允许用户自定义控制 consumer group 的前缀（默认值仍为 `cg-control`，保持向后兼容），让用户能在多租户/多环境下做更好的命名空间隔离与治理。

## 如何达成设计目的

按 Kafka Connect 标准的 `ConfigDef` 配置定义模式：

1. **配置定义**：在 `IcebergSinkConfig`（继承 `AbstractConfig`）中新增静态常量 `CONTROL_GROUP_ID_PREFIX_PROP = "iceberg.control.group-id-prefix"`，并在 `configDef.define(...)` 中注册：类型 `STRING`、默认值 `DEFAULT_CONTROL_GROUP_PREFIX`（即 `"cg-control-"`，沿用既有常量）、重要性 `LOW`、描述「Prefix of the control consumer group」；
2. **读取访问器**：新增 `public String controlGroupIdPrefix()` 方法，返回 `getString(CONTROL_GROUP_ID_PREFIX_PROP)`；
3. **使用点替换**：在 `Worker` 构造函数中，把原本直接引用 `IcebergSinkConfig.DEFAULT_CONTROL_GROUP_PREFIX + UUID.randomUUID()` 改为 `config.controlGroupIdPrefix() + UUID.randomUUID()`，让前缀可被配置覆盖；
4. **文档登记**：在 `docs/docs/kafka-connect.md` 的配置表格中新增一行说明该配置项。

这样既保持了默认行为不变（默认前缀仍是 `cg-control-`），又允许用户按需定制。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConfig.java`

**修改目的**：定义 `iceberg.control.group-id-prefix` 配置项并提供访问器。

**工作逻辑**：
- 新增静态常量（紧随 `CONTROL_TOPIC_PROP` 之后）：
  ```java
  private static final String CONTROL_GROUP_ID_PREFIX_PROP = "iceberg.control.group-id-prefix";
  ```
- 在 `configDef.define(...)` 链中、`CONTROL_TOPIC_PROP` 定义之后追加：
  ```java
  configDef.define(
      CONTROL_GROUP_ID_PREFIX_PROP,
      ConfigDef.Type.STRING,
      DEFAULT_CONTROL_GROUP_PREFIX,
      Importance.LOW,
      "Prefix of the control consumer group");
  ```
  复用既有常量 `DEFAULT_CONTROL_GROUP_PREFIX = "cg-control-"` 作为默认值；
- 新增访问器（紧随 `controlTopic()` 之后）：
  ```java
  public String controlGroupIdPrefix() {
    return getString(CONTROL_GROUP_ID_PREFIX_PROP);
  }
  ```

注意：`DEFAULT_CONTROL_GROUP_PREFIX` 常量在提交前已存在（值为 `"cg-control-"`，定义于第 96 行，且为 `public static final`），本提交未修改该常量本身，只是把使用点从直接引用常量改为通过配置访问器。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Worker.java`

**修改目的**：让控制 consumer group 前缀可被配置覆盖。

**工作逻辑**：在 `Worker` 构造函数中，把 group ID 拼接从：
```java
IcebergSinkConfig.DEFAULT_CONTROL_GROUP_PREFIX + UUID.randomUUID(),
```
改为：
```java
config.controlGroupIdPrefix() + UUID.randomUUID(),
```
其余参数（`config`、`clientFactory`、`context`）不变。注意原代码注释「pass transient consumer group ID to which we never commit offsets」依然成立——group ID 仍是「前缀 + 随机 UUID」的临时形态，不提交 offset，仅前缀来源从硬编码变为可配置。

### `docs/docs/kafka-connect.md`

**修改目的**：在配置表格中登记新配置项。

**工作逻辑**：在控制 topic 配置行之后插入一行：

| iceberg.control.group-id-prefix            | Prefix for the control consumer group, default is `cg-control`                                                   |

注意文档中描述的默认值写为 `cg-control`（不带尾随 `-`），与代码中实际默认值 `DEFAULT_CONTROL_GROUP_PREFIX = "cg-control-"`（带尾随 `-`）略有差异——文档侧重表达「前缀」概念，实际拼接时仍会加上 `-` 分隔符与 UUID。

## 小结

- **成效**：新增 `iceberg.control.group-id-prefix` 配置项（默认 `cg-control-`，重要性 `LOW`），允许用户自定义 Kafka Connect 控制通道 consumer group 的前缀，便于在多租户/多环境下做命名空间隔离、监控与 ACL 治理；默认行为不变，向后兼容。共 3 个文件、约 13 新增/1 删除。
- **影响范围**：`kafka-connect/kafka-connect` 主代码 2 个文件（`IcebergSinkConfig` 加配置定义与访问器、`Worker` 替换使用点）与文档 1 个文件。属功能扩展，配置默认值保持原行为，不影响既有部署。
- **回迁到 1.4.x 的注意事项**：本提交是 Kafka Connect connector 的功能扩展，回迁风险较低且有明显价值（多租户治理场景）。回迁前需确认：
  - 1.4.x 的 `kafka-connect` 模块是否已存在 `IcebergSinkConfig`、`Worker` 与 `DEFAULT_CONTROL_GROUP_PREFIX` 常量；若结构一致，可直接回迁；
  - 1.4.x 的 `ConfigDef` 注册与 `AbstractConfig` 用法若与 main 有差异（如配置定义风格），需按 1.4.x 既有写法对齐；
  - 文档默认值描述（`cg-control` vs `cg-control-`）的细微差异在回迁时可一并修正，避免用户误解；
  - 该配置属纯加项（新增可选配置，不改变默认行为），与 1.4.x 既有功能不冲突，可安全回迁。
