# 提交 0862：Core, Spark: Spark writes/actions should only perform cleanup if failure is cleanable (#10373)

## 提交信息

- **序号**：0862 / 4088
- **哈希**：c67c9124d324207d742307982de31e5ff2ebcd01
- **短哈希**：c67c9124d
- **日期**：2024-06-20（Thu Jun 20 08:27:59 2024 -0700）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Core, Spark: Spark writes/actions should only perform cleanup if failure is cleanable (#10373)
- **PR/Issue**：#10373

## 总体目的

Iceberg 在 Spark 写入和 actions（如 RewriteDataFiles、RewriteManifests、RewritePositionDeletes）流程中，原本的失败处理逻辑是：只要提交（commit）抛出非 `CommitStateUnknownException` 的异常，就一律清理（删除）本次写入新生成的数据文件/manifest 文件。这种"无差别清理"存在一个隐患——并非所有异常都意味着提交确实没有成功，例如网络中断、Catalog 服务端瞬时错误等引发的运行时异常，提交可能已经写入但调用方无法确认其状态。

如果在不确定提交状态的情况下贸然删除新生成的文件，可能误删已经被成功提交（即已写入 manifest 和 metadata）的文件，造成"已提交数据被当垃圾回收掉"的严重后果，用户后续查询会失败。本提交的目标是把"清理逻辑"收敛到只在"确定可清理"的失败下执行，从而避免误删已提交数据。

为此，本提交利用 Iceberg 已有的 `CleanableFailure` 标记接口（位于 `org.apache.iceberg.exceptions` 包，由 `CommitFailedException`、`ValidationException` 等明确失败的异常实现），将清理动作条件化为"仅当异常实现了 `CleanableFailure` 才清理"，而把其他任意运行时异常视作不可清理（保留新生成的文件作为孤儿文件，等后续 `RemoveOrphanFiles` 处理或人工介入）。

## 如何达成设计目的

整体设计思路是：在所有涉及"提交失败后清理新生成文件"的关键路径（包括 `BaseRewriteDataFilesAction`、`RewriteDataFilesCommitManager`、`RewritePositionDeletesCommitManager`、Spark 3.5 的 `RewriteManifestsSparkAction`、`SparkWrite`、`SparkPositionDeltaWrite`）中，把原本无条件的清理调用包裹在 `if (e instanceof CleanableFailure)` 判断里。

对 `SparkWrite` 和 `SparkPositionDeltaWrite`，原本通过捕获 `CommitStateUnknownException` 设置 `cleanupOnAbort = false`，其余情况默认 `cleanupOnAbort = true`。本提交反转了默认值——默认 `cleanupOnAbort = false`，只有在 `catch (Exception e)` 中判定 `e instanceof CleanableFailure` 才设为 `true`。这样在不修改 abort 调用本身的前提下，把"清理倾向"从默认清理变为默认不清理，更安全。

测试侧，新增了 `testCommitFailsWithUncleanableFailure` 验证抛出普通 `RuntimeException` 时不会被清理（留下孤儿文件），并把原有测试中的 `RuntimeException("Commit Failure")` 改为 `CommitFailedException("Commit Failure")`，以反映新的可清理语义——`CommitFailedException` 实现了 `CleanableFailure`，因此该测试场景的清理行为得以保留。

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/BaseRewriteDataFilesAction.java`

**修改目的**：在 `BaseRewriteDataFilesAction` 替换数据文件提交失败的清理路径上，加入 `CleanableFailure` 判定，避免在不可清理的失败下删除可能已提交的文件。

**工作逻辑**：原本 `catch (Exception e)` 中无条件调用 `Tasks.foreach(...).run(fileIO::deleteFile)` 删除 `addedDataFiles`。修改后改为：

```java
} catch (Exception e) {
  if (e instanceof CleanableFailure) {
    LOG.warn("Failed to commit rewrite, cleaning up rewritten files", e);
    Tasks.foreach(Iterables.transform(addedDataFiles, f -> f.path().toString()))
        .noRetry()
        .suppressFailureWhenFinished()
        .onFailure((location, exc) -> LOG.warn("Failed to delete: {}", location, exc))
        .run(fileIO::deleteFile);
  }

  throw e;
}
```

只有 `CleanableFailure` 类型异常才执行清理；否则直接向上抛出，留下未提交文件作为孤儿。同时引入了 `org.apache.iceberg.exceptions.CleanableFailure` 的 import。

### `core/src/main/java/org/apache/iceberg/actions/RewriteDataFilesCommitManager.java`

**修改目的**：在 `RewriteDataFilesCommitManager.commitFileGroups` 的统一提交失败分支上，加入 `CleanableFailure` 判定，避免对不可清理失败调用 `abortFileGroup` 删除已写文件。

**工作逻辑**：原本 `catch (Exception e)` 无条件 `rewriteGroups.forEach(this::abortFileGroup)`。修改后改为：

```java
} catch (Exception e) {
  if (e instanceof CleanableFailure) {
    LOG.error("Cannot commit groups {}, attempting to clean up written files", rewriteGroups, e);
    rewriteGroups.forEach(this::abortFileGroup);
  }

  throw e;
}
```

`abortFileGroup` 会删除每个 rewrite group 中刚写出的文件，因此必须确保只在确定未提交时调用。

### `core/src/main/java/org/apache/iceberg/actions/RewritePositionDeletesCommitManager.java`

**修改目的**：与 `RewriteDataFilesCommitManager` 同样的修复，但作用于 position deletes 重写流程。在统一提交失败分支加入 `CleanableFailure` 判定。

**工作逻辑**：原本 `catch (Exception e)` 无条件 `rewriteGroups.forEach(this::abort)`，改为：

```java
} catch (Exception e) {
  if (e instanceof CleanableFailure) {
    LOG.error("Cannot commit groups {}, attempting to clean up written files", rewriteGroups, e);
    rewriteGroups.forEach(this::abort);
  }

  throw e;
}
```

只有可清理异常才调用 `abort`，否则保留新生成的 delete 文件。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：在 Spark 3.5 的 manifest 重写动作提交失败清理路径上，加入 `CleanableFailure` 判定。

**工作逻辑**：原本 `catch (Exception e)` 中无条件 `deleteFiles(Iterables.transform(addedManifests, ManifestFile::path))` 删除新生成的 manifest 文件。修改后：

```java
} catch (Exception e) {
  if (e instanceof CleanableFailure) {
    // delete all new manifests because the rewrite failed
    deleteFiles(Iterables.transform(addedManifests, ManifestFile::path));
  }

  throw e;
}
```

`CommitStateUnknownException` 已经在前一个 catch 分支单独处理（不会清理 added manifest），所以这里进一步收窄到只清理 `CleanableFailure`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java`

