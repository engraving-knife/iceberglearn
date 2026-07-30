# 提交 1219：Spec: Add v3 types and type promotion (#10955)

## 提交信息

- **序号**：1219 / 4088
- **哈希**：67dc9e58cd57d953726677698e38975aac45908a
- **短哈希**：67dc9e58c
- **日期**：2024-10-09（Wed Oct 9 14:19:40 2024 -0700）
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Spec: Add v3 types and type promotion (#10955)
- **PR/Issue**：#10955

## 总体目的

本提交更新 Iceberg 表格式的核心规范文档 `format/spec.md`，落地 v3 版本的若干关键扩展：

1. **新增 `unknown` 原始类型**：一种"默认 / null 列类型"，用于 schema 演进场景下，当一个列的具体类型尚不明确时（例如某些引擎延迟解析列类型），先用 `unknown` 占位。`unknown` 列必须为 optional 且默认值为 null，不写入数据文件（在 Avro/Parquet/ORC 中均省略），读取时任何对应列必须被忽略并以 null 填充。`unknown` 可提升为任意类型。
2. **完善类型提升（type promotion）规则**：把原来简单的列表式规则（`int`→`long`、`float`→`double`、`decimal` 扩精度）改为按版本分列的表格，明确 v1/v2 与 v3+ 的差异，并新增 `date`→`timestamp`/`timestamp_ns` 的提升（v3+，不允许提升到 `timestamptz` 系列）。
3. **补充 bounds 类型推断规则**：因为 Iceberg 的 Avro manifest 不存储 lower/upper bounds 的类型，类型提升后既有 bounds 不会重写，需要按字节长度推断写入时的原始类型（例如 `long` 字段 4 字节 bounds 推断为原 `int`）。新增推断表格。
4. **补充分区字段与类型提升的约束**：当类型提升会改变分区 transform 的输出值时（例如 `bucket[N]` 对 `34` 与 `"34"` 哈希不同），不允许对该分区字段引用的列进行该提升。明确 `date`→`timestamp`/`timestamp_ns` 是受此约束的场景。
5. **同步更新各数据格式映射表、哈希规范表、JSON 序列化表、单值二进制序列化表、版本 3 兼容性说明**，把 `unknown` 类型补充进去。

这是 spec v3 的核心文档变更，为后续各引擎实现 v3 铺路。

## 如何达成设计目的

直接编辑 `format/spec.md` 一个文件，在以下各处增补内容：

- 版本 3 概述中补充 `unknown` 类型。
- 原始类型表新增 `unknown` 行。
- 默认值章节新增"`unknown` 列必须默认为 null"约束。
- 类型提升章节重写为表格形式，新增 bounds 推断表与分区约束说明。
- Avro / Parquet / ORC 数据类型映射表新增 `unknown` 行。
- 哈希规范表新增 `unknown` 行（always null）。
- JSON 序列化表新增 `unknown` 行。
- 单值二进制序列化表新增 `unknown` 行（Not supported）。
- 版本 3 兼容性说明把 `unknown` 加入新增类型列表。
- 文件末尾补一个换行符。

## 修改详情

### `format/spec.md`

#### 1. 版本 3 概述（第 48 行附近）

**修改目的**：在 v3 新数据类型列表中补充 `unknown`。

**工作逻辑**：将 "* New data types: nanosecond timestamp(tz)" 改为 "* New data types: nanosecond timestamp(tz), unknown"。

#### 2. 原始类型表（第 184 行附近）

**修改目的**：在原始类型表首行新增 `unknown`。

**工作逻辑**：新增一行：

| Added by version | Primitive type | Description | Requirements |
|---|---|---|---|
| [v3](#version-3) | **`unknown`** | Default / null column type used when a more specific type is not known | Must be optional with `null` defaults; not stored in data files |

#### 3. 默认值章节（第 221 行附近）

**修改目的**：明确 `unknown` 列的默认值约束。

**工作逻辑**：在"write-default 行为"段落后新增一段："All columns of `unknown` type must default to null. Non-null values for `initial-default` or `write-default` are invalid."

#### 4. 类型提升章节（第 230 行附近，核心改动）

**修改目的**：把列表式提升规则改为按版本分列的表格，新增 bounds 推断与分区约束。

**工作逻辑**：

- 把原来的：
  ```
  Valid type promotions are:
  * int to long
  * float to double
  * decimal(P, S) to decimal(P', S) if P' > P
  ```
  替换为标题 "Valid primitive type promotions are:" 加表格：

  | Primitive type | v1, v2 valid type promotions | v3+ valid type promotions | Requirements |
  |---|---|---|---|
  | `unknown` | | _any type_ | |
  | `int` | `long` | `long` | |
  | `date` | | `timestamp`, `timestamp_ns` | Promotion to `timestamptz` or `timestamptz_ns` is **not** allowed; values outside the promoted type's range must result in a runtime failure |
  | `float` | `double` | `double` | |
  | `decimal(P, S)` | `decimal(P', S)` if `P' > P` | `decimal(P', S)` if `P' > P` | Widen precision only |

- 新增 bounds 类型推断说明段：解释 Avro manifest 不存储 bounds 类型，类型提升不重写 bounds，需按字节长度推断写入时原始类型，给出推断表：

  | Current type | Length of bounds | Inferred type at write time |
  |---|---|---|
  | `long` | 4 bytes | `int` |
  | `long` | 8 bytes | `long` |
  | `double` | 4 bytes | `float` |
  | `double` | 8 bytes | `double` |
  | `timestamp` | 4 bytes | `date` |
  | `timestamp` | 8 bytes | `timestamp` |
  | `timestamp_ns` | 4 bytes | `date` |
  | `timestamp_ns` | 8 bytes | `timestamp_ns` |
  | `decimal(P, S)` | _any_ | `decimal(P', S)`; `P' <= P` |

- 新增分区字段约束说明：类型提升若改变分区 transform 输出值则不允许；以 `bucket[N]` 对 `34` 与 `"34"` 哈希不同为例说明，列举受影响场景：`date` → `timestamp`/`timestamp_ns`。

#### 5. Avro 数据类型映射表（第 973 行附近）

**修改目的**：补充 `unknown` 的 Avro 映射。

**工作逻辑**：新增一行 `| **unknown** | null or omitted | |`。

#### 6. Parquet 数据类型映射表（第 1027 行附近）

**修改目的**：补充 `unknown` 的 Parquet 映射与读取行为。

**工作逻辑**：新增一行 `| **unknown** | None | | Omit from data files |`，并在表后新增说明："When reading an `unknown` column, any corresponding column must be ignored and replaced with `null` values."

#### 7. ORC 数据类型映射表（第 1119 行附近）

**修改目的**：补充 `unknown` 的 ORC 映射。

**工作逻辑**：新增一行 `| **unknown** | None | | Omit from data files |`。

#### 8. 哈希规范表（第 1150 行附近）

**修改目的**：补充 `unknown` 的哈希行为。

**工作逻辑**：新增一行 `| **unknown** | always null | |`（即 `unknown` 类型在 bucket 等哈希场景下始终按 null 处理）。

#### 9. JSON 序列化表（第 1299 行附近）

**修改目的**：补充 `unknown` 的 JSON 表示。

**工作逻辑**：新增一行 `| **unknown** | JSON string: "unknown" | "unknown" |`。

#### 10. 单值二进制序列化表（第 1352 行附近）

**修改目的**：补充 `unknown` 的单值序列化行为。

**工作逻辑**：新增一行 `| **unknown** | Not supported |`（`unknown` 不支持单值二进制序列化，因为不存储数据）。

#### 11. 版本 3 兼容性说明（第 1355 行附近）

**修改目的**：把 `unknown` 加入 v3 新增类型列表。

**工作逻辑**：将 "Types `timestamp_ns` and `timestamptz_ns` are added in v3." 改为 "Types `unknown`, `timestamp_ns`, and `timestamptz_ns` are added in v3."。

#### 12. 文件末尾

**修改目的**：补换行符。

**工作逻辑**：文件末尾新增一个空行（修复 `No newline at end of file`）。

## 小结

- **成效**：Iceberg 规范文档完整定义了 v3 的 `unknown` 类型、按版本分列的类型提升规则、bounds 类型推断规则、分区字段与类型提升的约束，以及各数据格式（Avro/Parquet/ORC）、哈希、JSON、单值序列化对 `unknown` 的处理。这为各引擎实现 v3 奠定了规范基础。
- **影响范围**：仅 `format/spec.md` 一个文档文件，新增 42 行、删除 7 行。无代码改动。但该规范变更会影响后续 Iceberg Java 实现及各引擎（Spark/Flink/Trino 等）的 v3 实现路径。
- **回迁到 1.4.x 的注意事项**：规范文档本身可以低成本回迁（仅文档），但 1.4.x 作为维护分支，其代码实现并不支持 v3 spec（v3 支持需要 1213 默认值 API、1217 InternalReader 等大量基础设施配合）。**单独回迁规范文档意义不大**，反而可能让用户误以为 1.4.x 已支持 v3。若 1.4.x 不打算完整支持 v3，**不建议回迁**此规范变更。规范文档由 main 分支统一维护，1.4.x 保持其发布时的规范版本即可。
