# 提交 3579：Validate manifest sequence numbers are equal during inheritance (#16091)

## 提交信息

- **序号**：3579 / 4088
- **哈希**：8f611675e343c845c9df6e56718f1492aeb143e0
- **短哈希**：8f611675e
- **日期**：2026-04-23 16:53:45 -0700
- **作者**：Anoop Johnson
- **提交说明**：Validate manifest sequence numbers are equal during inheritance (#16091)
- **PR/Issue**：#16091

## 总体目的

该提交在 `TrackingStruct.inheritFrom` 方法中添加了验证清单（manifest）的数据序列号（data sequence number）和文件序列号（file sequence number）是否相等的检查。在 Iceberg 中，清单文件不区分数据序列号和文件序列号——它们只有一个序列号。之前 `inheritFrom` 方法在继承跟踪元数据时，直接使用文件序列号作为数据序列号的默认值，但未验证两者是否相等。

由于清单只有一个序列号，如果出现数据序列号和文件序列号不等的情况，说明数据不一致或存在 bug。该提交通过 `Preconditions.checkArgument` 在继承时验证两者相等，如果不等则抛出 `IllegalArgumentException`，帮助及早发现问题。同时更新了测试，将辅助方法 `createManifestTracking` 从接收两个不同的序列号参数改为接收一个统一的序列号参数。

## 如何达成设计目的

在 `inheritFrom` 方法中，在继承序列号逻辑之前添加 `Preconditions.checkArgument` 检查 `manifestTracking.dataSequenceNumber()` 和 `manifestTracking.fileSequenceNumber()` 是否相等。同时更新测试辅助方法使其只接收一个序列号参数，并新增专门测试不等序列号抛出异常的用例。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TrackingStruct.java` (+12/-3 lines)

**修改目的**：添加序列号相等的验证。

**工作逻辑**：
在 `inheritFrom` 方法中，继承序列号之前添加验证：
```java
Preconditions.checkArgument(
    Objects.equals(
        manifestTracking.dataSequenceNumber(), manifestTracking.fileSequenceNumber()),
    "Manifest data and file sequence numbers must be equal, got %s and %s",
    manifestTracking.dataSequenceNumber(),
    manifestTracking.fileSequenceNumber());
```
如果两个序列号不等，抛出 `IllegalArgumentException` 并包含具体的不等值。验证通过后，ADDED 状态的条目从文件序列号继承数据序列号。

### `core/src/test/java/org/apache/iceberg/TestTrackingStruct.java` (+36/-14 lines)

**修改目的**：更新测试以匹配新的验证逻辑。

**工作逻辑**：
- 辅助方法 `createManifestTracking` 从 `(snapshotId, dataSequenceNumber, fileSequenceNumber)` 改为 `(snapshotId, sequenceNumber)`，数据序列号和文件序列号设为相同值。
- 所有已有测试调用更新为使用单个序列号参数。
- 新增 `testInheritFromRejectsUnequalSequenceNumbers` 测试：构造数据序列号(50)和文件序列号(60)不等的 manifestTracking，验证 `inheritFrom` 抛出 `IllegalArgumentException`，消息包含 "Manifest data and file sequence numbers must be equal, got 50 and 60"。

## 总结

该提交添加了对清单序列号一致性的验证，确保在继承跟踪元数据时数据序列号和文件序列号相等。由于清单文件只有一个序列号，这个检查有助于及早发现数据不一致问题。这是一个防御性编程改进，通过 fail-fast 原则提高代码的健壮性。
