# 提交 1713：Spec: Support geo type (#10981)

## 提交信息

- **序号**：1713 / 4088
- **哈希**：20b18acb77847153c743bbd63e799f5c61a71d82
- **短哈希**：20b18acb7
- **日期**：2025-02-10 11:56:15 -0800
- **作者**：Szehon Ho
- **提交说明**：Spec: Support geo type (#10981)
- **PR/Issue**：#10981

## 总体目的

在 Iceberg 规范（spec）中引入地理空间数据类型支持，新增 `geometry(C)` 和 `geography(C, A)` 两种原始类型。这是 Iceberg 规范 v3 的重要扩展，使 Iceberg 能够原生存储和处理地理空间数据，而无需将几何数据以普通二进制方式存储。

地理空间数据在 GIS（地理信息系统）、位置服务、空间分析等领域非常重要。传统做法是将几何数据以 WKB（Well-Known Binary）格式存储为 `binary` 类型，但这种方式无法在规范层面表达坐标系、边缘插值算法等语义信息。通过引入专门的 `geometry` 和 `geography` 类型，Iceberg 规范可以：

1. 明确数据的坐标系（CRS），默认为 `OGC:CRS84`（WGS84 经纬度）。
2. 对 `geography` 类型指定边缘插值算法（如球面插值、Vincenty 公式等）。
3. 在文件级别支持边界框（bounding box）统计信息，用于空间过滤优化。
4. 在 Avro、Parquet、ORC 等文件格式中映射到对应的原生类型。

此提交仅修改规范文档，不包含实现代码。

## 如何达成设计目的

通过全面修改 `format/spec.md` 规范文档，在以下各个层面引入 geo 类型支持：

1. **原始类型定义**：在原始类型表中新增 `geometry(C)` 和 `geography(C, A)` 类型。
2. **CRS 参数说明**：新增 CRS（坐标参考系统）小节，定义默认值和自定义格式。
3. **边缘插值算法**：新增边缘插值算法小节，定义 geography 类型的可选算法。
4. **分区变换**：更新 `identity` 变换的源类型排除列表，加入 geometry 和 geography。
5. **文件级统计**：新增 geometry/geography 类型的 lower_bounds/upper_bounds 边界框编码说明。
6. **序列化规范**：在 Avro、Parquet、ORC 三种文件格式的类型映射表中加入 geo 类型。
7. **JSON 序列化**：在 JSON 类型序列化和单值序列化表中加入 geo 类型。
8. **二进制单值序列化和边界序列化**：定义 geo 类型的二进制编码方式。
9. **附录 G**：新增地理空间注释附录，引用 OGC Simple Feature Access 标准。

## 修改详情

### `format/spec.md`（修改, +60/-2 lines）

**修改目的**：在 Iceberg 规范中全面引入 geometry 和 geography 地理空间类型。

**工作逻辑**：

1. **原始类型表新增（第 219-220 行附近）**：在 v3 原始类型中新增 `geometry(C)` 和 `geography(C, A)`。geometry 由 CRS 参数 C 参数化，默认 `OGC:CRS84`；geography 由 CRS 参数 C 和边缘插值算法 A 参数化，默认 C 为 `OGC:CRS84`，A 为 `spherical`。引用 OGC Simple Feature Access 标准。

2. **CRS 小节**：新增 CRS 参数说明，定义默认值 `OGC:CRS84`（WGS84 经纬度），支持自定义 CRS 格式 `type:identifier`，type 可以是 `srid`（空间参考标识符）或 `projjson`（PROJJSON 格式，存储在表属性中）。对 geography 类型，自定义 CRS 必须是地理坐标系，经度范围 [-180, 180]，纬度范围 [-90, 90]。

3. **边缘插值算法小节**：为 geography 类型定义可选的边缘插值算法：`spherical`（球面大地线）、`vincenty`（Vincenty 公式）、`thomas`、`andoyer`、`karney`（Karney 算法）。

4. **分区变换更新（第 494 行）**：将 `identity` 变换的源类型从 "Any except for `variant`" 改为 "Any except for `geometry`, `geography`, and `variant`"，即 geo 类型不能用作分区字段。

5. **文件级统计说明（第 649 行附近）**：新增说明，geometry 和 geography 类型的 `lower_bounds` 和 `upper_bounds` 是包含 X, Y, Z, M 坐标的点，表示文件中所有对象的边界框。对 X 值，xmin 可能大于 xmax（跨越日期变更线的情况），此时匹配条件为 `x >= xmin OR x <= xmax`。对 geography 类型，坐标限制在 X[-180,180]、Y[-90,90] 范围内。

6. **Avro 类型映射（第 1207 行附近）**：geometry 和 geography 映射为 `bytes`，使用 WKB 格式。

7. **Parquet 类型映射（第 1264 行附近）**：geometry 映射为 `binary` / `GEOMETRY`，geography 映射为 `binary` / `GEOGRAPHY`，使用 WKB 格式。

8. **ORC 类型映射（第 1298 行附近）**：geometry 和 geography 都映射为 `binary`，通过 `iceberg.binary-type` 属性区分（`GEOMETRY`）。

9. **JSON 类型序列化（第 1396 行附近）**：geometry 序列化为 `{"type": "geometry", "crs": <C>}`，geography 序列化为 `{"type": "geography", "crs": <C>, "algorithm": <A>}`。

10. **二进制单值序列化（第 1523 行附近）**：修改了该小节的说明文字（从"用于 manifest 文件的 lower/upper bounds"改为通用的"存储单个二进制值"），并新增 geometry/geography 行，使用 WKB 格式。

11. **边界序列化小节（新增）**：新增 "Bound serialization" 小节，定义 geometry 和 geography 的边界值编码方式：单个点编码为 x:y:z:m 的 8 字节小端 IEEE 754 坐标值拼接。x 和 y 必选，z 和 m 可选。

12. **JSON 单值序列化（第 1585 行附近）**：geometry 和 geography 使用 WKT（Well-Known Text）表示，例如 `POINT (30 10)`。

13. **附录 G（新增）**：新增"Geospatial Notes"附录，引用 OGC Simple Feature Access 标准（1.2.1 版本），定义点的坐标顺序为 X（经度/easting）、Y（纬度/northing）、Z（高度，可选）、M（线性参考值，可选）。

## 小结

- **成效**：在 Iceberg 规范 v3 中引入了完整的地理空间类型支持，包括 geometry 和 geography 两种类型，定义了 CRS、边缘插值算法、文件格式映射、序列化方式和边界框统计等全套规范。
- **影响范围**：规范层面的重大变更，影响所有实现 Iceberg 规范 v3 的引擎和工具。但此提交仅修改规范文档，不包含代码实现。后续需要各模块（Core、Spark、Flink 等）的实现工作来支持这些新类型。
- **回迁到 1.4.x 的注意事项**：不建议回迁到 1.4.x 分支。geo 类型是 v3 规范的新特性，1.4.x 分支基于较早的规范版本，回迁此规范变更需要 1.4.x 已支持 v3 规范。此外，规范变更需要配套的代码实现才有意义，单独回迁规范文档可能导致文档与实现不匹配。如果 1.4.x 不计划支持 v3 的 geo 类型，则不应回迁。
