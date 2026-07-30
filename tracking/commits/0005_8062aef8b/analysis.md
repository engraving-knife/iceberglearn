# 提交 0005：Python: ManifestWriter and ManifestListWriter (#8622)

## 提交信息

- **序号**：0005 / 4088
- **哈希**：8062aef8b68ef18647ea10e01ff6e82a89582fe9
- **短哈希**：8062aef8b
- **日期**：2023-09-29 11:00:29 +0200
- **作者**：HonahX
- **提交说明**：Python: ManifestWriter and ManifestListWriter (#8622)
- **PR/Issue**：#8622

## 总体目的

这个提交为 Iceberg Python 实现了 Manifest 文件与 Manifest List 文件的写入能力，使 pyiceberg 从"只能读取 manifest"演进到"能写 manifest"，是 Python 端走向完整写入链路的关键一步。在此之前，pyiceberg 已经能通过 `AvroFile`/`read_manifest`/`read_manifest_list` 读取 Iceberg 表的 manifest 文件与 manifest list（即快照的 snap-*.avro 文件），但缺少对应的写入器，意味着 Python 引擎无法独立完成"提交快照"所需的元数据文件产出，必须依赖 Java/Spark 等其他引擎来写 manifest。本提交补全了这一能力。

Iceberg 的写入链路在元数据层面包含两个步骤：第一步是把若干 `ManifestEntry`（每个 entry 包装一个 `DataFile` 及其状态 ADDED/EXISTING/DELETED 与序列号信息）写入一个 manifest 文件（Avro 格式，schema 由 `MANIFEST_ENTRY_SCHEMA` 派生），同时收集每个分区字段的统计信息（min/max/contains_null/contains_nan）以填充 `ManifestFile.partitions`；第二步是把若干 `ManifestFile` 写入一个 manifest list 文件（即 snap-*.avro，schema 为 `MANIFEST_FILE_SCHEMA`），并为每个 manifest 分配/校验序列号。本提交对这两步分别实现了 `ManifestWriter` 与 `ManifestListWriter` 抽象基类，并为 Iceberg 表格式 v1 与 v2 各提供一个具体子类（`ManifestWriterV1`/`ManifestWriterV2`/`ManifestListWriterV1`/`ManifestListWriterV2`），通过工厂函数 `write_manifest`/`write_manifest_list` 按 `format_version` 选择具体实现。

v1 与 v2 的核心差异有三点：(1) v1 的 `DataFile` 结构包含 `block_size_in_bytes` 字段（field_id=105，v2 中已移除），v1 写入时需填默认值 `DEFAULT_BLOCK_SIZE = 64MB`，v2 不写该字段；(2) v2 的 manifest 元数据多一个 `content` 字段（data/deletes），v2 manifest list 元数据多一个 `sequence-number` 字段；(3) v2 引入序列号机制，`ManifestListWriterV2.prepare_manifest` 需要把 `UNASSIGNED_SEQ`(-1) 的 `sequence_number`/`min_sequence_number` 替换为本次提交的序列号并做合法性校验，而 v1 不处理序列号。本提交通过 `prepare_entry`/`prepare_manifest` 两个抽象方法把这些版本差异隔离到子类，基类只负责通用的计数、分区统计与 Avro 写出流程。

此外，本提交对 `DataFile` 类型做了重要重构：把原 `DATA_FILE_TYPE` 拆分为 `DATA_FILE_TYPE_V1`（含 block_size_in_bytes）与 `DATA_FILE_TYPE_V2`（不含），`DataFile.__init__` 增加 `format_version` 参数选择 struct；并新增 `data_file_with_partition`/`partition_field_to_data_file_partition_field`/`manifest_entry_schema_with_data_file_type` 等工具函数，根据分区 spec 动态生成 data_file 的 partition 子结构类型，并按 Iceberg 规范把 LongType/DateType/TimeType/TimestampType 等分区字段统一映射为 IntegerType（Iceberg 用 int 存储这些分区值的二进制表示）。配套新增 `PartitionFieldStats`/`construct_partition_summaries` 用于在写入过程中累积每个分区字段的统计并生成 `PartitionFieldSummary`。这些都是写 manifest 必备的、原 pyiceberg 缺失的能力。

## 如何达成设计目的

整体设计思路是"抽象基类 + 版本子类 + 工厂函数"。`ManifestWriter`（ABC）封装通用的计数器（added/existing/deleted files/rows）、分区收集、min data sequence number 跟踪、`add_entry`→`prepare_entry`→`writer.write_block` 流程，以及 `to_manifest_file` 产出 `ManifestFile`；抽象方法 `content`/`new_writer`/`prepare_entry` 由 `ManifestWriterV1`/`ManifestWriterV2` 实现版本差异。`ManifestListWriter`（ABC）封装 `add_manifests`→`prepare_manifest`→`write_block` 流程，`prepare_manifest` 由 v1/v2 子类实现（v1 仅校验 content 不为 deletes，v2 处理序列号赋值）。工厂函数 `write_manifest(format_version, ...)`/`write_manifest_list(format_version, ...)` 按 format_version 实例化对应子类。两个 writer 都实现上下文管理器协议（`__enter__`/`__exit__`），底层委托给 `AvroOutputFile`。

## 修改详情

### `python/pyiceberg/manifest.py`

**修改目的**：实现 ManifestWriter 与 ManifestListWriter 及配套的 DataFile 类型重构、分区统计、版本差异处理。

**工作逻辑**：

1. 新增常量 `UNASSIGNED_SEQ = -1`（表示序列号未分配）、`DEFAULT_BLOCK_SIZE = 67108864`（64MB，v1 写 block_size_in_bytes 的默认值）。

2. `DATA_FILE_TYPE` 拆分为 `DATA_FILE_TYPE_V1` 与 `DATA_FILE_TYPE_V2`：
   - `DATA_FILE_TYPE_V1` 在原结构基础上新增 field_id=105 `block_size_in_bytes`（LongType, required=False, doc 标注 "Deprecated. Always write a default in v1. Do not write in v2."）。
   - `DATA_FILE_TYPE_V2` 通过列表推导从 V1 中剔除 field_id=105 得到。
   - `MANIFEST_ENTRY_SCHEMA` 中 `data_file` 字段类型由 `DATA_FILE_TYPE` 改为 `DATA_FILE_TYPE_V1`。

3. 新增 `partition_field_to_data_file_partition_field`（`@singledispatch`）：把分区字段类型映射为 data_file 中 partition 子结构用的类型。默认实现抛 TypeError；为 `LongType`/`DateType`/`TimeType`/`TimestampType`/`TimestamptzType` 注册的实现返回 `IntegerType()`（Iceberg 规范：这些类型在 manifest 的 partition 结构里以 int 编码存储）；为 `PrimitiveType` 注册的实现返回原类型（其他 primitive 类型如 StringType/BinaryType/BooleanType 直接保留）。非 primitive 分区类型会触发默认实现的 TypeError。

4. 新增 `data_file_with_partition(partition_type, format_version)`：根据分区 spec 的类型构造一个完整的 data_file StructType，其中 field_id=102 的 `partition` 字段类型替换为基于 `partition_type` 构造的 `data_file_partition_type`（每个分区字段用上面的映射函数转换类型），其余字段沿用 `DATA_FILE_TYPE_V1.fields`（v1）或 `DATA_FILE_TYPE_V2.fields`（v2）。这样写入时 Avro schema 与实际 partition 数据形状匹配。

5. 新增 `manifest_entry_schema_with_data_file(data_file)`：把 `MANIFEST_ENTRY_SCHEMA` 中 field_id=2 的 `data_file` 字段类型替换为传入的 data_file StructType，返回新 Schema。用于在 `new_writer` 中根据当前 spec 动态构造写入 schema。

6. `DataFile` 类改动：
   - `__slots__` 增加 `block_size_in_bytes`，类注解增加 `block_size_in_bytes: Optional[int]`。
   - `__init__` 增加 `format_version: Literal[1, 2] = 1` 参数（注意是位置参数放在 *args 之前，调用时需 keyword 传），根据 version 选择 `DATA_FILE_TYPE_V1` 或 `DATA_FILE_TYPE_V2` 作为 struct。

7. 新增 `PartitionFieldStats` 类：用于在写入过程中累积单个分区字段的统计。`update(value)` 区分 None（设 contains_null）、float NaN（设 contains_nan）、其他值（更新 min/max）；`to_summary()` 用 `to_bytes(self._type, ...)` 把 min/max 序列化为字节串构造 `PartitionFieldSummary`（与读取侧的 lower_bound/upper_bound 字节表示一致）。

8. 新增 `construct_partition_summaries(spec, schema, partitions)`：根据 spec 与 schema 取得分区字段类型列表，为每个字段建一个 `PartitionFieldStats`，遍历所有 partition Record 更新统计，最后返回 `List[PartitionFieldSummary]`。非 primitive 分区字段抛 ValueError。

9. `ManifestWriter`（ABC）：
   - 持有 spec/schema/output_file/snapshot_id/meta 与各计数器、`_min_data_sequence_number`、`_partitions`。
   - `__enter__` 调 `new_writer()` 创建 `AvroOutputFile[ManifestEntry]` 并 enter；`__exit__` 设 `closed=True` 并 exit 底层 writer。
   - `add_entry(entry)`：closed 时抛 RuntimeError；按 entry.status 累加对应计数器与 rows；把 `entry.data_file.partition` 收入 `_partitions`；对 ADDED/EXISTING 状态更新 min data sequence number；最后 `self._writer.write_block([self.prepare_entry(entry)])`。
   - `to_manifest_file()`：设 closed=True，返回 `ManifestFile`，其中 `manifest_length = len(self._writer.output_file)`、`sequence_number = UNASSIGNED_SEQ`（待 manifest list 阶段赋值）、`min_sequence_number = self._min_data_sequence_number or UNASSIGNED_SEQ`、`partitions = construct_partition_summaries(...)`、`key_metadatas=None`。
   - 抽象方法：`content()`/`new_writer()`/`prepare_entry(entry)`。

10. `ManifestWriterV1`：
    - meta 含 schema/partition-spec/partition-spec-id/format-version=1。
    - `new_writer` 用 `data_file_with_partition(spec.partition_type(schema), 1)` 构造 v1 data_file 类型，再用 `manifest_entry_schema_with_data_file` 包成完整 schema，创建 `AvroOutputFile[ManifestEntry]`，record_name="manifest_entry"。
    - `prepare_entry`：拷贝 entry，把 `data_file.block_size_in_bytes` 设为 `DEFAULT_BLOCK_SIZE`（v1 必须写该字段）。

11. `ManifestWriterV2`：
    - meta 在 v1 基础上多 `content=data`。
    - `new_writer` 同上但 format_version=2。
    - `prepare_entry`：先校验 data_sequence_number 为 null 时的合法性（snapshot_id 必须匹配且 status 必须为 ADDED），然后用 `DataFile(format_version=2, ...)` 显式构造一个不含 block_size_in_bytes 的 v2 DataFile（避免 v1 struct 残留字段），再包成新 ManifestEntry。这一步通过显式字段拷贝确保 v2 写出的 data_file 严格符合 v2 schema。

12. `write_manifest(format_version, spec, schema, output_file, snapshot_id)` 工厂函数按 version 返回 V1/V2 实例，其他 version 抛 ValueError。

13. `ManifestListWriter`（ABC）：
    - 持有 output_file/meta/_writer。
    - `__enter__` 创建 `AvroOutputFile[ManifestFile](output_file, MANIFEST_FILE_SCHEMA, "manifest_file", meta)` 并 enter；`__exit__` 委托。
    - `add_manifests(manifest_files)`：`self._writer.write_block([self.prepare_manifest(m) for m in manifest_files])`。
    - 抽象方法 `prepare_manifest(manifest_file)`。

14. `ManifestListWriterV1`：
    - meta 含 snapshot-id/parent-snapshot-id/format-version=1。
    - `prepare_manifest`：仅校验 `manifest_file.content != DATA` 时抛 `ValidationError("Cannot store delete manifests in a v1 table")`（v1 不支持 delete manifest），原样返回。

15. `ManifestListWriterV2`：
    - meta 在 v1 基础上多 `sequence-number`。
    - 持有 `_commit_snapshot_id`/`_sequence_number`。
    - `prepare_manifest`：拷贝 manifest_file；若 `sequence_number == UNASSIGNED_SEQ`，校验 `added_snapshot_id == _commit_snapshot_id`（确保是本次提交新创建的 manifest），然后赋值为 `_sequence_number`；若 `min_sequence_number == UNASSIGNED_SEQ`，同样校验后赋值（表示 manifest 内所有 entry 都没序列号，用本次提交的序列号兜底）；返回修改后的 manifest_file。

16. `write_manifest_list(format_version, output_file, snapshot_id, parent_snapshot_id, sequence_number)` 工厂函数按 version 返回 V1/V2 实例。

### `python/pyiceberg/avro/writer.py`

**修改目的**：让 `StructWriter.write` 接受 `Record`（而非 `StructType`）作为参数类型，匹配实际写入对象。

**工作逻辑**：原签名 `write(self, encoder, val: StructType)` 改为 `write(self, encoder, val: Record)`。`StructType` 是类型定义，而实际写入的对象是 `Record`（`DataFile`/`ManifestEntry`/`ManifestFile` 都继承自 `Record`），原类型注解不准确。方法体不变，仍调用 `val.record_fields()` 取字段值列表。导入由 `from pyiceberg.types import StructType` 改为 `from pyiceberg.typedef import Record`。

### `python/pyiceberg/typedef.py`

**修改目的**：为 `Record.record_fields` 补充 docstring 说明。

**工作逻辑**：仅给 `record_fields` 方法加一行 docstring "Return values of all the fields of the Record class except those specified in skip_fields."（注：docstring 提到的 skip_fields 在该方法中并未实际实现，描述与实现略有出入，但本提交未改方法体）。

### `python/tests/avro/test_file.py`

**修改目的**：适配 DataFile 新增的 `block_size_in_bytes` 字段。

**工作逻辑**：在 `test_write_manifest_entry_with_iceberg_read_with_fastavro` 构造 `DataFile` 时补一行 `block_size_in_bytes=67108864`，使构造的 v1 DataFile 满足新 schema 要求。

### `python/tests/utils/test_manifest.py`

**修改目的**：为 `write_manifest`/`write_manifest_list` 新增参数化单元测试。

**工作逻辑**：
- 新增 `_verify_metadata_with_fastavro` 辅助函数：用 fastavro 读取 Avro 文件的 metadata，断言期望的 key/value 都存在且相等，用于校验写入器产出的 Avro 文件元数据正确。
- `test_write_manifest`（参数化 format_version=1/2）：从一个已生成的 manifest list 文件读取 snapshot→manifest_file→manifest_entries，构造测试 schema 与 spec，用 `write_manifest` 把 entries 重新写入临时文件；验证 `to_manifest_file()` 后再调用 `add_entry` 会抛 RuntimeError（closed）；用 fastavro 校验 Avro metadata（v1/v2 各自的 schema/partition-spec/format-version/content）；重新读取写入的 manifest entry，逐字段断言 status/snapshot_id/data_sequence_number/data_file 各字段（file_path/file_format/partition/record_count/file_size_in_bytes/block_size_in_bytes v1=64MB v2=None/column_sizes/value_counts/null_value_counts/nan_value_counts/lower_bounds/upper_bounds/key_metadata/split_offsets/equality_ids/sort_order_id）与原数据一致。
- `test_write_manifest_list`（参数化 format_version=1/2）：从一个已生成的 manifest list 读取 manifests，用 `write_manifest_list` 重新写入临时文件；用 fastavro 校验 metadata（v2 多 sequence-number）；重新读取后断言 manifest_file 的 manifest_length/partition_spec_id/content（v1=DATA v2=DELETES，因测试数据源 v2 含 delete manifest）/sequence_number/min_sequence_number/added_snapshot_id/各计数器/partitions[0] 的 contains_null/contains_nan/lower_bound/upper_bound，以及 fetch_manifest_entry 的关键字段。

### `python/tests/test_integration_manifest.py`（新增）

**修改目的**：新增集成测试，用真实 Iceberg REST catalog 与 docker 环境（local rest catalog + minio s3）验证 `write_manifest` 在 v2 表上的端到端正确性。

**工作逻辑**：
- 定义 `todict` 辅助函数：把 pyiceberg 对象递归转成可比较的 dict/list 结构（处理 LazyDict 转 key/value 列表、Enum 取 value、可迭代对象转列表、有 `__dict__` 对象转 dict），以便与 fastavro 读出的 dict 直接对比。
- `catalog` fixture：`load_catalog("local", type=rest, uri=http://localhost:8181, s3.endpoint=http://localhost:9000, s3.access-key-id=admin, s3.secret-access-key=password)`。
- `table_test_all_types` fixture：`catalog.load_table("default.test_all_types")`。
- `test_write_sample_manifest`（标记 `@pytest.mark.integration`）：取 `table_test_all_types` 当前 snapshot 的第一个 manifest 的第一个 entry，构造一个 v2 DataFile（显式 `format_version=2` 拷贝所有字段），用 `write_manifest(format_version=2, ...)` 写入临时 avro 文件；用 fastavro 读取该文件，断言 schema、partition-spec、partition-spec-id、format-version=2、content=data 等 metadata，并断言第一条记录的各字段值与原 entry 一致（通过 todict 对比）。该测试需要 docker 环境跑 rest catalog 与 minio，故标 integration，CI 中默认跳过。

## 小结

本提交为 pyiceberg 补全了 Manifest 与 Manifest List 的写入能力，通过抽象基类 + v1/v2 子类 + 工厂函数的分层设计隔离版本差异，并配套重构 DataFile 类型、新增分区统计与动态 schema 构造，使 Python 引擎能独立产出符合 Iceberg 规范的 manifest 元数据文件，是 pyiceberg 走向完整写入链路的关键一步。
