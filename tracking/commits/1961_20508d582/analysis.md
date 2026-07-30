# 提交 1961：Core: Update deprecation msg (#12720)

## 提交信息

- **序号**：1961 / 4088
- **哈希**：20508d582d5054bd824af33295b546e7833d789b
- **短哈希**：20508d582
- **日期**：2025-04-04 08:34:49 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Update deprecation msg (#12720)
- **PR/Issue**：#12720

## 总体目的

上一个提交（1960，#12670）在 `MetadataUpdate.RemoveSnapshot` 类上添加了 `@Deprecated` 注解，Javadoc 中写的是 "will be removed in 2.0.0"。但根据实际的弃用策略，该弃用类应在 1.10.0 版本移除而非 2.0.0。本提交修正该弃用消息中的版本号，使弃用说明与计划一致。

## 如何达成设计目的

直接修改 `MetadataUpdate.java` 中 `RemoveSnapshot` 类的 `@deprecated` Javadoc 文本，将 "will be removed in 2.0.0" 改为 "will be removed in 1.10.0"。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetadataUpdate.java` (修改, +1/-1 lines)

**修改目的**：修正弃用版本号。

**工作逻辑**：将 `RemoveSnapshot` 的 Javadoc 由：
```
@deprecated since 1.9.0, will be removed in 2.0.0; Use {@link MetadataUpdate.RemoveSnapshots} instead.
```
改为：
```
@deprecated since 1.9.0, will be removed in 1.10.0; Use {@link MetadataUpdate.RemoveSnapshots} instead.
```

## 总结

本提交修正 `MetadataUpdate.RemoveSnapshot` 弃用 Javadoc 中的移除版本，从 2.0.0 改为 1.10.0，使弃用计划与实际策略一致。
