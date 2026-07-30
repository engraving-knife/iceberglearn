# 提交 3208：Spec: Introduce SQL UDF specification (#14117)

## 提交信息

- **序号**：3208 / 4088
- **哈希**：b35624e0d0b45f6dcee7a8c157c47c05593566dc
- **短哈希**：b35624e0d
- **日期**：2026-02-05
- **作者**：Yufei Gu
- **提交说明**：Spec: Introduce SQL UDF specification (#14117)
- **PR/Issue**：#14117

## 总体目的

Iceberg 已经为表（table）和视图（view）定义了标准化的、自包含的元数据格式，并通过目录（catalog）以原子方式切换元数据文件来管理对象。然而对于 SQL 用户自定义函数（UDF 标量函数与 UDTF 表函数），此前 Iceberg 并没有统一的元数据规范——不同引擎（Spark、Trino、Flink 等）各自管理函数定义，难以在目录间迁移、难以支持函数演进与回滚，也缺乏一致的跨引擎语义。本提交在 `format/` 目录下新增 `udf-spec.md`，正式引入"Iceberg UDF 规范"，填补这一空白。

规范的核心设计延续 Iceberg 表/视图元数据的既有原则：每个 UDF 由一个**自包含的元数据文件**表示，元数据文件不可变，任何修改（新定义、更新表示、改属性等）都生成新的元数据文件，目录通过原子切换元数据文件来更新某个标识符所指向的函数。这样 UDF 元数据可随目录迁移、可独立存在，并天然支持版本化与回滚——元数据文件内保留最近若干定义版本，无需外部状态即可回滚。

规范明确区分了标量函数（`udf`，返回单值）与表函数（`udtf`，返回多行多列、`return-type` 必须为 `struct`）。它定义了完整的元数据字段体系：UDF 元数据层（`function-uuid`、`format-version`，目前固定为 `1`、`definitions`、`definition-log`、`location`、`properties`、`secure`、`doc`）；每个 `definition` 代表一个函数签名（重载），由参数类型有序列表唯一标识；`definition` 下含若干 `version`，每个版本携带一组 `representation`（目前仅 `sql` 表示，含 `dialect` 与 `sql` 正文）。规范还规定 UDF 名字不存储在元数据中，而由目录负责名到元数据文件位置的映射，这与表/视图元数据的命名分离原则一致。

## 如何达成设计目的

整体思路是定义一套与表/视图元数据同构的 UDF 元数据模型，使函数对象能与表/视图享受相同的管理与演进能力。文档通过分层字段定义（UDF 元数据 → definition → parameter/version → representation）与 definition-log 版本日志实现版本化与回滚；通过基于 Iceberg 类型 JSON 表示的类型系统保证类型可移植；通过"函数调用约定与解析"一节把重载/类型匹配的责任交给引擎，仅给出推荐策略。此外附两个完整示例（重载标量 UDF、UDTF），使规范具备直接可参照的落地依据。

## 修改详情

### `format/udf-spec.md` (+352/-0)

**修改目的**：新增 Iceberg SQL UDF 元数据规范文档。

**工作逻辑**：
文档主体由若干章节构成：

- **Background and Motivation / Goals**：阐明动机——为标量与表 UDF 提供可移植、自包含、支持演进与回滚、跨引擎一致的元数据格式。

- **UDF Metadata**：顶层元数据字段表，含必需的 `function-uuid`（创建时一次性生成的 UUID）、`format-version`（须为 `1`）、`definitions`（函数定义列表）、`definition-log`（版本历史），以及可选的 `location`、`properties`、`secure`（默认 `false`，标记为 `true` 时引擎应防止敏感信息泄露）、`doc`。注释说明 `properties` 仅作提示、UDF 名不存于元数据。

- **Definition**：每个 `definition` 对应一个签名（如 `add_one(int)` 与 `add_one(float)`），由参数类型有序列表唯一标识，同一签名只能有一个 definition。字段含 `definition-id`、`parameters`、`return-type`（可为字符串或对象）、`return-nullable`、`versions`、`current-version-id`、`function-type`（`udf` 或 `udtf`，后者 `return-type` 须为 `struct`）、`doc`。

- **Parameter / Types / Definition ID**：参数含 `type`、`name`、`doc`，不支持变长参数与参数化签名。类型基于 Iceberg 类型 JSON 表示，原始与半结构化类型用字符串（`int`、`decimal(9,2)`、`variant`），嵌套类型 `list`/`map`/`struct` 用对象并要求特定字段。`definition-id` 由参数类型按规范派生，例如 `int,list<int>,struct<id:int,name:string>`，作为签名的规范化标识。

- **Definition Version / Null Input Handling**：`definition version` 表示某定义在某时点的具体实现，含 `version-id`（单调递增）、`representations`、`deterministic`（默认 `false`）、`on-null-input`（`return-null` 或 `call`，默认 `call`）、`timestamp-ms`。`on-null-input` 是查询引擎优化提示：`return-null` 时若任一入参为 NULL 则函数恒返回 NULL，引擎可据此下推谓词或跳过求值；`call` 时函数自行处理 NULL（如 `COALESCE`），引擎必须真正调用。

- **Representation / SQL Representation**：表示对象至少含 `type`，目前仅定义 `sql` 一种，字段为 `dialect`（如 `spark`、`trino`）与 `sql` 正文；一个 definition version 可含多种方言的 SQL 表示，但每种方言至多一个，且 `sql` 必须按 `parameters` 中声明的名字引用参数。

- **Definition Log**：记录某时刻各 definition 所选版本，字段为 `timestamp-ms` 与 `definition-versions`（`definition-id` 到 `version-id` 的映射列表），支撑回滚。

- **Function Call Convention and Resolution in Engines**：把定义选择交由引擎，给出推荐策略——优先精确匹配、必要时安全加宽、不安全/不直观转换要求显式 cast、按参数个数与位置/命名匹配等。

- **Appendix A / Appendix B**：给出两个完整 JSON 示例。A 为重载标量函数 `add_one`，含 `int` 与 `float` 两个 definition，`int` definition 含两个 version（version 1 的 trino SQL 为 `x + 2`，version 2 修正为 `x + 1` 并新增 spark 方言），`current-version-id` 指向 2，`definition-log` 记录三次版本演进，体现版本化与回滚能力。B 为 UDTF `fruits_by_color`，`return-type` 为 `struct<name:string,color:string>`、`function-type` 为 `udtf`，含 trino 与 spark 两种方言的 SQL 表示。

## 总结

本提交以一份完整的规范文档为 Iceberg 引入了标准化的 SQL UDF 元数据格式，使 UDF 对象获得与表/视图同等的可移植、自包含、可版本化、可回滚的管理能力，并为跨引擎一致的函数表示奠定基础。该规范为后续在 catalog 与各引擎中实现 UDF 注册、解析、演进提供了权威依据，是 Iceberg 向"统一目录治理表/视图/函数"演进的重要一步。