**修改目的**：将 `SparkPositionDeltaWrite` 中 `cleanupOnAbort` 的默认语义从"默认清理"改为"默认不清理，仅在 `CleanableFailure` 时清理"。

**工作逻辑**：

1. 把字段默认值 `private boolean cleanupOnAbort = true;` 改为 `private boolean cleanupOnAbort = false;`。
2. 把 import 由 `CommitStateUnknownException` 改为 `CleanableFailure`。
3. 把 `commit` 方法的 catch 块由仅捕获 `CommitStateUnknownException` 改为捕获所有 `Exception`，并据 `instanceof CleanableFailure` 设置 `cleanupOnAbort`：

```java
} catch (Exception e) {
  cleanupOnAbort = e instanceof CleanableFailure;
  throw e;
}
```

这样 `abort` 阶段会通过 `cleanupOnAbort` 决定是否删除已写数据文件。`CommitStateUnknownException` 不是 `CleanableFailure`，因此也会落入默认不清理分支，与原逻辑保持一致；而 `CommitFailedException` 等 `CleanableFailure` 实现会触发清理。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java`

**修改目的**：与 `SparkPositionDeltaWrite` 完全对应的修复，把 `SparkWrite` 的 `cleanupOnAbort` 默认改为不清理，并按异常类型决定是否清理。

**工作逻辑**：

1. 字段默认值 `cleanupOnAbort = true;` 改为 `cleanupOnAbort = false;`。
2. import 从 `CommitStateUnknownException` 改为 `CleanableFailure`。
3. `commit` 方法的 catch 改为：

```java
} catch (Exception e) {
  cleanupOnAbort = e instanceof CleanableFailure;
  throw e;
}
```

由于 `commit()` 调用 `operation.commit()` 时若抛异常会自动触发 `abort`，因此 `cleanupOnAbort` 是 abort 阶段决定是否删除数据文件的开关。

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java`

