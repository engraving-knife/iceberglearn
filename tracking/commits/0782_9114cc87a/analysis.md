# 提交 0782：Hive: Use base table metadata to create HiveLock (#10016)

## 提交信息

- **序号**：0782 / 4088
- **哈希**：9114cc87ac48ac77402eb41d398c6371a79f3c79
- **短哈希**：9114cc87a
- **日期**：2024-05-24 03:23:24 +0800
- **作者**：Rui Li
- **提交说明**：Hive: Use base table metadata to create HiveLock (#10016)
- **PR/Issue**：#10016

## 总体目的

本提交修复 `HiveTableOperations` 中一个关于 Hive 锁机制配置生效时机的缺陷。在 Iceberg + Hive Metastore 的集成中，提交（commit）时是否启用 Hive 锁由表属性 `iceberg.hive.lock-enabled`（`TableProperties.HIVE_LOCK_ENABLED`）决定。原实现错误地使用**本次提交将要写入的新 metadata**（`metadata` 参数）来判断本次提交该用哪种锁，而非使用**本次提交所基于的当前 metadata**（`base` 参数）。这导致：当某次提交恰好修改了 `HIVE_LOCK_ENABLED` 这个属性时，新的锁设置会在**本次提交本身**就生效，而不是在提交成功之后的下一次提交才生效——这违背了"配置变更应在提交完成后才对外生效"的语义一致性原则，并可能在关闭锁→开启锁的切换瞬间引入并发安全问题。本提交将锁的判定依据从 `metadata` 改为 `base`，并补齐 `base` 可能为 `null`（新建表场景）的空指针保护。

## 如何达成设计目的

### bug 成因

`HiveTableOperations.doCommit(TableMetadata base, TableMetadata metadata)` 是提交入口，其中：
- `base`：本次提交所基于的当前表元数据（提交前的状态）。
- `metadata`：本次提交将要写入的新表元数据（提交后的状态）。

提交流程中需要先获取一把锁（`HiveLock`），锁的类型由 `lockObject(TableMetadata)` 决定：
- 若 `hiveLockEnabled` 返回 `true`，则使用 `MetastoreLock`（基于 Hive Metastore 的真正分布式锁，配合心跳续约）。
- 否则使用 `NoLock`（无操作锁，不进行任何加锁）。

原代码在 `doCommit` 内部这样调用：

```java
HiveLock lock = lockObject(metadata);          // 第 182 行：用新 metadata 判定锁
...
persistTable(tbl, updateHiveTable, hiveLockEnabled(metadata, conf) ? null : baseMetadataLocation);  // 第 245 行：同样用 metadata
```

注意 `persistTable` 的第三个参数：当**未启用** Hive 锁时，传入 `baseMetadataLocation`（旧 metadata 位置），用于在 HMS 的 `alter_table` 调用中做"乐观并发检查"——Hive Metastore 会校验当前表的 metadata location 是否仍是这个旧值，若已被别人改掉则提交失败，相当于用 `alter_table` 的 CAS 语义兜底实现并发控制。当**启用** Hive 锁时，因为已经有了显式锁保护，就不需要再传 `baseMetadataLocation` 做乐观校验，传 `null` 即可。

问题在于：判定锁类型用了 `metadata`（新状态）。设想一个场景——用户原本表属性 `HIVE_LOCK_ENABLED=false`（用 NoLock，靠 alter_table CAS 兜底），现在通过 `alterTable` 把它改成 `true`（希望后续提交用 MetastoreLock）。在原实现下：

1. 本次 `doCommit` 调用 `lockObject(metadata)`，`metadata` 里 `HIVE_LOCK_ENABLED=true`，于是返回 `MetastoreLock`。
2. 本次提交**本身**就用了新的 MetastoreLock，并且因为 `hiveLockEnabled(metadata)=true`，`persistTable` 第三参数传 `null`，跳过了 alter_table 的 CAS 兜底。
3. 但此时 MetastoreLock 是否能正确加锁取决于 HMS 的锁服务是否就绪。如果切换瞬间存在并发提交，且旧的 NoLock 路径本应靠 CAS 兜底却因提前切到 MetastoreLock 而被跳过，就可能出现并发覆盖。

更本质的问题是语义错位：**本次提交应遵循的是"提交前"的锁约定**（即 `base` 所声明的策略），因为本次提交是在 `base` 状态之上进行的；新策略（`metadata`）应等到本次提交成功落地、`base` 变成新的 `metadata` 之后，才在**下一次**提交生效。这与数据库迁移、配置热更新等场景下"变更不作用于变更过程本身"的原则一致。

### 修复逻辑

将三处对 `metadata` 的判定改为 `base`：

1. `HiveLock lock = lockObject(base);` —— 用提交前状态决定本次锁类型。
2. `persistTable(tbl, updateHiveTable, hiveLockEnabled(base, conf) ? null : baseMetadataLocation);` —— 用提交前状态决定是否需要 alter_table CAS 兜底。两者必须一致，否则会出现"用了 NoLock 却又跳过 CAS 兜底"或"用了 MetastoreLock 却还多做一次 CAS"的不一致。
3. `hiveLockEnabled` 方法增加 `metadata != null` 判空 —— 因为 `base` 在新建表（`newTable = base == null`）时为 `null`，直接 `metadata.properties().get(...)` 会 NPE。原来的 `metadata`（新状态）在新建表时非空，所以不需要判空；改用 `base` 后必须补判空。`null` 时走 else 分支，回退到 `hive-site.xml` 的 `ConfigProperties.LOCK_HIVE_ENABLED` 配置或默认值 `HIVE_LOCK_ENABLED_DEFAULT`。

修复后的语义：本次提交用 `base`（提交前状态）的锁策略完成提交；提交成功后，表的元数据变为 `metadata`，下一次提交时 `base` 就是这个新状态，新策略自然生效。这样 `HIVE_LOCK_ENABLED` 的切换就具备了"提交原子性"——切换本身用旧策略提交，切换结果在下次提交才体现。

### 测试验证

1. **既有测试适配**：`TestHiveCommits` 中 `testSuppressUnlockExceptions` 和另一个并发提交测试原本 mock `spyOps.lockObject(metadataV1)`。这两个测试场景是 `spyOps.commit(metadataV2, metadataV1)`（即 base=metadataV2，metadata=metadataV1，模拟回滚到 V1）。修复后 `doCommit` 调用的是 `lockObject(base)`=`lockObject(metadataV2)`，因此 mock 桩必须从 `metadataV1` 改为 `metadataV2` 才能命中。这正是 diff 中两处 `metadataV1` → `metadataV2` 的原因——不是改测试逻辑，而是让 mock 桩匹配新的被调参数。

2. **新增专项测试 `testChangeLockWithAlterTable`**：
   - 加载表，获取当前 `base = ops.current()` 和初始锁 `initialLock = ops.lockObject(base)`。
   - 构造 `newMetadata`：把 `HIVE_LOCK_ENABLED` 翻转——若初始是 `NoLock` 则设为 `"true"`，否则设为 `"false"`。
   - 用 spy 捕获 `lockObject(base)` 实际返回的锁实例到 `lockRef`。
   - 执行 `spyOps.commit(base, newMetadata)`。
   - 断言 `lockRef.get()` 与 `initialLock` 是**同一个类**（`hasSameClassAs`），即本次提交用的仍是旧策略对应的锁类型，新策略未提前生效。

   这个测试精准覆盖了 bug 场景：在原实现下，翻转 `HIVE_LOCK_ENABLED` 的提交会用 `metadata`（新策略）生成锁，`lockRef.get()` 的类会与 `initialLock` 不同，断言失败；修复后两者同类，断言通过。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java`

**修改目的**：将锁判定依据从 `metadata` 改为 `base`，并补齐空指针保护。

**修改点 1**（`doCommit` 方法，约第 182 行）：

```java
- HiveLock lock = lockObject(metadata);
+ HiveLock lock = lockObject(base);
```

提交时获取锁的依据改为提交前状态。

**修改点 2**（`doCommit` 方法，约第 245 行）：

```java
- persistTable(tbl, updateHiveTable, hiveLockEnabled(metadata, conf) ? null : baseMetadataLocation);
+ persistTable(tbl, updateHiveTable, hiveLockEnabled(base, conf) ? null : baseMetadataLocation);
```

是否传 `baseMetadataLocation` 做 alter_table CAS 兜底，也改为依据 `base`，与锁类型判定保持一致。

**修改点 3**（`hiveLockEnabled` 静态方法）：

```java
- if (metadata.properties().get(TableProperties.HIVE_LOCK_ENABLED) != null) {
+ if (metadata != null && metadata.properties().get(TableProperties.HIVE_LOCK_ENABLED) != null) {
```

新增 `metadata != null` 短路判空。注意此处的形参名仍叫 `metadata`，但调用方实际传入的是 `base`，新建表时为 `null`。判空后走 `else` 分支读取 `hive-site.xml` 的 `ConfigProperties.LOCK_HIVE_ENABLED`，若也未配置则用默认值 `HIVE_LOCK_ENABLED_DEFAULT`。`hiveEngineEnabled` 方法未做同样修改，因为其调用点仍传 `metadata`（新状态，非空）。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCommits.java`

**修改目的**：适配 mock 桩参数 + 新增专项回归测试。

**修改点 1**：新增 import `static org.apache.iceberg.TableProperties.HIVE_LOCK_ENABLED` 和 `ImmutableMap`。

**修改点 2**（`testSuppressUnlockExceptions`）：将 `when(spyOps.lockObject(metadataV1))` 改为 `when(spyOps.lockObject(metadataV2))`，因为该测试调用 `spyOps.commit(metadataV2, metadataV1)`，修复后 `doCommit` 调 `lockObject(base=metadataV2)`。

**修改点 3**（另一并发提交测试，约第 273-280 行）：同理，两处 `metadataV1` 改为 `metadataV2`（`lock.set(ops.lockObject(metadataV2))` 与 `.lockObject(metadataV2)`）。

**修改点 4**（新增 `testChangeLockWithAlterTable`）：见上文"测试验证"小节。该测试是本 PR 的核心回归保护，确保锁策略切换不会在切换提交本身提前生效。

## 小结

- **成效**：修复了 Hive 锁策略配置（`HIVE_LOCK_ENABLED`）在切换提交中提前生效的时序缺陷，使锁策略变更遵循"提交成功后才对外生效"的语义一致性。同时补齐了新建表（`base == null`）场景下 `hiveLockEnabled` 的空指针保护。新增的 `testChangeLockWithAlterTable` 提供了精准的回归覆盖。
- **影响范围**：仅影响 `HiveTableOperations`（Hive Metastore 集成的提交路径）。所有使用 HiveCatalog 的用户在执行涉及 `HIVE_LOCK_ENABLED` 属性变更的 `alterTable`/`updateProperties` 提交时行为会变化——此前这些提交会用新策略，现在用旧策略。对不涉及该属性切换的常规提交无影响（`base` 与 `metadata` 的 `HIVE_LOCK_ENABLED` 值相同，判定结果一致）。新建表场景此前不报错（用 `metadata` 非空），修复后路径仍正确（走 `hive-site.xml` 默认）。
- **回迁注意事项**：
  1. 这是一个语义修复，回迁到 1.4.x 分支需确认 1.4.x 的 `HiveTableOperations.doCommit` 仍是用 `metadata` 而非 `base`（即 bug 存在），cherry-pick 才有意义。
  2. `hiveLockEnabled` 的判空保护与 `lockObject(base)` 改动必须一起回迁，缺一不可——只改 `lockObject(base)` 而不判空，会导致新建表提交 NPE。
  3. 测试文件中既有测试的 `metadataV1`→`metadataV2` 适配是 cherry-pick 的易冲突点：若 1.4.x 的 `TestHiveCommits` 已有其他对这两个测试的改动，需手动合并。`testChangeLockWithAlterTable` 依赖 `HiveTableBaseTest` 的 `catalog`、`TABLE_IDENTIFIER` 基础设施，1.4.x 中应可用。
  4. 该修复改变了 `HIVE_LOCK_ENABLED` 切换提交的行为，理论上属于 bug fix 而非破坏性变更，但若有用户依赖了"切换当次即生效"的旧行为（极少见），回迁后需告知。
