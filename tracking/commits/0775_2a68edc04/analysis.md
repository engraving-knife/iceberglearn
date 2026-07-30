# 提交 0775：Use a unique field-id for delete files elements (#10347)

## 提交信息

- **序号**：0775 / 4088
- **哈希**：2a68edc04e5e0426f449f099b0149c204a4c97e6
- **短哈希**：2a68edc04
- **日期**：2024-05-17 12:32:00 -0400
- **作者**：Farooq Qaiser
- **提交说明**：Use a unique field-id for delete files elements (#10347)
- **PR/Issue**：#10347

## 总体目的

这个提交修复了 Iceberg Kafka Connect 事件模块（`kafka-connect-events`）中 `DataWritten` 事件类的一个 field-id 重复问题。`DELETE_FILES_ELEMENT` 常量（删除文件列表中单个元素的 field-id）与 `DELETE_FILES` 常量（删除文件列表字段本身的 field-id）被错误地定义为相同的值 `10_304`，导致 Avro 序列化/反序列化时两个不同字段共享同一个 field-id，产生数据损坏或解析错误。本提交将 `DELETE_FILES_ELEMENT` 的 field-id 改为唯一值 `10_305`，确保事件 schema 中每个字段拥有全局唯一的 ID。

## 如何达成设计目的

### 设计背景：Iceberg Kafka Connect 事件与 Avro field-id

Iceberg 的 Kafka Connect 集成模块通过事件（events）机制传递写入操作的元信息。`DataWritten` 是其中一种事件 payload，表示"数据已写入"，它携带了写入的数据文件列表（`DATA_FILES`）和删除文件列表（`DELETE_FILES`）。这些事件使用 Avro 格式序列化，并通过 Avro reflection（反射）机制将 Java 类映射为 Avro schema。

在 Avro 中，每个字段由 name 和 field-id 标识。Iceberg 体系内（包括其事件 schema）遵循一个核心约定：每个字段的 field-id 必须在所属 schema 范围内唯一，这是 Iceberg schema 演进（schema evolution）的基础——字段通过稳定的 ID 而非名称或位置来标识，使得重命名、增删字段不会破坏前后兼容性。

`DataWritten` 类中定义了一组 `static final int` 常量作为各字段的 field-id，并通过 Avro reflection 注解（在相关工具类中引用这些常量）将其绑定到字段上：

```java
static final int DATA_FILES = 10_302;          // 数据文件列表字段
static final int DATA_FILES_ELEMENT = 10_303;  // 数据文件列表的单个元素
static final int DELETE_FILES = 10_304;        // 删除文件列表字段
static final int DELETE_FILES_ELEMENT = 10_304; // BUG: 与 DELETE_FILES 重复
```

这里 `DELETE_FILES` 与 `DELETE_FILES_ELEMENT` 都是 `10_304`。前者代表"删除文件列表"这一容器字段，后者代表列表中"单个删除文件元素"的字段（在 Avro 中，数组/列表类型会为元素本身分配一个 field-id）。二者语义不同但 ID 相同，违反了 field-id 唯一性约定。

### Bug 成因

这很可能是一个复制粘贴遗漏修改的错误：`DELETE_FILES_ELEMENT` 的定义是从 `DATA_FILES_ELEMENT`（10_303）的模式复制而来，但值被设成了与 `DELETE_FILES`（10_304）相同，而非递增为 10_305。

### 重复 field-id 的影响

在 Avro schema 中，如果两个不同字段共享同一 field-id，会导致：
- Avro schema 生成时出现歧义，序列化器/反序列化器可能无法正确区分两个字段。
- 事件数据的写入与读取不一致：一个 `DataWritten` 事件中携带的删除文件列表可能在序列化后无法被正确还原，或与 `DELETE_FILES` 字段本身混淆，造成数据丢失或错位。
- 使用 Iceberg Kafka Connect 进行 CDC（变更数据捕获）或增量同步的 pipeline，若涉及删除文件（即 position-based equality deletes 或 row-level deletes），事件传递可能出错。

### 修复逻辑

将 `DELETE_FILES_ELEMENT` 的值由 `10_304` 改为 `10_305`，使其在事件 schema 中唯一：

```java
static final int DELETE_FILES = 10_304;
static final int DELETE_FILES_ELEMENT = 10_305;  // FIXED: 唯一值
```

这是一个 1 字符级改动（`4` -> `5`），但语义重大。改动后，`DataWritten` 事件 schema 中所有 field-id 序列为：`10_302, 10_303, 10_304, 10_305`，全部唯一。

### 兼容性考量

由于这些 field-id 用于 Avro 序列化，改动 field-id 会改变生成的 Avro schema。但是：
- 该 bug 存在时事件本就无法被正确解析（field-id 冲突），因此不存在"依赖旧 field-id 的正确消费者"需要兼容的场景。
- 修复后，事件的序列化与反序列化才能正确工作，属于"使功能从损坏到可用"的修复，而非破坏性变更。
- Kafka Connect 事件模块是较新引入的功能，生产环境使用尚不广泛，修复时机合适。

## 修改详情

### `kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/DataWritten.java`

**修改目的**：为 `DELETE_FILES_ELEMENT` 分配唯一的 field-id。

**工作逻辑**：

`DataWritten` 类定义了 `DataWritten` 事件 payload 的字段常量。修改前，`DELETE_FILES_ELEMENT` 与 `DELETE_FILES` 同为 `10_304`；修改后，`DELETE_FILES_ELEMENT` 为 `10_305`，与相邻常量形成连续递增序列（`10_302` -> `10_303` -> `10_304` -> `10_305`），每个字段 ID 唯一。

```java
   static final int DELETE_FILES = 10_304;
-  static final int DELETE_FILES_ELEMENT = 10_304;
+  static final int DELETE_FILES_ELEMENT = 10_305;
```

这些常量通过 Avro reflection 被 `DataWritten` 的字段映射逻辑引用（如 `@AvroName`、自定义 `AvroSchema` 构建等），用于在序列化/反序列化时标识字段。修改常量值即改变了对应字段在 Avro schema 中的 field-id。

**diff 摘要**：1 file changed, 1 insertion(+), 1 deletion(-)。

## 小结

### 成效

修复了 `DataWritten` 事件中 `DELETE_FILES_ELEMENT` 与 `DELETE_FILES` field-id 重复的问题，使 Kafka Connect 事件中删除文件列表的序列化/反序列化能够正确工作。修复后事件 schema 的所有 field-id 唯一，符合 Iceberg schema 演进的 ID 唯一性约定，保障了涉及删除文件的 CDC/增量同步 pipeline 的数据正确性。

### 影响范围

改动仅涉及 Kafka Connect 事件模块的 `DataWritten` 类，不影响 Iceberg 核心或其他 Spark/Flink/REST 模块。影响对象为使用 Iceberg Kafka Connect 且产生/消费 `DataWritten` 事件（含删除文件信息）的 pipeline。

### 回迁注意事项

- 该修复改动极小（1 行），cherry-pick 无冲突风险，可直接回迁到 1.4.x 分支。
- 回迁前确认 1.4.x 分支的 `DataWritten.java` 中 `DELETE_FILES_ELEMENT` 是否仍为 `10_304`。若 1.4.x 已有其他改动调整了这些常量值，需确认新值 `10_305` 不与事件 schema 中其他 field-id 冲突。
- 由于修复改变了 Avro schema（field-id 变更），回迁后 1.4.x 分支的 Kafka Connect 事件与旧版本（含 bug 的版本）不兼容。但如前所述，含 bug 的版本本就无法正确处理删除文件事件，因此这种"不兼容"实际上是修复，不构成实际问题。
- 若 1.4.x 分支的 Kafka Connect 模块已有生产用户，回迁后建议升级所有事件生产者与消费者至含修复的版本，确保双方使用一致的 field-id。
- 注意检查 `kafka-connect-events` 模块中是否有其他事件类（如 `DataDeleted` 等）存在类似的 field-id 重复问题，可借此回迁一并排查。
