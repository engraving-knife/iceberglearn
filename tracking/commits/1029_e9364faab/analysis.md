# 提交 1029：API: Add SupportsRecoveryOperations mixin for FileIO (#10711)

## 提交信息

- **序号**：1029 / 4088
- **哈希**：e9364faabcc67eef6c61af2ecdf7bcf9a3fef602
- **短哈希**：e9364faab
- **日期**：2024-08-05 14:36:20 -0700
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：API: Add SupportsRecoveryOperations mixin for FileIO (#10711)
- **PR/Issue**：#10711

## 总体目的

Iceberg 表的元数据（manifest、manifest list、metadata 文件）会引用底层存储上的数据文件。当存储层出现故障或人工误操作时，可能出现"元数据引用的文件在磁盘上已不存在"的损坏情形——例如某个 live manifest 指向一个已被删除/丢失的 data file entry。这种情况下读取任务会因为找不到文件而失败，但表元数据本身仍认为该文件可达。

为了支持这种损坏场景的修复，需要一个让 `FileIO` 实现能够提供"尽力而为（best-effort）文件恢复"能力的扩展点。本提交在 `api` 模块中新增一个 mixin 接口 `SupportsRecoveryOperations`，作为 `FileIO` 的可选扩展，允许具体的 `FileIO` 实现去实现该接口以提供按路径恢复文件的能力（例如从副本、快照或备份中恢复）。

这是一个纯 API 增量提交：只定义接口契约，不提供任何实现，也不修改既有 `FileIO` 接口，保持向后兼容。后续可在具体 `FileIO` 实现（如 S3FileIO、HadoopFileIO 等）中按需实现该接口。

## 如何达成设计目的

在 `api/src/main/java/org/apache/iceberg/io/` 包下新增一个独立接口文件 `SupportsRecoveryOperations.java`，仅声明一个方法 `recoverFile(String path)`，返回 `boolean` 表示恢复是否成功。接口设计为 mixin：不继承 `FileIO`，而是让 `FileIO` 实现类"选择性地 implements"该接口，从而既有 `FileIO` 接口和现有实现完全不受影响。Javadoc 明确说明这是 best-effort 的恢复操作，用于修复"可达文件在磁盘上丢失"的损坏表。

## 修改详情

### `api/src/main/java/org/apache/iceberg/io/SupportsRecoveryOperations.java`（新增文件）

**修改目的**：定义 `FileIO` 实现可选实现的恢复操作 mixin 接口，为修复"元数据引用的文件在磁盘丢失"的损坏表提供 API 扩展点。

**工作逻辑**：新文件完整内容如下（去掉许可证头后）：

```java
package org.apache.iceberg.io;

/**
 * This interface is intended as an extension for FileIO implementations to provide additional
 * best-effort recovery operations that can be useful for repairing corrupted tables where there are
 * reachable files missing from disk. (e.g. a live manifest points to data file entry which no
 * longer exists on disk)
 */
public interface SupportsRecoveryOperations {

  /**
   * Perform a best-effort recovery of a file at a given path
   *
   * @param path Absolute path of file to attempt recovery for
   * @return true if recovery was successful, false otherwise
   */
  boolean recoverFile(String path);
}
```

关键设计要点：
- **mixin 而非继承 `FileIO`**：接口独立存在，不继承 `FileIO`，也不修改 `FileIO` 接口。具体 `FileIO` 实现若要支持恢复操作，需同时 `implements FileIO, SupportsRecoveryOperations`；调用方通过 `instanceof SupportsRecoveryOperations` 判断当前 `FileIO` 是否具备恢复能力，实现可选能力的按需发现。
- **best-effort 语义**：Javadoc 多处强调"best-effort"，即恢复不保证成功，返回 `false` 表示无法恢复，调用方需自行决定后续处理（如标记文件不可达、跳过等）。
- **按绝对路径恢复**：`recoverFile` 接收绝对路径字符串，与 `FileIO` 现有方法（如 `newInputFile`/`newOutputFile`）使用的 path 概念一致，便于复用路径信息。
- **用途场景**：Javadoc 给出的典型例子是"live manifest 指向一个磁盘上已不存在的 data file entry"，这正是该接口要修复的损坏模式。

## 小结

- **成效**：新增 `SupportsRecoveryOperations` mixin 接口，为 `FileIO` 实现提供了可选的"尽力而为文件恢复"扩展点，为后续修复"元数据引用文件丢失"的损坏表场景奠定 API 基础。本提交只定义契约，无实现、无调用方改动，纯增量且向后兼容。
- **影响范围**：仅新增 `api/src/main/java/org/apache/iceberg/io/SupportsRecoveryOperations.java` 一个文件（36 行，含许可证头），不修改任何既有类。
- **回迁到 1.4.x 的注意事项**：纯新增公共 API，向后兼容，回迁风险低。回迁到 1.4.x 会向 1.4.x 的公共 API 添加一个新接口——需评估这是否符合 1.4.x 的 API 冻结/兼容策略。若 1.4.x 不打算扩展公共 API，可不回迁；若 1.4.x 也希望支持文件恢复扩展点则可安全回迁，因不破坏任何既有行为。注意：本提交只是接口定义，单独回迁无实际效果，需配合后续实现提交才有意义。
