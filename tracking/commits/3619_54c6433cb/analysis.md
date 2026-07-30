# 提交 3619：Flink: Fix JdbcLockFactory to allow ClientPoolImpl connection retry (#16049)

## 提交信息

- **序号**：3619 / 4088
- **哈希**：54c6433cbd08457cb43f8968efa5cb26917a3bef
- **短哈希**：54c6433cb
- **日期**：2026-04-30 13:55:51 +0200
- **作者**：Anupam Yadav
- **提交说明**：Flink: Fix JdbcLockFactory to allow ClientPoolImpl connection retry (#16049)
- **PR/Issue**：#16049

## 总体目的

这个提交修复了 Flink 的 `JdbcLockFactory` 中过早捕获 `SQLException` 导致 `ClientPoolImpl` 无法进行连接重试的问题。

`JdbcLockFactory` 使用 JDBC 连接池来管理锁操作。`ClientPoolImpl` 内置了连接重试机制，当数据库连接出现问题时，它会自动重试。然而，`JdbcLockFactory` 在两个位置（删除锁信息和获取锁信息）捕获了 `SQLException` 并立即将其包装为 `UncheckedSQLException` 抛出，这阻止了 `ClientPoolImpl` 的重试机制生效。

当数据库暂时不可用（如网络抖动、数据库重启）时，`SQLException` 会被提前捕获并转换为不可恢复的异常，而不是让连接池进行重试。这导致锁操作在短暂的数据库中断时失败，而不是自动恢复。

## 如何达成设计目的

移除两处 `catch (SQLException e)` 块，让 `SQLException` 向上传播到 `ClientPoolImpl`，由连接池决定是否重试。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/JdbcLockFactory.java` (+0/-7 lines)

**修改目的**：移除过早的 SQLException 捕获，允许连接池重试。

**工作逻辑**：

1. **删除锁信息的位置**（约 260 行）：移除了：
```java
} catch (SQLException e) {
  // SQL exception happened when deleting lock information
  throw new UncheckedSQLException(
      e, "Failed to delete %s lock with instanceId %s", this, instanceId);
}
```

2. **获取锁信息的位置**（约 298 行）：移除了：
```java
} catch (SQLException e) {
  // SQL exception happened when getting lock information
  throw new UncheckedSQLException(e, "Failed to get lock information for %s", type);
}
```

移除后，`SQLException` 会自然向上传播到调用者（`ClientPoolImpl`），连接池可以根据自身的重试策略进行处理。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestJdbcLockFactory.java` (+65/-0 lines)

**修改目的**：添加测试验证连接重试行为。

**工作逻辑**：
新增测试验证当数据库连接出现暂时性故障时，`JdbcLockFactory` 能够通过 `ClientPoolImpl` 的重试机制自动恢复，而不是立即失败。

### Flink 1.20 和 2.0 版本

同样的修改（各 +58/-7 lines）也应用到了 `flink/v1.20/` 和 `flink/v2.0/` 目录下的对应文件。

## 总结

这个提交修复了 `JdbcLockFactory` 中过早捕获 `SQLException` 阻止连接池重试的问题。通过移除两处 catch 块，让 `SQLException` 传播到 `ClientPoolImpl`，使锁操作能够在数据库暂时不可用时自动重试，提高了系统的容错能力。修复同时应用于 Flink 1.20、2.0 和 2.1 三个版本。
