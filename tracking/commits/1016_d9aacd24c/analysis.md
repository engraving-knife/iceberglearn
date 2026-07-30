# 提交 1016：Core, API: UpdatePartitionSpec: Added ability to create a new Partition Spec but not set it as the Default

## 提交信息

- **序号**：1016 / 4088
- **哈希**：d9aacd24cc9d730d6416a93d31dd5cde8cbd260a
- **短哈希**：d9aacd24c
- **日期**：2024-08-04 21:45:46 -0500
- **作者**：Shani Elharrar <shani.elha@gmail.com>
- **提交说明**：Core, API: UpdatePartitionSpec: Added ability to create a new Partition Spec but not set it as the Default
- **PR/Issue**：无（提交说明中未含 #编号）

## 总体目的

Iceberg 表的分区规格（PartitionSpec）通过 `UpdatePartitionSpec` 这个 `PendingUpdate` 接口来增删字段、最后 `commit()`。原有 `BaseUpdatePartitionSpec.commit()` 的实现固定走 `TableMetadata.updatePartitionSpec(apply())`，而 `updatePartitionSpec` 内部调用 `Builder.setDefaultPartitionSpec(...)`——也就是说：**每次提交新 spec 都会把它设为表的默认（当前生效）spec**。

但在某些场景下，用户希望"预先注册一个新的分区规格，但暂不切换默认"：例如准备一个新 spec 供后续切换、或在多 spec 共存的表上只想新增一个可选 spec 而保留现有默认 spec 继续生效。原 API 不支持这种"只添加不切默认"的操作，用户只能先提交让新 spec 成为默认，然后再用其他 API 切回旧 spec，过程繁琐且会产生多余的事务/快照。

本提交的目的：在 `UpdatePartitionSpec` 接口上新增 `addNonDefaultSpec()` 方法，调用后该次 `commit()` 只把新 spec 追加到表的 specs 列表（分配新的 spec id），不修改表的 `default-spec-id`。这样用户可以"注册但不激活"一个新分区规格，为后续灵活的 spec 管理提供原子化的单次提交操作。

## 如何达成设计目的

设计思路是"在 update action 上加一个开关 + 在 TableMetadata 上提供只添加不设默认的入口"：

