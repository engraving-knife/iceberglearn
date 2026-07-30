# 提交 2492：nit: Make style consistent (#13794)

## 提交信息

- **序号**：2492 / 4088
- **哈希**：226f9dbea70a1c54351857fc22dfec0fc5b2baba
- **短哈希**：226f9dbea
- **日期**：2025-08-13 08:34:06 +0200
- **作者**：Fokko Driesprong
- **提交说明**：nit: Make style consistent (#13794)
- **PR/Issue**：#13794

## 总体目的

本提交是一个代码风格统一的小改动。作者在检查 PyIceberg 相关逻辑时，注意到 `MetadataUpdateParser` 中 `REMOVE_SNAPSHOTS` 分支的写法与其他分支风格不一致。

在该 switch 分支中，原本将类型转换和写入操作分成了两步：先声明一个局部变量 `removeSnapshots`，再赋值，最后调用写入方法。而文件中其他分支（如 `ADD_SNAPSHOT`、`REMOVE_SNAPSHOT_REF` 等）都采用直接在方法调用处进行类型转换的内联写法。本提交将 `REMOVE_SNAPSHOTS` 分支改为与其他分支一致的内联写法。

## 如何达成设计目的

将原来的三行代码：
```java
MetadataUpdate.RemoveSnapshots removeSnapshots;
removeSnapshots = (MetadataUpdate.RemoveSnapshots) metadataUpdate;
writeRemoveSnapshots(removeSnapshots, generator);
```
简化为一行内联写法：
```java
writeRemoveSnapshots((MetadataUpdate.RemoveSnapshots) metadataUpdate, generator);
```

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetadataUpdateParser.java` (+1/-3 lines)

**修改目的**：统一 switch 分支的代码风格。

**工作逻辑**：在 `write` 方法的 `REMOVE_SNAPSHOTS` case 中，去掉先声明后赋值的局部变量，改为直接在 `writeRemoveSnapshots` 调用参数中进行类型转换，与同文件中其他 case 分支保持一致。

## 总结

本提交是一个纯粹的代码风格统一改动，不涉及任何功能逻辑变更。通过消除不一致的写法，提升了代码可读性和可维护性，便于后续开发者理解代码风格约定。
