# 提交 0032：Build: Bump to Avro 1.11.3 (#8587)

## 提交信息

- **序号**：0032 / 4088
- **哈希**：1413984e50de7e69522dd15d6bdbc76e253a1451
- **短哈希**：1413984e5
- **日期**：2023-10-11 09:01:27 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Build: Bump to Avro 1.11.3 (#8587)
- **PR/Issue**：#8587

## 总体目的

这个提交把 Iceberg 依赖的 Apache Avro 版本从 1.11.1 升级到 1.11.3。Avro 是 Iceberg 的核心底层依赖之一：Iceberg 用 Avro 格式存储 manifest 文件、manifest list（snap-*.avro）以及 metadata.json 之外的二进制元数据文件，Iceberg 自身的 `org.apache.iceberg.avro.UUIDConversion` 等类也直接继承/调用 Avro 的 `Conversion<T>` API。因此 Avro 的版本升级属于影响面较广的基础库升级。

升级到 1.11.3 的主要动机是安全：Avro 1.11.2 与 1.11.3 是 1.11.x 系列的维护版本，其中 1.11.3 修复了 CVE-2023-39410（Avro 在反序列化时存在信息泄露/拒绝服务风险，需要解析不可信 Avro 数据的场景受影响）。Iceberg 在读取 manifest/metadata 时会反序列化 Avro 数据，若数据来源不可信（例如共享存储上的恶意文件），则受该漏洞影响，因此升级到修复版本具有实际安全意义。除安全修复外，1.11.2/1.11.3 也包含若干 bugfix 与改进。

由于是同一次版本号（1.11.x）内的升级，按理应保持二进制兼容，但本次升级带来一处 API 上的泛型签名变化：`org.apache.avro.Conversion<T>::toEnumSymbol(...)` 的返回类型由裸 `GenericEnumSymbol` 变为带通配符的 `GenericEnumSymbol<?>`。Iceberg 的 `UUIDConversion` 继承了该方法，因此 revapi（Palantir 的二进制兼容性检查工具）会把这个签名变化报告为 `java.method.returnTypeTypeParametersChanged` 的 breaking change。提交在 `.palantir/revapi.yml` 中把这一条加入 `acceptedBreaks`，标注 justification 为 "Generic has been added"，表示这是 Avro 上游加泛型参数导致的、可接受的源兼容改动，不影响实际使用。

## 如何达成设计目的

整体思路是"升级版本号 + 接受兼容性差异"。第一步在 [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml) 中把 `avro` 版本引用从 `1.11.1` 改为 `1.11.3`，所有通过 `version.ref = "avro"` 引用该版本的 library 坐标（如 `org.apache.avro:avro`）会一并升级。第二步在 [.palantir/revapi.yml](file:///Users/fengxiaohang/trae/iceberglearn/.palantir/revapi.yml) 的 `acceptedBreaks` 列表中追加一条记录，把 revapi 报告的 `toEnumSymbol` 返回类型泛型变化标记为已知且可接受，避免 CI 因兼容性检查失败而阻断升级。

## 修改详情

### [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml)

**修改目的**：将 Avro 依赖版本从 1.11.1 升级到 1.11.3。

**工作逻辑**：把 `[versions]` 段中的 `avro = "1.11.1"` 改为 `avro = "1.11.3"`。该版本号被 `[libraries]` 段的 `avro-avro = { module = "org.apache.avro:avro", version.ref = "avro" }` 引用，从而统一升级所有使用 Avro 的模块。注意 Flink 相关的 `flink-*-avro` 坐标使用各自独立的 `flink1xx` 版本号，不受本次升级影响。

### [.palantir/revapi.yml](file:///Users/fengxiaohang/trae/iceberglearn/.palantir/revapi.yml)

**修改目的**：把 Avro 1.11.3 引入的一处二进制兼容性差异加入已接受破坏列表，使 revapi 兼容性检查通过。

**工作逻辑**：在 `acceptedBreaks` 下列表中追加一条：

- `code: "java.method.returnTypeTypeParametersChanged"`
- `old`: `method org.apache.avro.generic.GenericEnumSymbol org.apache.avro.Conversion<T>::toEnumSymbol(T, org.apache.avro.Schema, org.apache.avro.LogicalType) @ org.apache.iceberg.avro.UUIDConversion`
- `new`: `method org.apache.avro.generic.GenericEnumSymbol<?> org.apache.avro.Conversion<T>::toEnumSymbol(T, org.apache.avro.Schema, org.apache.avro.LogicalType) @ org.apache.iceberg.avro.UUIDConversion`
- `justification: "Generic has been added"`

差别仅是返回类型由裸 `GenericEnumSymbol` 变为 `GenericEnumSymbol<?>`（Avro 上游为该返回类型补全了泛型参数）。由于这是上游加通配符泛型、调用方代码仍可编译运行，故标记为可接受。`UUIDConversion` 是 Iceberg 自定义的 Avro `Conversion<String>`，用于把 UUID 在 Avro schema 的 `uuid` logical type 与 Java `String` 之间转换，正是 Iceberg 写 manifest 时会用到的转换器。

## 小结

本提交把 Avro 从 1.11.1 升级到 1.11.3，主要带来 CVE-2023-39410 等安全修复与 bugfix，并通过在 revapi 配置中接受一处上游引入的返回类型泛型变化（`GenericEnumSymbol` → `GenericEnumSymbol<?>`）使兼容性检查保持绿色，是 Iceberg 基础库安全维护的常规且必要的一次升级。
