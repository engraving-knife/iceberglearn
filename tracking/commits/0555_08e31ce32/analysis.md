# 提交 0555：修复 FileIO 反射初始化失败时的误导性错误信息

## 提交信息

- **序号**：0555 / 4088
- **哈希**：08e31ce32d506e0dd95e4710ec9e9afba35efa7f
- **短哈希**：08e31ce32
- **日期**：2024-02-29（Thu Feb 29 17:02:34 2024 -0600）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Core: FileIO Reflection Error Message is Misleading (#9840)
- **PR/Issue**：#9840

## 总体目的

`CatalogUtil.loadFileIO(String impl, Map<String, String> properties, Object hadoopConf)` 通过反射加载用户自定义的 `FileIO` 实现。当反射查找构造器失败时（抛出 `NoSuchMethodException`），原代码抛出的 `IllegalArgumentException` 错误信息是：

```
Cannot initialize FileIO, missing no-arg constructor: <impl>
```

这条信息具有误导性：

1. **并非一定是"无参构造器"缺失**：`DynConstructors.builder(FileIO.class).impl(impl).buildChecked()` 在底层会尝试匹配多种构造器候选（包括无参、`Map`、`Configuration` 等），失败原因可能是：构造器存在但为 `private`、构造器参数类型不匹配、类无法加载、安全限制等等。底层 `NoSuchMethodException` 的真实信息是 `"Cannot find constructor for org.apache.iceberg.FileIO\n" + formatProblems(problems)`，列出了所有尝试过的候选及其失败原因。
2. **丢失了底层诊断信息**：原 message 完全没有引用 `e.getMessage()`，用户看到的只是"missing no-arg constructor"，无法判断到底是哪个候选失败了，调试困难。

本提交的目的是让错误信息准确反映真实的失败原因，把底层的诊断细节透传给用户，避免误导。

## 如何达成设计目的

整体思路非常简单：把异常信息从"硬编码猜测原因"改为"展示底层异常的真实 message"。

实现路径：
1. 修改 `CatalogUtil.loadFileIO` 中 `catch (NoSuchMethodException e)` 分支的 `String.format`，把 `"Cannot initialize FileIO, missing no-arg constructor: %s"` 改为 `"Cannot initialize FileIO implementation %s: %s"`，第二个 `%s` 填入 `e.getMessage()`。
2. 同步更新 `TestCatalogUtil` 中对应的断言：把 `hasMessageStartingWith("Cannot initialize FileIO, missing no-arg constructor")` 改为以新格式开头，并显式包含测试用例 `TestFileIOBadArg` 的全限定类名与底层消息前缀 `"Cannot find constructor"`，确保新信息确实透传了底层细节。

设计上的考虑：
- 仍然把 `e` 作为 cause 传入 `IllegalArgumentException`，保留完整的异常链，便于日志工具沿栈追溯。
- 仍然在 message 里包含 `impl`（实现类全限定名），让用户一眼看到出问题的具体实现。
- 不改动其他分支（`ClassCastException` 分支）的错误信息，因为那条信息本身就准确（"does not implement FileIO"）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/CatalogUtil.java`

**修改目的**：把 `loadFileIO` 中构造器查找失败的错误信息从硬编码的"missing no-arg constructor"改为透传底层 `NoSuchMethodException` 的真实 message，避免误导。

**工作逻辑**：

原代码：
```java
} catch (NoSuchMethodException e) {
  throw new IllegalArgumentException(
      String.format("Cannot initialize FileIO, missing no-arg constructor: %s", impl), e);
}
```

新代码：
```java
} catch (NoSuchMethodException e) {
  throw new IllegalArgumentException(
      String.format("Cannot initialize FileIO implementation %s: %s", impl, e.getMessage()), e);
}
```

关键差异：
- 删除"missing no-arg constructor"这一硬编码猜测。
- 加入 `e.getMessage()`，让用户看到 `DynConstructors.buildCheckedException` 生成的真实诊断（形如 `"Cannot find constructor for org.apache.iceberg.FileIO\n" + formatProblems(problems)`），其中 `formatProblems` 会列出每个候选构造器的尝试结果与失败原因。
- 把"FileIO"后加"implementation"一词，让信息更明确——是某个具体的 FileIO 实现类初始化失败，而非 FileIO 接口本身。

例如，对一个只有 `TestFileIOBadArg(String arg)` 构造器的实现，新信息大致为：
```
Cannot initialize FileIO implementation org.apache.iceberg.TestCatalogUtil$TestFileIOBadArg: Cannot find constructor for org.apache.iceberg.FileIO
...
```

### `core/src/test/java/org/apache/iceberg/TestCatalogUtil.java`

**修改目的**：同步更新对应单元测试的断言，确保新错误信息格式被验证。

**工作逻辑**：

测试用例使用 `TestFileIOBadArg`（一个只有 `TestFileIOBadArg(String arg)` 构造器、没有无参构造器的 `FileIO` 实现）触发 `loadFileIO` 的失败路径。原断言：
```java
.hasMessageStartingWith("Cannot initialize FileIO, missing no-arg constructor");
```

新断言：
```java
.hasMessageStartingWith(
    "Cannot initialize FileIO implementation "
        + "org.apache.iceberg.TestCatalogUtil$TestFileIOBadArg: Cannot find constructor");
```

新断言不仅验证了新格式的前缀，还显式断言底层 `NoSuchMethodException` 的 "Cannot find constructor" 文案出现在信息中——这正是修复的核心目标：让底层诊断信息透传到上层错误信息里。`TestFileIOBadArg` 的全限定类名也被写入断言，确保 `impl` 字段确实被正确包含在错误信息中。

## 小结

这是一次小而精的错误信息修复：1 行产品代码改动 + 1 处测试断言更新。它不会改变任何控制流或 API 签名，仅让失败时的诊断信息更准确、更有用。对调试自定义 `FileIO` 实现的用户尤其有帮助——以前看到"missing no-arg constructor"可能误以为只要加个无参构造器就行，但实际上可能是 `private` 修饰符或参数类型不匹配导致的；新信息会直接列出所有失败候选与原因。

**影响范围**：
- 仅影响 `core` 模块，且仅修改一处异常信息字符串。
- 不影响任何成功路径、不影响 API 签名、不影响序列化兼容性。
- 任何依赖旧错误信息文本做匹配的外部代码（极少见，且本就不该依赖异常文本）需要更新匹配模式——但本提交的测试改动也提示了这种外部代码应改为匹配新格式。

**回迁到 1.4.x 的注意事项**：
- 回迁极其安全，无任何运行时风险。
- 1.4.x 中若 `CatalogUtil.loadFileIO` 的 catch 块结构与 main 一致，可直接套用本修复。
- 若 1.4.x 的 `DynConstructors` 版本与 main 不同（如生成的 message 文案略有差异），需要相应调整测试断言中的 `"Cannot find constructor"` 前缀——产品代码本身无需调整，因为它透传 `e.getMessage()`，不依赖具体文案。
- 由于只是改异常文本，不会引起合并冲突，回迁成本极低。