1. **API 层**：在 `UpdatePartitionSpec` 接口上新增 `default UpdatePartitionSpec addNonDefaultSpec()` 方法，默认抛 `UnsupportedOperationException`（默认方法模式，避免破坏既有实现该接口的第三方类）。Javadoc 说明：调用此方法表示新 spec 不设为默认，默认行为（不调用此方法）仍是设为默认。
2. **实现层**：`BaseUpdatePartitionSpec` 新增 `private boolean setAsDefault;` 字段，两个构造器中均初始化为 `true`（保持原默认行为）。实现 `addNonDefaultSpec()` 把 `setAsDefault` 置为 `false` 并返回 `this`（链式调用）。`commit()` 中根据 `setAsDefault` 分支：true 走原 `base.updatePartitionSpec(apply())`（添加并设默认），false 走新 `base.addPartitionSpec(apply())`（只添加不设默认）。
3. **TableMetadata 层**：新增 `public TableMetadata addPartitionSpec(PartitionSpec newPartitionSpec)`，内部委托给已有的 `Builder.addPartitionSpec(spec)`（仅调用 `addPartitionSpecInternal`，把 spec 加入 specs 列表并分配 spec id，但不调用 `setDefaultPartitionSpec`），与既有的 `updatePartitionSpec`（= `setDefaultPartitionSpec` 路径，添加并设默认）形成对称入口。
4. **测试**：新增 `testCommitUpdatedSpecWithoutSettingNewDefault` 验证：对一个已有 spec（partitioned by `bucket(data,16)`）的表，调用 `updateSpec().addField("id").addNonDefaultSpec().commit()` 后，`table.spec()`（即默认 spec）仍是原 spec（`isSameAs(originalSpec)`），而 `table.specs().get(1)` 是新创建的 spec（spec id=1，含 `bucket(data,16)` 与 `identity(id)`）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/UpdatePartitionSpec.java`

**修改目的**：在公开 API 上声明新的"不设默认"能力。

**工作逻辑**：在接口末尾新增默认方法：
```java
default UpdatePartitionSpec addNonDefaultSpec() {
  throw new UnsupportedOperationException(
      this.getClass().getName() + " doesn't implement addNonDefaultSpec()");
};
```
采用 default 方法 + 抛 UnsupportedOperationException 的模式，这样：既有实现该接口的非 Iceberg 自有类（如第三方 catalog 适配）不需要立即实现即可编译通过；只有真正调用该方法时才会抛异常，提示该实现未支持。Javadoc 说明：调用后新 spec 不会被设为表默认 spec；默认行为（不调用）仍是设为默认。

### `core/src/main/java/org/apache/iceberg/BaseUpdatePartitionSpec.java`

**修改目的**：实现 `addNonDefaultSpec()`，并在 `commit()` 时根据开关选择"添加并设默认"或"仅添加"。

**工作逻辑**：
- 新增字段 `private boolean setAsDefault;`。
- 两个构造器（`BaseUpdatePartitionSpec(TableOperations ops)` 和另一个用于测试/复用的构造器）中均加 `this.setAsDefault = true;`，保持原有"提交即设默认"的默认行为不变。
- 新增方法：
  ```java
  @Override
  public UpdatePartitionSpec addNonDefaultSpec() {
    this.setAsDefault = false;
    return this;
  }
  ```
  链式返回 `this`。
- `commit()` 由原单行 `TableMetadata update = base.updatePartitionSpec(apply());` 改为分支：
  ```java
  TableMetadata update;
  if (setAsDefault) {
    update = base.updatePartitionSpec(apply());
  } else {
    update = base.addPartitionSpec(apply());
  }
  ops.commit(base, update);
  ```
  `apply()` 仍然计算出新 `PartitionSpec`（含字段增删改的结果），区别只在于该 spec 是否被设为表的 `default-spec-id`。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：提供"只添加分区规格、不设默认"的 TableMetadata 级入口。

**工作逻辑**：在已有的 `updatePartitionSpec(PartitionSpec)`（内部走 `setDefaultPartitionSpec`，添加并设默认）旁边新增对称方法：
```java
public TableMetadata addPartitionSpec(PartitionSpec newPartitionSpec) {
  return new Builder(this).addPartitionSpec(newPartitionSpec).build();
}
```
`Builder.addPartitionSpec(PartitionSpec)` 在该文件中已存在（第 1607 行附近），它只调用 `addPartitionSpecInternal(spec)`（把 spec 加入 specs 列表、分配 spec id、记录 `MetadataUpdate.AddPartitionSpec` change），不修改 `defaultSpecId`，也不产生 `SetDefaultPartitionSpec` change。因此新方法产生的新 `TableMetadata` 会包含新 spec 但默认 spec id 保持不变。

### `core/src/test/java/org/apache/iceberg/TestTableUpdatePartitionSpec.java`

**修改目的**：验证 `addNonDefaultSpec()` 行为正确——新 spec 被添加但默认 spec 不变。

**工作逻辑**：新增测试 `testCommitUpdatedSpecWithoutSettingNewDefault`：
- 记录 `PartitionSpec originalSpec = table.spec();`（表初始 spec 为 `bucket(data,16)`，spec id=0）。
- 执行 `table.updateSpec().addField("id").addNonDefaultSpec().commit();`——添加 `identity(id)` 字段，并标记不设默认。
- 断言 1：`table.spec()` 仍是 `originalSpec`（用 `isSameAs` 验证引用相同，即默认 spec id 未变）。
- 断言 2：`table.specs().get(1)`（新添加的 spec，spec id=1）等于预期 `PartitionSpec.builderFor(table.schema()).withSpecId(1).bucket("data",16).identity("id").build()`，验证新 spec 已被注册到 specs 列表且内容正确。

## 小结

- **成效**：在 `UpdatePartitionSpec` API 上新增 `addNonDefaultSpec()` 开关，支持"只注册新分区规格、不切换默认 spec"的单次原子提交，填补了原 API 只能"添加即设默认"的缺口。配套在 `TableMetadata` 上暴露 `addPartitionSpec` 入口，与已有 `updatePartitionSpec` 形成对称能力。测试覆盖了"默认 spec 不变 + 新 spec 已注册"两条核心断言。
- **影响范围**：4 个文件，49 增 / 1 删。API 层 `UpdatePartitionSpec` 接口加 1 个 default 方法（向后兼容）；core 层 `BaseUpdatePartitionSpec` 加 1 字段 + 1 方法 + commit 分支；`TableMetadata` 加 1 个 public 方法（复用已有 Builder 能力）；测试加 1 个用例。不修改任何既有行为（默认 `setAsDefault=true` 与原逻辑等价）。
- **回迁到 1.4.x 的注意事项**：本提交是向后兼容的 API 增量（新增 default 方法 + 新增 TableMetadata 方法），不改变既有行为，回迁风险低。需注意：(1) 1.4.x 分支的 `UpdatePartitionSpec` 接口与 `BaseUpdatePartitionSpec` 实现应与该提交假设的结构一致（字段 `setAsDefault`、构造器、`commit()` 逻辑）；(2) `TableMetadata.Builder.addPartitionSpec(PartitionSpec)` 与 `addPartitionSpecInternal` 在 1.4.x 中应已存在（这是较早期就有的能力），若不存在需先确认 Builder 内部实现；(3) 该 API 是新能力，回迁后不会影响 1.4.x 已有用户行为，可安全发布。整体适合回迁。
