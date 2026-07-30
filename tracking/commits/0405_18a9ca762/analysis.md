# 提交 0405：Build: Fix errorprone warning (#9531)

## 提交信息

- **序号**：0405
- **哈希**：18a9ca7624159e12437ddd540a9726f5ac5f4b35
- **短哈希**：18a9ca762
- **日期**：2024-01-23（AuthorDate: 2024-01-23 12:28:26 +0530；CommitDate: 2024-01-23 07:58:26 +0100）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Build: Fix errorprone warning (#9531)
- **PR/Issue**：#9531

## 总体目的

Errorprone 是 Google 开发的 javac 插件，在编译期对 Java 代码做静态 bug 模式检查，Iceberg 通过 `baseline.gradle` 把一批 errorprone 检查以 `ERROR` 级别启用，任何违反都会让构建失败。本提交的目的是把 `CollectionUndefinedEquality` 这个新的检查项加入 ERROR 级别启用列表，并修复它在一处现有代码上触发的告警，从而让 CI 在加入该检查后仍然能通过。

`CollectionUndefinedEquality` 检查针对的是：当代码对元素/键类型"相等性未定义"的集合调用 `get`/`contains`/`remove` 等依赖 `equals` 的方法时报错。最典型的就是 `CharSequence`——Java 规范里 `CharSequence` 接口本身没有定义 `equals`/`hashCode` 语义，因此一个 `String` 和一个 `StringBuilder` 即便内容相同也不相等；用 `CharSequence` 作为 `Map` 的键去 `get` 在 errorprone 看来就是潜在的 bug 来源。Iceberg 的 `BaseDeleteLoader.getOrReadPosDeletes` 正好命中了这个模式：它用 `CharSequenceMap<PositionDeleteIndex>` 并以 `CharSequence filePath` 为键调用 `getOrDefault`。

但这里的微妙之处是：`CharSequenceMap` 是 Iceberg 自己实现的 Map，它**特意**重写了 `CharSequence` 键的相等性比较（按内容比较，而不是 `Object.equals`），所以这里的 `getOrDefault` 实际上是安全的。errorprone 无法识别这个自定义 Map 的特殊语义，于是产生误报。因此提交选择"启用检查 + 在误报处抑制"的组合，而不是"不启用检查"——前者把检查推广到全项目其他真正可能有 bug 的地方，后者会因为一处误报而放弃整条检查线。

## 如何达成设计目的

实现分两步：(1) 在 `baseline.gradle` 的 errorprone 配置数组中追加 `'-Xep:CollectionUndefinedEquality:ERROR'`，把该检查升为 ERROR 级别（构建失败级）；(2) 在 `BaseDeleteLoader.getOrReadPosDeletes` 方法上加 `@SuppressWarnings("CollectionUndefinedEquality")`，明确标记此处为已审计的误报。这是一种"宽启用、窄抑制"的静态治理策略：让新检查覆盖全项目，仅在确认为误报的局部点显式抑制并留下审计痕迹。

## 修改详情

### baseline.gradle

**修改目的**：把 `CollectionUndefinedEquality` 检查升为 ERROR 级别并加入项目的 errorprone 启用列表。

**工作逻辑**：`baseline.gradle` 在 `subprojects` 块里配置 errorprone，`errorprone.errorprone.args` 数组列出了所有以 ERROR 级别启用的检查项（如 `-Xep:IntLongMath:ERROR`、`-Xep:MissingSummary:ERROR`、`-Xep:AnnotateFormatMethod:ERROR` 等）。本次在末尾追加一行：

```groovy
'-Xep:CollectionUndefinedEquality:ERROR',
```

这意味着此后任何子模块编译时，只要 errorprone 检测到"对元素相等性未定义的集合做依赖 equals 的操作"，构建即失败。这是一次单向收紧：启用后无法再写出新的此类代码，旧代码必须修复或显式抑制。

### data/src/main/java/org/apache/iceberg/data/BaseDeleteLoader.java

**修改目的**：抑制 errorprone 在 `getOrReadPosDeletes` 上的 `CollectionUndefinedEquality` 误报。

**工作逻辑**：在方法签名上加注解：

```java
@SuppressWarnings("CollectionUndefinedEquality")
private PositionDeleteIndex getOrReadPosDeletes(DeleteFile deleteFile, CharSequence filePath) {
  long estimatedSize = estimatePosDeletesSize(deleteFile);
  if (canCache(estimatedSize)) {
    String cacheKey = deleteFile.path().toString();
    CharSequenceMap<PositionDeleteIndex> indexes =
        getOrLoad(cacheKey, () -> readPosDeletes(deleteFile), estimatedSize);
    return indexes.getOrDefault(filePath, PositionDeleteIndex.empty());
  } else {
    return readPosDeletes(deleteFile, filePath);
  }
}
```

触发检查的是 `indexes.getOrDefault(filePath, ...)`：`indexes` 类型为 `CharSequenceMap<PositionDeleteIndex>`，键类型是 `CharSequence`，而 `filePath` 也是 `CharSequence`。errorprone 看到"以 CharSequence 为键做 get"就报警。但实际上 `CharSequenceMap`（`org.apache.iceberg.util.CharSequenceMap`）是 Iceberg 专为 `CharSequence` 键设计的实现，其 `getOrDefault` 按字符内容而非 `Object.equals` 比较键，所以这里**是安全的**——这正是 `@SuppressWarnings` 想表达的"已审计、确认为误报"。注解加在方法级而非更窄的语句级，是因为 Java 的 `@SuppressWarnings` 不支持到单条语句，方法级是该抑制能放的最小范围。

## 小结

本提交是静态检查治理的样板操作："启用一条新 errorprone 检查（升为 ERROR）+ 在唯一一处误报上加 `@SuppressWarnings`"。它体现了几点工程纪律：(1) 不因一处误报就放弃整条检查，而是把检查推广到全项目，防止未来重蹈同类 bug；(2) 抑制必须落在最小作用域（方法级）并明确指明检查名（`CollectionUndefinedEquality`），而不是笼统的 `@SuppressWarnings("all")`，留下可审计痕迹；(3) 修改面极小（两个文件各一行），风险极低，典型的 build/lint 类清理提交。对 1.4.x 用户而言无运行时行为变化，但下游若 fork 了 Iceberg 构建配置或贡献代码，需注意 `CollectionUndefinedEquality` 现已是 ERROR 级别，新增代码不能再以 `CharSequence` 为普通 Map 键做 `get/contains`，要么改用 `CharSequenceMap`，要么显式抑制并说明理由。