**修改目的**：调整 Spark 3.3 测试以反映新的可清理语义，并新增不可清理失败场景测试。

**工作逻辑**：

1. 引入 `CommitFailedException` 的 import。
2. 原 `testCommitFails` 等用例中，将模拟抛出的 `new RuntimeException("Commit Failure")` 改为 `new CommitFailedException("Commit Failure")`，并相应调整断言（用 `hasMessageContaining("Cannot commit rewrite")` 替代精确匹配 `hasMessage("Commit Failure")`，因为 `CommitFailedException` 会被 `commitFileGroups` 的 catch 块包装成 "Cannot commit rewrite..." 消息）。
3. `testParallelSingleCommitWithRewriteFailure` 中将 `new RuntimeException("Rewrite Failed")` 改为 `new CommitFailedException("Rewrite Failed")`，并把断言类型从 `RuntimeException.class` 收紧为 `CommitFailedException.class`。
4. 多次提交场景中 `new RuntimeException("Commit Failed")` 也改为 `CommitFailedException`。
5. 新增 `testCommitFailsWithUncleanableFailure`：mock 抛出 `RuntimeException("Arbitrary Failure")`，断言 `shouldHaveOrphans(table)`，即新生成的文件未被清理，留作孤儿。
6. 新增辅助方法 `shouldHaveOrphans(Table table)`，调用 `deleteOrphanFiles` 动作并断言结果非空。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java`

**修改目的**：Spark 3.4 测试同步应用与 Spark 3.3 相同的修改。

**工作逻辑**：与 Spark 3.3 测试改动一致——`RuntimeException` 改 `CommitFailedException`、调整断言、新增 `testCommitFailsWithUncleanableFailure` 和 `shouldHaveOrphans` 辅助方法。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java`

**修改目的**：Spark 3.5 测试同步应用与 Spark 3.3/3.4 相同的修改。

**工作逻辑**：与上述两个测试文件改动一致——`RuntimeException` 改 `CommitFailedException`、调整断言、新增 `testCommitFailsWithUncleanableFailure` 和 `shouldHaveOrphans` 辅助方法。

## 小结

- **成效**：把 Spark 写入和 actions 的失败清理行为收敛到只在"明确可清理"的失败（`CleanableFailure` 实现类）下执行，避免在网络中断、Catalog 瞬时错误等不确定提交状态场景下误删可能已提交的数据/manifest 文件，从根上消除了一类潜在数据丢失风险。
- **影响范围**：涉及 core 模块 3 个类（`BaseRewriteDataFilesAction`、`RewriteDataFilesCommitManager`、`RewritePositionDeletesCommitManager`）和 spark v3.5 模块 3 个类（`RewriteManifestsSparkAction`、`SparkWrite`、`SparkPositionDeltaWrite`），以及 spark v3.3/v3.4/v3.5 三个版本的 `TestRewriteDataFilesAction` 测试，共 9 个文件，193 行新增、37 行删除。
- **回迁到 1.4.x 的注意事项**：本提交是对提交失败清理语义的关键修正，**强烈建议回迁**到 1.4.x 维护分支，以避免维护版本同样存在误删已提交文件的风险。回迁时需注意：(1) 1.4.x 分支依赖的 `CleanableFailure` 接口必须已存在（该接口位于 iceberg-api 模块，1.4.x 应已具备，否则需先回迁引入该接口的提交）；(2) 1.4.x 对应的 Spark 版本（3.3/3.4/3.5）需同时回迁，本提交已包含三个版本改动；(3) 本提交后续还有一个 `#10546`（提交 0863）专门把 #10373 的 Spark 3.3/3.4 部分单独 backport，1.4.x 若已合入本提交则无需再单独 backport 0863，两者二选一即可；(4) 测试中 `shouldHaveOrphans` 等辅助方法在 1.4.x 现有测试基类中若已有不同实现需注意合并冲突；(5) 行为变化：原本"提交失败就清理"的用例现在需要抛 `CommitFailedException` 才能触发清理，下游自定义 action/测试若依赖原行为也需同步调整。
