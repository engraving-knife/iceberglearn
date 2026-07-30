# 提交 2904：Core: Add min-rows-requested to PlanTableScanRequest (#14614)

## 提交信息

- **序号**：2904 / 4088
- **哈希**：f09dc3c108b746852a3febda49978ae1546797e8
- **短哈希**：f09dc3c10
- **日期**：2025-11-21 12:56:24 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add min-rows-requested to PlanTableScanRequest (#14614)
- **PR/Issue**：#14614

## 总体目的

本提交是提交 2903（OpenAPI 规范新增 `min-rows-requested` 字段）的 Java 实现配对。2903 在 REST Catalog 的 OpenAPI 契约（`.yaml` / `.py`）中定义了 `PlanTableScanRequest` 的可选字段 `min-rows-requested`，作为客户端给服务端的"最少行数提示"；本提交则在 Java 侧把这个新字段真正落地到请求模型、JSON 序列化/反序列化器以及单元测试中，使 Iceberg 的 Java REST 客户端能够构造并发送该字段，服务端 `PlanTableScanRequest` 也能解析并持有该值。

具体动机与 2903 一致：在远程扫描规划场景下，客户端通过这个字段告诉服务端"我至少需要这么多行的扫描任务"，服务端可据此提前停止规划、减少返回的 `FileScanTask` 数量，降低网络与客户端开销。字段语义为提示而非强约束（服务端可返回更少或更多行），因此本实现只做"持有 + 透传 + 非负校验"，不在此层面对该字段施加更多业务逻辑——真正的"按 min-rows 提前停止规划"由后续服务端规划逻辑（`CatalogHandlers` 等）消费。

## 如何达成设计目的

整体思路是按既有的 `PlanTableScanRequest` 字段模式照搬：在请求 POJO 中新增 `minRowsRequested` 字段及其访问器、Builder 方法、`validate()` 中的非负校验和 `toString()` 输出；在 `PlanTableScanRequestParser` 中新增常量 `MIN_ROWS_REQUESTED = "min-rows-requested"`，在 `write`/`read` 两个方向上对该字段做条件序列化（仅在非 null 时写出）与 `getLongOrNull` 反序列化；在 `TestPlanTableScanRequestParser` 中新增非法值校验、不带该字段的 round-trip、带该字段的 round-trip 等测试，并把已有全字段用例补上 `withMinRowsRequested(23L)`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/requests/PlanTableScanRequest.java` (+24/-1 lines)

**修改目的**：在 `PlanTableScanRequest` POJO 中新增 `minRowsRequested` 字段及其访问器、Builder 方法、校验与 toString。

**工作逻辑**：
- 新增私有字段 `private final Long minRowsRequested;`（用 `Long` 装箱以允许 null，表示未设置）。
- 新增访问器 `public Long minRowsRequested() { return minRowsRequested; }`。
- 私有构造器签名末尾新增参数 `Long minRowsRequested`，并在构造体中 `this.minRowsRequested = minRowsRequested;`，随后调用 `validate()`。
- `validate()` 中新增非负校验：
  ```java
  if (null != minRowsRequested) {
    Preconditions.checkArgument(
        minRowsRequested >= 0L, "Invalid scan: minRowsRequested is negative");
  }
  ```
  仅在字段非 null 时校验，允许 0；负值会抛 `IllegalArgumentException("Invalid scan: minRowsRequested is negative")`。这与 2903 中"提示性字段"的定位一致——不做上界或更复杂校验，只防止明显的非法负值。
- `toString()` 中新增 `.add("minRowsRequested", minRowsRequested)`。
- Builder 新增字段 `private Long minRowsRequested;` 与 `withMinRowsRequested(Long rowsRequested)` 链式方法；`build()` 把该值传入构造器。

### `core/src/main/java/org/apache/iceberg/rest/requests/PlanTableScanRequestParser.java` (+8/-1 lines)

**修改目的**：让 `PlanTableScanRequest` 的 JSON 序列化/反序列化支持 `min-rows-requested` 字段。

**工作逻辑**：
- 新增常量 `private static final String MIN_ROWS_REQUESTED = "min-rows-requested";`，与 OpenAPI 中的连字符命名一致。
- 序列化（`write` 方向）：在 `STATS_FIELDS` 之后、`writeEndObject()` 之前新增
  ```java
  if (null != request.minRowsRequested()) {
    gen.writeNumberField(MIN_ROWS_REQUESTED, request.minRowsRequested());
  }
  ```
  仅在非 null 时写出，保证未设置该字段的请求序列化结果与旧版一致，向后兼容。
- 反序列化（`read` 方向）：用 `Long minRowsRequested = JsonUtil.getLongOrNull(MIN_ROWS_REQUESTED, json);` 读取，并在 Builder 链上 `.withMinRowsRequested(minRowsRequested)`。`getLongOrNull` 在字段类型不是数字（例如写成字符串 `"23"`）时会抛 `IllegalArgumentException("Cannot parse to a long value: min-rows-requested: \"23\"")`，这一行为被测试覆盖。

### `core/src/test/java/org/apache/iceberg/rest/requests/TestPlanTableScanRequestParser.java` (+82/-9 lines)

**修改目的**：覆盖 `minRowsRequested` 的非法输入校验、缺省 round-trip、带值 round-trip，并把已有全字段用例补上该字段。

**工作逻辑**：
- `invalidMinRowsRequested()`：三组断言。一是 JSON 中 `min-rows-requested` 为字符串 `"23"` 时期望抛 `Cannot parse to a long value: min-rows-requested: "23"`；二是 JSON 中为 `-1` 时期望抛 `Invalid scan: minRowsRequested is negative`（验证 `validate()` 的非负校验在 `fromJson` → `build()` 路径生效）；三是直接 `PlanTableScanRequest.builder().withMinRowsRequested(-1L).build()` 期望同样抛非负异常（验证 Builder 路径）。
- `roundTripSerdeWithoutMinRowsRequested()`：不设置该字段时，序列化 JSON 不含 `min-rows-requested` 键，反序列化后 `minRowsRequested()` 为 null，再次序列化仍一致——验证缺省场景的向后兼容。
- `roundTripSerdeWithMinRowsRequested()`：设置 `23L` 时，JSON 含 `"min-rows-requested" : 23`，反序列化后值仍为 23，再次序列化一致——验证带值 round-trip。
- 已有的全字段用例（`planTableScanRequestWithAllFieldsInvalidRequest` 之后的合法全字段 round-trip 与 toString 用例）补上 `.withMinRowsRequested(23L)`，并把期望 JSON 增加 `"min-rows-requested":23`。
- toString 用例从原先多条 `assertThat(str).contains(...)` 改写为 AssertJ 的 `assertThat(request).asString().contains(...).contains("minRowsRequested=23")` 链式断言，更简洁且覆盖新字段。

## 总结

本提交作为 2903 的 Java 实现配对，在 `PlanTableScanRequest` POJO、JSON 解析器与单元测试三个层面落地了 `min-rows-requested` 字段：POJO 持有 `Long minRowsRequested` 并在 `validate()` 中做非负校验；解析器在非 null 时条件序列化、用 `getLongOrNull` 反序列化；测试覆盖非法值、缺省 round-trip、带值 round-trip 与全字段场景。改动严格保持向后兼容（字段可选、缺省不写键），为后续服务端据此"按最少行数提前停止规划"提供了数据通路。
