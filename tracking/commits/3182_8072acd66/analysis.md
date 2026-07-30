# 提交 3182：API, Hive, Spark: Fix typos in comments and error messages (#15181)

## 提交信息

- **序号**：3182 / 4088
- **哈希**：8072acd66e69bd0888ca67c8ffb9b213501be8fc
- **短哈希**：8072acd66
- **日期**：2026-01-29
- **作者**：Manu Zhang
- **提交说明**：API, Hive, Spark: Fix typos in comments and error messages (#15181)
- **PR/Issue**：#15181

## 总体目的

本提交修复 Iceberg 代码库中多处英文拼写错误，涉及注释和异常错误消息。虽然拼写错误不影响代码运行逻辑，但会影响代码可读性与专业性，尤其是错误消息中的拼写问题可能影响用户排障体验——用户在日志或异常栈中看到 "Cant" 而非 "Can't" 时，会降低对项目质量的感知。提交说明中标注了 Claude（claude-sonnet-4.5）作为协作者，表明这是人机协作完成的拼写清理工作。

修复的三类拼写：
- `"cant"` → `"can't"`：出现在 Spark 各版本 `IcebergSource.java` 的注释中（解释为何抛出 Iceberg 自有的 `NoSuchTableException` 而非 Spark 类型化的异常），以及 Hive `HiveTableOperations.java` 的运行时异常消息中。
- `"commited"` → `"committed"`：出现在 API 层 `Snapshot.java` 的 Javadoc 注释中，描述行谱系（row lineage）相关语义。

## 如何达成设计目的

改动覆盖 API、Hive metastore、Spark v3.4/v3.5/v4.0/v4.1 五个模块/版本，每个文件仅修改 1-2 处拼写。注释类修改无运行时影响，异常消息类修改（`HiveTableOperations.java`）会改变用户可见的错误文本。各 Spark 版本的 `IcebergSource.java` 改动完全一致。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Snapshot.java` (+1/-1 lines)

**修改目的**：修复 Javadoc 中 "commited" 拼写错误。

**工作逻辑**：
在 `firstRowId()` 方法的 Javadoc 中，将 "but not necessarily commited to this branch" 修改为 "but not necessarily committed to this branch"。该注释描述行谱系语义：快照中新增行的 row-id 分配规则，以及历史快照中行的归属关系。修改纯文本，无逻辑影响。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java` (+1/-1 lines)

**修改目的**：修复运行时异常消息中的 "Cant" 拼写错误。

**工作逻辑**：
当表配置了密钥 ID（`tableKeyId != null`）但密钥管理客户端（`keyManagementClient`）未设置时，抛出的 `RuntimeException` 消息从 "Cant create encryption manager, because key management client is not set" 改为 "Can't create encryption manager, because key management client is not set"。此消息对用户可见，修正后更规范。注意：若有下游代码通过消息文本匹配异常（不推荐但可能存在），此修改可能产生影响，但 Iceberg 通常通过异常类型而非消息文本进行判断。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/IcebergSource.java` (+2/-2 lines)

**修改目的**：修复注释中 "cant" 拼写错误。

**工作逻辑**：
在 `loadTable` 方法的两处注释中，将 "the Spark one is typed and cant be thrown from this interface" 改为 "can't be thrown"。这两处注释解释了为何在 Spark DataSource V2 接口中需要将 Spark 的 `NoSuchTableException` 转换为 Iceberg 的 `NoSuchTableException`：Spark 的异常是类型化的受检异常，无法从该接口方法签名抛出。纯注释修改，无逻辑影响。

### `spark/v3.5/spark/src/main/java/.../IcebergSource.java` (+2/-2 lines)

**修改目的**：同 v3.4，修复注释拼写。

**工作逻辑**：与 v3.4 完全相同的两处 "cant" → "can't" 修改。

### `spark/v4.0/spark/src/main/java/.../IcebergSource.java` (+2/-2 lines)

**修改目的**：同 v3.4，修复注释拼写。

**工作逻辑**：与 v3.4 完全相同的两处 "cant" → "can't" 修改。

### `spark/v4.1/spark/src/main/java/.../IcebergSource.java` (+2/-2 lines)

**修改目的**：同 v3.4，修复注释拼写。

**工作逻辑**：与 v3.4 完全相同的两处 "cant" → "can't" 修改。

## 总结

本提交是纯拼写修正，覆盖 API、Hive、Spark 四个版本的六处文件，修正了 "cant"→"can't" 和 "commited"→"committed" 两类拼写错误。其中 Hive 异常消息的修改对用户可见，提升了错误输出的规范性；其余为注释修正，改善代码可读性。改动无逻辑影响，属于代码质量维护类提交。
