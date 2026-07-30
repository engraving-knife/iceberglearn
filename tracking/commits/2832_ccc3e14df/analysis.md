# 提交 2832：Spark: Improve namespace existence verification logic (#14507)

## 提交信息

- **序号**：2832 / 4088
- **哈希**：ccc3e14df9a3a76be5c7b469c8a662d2da363400
- **短哈希**：ccc3e14df
- **日期**：2025-11-05 16:37:35 +0100
- **作者**：roryqi
- **提交说明**：Spark: Improve namespace existence verification logic (#14507)
- **PR/Issue**：#14507

## 总体目的

Spark 的 `TableCatalog` 接口在较新版本中提供了 `namespaceExists(String[] namespace)` 默认方法。默认实现通常通过尝试 `loadNamespaceMetadata` 并捕获 `NoSuchNamespaceException` 来判断 namespace 是否存在，这种"试错"方式既低效（要抛/捕异常），也会在某些 catalog 实现下产生副作用或额外日志。

Iceberg 的 `SparkCatalog` 持有一个底层 `asNamespaceCatalog`（实现了 `SupportsNamespaces` 的 Iceberg catalog）。`SupportsNamespaces` 本身已经有原生的 `namespaceExists(Namespace)` 方法，可以直接、高效地返回布尔结果。该提交重写 `SparkCatalog.namespaceExists`，让它直接委托给底层 Iceberg catalog 的 `namespaceExists`，避免异常驱动的存在性检查，提升性能与语义准确性。

修改同时应用到 Spark 3.4、3.5、4.0 三个版本分支。

## 如何达成设计目的

在每个版本分支的 `SparkCatalog.java` 中新增 `namespaceExists(String[] namespace)` 方法：

1. 先判断 `asNamespaceCatalog != null`，即当前 catalog 是否真的支持 namespace 操作；若不支持直接返回 false（避免 NPE）。
2. 若支持，调用 `asNamespaceCatalog.namespaceExists(Namespace.of(namespace))` 把 Spark 的 `String[]` 转成 Iceberg 的 `Namespace`，再委托给底层实现。
3. 使用 `@Override` 表明这是对 `TableCatalog` 默认方法的覆盖。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+6/-0 lines)

**修改目的**：在 Spark 3.4 版本的 `SparkCatalog` 中覆盖 `namespaceExists`，直接委托底层 catalog。

**工作逻辑**：
```java
@Override
public boolean namespaceExists(String[] namespace) {
    return asNamespaceCatalog != null
        && asNamespaceCatalog.namespaceExists(Namespace.of(namespace));
}
```
短路求值确保 `asNamespaceCatalog` 为 null 时直接返回 false，不会调用其方法。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+6/-0 lines)

**修改目的**：与 3.4 相同的修改应用到 Spark 3.5 版本。逻辑完全一致。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+6/-0 lines)

**修改目的**：与 3.4 相同的修改应用到 Spark 4.0 版本。逻辑完全一致。

## 总结

该提交通过覆盖 `SparkCatalog.namespaceExists` 直接调用底层 Iceberg `SupportsNamespaces.namespaceExists`，替代了 Spark 默认的"抛异常试错"实现，提升了 namespace 存在性检查的性能与语义正确性。修改同步应用到 Spark 3.4/3.5/4.0 三个版本，保持版本间一致性。这与后续 2840（table/view/function existence verification）属于同一系列的存在性验证改进。
