# 提交 0864：Spark: Backport #10373 to Spark 3.3 and 3.4 (#10546)

## 提交信息

- **序号**：0864 / 4088
- **哈希**：e57b9f69543886236297db43923a08994f9e18c4
- **短哈希**：e57b9f695
- **日期**：2024-06-21（Fri Jun 21 07:33:26 2024 -0700）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Spark: Backport #10373 to Spark 3.3 and 3.4 (#10546)
- **PR/Issue**：#10546（backport 自 #10373）

## 总体目的

本提交是上一个提交 #10373（提交 0861）的补丁性 backport。#10373 在 main 分支上对 core 模块（`BaseRewriteDataFilesAction`、`RewriteDataFilesCommitManager`、`RewritePositionDeletesCommitManager`）、Spark 3.5 模块（`RewriteManifestsSparkAction`、`SparkWrite`、`SparkPositionDeltaWrite`）以及三个 Spark 版本的测试都做了"按 `CleanableFailure` 判定后才清理"的修改。

然而 #10373 只覆盖了 Spark 3.5 模块的主代码（spark v3.5/spark/src/main/...），Spark 3.3 与 Spark 3.4 的 `RewriteManifestsSparkAction`、`SparkWrite`、`SparkPositionDeltaWrite` 主代码并未同步改动（虽然 #10373 的测试改动覆盖了三个版本，但 main 代码只改了 3.5）。这导致 Spark 3.3 和 3.4 仍然存在"提交失败无条件清理已写文件"的隐患，与 #10373 的设计意图不一致。

本提交的目的是把同样的"按 `CleanableFailure` 判定"修复补丁应用到 Spark 3.3 与 3.4 的三个主代码文件上，确保所有受支持的 Spark 版本在写入/actions 失败清理行为上一致，避免 Spark 3.3/3.4 用户继续暴露在误删已提交文件的风险下。

## 如何达成设计目的

实现思路与 #10373 完全一致，只是作用对象从 spark/v3.5 改为 spark/v3.3 与 spark/v3.4：

1. 在 `RewriteManifestsSparkAction` 的 `catch (Exception e)` 中把无条件 `deleteFiles(...)` 包裹在 `if (e instanceof CleanableFailure)` 中。
2. 在 `SparkWrite` 与 `SparkPositionDeltaWrite` 中，把 `cleanupOnAbort` 默认值由 `true` 改为 `false`，并把 `catch (CommitStateUnknownException)` 改为 `catch (Exception e)`，按 `e instanceof CleanableFailure` 设置 `cleanupOnAbort`。

本提交只改主代码，不包含测试改动（#10373 已包含三个版本的测试改动）。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：在 Spark 3.3 manifest 重写动作的提交失败清理路径上加入 `CleanableFailure` 判定。

**工作逻辑**：

```java
} catch (Exception e) {
  if (e instanceof CleanableFailure) {
    // delete all new manifests because the rewrite failed
    deleteFiles(Iterables.transform(addedManifests, ManifestFile::path));
  }

  throw e;
}
```

只有可清理异常才删除新生成的 manifest 文件，避免在不确定提交状态时误删已提交 manifest。同时新增 `CleanableFailure` 的 import。

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java`

**修改目的**：将 Spark 3.3 `SparkPositionDeltaWrite` 的 `cleanupOnAbort` 默认值改为不清理，并按异常类型决定是否清理。

**工作逻辑**：

1. 字段默认值 `cleanupOnAbort = true;` 改为 `cleanupOnAbort = false;`。
2. import 从 `CommitStateUnknownException` 改为 `CleanableFailure`。
3. `commit` 方法 catch 块改为：

```java
} catch (Exception e) {
  cleanupOnAbort = e instanceof CleanableFailure;
  throw e;
}
```

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java`

**修改目的**：与 `SparkPositionDeltaWrite` 对应的 Spark 3.3 写入清理修复。

**工作逻辑**：与 `SparkPositionDeltaWrite` 完全一致——字段默认值改 `false`、import 改 `CleanableFailure`、catch 改为 `catch (Exception e)` 并按 `instanceof CleanableFailure` 设置 `cleanupOnAbort`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：在 Spark 3.4 manifest 重写动作的提交失败清理路径上加入 `CleanableFailure` 判定。

**工作逻辑**：与 Spark 3.3 对应文件改动完全一致。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java`

**修改目的**：将 Spark 3.4 `SparkPositionDeltaWrite` 的 `cleanupOnAbort` 默认值改为不清理，并按异常类型决定是否清理。

**工作逻辑**：与 Spark 3.3 对应文件改动完全一致。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java`

**修改目的**：与 `SparkPositionDeltaWrite` 对应的 Spark 3.4 写入清理修复。

**工作逻辑**：与 Spark 3.3 对应文件改动完全一致。

## 小结

- **成效**：把 #10373 的清理安全修复补齐到 Spark 3.3 与 Spark 3.4 主代码，使三个 Spark 版本（3.3/3.4/3.5）的写入与 actions 失败清理行为一致——都只在 `CleanableFailure` 时清理新生成文件。
- **影响范围**：6 个文件，分布在 spark/v3.3 与 spark/v3.4 两个模块下，共 32 行新增、24 行删除。无测试改动（测试已由 #10373 处理）。
- **回迁到 1.4.x 的注意事项**：本提交与 #10373（提交 0861）是配套关系，**应作为一个整体回迁到 1.4.x**。如果 1.4.x 分支已 cherry-pick #10373（0861），则 0861 已包含 Spark 3.3/3.4 测试改动但 main 代码只改了 3.5，因此 1.4.x 上**仍需要单独回迁本提交（0863）**才能让 Spark 3.3/3.4 主代码行为一致；若 1.4.x 上只回迁本提交而不回迁 0861，则 Spark 3.5、core 模块的清理逻辑以及所有版本的测试不会被修复，因此两者都需要回迁。回迁时需确认 1.4.x 分支上 `CleanableFailure` 接口已存在；本提交是纯 main 代码改动，与 #10373 测试改动不冲突，可放心 cherry-pick。
