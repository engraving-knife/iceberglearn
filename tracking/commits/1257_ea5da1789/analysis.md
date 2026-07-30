# 提交 1257：AWS: Switch to base2 entropy in ObjectStoreLocationProvider for optimized S3 performance (#11112)

## 提交信息

- **序号**：1257 / 4088
- **哈希**：ea5da1789f3eb80fc3196bcdd787a95ac0c493ba
- **短哈希**：ea5da1789
- **日期**：2024-10-18（Fri Oct 18 16:48:00 2024 -0700）
- **作者**：Ozan Okumusoglu <ookumuso@amazon.com>；共同作者：Drew Schleit <aschleit@amazon.com>
- **提交说明**：AWS: Switch to base2 entropy in ObjectStoreLocationProvider for optimized S3 performance (#11112)
- **PR/Issue**：#11112

## 总体目的

Iceberg 的 `ObjectStoreLocationProvider` 用于在对象存储（尤其 S3）上为每个数据文件生成一个带「熵（hash）」前缀的路径，使文件均匀分散到多个键前缀下，规避 S3 对同一前缀的请求限流，从而最大化吞吐。此前实现用 murmur3_32 对文件名做哈希，取 4 字节做 base64url 编码，得到形如 `2d3905f8` 的 8 字符串作为单一目录前缀。

本提交对该熵生成方式做两项重大调整以优化 S3 性能：

1. **熵编码从 base64 改为 base2（二进制 0/1 字符串）**：取 20 位二进制作为熵。base2 的前缀字符空间只有 0/1 两种，相比 base64 的 64 种字符，能在 S3 通用型桶上获得更均匀、更可预测的自动分区扩展行为（S3 按对象键前缀做分区扩展，base2 的前几位即可形成均匀的若干分区根）。这直接改善 S3 的 IOPS 自动扩展。

2. **把熵切分为多级目录**：20 位熵按每 4 位一组切为 3 个目录 + 末尾 8 位（如 `0101/0110/1001/10110010`）。目录化熵后，Iceberg 的孤儿文件清理（orphan cleanup）可按目录分片并行遍历，显著提升清理效率——此前单目录下文件量大，遍历成本高。

同时新增表属性 `write.object-storage.partitioned-paths`（默认 `true`），允许用户将分区值从文件路径中剔除（设为 `false`）：Iceberg 的分区信息保存在 metadata 中，并不依赖路径里的分区值，剔除可进一步缩短对象键长度；此时末尾 8 位熵改为用 `-` 拼接到文件名前（如 `10110010-00000-...parquet`）。

注意：这是一项**行为变更**，会改变 `ObjectStoreLocationProvider` 生成的新文件路径布局，对存量表的新写入立即生效；已有文件路径不变（不会移动）。

## 如何达成设计目的

通过重写 `ObjectStoreLocationProvider` 的 `computeHash` 与 `newDataLocation` 逻辑达成：

- **base2 编码 + 保留前导零**：`Integer.toBinaryString` 会丢弃前导零，导致短哈希。用 `hashCode.asInt() | Integer.MIN_VALUE` 强制最高位置 1，再取末 20 位，保证固定 20 位长度且前导零不丢失。
- **目录化切分**：`dirsFromHash` 按 `ENTROPY_DIR_DEPTH=3` × `ENTROPY_DIR_LENGTH=4` 把前 12 位切成 3 个 4 位目录，剩余 8 位作为第 4 段（目录或拼到文件名）。
- **partitioned-paths 开关**：在 provider 构造时读取表属性，决定 `newDataLocation(spec, partitionData, filename)` 是否拼接分区路径，以及无 context 时末段熵是作为目录（`/hash/filename`）还是用 `-` 拼到文件名（`/hash-filename`）。
- **配套文档**：更新 aws.md 详述 base2 与目录化动机、新示例路径、partitioned-paths 选项；configuration.md 表格补一行属性说明。

## 修改详情

### `core/src/main/java/org/apache/iceberg/LocationProviders.java`

**修改目的**：重写 `ObjectStoreLocationProvider` 的熵生成与路径拼接逻辑。

**工作逻辑**：

- 移除 `BaseEncoding` 的 import 与 `BASE64_ENCODER`、`ThreadLocal<byte[]> TEMP` 常量。
- 新增常量：`HASH_BINARY_STRING_BITS = 20`（熵位数）、`ENTROPY_DIR_LENGTH = 4`（每段目录位数）、`ENTROPY_DIR_DEPTH = 3`（目录深度）。
- 新增字段 `boolean includePartitionPaths`，构造时从 `TableProperties.WRITE_OBJECT_STORE_PARTITIONED_PATHS` 读取（默认 true）。
- `newDataLocation(PartitionSpec, StructLike, filename)`：`includePartitionPaths` 为 true 时拼 `partitionToPath/filename`（保持旧行为），否则只用 `filename`（不含分区）。
- `newDataLocation(filename)`（无 context 分支，context==null）：`includePartitionPaths` 为 true 时输出 `storageLocation/hash/filename`；为 false 时输出 `storageLocation/hash-filename`（末段熵用 `-` 接到文件名前）。
- `computeHash(fileName)`：`HASH_FUNC.hashString(fileName, UTF_8)` 得 `HashCode`；`Integer.toBinaryString(hashCode.asInt() | Integer.MIN_VALUE)` 强制最高位置 1 以保留前导零；取末 20 位子串；调用 `dirsFromHash`。
- `dirsFromHash(hash)`：按 4 位一组循环 `ENTROPY_DIR_DEPTH` 次，每组前补 `/` 分隔形成 3 个目录；剩余位（第 13–20 位，共 8 位）再补一个 `/` 段。例如 20 位 `10011001100110011001` → `1001/1001/1001/10011001`。

### `core/src/main/java/org/apache/iceberg/TableProperties.java`

**修改目的**：新增 `write.object-storage.partitioned-paths` 表属性。

**工作逻辑**：新增常量 `WRITE_OBJECT_STORE_PARTITIONED_PATHS = "write.object-storage.partitioned-paths"` 与默认值 `WRITE_OBJECT_STORE_PARTITIONED_PATHS_DEFAULT = true`，注释说明「为 true 时在路径中包含分区值」。

### `core/src/test/java/org/apache/iceberg/TestLocationProvider.java`

**修改目的**：适配新路径布局并新增覆盖测试。

**工作逻辑**：

- 既有用例（无 context 路径）断言更新：`parts` 由 4 段变为 7 段（多了 3 个熵目录 + 末段），断言 elements(2,3,4,5)（熵目录）非空、element(6) 为文件名；删除一处多余空行。
- 新增 `testExcludePartitionInPath`：开启 object-storage 并设 `partitioned-paths=false`，断言生成路径以 `/data/0110/1010/0011/11101000-test.parquet` 结尾——验证分区值被剔除、末段熵用 `-` 拼到文件名前。
- 新增 `testHashInjection`：开启 object-storage，对文件名 a/b/c/d 断言熵分别为 `0101/0110/1001/10110010`、`1110/0111/1110/00000011`、`0010/1101/0110/01011111`、`1001/0001/0100/01110011`——固定期望值锁定 base2 + 目录化行为，防回归。

### `docs/docs/aws.md`

**修改目的**：更新 S3 性能优化章节文档。

**工作逻辑**：措辞微调（`shared and short` → `shared`）；示例 `write.data.path` 由 `s3://my-table-data-bucket` 改为带子目录 `s3://my-table-data-bucket/my_table`；把原「8 字符 base64 hash `2d3905f8`」示例改为「20 位 base2 hash `01010110100110110010`」并给出新路径示例 `s3://.../my_table/0101/0110/1001/10110010/category=orders/00000-....parquet`；新增段落说明 base2 与目录化的动机（S3 自动扩展、orphan 清理并行化）；新增 `write.object-storage.partitioned-paths=false` 的用法与示例路径（分区值剔除、末段熵用 `-` 接文件名）。

### `docs/docs/configuration.md`

**修改目的**：在表属性总表补一行。

**工作逻辑**：新增一行 `write.object-storage.partitioned-paths | true | Includes the partition values in the file path`。

## 小结

- **成效**：`ObjectStoreLocationProvider` 的熵从 base64（8 字符单目录）改为 base2（20 位二进制，切为 3 个 4 位目录 + 末 8 位），优化 S3 通用型桶的自动分区扩展与 orphan 清理并行效率；新增 `write.object-storage.partitioned-paths` 选项允许剔除分区值以缩短键长；测试与文档同步更新。
- **影响范围**：core 模块 2 个主代码文件（`LocationProviders.java`、`TableProperties.java`）、1 个测试文件、2 个文档文件，共约 123 行增改。属**运行时行为变更**：开启 object-storage 的表新写入文件路径布局变化，存量文件不动、不会被迁移。
- **回迁到 1.4.x 的注意事项**：**需谨慎评估，原则上不建议直接回迁**。这是一项改变文件路径生成策略的行为变更，回迁后 1.4.x 上开启 `ObjectStoreLocationProvider` 的表新写入文件会采用 base2+目录化路径，与回迁前的 base64 路径布局不一致——同一表内新旧文件路径风格混杂，虽不影响读取（路径都记在 metadata 中），但可能影响用户既有的下游工具（如按前缀做生命周期策略、按目录做并行清理脚本）。若 1.4.x 确有 S3 性能诉求需回迁，应：1) 同步回迁 `TableProperties` 新属性与 `LocationProviders` 逻辑及测试；2) 在 release notes 明确标注行为变更；3) 评估是否需要提供回退到 base64 的开关（本提交未提供回退开关，base64 路径已成历史）。此外 `testHashInjection` 中的固定期望值依赖 murmur3 的具体实现，回迁时需在 1.4.x 环境实跑确认一致。
