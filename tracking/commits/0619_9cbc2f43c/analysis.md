# 提交 0619：消除 SnapshotUpdate 原始类型（raw type）使用

## 提交信息

- **序号**：0619 / 4088
- **哈希**：9cbc2f43c4a7e3feb15703dd4a0dd0f4423f2ced
- **短哈希**：9cbc2f43c
- **日期**：2024-03-22 08:19:29 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Use <?> as type parameter instead of raw type for SnapshotUpdate (#10015)
- **PR/Issue**：#10015

## 总体目的

本提交旨在消除 `core` 测试代码中对 `SnapshotUpdate` 与 `SnapshotProducer` 的"原始类型（raw type）"使用，改为显式声明通配符类型参数 `<?>`，以消除编译器与 IDE 的 raw-type 警告、提升类型安全度、并使代码更符合现代 Java 泛型规范。

背景动机：

- Iceberg 的 `org.apache.iceberg.SnapshotUpdate<ThisT>` 接口采用 CRTP（Curiously Recurring Template Pattern）风格，自身带有一个类型参数 `ThisT`，子类型在实现时应将 `ThisT` 替换为自身，使 `set(...)` / `toBranch(...)` 等链式方法返回具体子类型。
- 在测试代码中，有时需要写一个通用 helper（如 `commit(Table, SnapshotUpdate, String)` 与 `apply(SnapshotUpdate, String)`），接收任意 `SnapshotUpdate` 实例，而不关心具体子类型。原来直接写 `SnapshotUpdate`（原始类型）会触发 `SnapshotUpdate is a raw type. References to generic type SnapshotUpdate<ThisT> should be parameterized` 警告。
- 同样，原代码用 `((SnapshotProducer) snapshotUpdate)` 强转也属于原始类型使用。`SnapshotProducer` 同样是带泛型参数的类（`SnapshotProducer<T> implements SnapshotUpdate<T>`），应改写为 `((SnapshotProducer<?>) snapshotUpdate)`。
- 本提交用通配符 `<?>` 显式声明"接收任意 `SnapshotUpdate`/`SnapshotProducer` 实例"，既消除警告，又表达"对类型参数不关心"的意图，是泛型代码的标准写法。

## 如何达成设计目的

设计思路是机械性的"原始类型 → 通配符类型"替换：

- 凡是声明 `SnapshotUpdate snapshotUpdate` 形参/局部变量，改为 `SnapshotUpdate<?> snapshotUpdate`。
- 凡是强转 `(SnapshotProducer) x`，改为 `(SnapshotProducer<?>) x`。
- 不修改任何业务逻辑、不改测试覆盖、不改方法签名（除泛型参数外）。
- 在 JUnit4 版本（`TableTestBase`）与 JUnit5 版本（`TestBase`）的同一对 helper 方法上做对称修改，保证两套测试基类行为一致。

注意：本提交触及两个版本基类（`TableTestBase` 与 `TestBase`），是因为 1.4.x 时期 Iceberg 处于 JUnit4 → JUnit5 迁移过渡期，两套基类并存。`TestBase` 是 JUnit5 版本（见提交 0617 的分析），`TableTestBase` 是 JUnit4 版本。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TableTestBase.java`

**修改目的**：消除 JUnit4 版本测试基类中 `commit` 与 `apply` helper 的 raw type 使用。

**工作逻辑**：

- `Snapshot commit(Table table, SnapshotUpdate snapshotUpdate, String branch)` → `Snapshot commit(Table table, SnapshotUpdate<?> snapshotUpdate, String branch)`：声明接收任意 `SnapshotUpdate`。
- 在非 main 分支路径中，`((SnapshotProducer) snapshotUpdate.toBranch(branch)).commit()` → `((SnapshotProducer<?>) snapshotUpdate.toBranch(branch)).commit()`：强转也带上通配符。
- `Snapshot apply(SnapshotUpdate snapshotUpdate, String branch)` → `Snapshot apply(SnapshotUpdate<?> snapshotUpdate, String branch)`。
- main 分支：`((SnapshotProducer) snapshotUpdate).apply()` → `((SnapshotProducer<?>) snapshotUpdate).apply()`。
- 非 main 分支：`((SnapshotProducer) snapshotUpdate.toBranch(branch)).apply()` → `((SnapshotProducer<?>) snapshotUpdate.toBranch(branch)).apply()`。

`commit` 方法的行为不变：当 `branch` 等于 `SnapshotRef.MAIN_BRANCH`（即 "main"）时，直接调用 `snapshotUpdate.commit()` 并取 `table.currentSnapshot()`；否则调用 `toBranch(branch)` 切换目标分支后再 commit，最后取 `table.snapshot(branch)`。`apply` 方法类似：main 分支直接 apply，非 main 分支先 toBranch 再 apply。

### `core/src/test/java/org/apache/iceberg/TestBase.java`

**修改目的**：消除 JUnit5 版本测试基类中相同的 raw type 使用。

**工作逻辑**：与 `TableTestBase` 完全对称的 4 处替换。注意 `TestBase` 是 PR #9161 引入的 JUnit5 版本基类，本提交在迁移过渡期一并修正。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java`

**修改目的**：消除 `TestRowDelta` 测试类中 4 处 `SnapshotUpdate` 局部变量的 raw type 使用。

**工作逻辑**：

- `testAddDeleteFile`：`SnapshotUpdate rowDelta = table.newRowDelta().addRows(FILE_A).addDeletes(FILE_A_DELETES).addDeletes(FILE_B_DELETES);` → `SnapshotUpdate<?> rowDelta = ...`。
- `testValidateDataFilesExistDefaults`：
  - `SnapshotUpdate rowDelta1 = table.newAppend().appendFile(FILE_A).appendFile(FILE_B);` → `SnapshotUpdate<?> rowDelta1 = ...`
  - `SnapshotUpdate rowDelta2 = table.newOverwrite().deleteFile(FILE_A).addFile(FILE_A2);` → `SnapshotUpdate<?> rowDelta2 = ...`
  - `SnapshotUpdate rowDelta3 = table.newDelete().deleteFile(FILE_B);` → `SnapshotUpdate<?> rowDelta3 = ...`

注意 `table.newRowDelta()` / `table.newAppend()` / `table.newOverwrite()` / `table.newDelete()` 返回的具体子类型（如 `RowDelta` / `AppendFiles` / `OverwriteFiles` / `DeleteFiles`）都是 `SnapshotUpdate<ThisT>` 的具体参数化实现，原本赋值给 raw `SnapshotUpdate` 会触发"未经检查的转换（unchecked conversion）"警告；改用 `SnapshotUpdate<?>` 后，编译器会接受这一赋值（因为 `?` 是上界通配符），不再产生 raw-type 警告。

## 小结

本提交是纯粹的代码质量改进，无任何行为变化。共修改 3 个文件、14 行（每处都是 `SnapshotUpdate` → `SnapshotUpdate<?>` 或 `SnapshotProducer` → `SnapshotProducer<?>`），不改业务逻辑、不改测试覆盖、不改公开 API。

**影响范围**：

- 仅触及 `core/src/test/java/org/apache/iceberg/` 下 3 个测试文件，不影响发布产物。
- 不影响任何被测代码的运行时行为，只影响编译期的类型检查严格度。
- 与提交 0617（JUnit5 迁移）属同一时期的"测试代码现代化"工作，二者方向一致但相互独立。

**回迁到 1.4.x 的注意事项**：

1. **零风险回迁**：本提交是机械替换，无依赖、无副作用，1.4.x 上若想消除 raw type 警告可直接回迁。
2. **基类并存**：1.4.x 上若仍同时保留 `TableTestBase`（JUnit4）与 `TestBase`（JUnit5），需要两边都改，否则迁移到 `TestBase` 的子类会编译失败（因为 `TestBase.commit/apply` 形参类型变了，但调用方传 raw 类型不会自动转 wildcard——实际上传 raw 仍能编译但带警告，所以不是硬错误）。
3. **价值有限**：作为维护分支，1.4.x 通常不做这种纯代码质量优化；但如果在 1.4.x 上要新增涉及 `SnapshotUpdate` 的测试 helper，回迁本提交可以避免引入新的 raw type 警告。
4. **与 `actions.SnapshotUpdate` 的区别**：注意 Iceberg 还有一个不同包的 `org.apache.iceberg.actions.SnapshotUpdate<ThisT, R>` 接口（用于 actions 模块），本提交仅触及 `org.apache.iceberg.SnapshotUpdate<ThisT>`（表更新接口），不涉及 actions 版本，回迁时不要混淆。
