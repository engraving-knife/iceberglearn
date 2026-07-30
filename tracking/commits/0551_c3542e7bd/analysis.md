# 提交 0551：修复 Javadoc link 标签相关的构建警告

## 提交信息

- **序号**：0551 / 4088
- **哈希**：c3542e7bdbd2a6c266841cbe41338d8e0f278bf9
- **短哈希**：c3542e7bd
- **日期**：2024-02-28（Wed Feb 28 20:37:59 2024 +0530）
- **作者**：Naveen Kumar <nk1506@gmail.com>
- **提交说明**：Core, Spark: Fix build warning related to Javadoc link tag (#9823)
- **PR/Issue**：#9823

## 总体目的

在构建 Iceberg 项目时，Javadoc 工具会针对 `{@link #methodName}` 这种简写形式抛出警告。Javadoc 规范要求 `{@link}` 标签引用成员时应当使用类名前缀（`{@link ClassName#methodName}`），以便 Javadoc 工具能够正确解析跨类引用、避免歧义。

本提交的目标是消除由于 `{@link #rewritesPerCommit}` 和 `{@link #executorService(int, String)}` 等不带类名前缀的简写引起的构建警告，保持构建输出的洁净，并遵循 Javadoc 规范。

## 如何达成设计目的

整体设计思路非常直接：扫描代码库中存在的不规范 `{@link #xxx}` 简写形式，将其改为 `{@link ClassName#xxx}` 形式。这种方式既符合 Javadoc 规范，也确保 IDE 与构建工具（如 javadoc -Xdoclint）不会因无法解析引用而报警。

实现路径：
1. 在 core 模块的 `BaseCommitService.java` 中将 `{@link #rewritesPerCommit}` 改为 `{@link BaseCommitService#rewritesPerCommit}`。
2. 在 spark 模块的 3 个版本（v3.3、v3.4、v3.5）的 `BaseProcedure.java` 中将 `{@link #executorService(int, String)}` 改为 `{@link BaseProcedure#executorService(int, String)}`。

由于 Spark 模块对 3.3、3.4、3.5 三个版本有并行代码副本，每个副本都需独立修复，因此共修改 4 个文件、4 行。

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/BaseCommitService.java`

**修改目的**：修复该文件中一处 Javadoc `{@link}` 简写形式导致的构建警告。

**工作逻辑**：原代码在方法 `offer`（注释说"Places a file group in the queue and commits a batch..."）的 Javadoc 注释中使用 `{@link #rewritesPerCommit}` 引用本类的字段 `rewritesPerCommit`。修复后改为 `{@link BaseCommitService#rewritesPerCommit}`，使 Javadoc 工具在跨类上下文（如生成的 HTML 页面中该方法被引用时）能够明确解析目标。这只是注释文本的修改，不影响任何字节码或运行时行为。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/procedures/BaseProcedure.java`

**修改目的**：修复 Spark 3.3 版本 `BaseProcedure` 类中 `{@link #executorService(int, String)}` 的 Javadoc 警告。

**工作逻辑**：在 `closeService()` 方法的 Javadoc 注释中说明该方法会关闭"由 `executorService(int, String)` 创建的 executor service"。原 `{@link #executorService(int, String)}` 改为 `{@link BaseProcedure#executorService(int, String)}`。仅注释变更，无功能影响。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/BaseProcedure.java`

**修改目的**：同上，针对 Spark 3.4 版本的相同代码副本进行相同修复。

**工作逻辑**：Spark 3.4 的 `BaseProcedure.java` 与 3.3 版本是独立维护的副本，相同的注释简写也需修复。改动内容与 3.3 版本完全一致。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/BaseProcedure.java`

**修改目的**：同上，针对 Spark 3.5 版本的相同代码副本进行相同修复。

**工作逻辑**：Spark 3.5 的 `BaseProcedure.java` 同样存在相同的简写，按相同方式修复。

## 小结

本提交是一次纯文档修复，不改变任何运行时行为，对功能、API、二进制兼容性均无影响。修复后 Iceberg 的 Javadoc 构建输出将不再产生这些与简写 link 标签相关的警告，便于开发者识别真正需要关注的告警。

**回迁到 1.4.x 的注意事项**：
- 该改动极其安全，无任何运行时风险，回迁时只需确保 1.4.x 中对应的 4 个文件路径存在。
- 需特别注意 1.4.x 分支支持的 Spark 版本范围：若 1.4.x 仅支持 Spark 3.3/3.4/3.5 中的一部分，则只需回迁对应版本目录下的修改；若 1.4.x 已支持 Spark 3.4/3.5，则同样需修复其副本。
- 由于仅涉及注释文本，回迁不会产生合并冲突，但建议与上游保持完全一致以减少后续合并工作量。
