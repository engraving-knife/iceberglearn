# 提交 0996：Build: Declare avro as an api dependency of iceberg-core (#10573)

## 提交信息

- **序号**：0996 / 4088
- **哈希**：0ff90e7732574fa1a1e094bb66c7c3793e3d1ebb
- **短哈希**：0ff90e773
- **日期**：2024-07-30（Tue Jul 30 14:26:55 2024 -0700）
- **作者**：Devin Smith <devinsmith@deephaven.io>
- **提交说明**：Build: Declare avro as an api dependency of iceberg-core (#10573)
- **PR/Issue**：#10573

## 总体目的

`iceberg-core` 是 Iceberg 的核心模块，其公开 API 中实际暴露了 Avro 类型。例如公开类 `org.apache.iceberg.PartitionData` 直接继承了 Avro 特有的类型（`org.apache.avro.specific.SpecificRecord`），而 `org.apache.iceberg.avro.AvroSchemaUtil` 等公开类的方法签名也以 Avro 的 `Schema` 等类型作为参数和返回值。

按照 Gradle 的依赖可见性语义，`iceberg-core` 对 Avro 的依赖此前声明为 `implementation`。`implementation` 依赖不会出现在模块的"消费 API"（compile classpath）上，因此下游依赖 `iceberg-core` 的模块（或用户直接依赖 `iceberg-core`）在编译时无法访问 Avro 类型，尽管它们通过 `iceberg-core` 的公开 API 实际上会接触到这些类型。这会导致下游编译失败、IDE 无法解析符号，或者迫使下游显式地自行声明 Avro 依赖，与"模块应自行声明其 API 所需依赖"的良好实践相违背。

本提交将 `iceberg-core` 对 Avro 的依赖从 `implementation` 提升为 `api`，使 Avro 成为 `iceberg-core` 对外暴露 API 的一部分，下游依赖 `iceberg-core` 即可传递获得 Avro 的编译期可见性，与实际公开的 API 契约保持一致。

## 如何达成设计目的

只需修改根构建脚本 `build.gradle` 中 `:iceberg-core` 项目块的一行依赖声明，把 `implementation(libs.avro.avro)` 改为 `api(libs.avro.avro)`，其余配置（如排除 `org.tukaani` 的 xz 压缩依赖）保持不变。`api` 配置在 Gradle 中意味着该依赖会出现在模块的 API 上，对下游消费者在编译期和运行期均可见（传递依赖）。

## 修改详情

### `build.gradle`

**修改目的**：将 Avro 从 `iceberg-core` 的 `implementation` 依赖提升为 `api` 依赖。

**工作逻辑**：在 `project(':iceberg-core')` 块内：

```diff
-    implementation(libs.avro.avro) {
+    api(libs.avro.avro) {
       exclude group: 'org.tukaani' // xz compression is not supported
     }
```

`api` 与 `implementation` 的区别在于：`api` 依赖会出现在模块的编译 API 上，传递给下游消费者；`implementation` 仅在模块自身编译/运行时可见，不传递到下游编译 classpath。因为 `iceberg-core` 的公开类型（`PartitionData`、`AvroSchemaUtil` 等）直接暴露了 Avro 类型，使用 `api` 才能保证下游在不额外声明 Avro 的情况下正常编译使用这些 API。`exclude group: 'org.tukaani'` 维持不变，仍排除不被支持的 xz 压缩传递依赖。

## 小结

- **成效**：使 `iceberg-core` 的依赖声明与其公开 API 契约一致，下游模块/用户依赖 `iceberg-core` 后即可编译期访问 Avro 类型，无需自行显式声明 Avro 依赖，避免了"API 暴露类型但依赖不可见"的矛盾。
- **影响范围**：仅根 `build.gradle` 一个文件，1 行改动。影响的是 `iceberg-core` 模块对 Avro 依赖的可见性范围，会使其作为传递依赖暴露给所有依赖 `iceberg-core` 的下游模块（如 `iceberg-parquet`、`iceberg-orc`、各引擎集成模块及外部用户）。
- **回迁到 1.4.x 的注意事项**：这是一个构建正确性修复，风险极低，适合回迁。回迁时需确认 1.4.x 分支的 `build.gradle` 中该行仍以 `implementation(libs.avro.avro)` 形式存在（变量名 `libs.avro.avro` 的具体路径在 1.4.x 应一致）。注意该改动会让 Avro 成为 `iceberg-core` 对外暴露的传递依赖，极少数对依赖树敏感的下游若依赖了 `iceberg-core` 但此前自行声明了不同版本的 Avro，可能出现版本冲突需调和，但总体影响正面。
