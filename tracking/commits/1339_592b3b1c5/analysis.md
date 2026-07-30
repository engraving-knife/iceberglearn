# 提交 1339：Spark 3.5: Preserve content offset and size during manifest rewrites (#11469)

## 提交信息

- **序号**：1339 / 4088
- **哈希**：592b3b1c51fb0302bc4e970fccfc462a49009ad2
- **短哈希**：592b3b1c5
- **日期**：2024-11-05（Tue Nov 5 08:50:39 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Spark 3.5: Preserve content offset and size during manifest rewrites (#11469)
- **PR/Issue**：#11469

## 总体目的

这是 1337（在 Spark manifest 重写中保留 `referencedDataFile`）的"姊妹提交"，针对的是 1335 引入的两个 DV 字段——`contentOffset` 与 `contentSizeInBytes`。同样的问题：`SparkContentFile` 此前没有实现这两个新增字段的读取，导致 manifest 重写走 Spark 路径时这两个 DV 必需字段会丢失（读到 `null`），从而 DV 在 manifest rewrite 后无法定位 Puffin blob。

本提交在 `SparkContentFile` 中补上这两个字段的列位置解析与读取实现，并扩展 1337 引入的 `testRewriteManifestsPreservesOptionalFields` 测试，使其在 V3 表（`formatVersion >= 3`）上额外用 DV（Puffin 格式的位置删除）做一次 manifest rewrite 验证，确认 `contentOffset`、`contentSizeInBytes`、`referencedDataFile` 在重写后都能保留。

这形成了 DV 端到端最小验证链：写入带 DV 的 V3 表 → Spark manifest rewrite → 读取并验证 DV 字段完整。

## 如何达成设计目的

1. 在 `SparkContentFile` 中新增 `contentOffsetPosition`、`contentSizePosition` 两个字段位置；在构造方法中通过 `positions.get(DataFile.CONTENT_OFFSET.name())` 与 `positions.get(DataFile.CONTENT_SIZE.name())` 取得列位置；新增 `contentOffset()`、`contentSizeInBytes()` 两个方法（null safe 读取 Long）。
2. 在 `TestRewriteManifestsAction` 中：
   - 在参数化测试矩阵新增一组 `{"false", "false", false, 3}`（与原有组重复参数但 ID 不同，用于多触发一次测试运行覆盖 DV 路径）。
   - 把 1337 测试中 `newDeleteFileWithRef(table, dataFileN)` 改为 `newDeletes(table, dataFileN)`，后者按 `formatVersion` 选择 DV（V3）或带 ref 的 position delete（V2）。
   - 新增 `assertDeletes(dataFile, deleteFile)`：对 V3 断言 `contentOffset`、`contentSizeInBytes` 非 null；对 V2 断言为 null。
   - 新增 `assertEqual(d1, d2)`：除 location/content/specId/partition/format/referencedDataFile 外，新增 `contentOffset`、`contentSizeInBytes` 比较。
   - 新增私有方法 `newDV(table, dataFile)` 转调 `FileGenerationUtil.generateDV`。
3. 这样测试既覆盖了 V2 的"位置删除带 referencedDataFile"路径，也覆盖了 V3 的"DV 带 content offset/size"路径，且在重写后两种场景都验证字段保留。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java`

**修改目的**：让 Spark 行包装的 `ContentFile` 也能读取 1335 新增的 DV 字段。

**工作逻辑**：

新增字段位置：

```java
private final int contentOffsetPosition;
private final int contentSizePosition;
```

在构造方法中初始化：

```java
this.contentOffsetPosition = positions.get(DataFile.CONTENT_OFFSET.name());
this.contentSizePosition = positions.get(DataFile.CONTENT_SIZE.name());
```

新增两个方法（null safe 读 Long）：

```java
public Long contentOffset() {
    if (wrapped.isNullAt(contentOffsetPosition)) {
        return null;
    }
    return wrapped.getLong(contentOffsetPosition);
}

public Long contentSizeInBytes() {
    if (wrapped.isNullAt(contentSizePosition)) {
        return null;
    }
    return wrapped.getLong(contentSizePosition);
}
```

注意：这两个字段在 manifest entry schema 中是 `optional`，因此读取时必须先 `isNullAt` 判断。这是本提交唯一的产品代码改动。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`

**修改目的**：扩展 manifest 重写测试覆盖 V3 DV 场景。

**工作逻辑**：

1. 参数化矩阵新增第 4 组（实际参数与第 3 组相同，但 JUnit 会作为独立运行触发更多覆盖）：

```java
new Object[] {"false", "false", false, 2},
new Object[] {"false", "false", false, 3}   // 新增
```

2. 测试方法 `testRewriteManifestsPreservesOptionalFields` 中替换三处：

```java
// 旧
DeleteFile deleteFile1 = newDeleteFileWithRef(table, dataFile1);
assertThat(deleteFile1.referencedDataFile()).isEqualTo(dataFile1.location());
// 新
DeleteFile deleteFile1 = newDeletes(table, dataFile1);
assertDeletes(dataFile1, deleteFile1);
```

3. 重写后断言增加 `assertEqual(deleteFile, deleteFileN)`，做完整字段对比：

```java
if (dataFile.location().equals(dataFile1.location())) {
    assertThat(deleteFile.referencedDataFile()).isEqualTo(deleteFile1.referencedDataFile());
    assertEqual(deleteFile, deleteFile1);
}
```

4. 新增私有方法：

```java
private DeleteFile newDeletes(Table table, DataFile dataFile) {
    return formatVersion >= 3 ? newDV(table, dataFile) : newDeleteFileWithRef(table, dataFile);
}

private DeleteFile newDV(Table table, DataFile dataFile) {
    return FileGenerationUtil.generateDV(table, dataFile);
}

private void assertDeletes(DataFile dataFile, DeleteFile deleteFile) {
    assertThat(deleteFile.referencedDataFile()).isEqualTo(dataFile.location());
    if (formatVersion >= 3) {
        assertThat(deleteFile.contentOffset()).isNotNull();
        assertThat(deleteFile.contentSizeInBytes()).isNotNull();
    } else {
        assertThat(deleteFile.contentOffset()).isNull();
        assertThat(deleteFile.contentSizeInBytes()).isNull();
    }
}

private void assertEqual(DeleteFile deleteFile1, DeleteFile deleteFile2) {
    assertThat(deleteFile1.location()).isEqualTo(deleteFile2.location());
    assertThat(deleteFile1.content()).isEqualTo(deleteFile2.content());
    assertThat(deleteFile1.specId()).isEqualTo(deleteFile2.specId());
    assertThat(deleteFile1.partition()).isEqualTo(deleteFile2.partition());
    assertThat(deleteFile1.format()).isEqualTo(deleteFile2.format());
    assertThat(deleteFile1.referencedDataFile()).isEqualTo(deleteFile2.referencedDataFile());
    assertThat(deleteFile1.contentOffset()).isEqualTo(deleteFile2.contentOffset());
    assertThat(deleteFile1.contentSizeInBytes()).isEqualTo(deleteFile2.contentSizeInBytes());
}
```

这样 V2 测试用 position delete + referencedDataFile（content offset/size 为 null），V3 测试用 DV（content offset/size 非 null），覆盖两种路径。

## 小结

- **成效**：补齐 Spark 3.5 manifest 重写路径对 1335 引入的 DV 字段（`content_offset`、`content_size_in_bytes`）的读取，使 DV 在经过 Spark manifest rewrite 后仍能正确定位 Puffin blob；测试同时覆盖 V2 位置删除与 V3 DV 两条路径。
- **影响范围**：1 个产品文件（`SparkContentFile.java`，+18 行）、1 个测试文件（`TestRewriteManifestsAction.java`，+48/-7 行），共 2 个文件、+59/-7 行。
- **回迁到 1.4.x 的注意事项**：
  1. 本提交与 1335、1337 强耦合：**必须**在 1335（DV 字段）和 1337（`referencedDataFile` 保留）已回迁的基础上才有意义。如果 1.4.x 不引入 DV 整体特性，则本提交可跳过。
  2. 1339 强依赖 1335 提供的 `contentOffset()`、`contentSizeInBytes()` 接口与 `DataFile.CONTENT_OFFSET`、`DataFile.CONTENT_SIZE` 字段常量；缺失则 `SparkContentFile` 编译不通过。
  3. 测试 `newDV` 依赖 1335 在 `FileGenerationUtil` 中新增的 `generateDV` 方法；缺失则测试编译不通过。
  4. 若 1.4.x 决定回迁 DV 整组特性（1334→1335→1336→1337→1338→1339→1340），本提交应作为该组的一部分一同回迁；单独回迁无意义且不可编译。
