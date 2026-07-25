/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.spark;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.NullOrder;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.UpdateProperties;
import org.apache.iceberg.UpdateSchema;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.ExpressionVisitors;
import org.apache.iceberg.expressions.Term;
import org.apache.iceberg.expressions.UnboundPredicate;
import org.apache.iceberg.expressions.Zorder;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.io.BaseEncoding;
import org.apache.iceberg.spark.SparkTableUtil.SparkPartition;
import org.apache.iceberg.spark.source.HasIcebergCatalog;
import org.apache.iceberg.spark.source.SparkTable;
import org.apache.iceberg.transforms.PartitionSpecVisitor;
import org.apache.iceberg.transforms.SortOrderVisitor;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.iceberg.util.Pair;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.CatalystTypeConverters;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.apache.spark.sql.catalyst.parser.ParseException;
import org.apache.spark.sql.catalyst.parser.ParserInterface;
import org.apache.spark.sql.connector.catalog.CatalogManager;
import org.apache.spark.sql.connector.catalog.CatalogPlugin;
import org.apache.spark.sql.connector.catalog.CatalogV2Implicits;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.Table;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.catalog.TableChange;
import org.apache.spark.sql.connector.expressions.Expression;
import org.apache.spark.sql.connector.expressions.Expressions;
import org.apache.spark.sql.connector.expressions.Literal;
import org.apache.spark.sql.connector.expressions.NamedReference;
import org.apache.spark.sql.connector.expressions.Transform;
import org.apache.spark.sql.execution.datasources.FileStatusCache;
import org.apache.spark.sql.execution.datasources.InMemoryFileIndex;
import org.apache.spark.sql.execution.datasources.PartitionDirectory;
import org.apache.spark.sql.types.IntegerType;
import org.apache.spark.sql.types.LongType;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;
import scala.Option;
import scala.collection.JavaConverters;
import scala.collection.immutable.Seq;

/**
 * Iceberg Spark 集成相关组件。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 Spark3Util。
 */
public class Spark3Util {

  private static final Set<String> RESERVED_PROPERTIES =
      ImmutableSet.of(TableCatalog.PROP_LOCATION, TableCatalog.PROP_PROVIDER);
  private static final Joiner DOT = Joiner.on(".");

  /** 构造 Spark3Util 实例。 */
  private Spark3Util() {}

  /** 设置option。 */
  public static CaseInsensitiveStringMap setOption(
      String key, String value, CaseInsensitiveStringMap options) {
    Map<String, String> newOptions = Maps.newHashMap();
    newOptions.putAll(options);
    newOptions.put(key, value);
    /** 执行该方法的具体逻辑。 */
    return new CaseInsensitiveStringMap(newOptions);
  }

  /** 执行该方法的具体逻辑。 */
  public static Map<String, String> rebuildCreateProperties(Map<String, String> createProperties) {
    ImmutableMap.Builder<String, String> tableProperties = ImmutableMap.builder();
    createProperties.entrySet().stream()
        .filter(entry -> !RESERVED_PROPERTIES.contains(entry.getKey()))
        .forEach(tableProperties::put);

    String provider = createProperties.get(TableCatalog.PROP_PROVIDER);
    if ("parquet".equalsIgnoreCase(provider)) {
      tableProperties.put(TableProperties.DEFAULT_FILE_FORMAT, "parquet");
    } else if ("avro".equalsIgnoreCase(provider)) {
      tableProperties.put(TableProperties.DEFAULT_FILE_FORMAT, "avro");
    } else if ("orc".equalsIgnoreCase(provider)) {
      tableProperties.put(TableProperties.DEFAULT_FILE_FORMAT, "orc");
    } else if (provider != null && !"iceberg".equalsIgnoreCase(provider)) {
      throw new IllegalArgumentException("Unsupported format in USING: " + provider);
    }

    return tableProperties.build();
  }

  /** 执行核心逻辑。 */
  public static UpdateProperties applyPropertyChanges(
      UpdateProperties pendingUpdate, List<TableChange> changes) {
    for (TableChange change : changes) {
      if (change instanceof TableChange.SetProperty) {
        TableChange.SetProperty set = (TableChange.SetProperty) change;
        pendingUpdate.set(set.property(), set.value());

      } else if (change instanceof TableChange.RemoveProperty) {
        TableChange.RemoveProperty remove = (TableChange.RemoveProperty) change;
        pendingUpdate.remove(remove.property());

      } else {
        throw new UnsupportedOperationException("Cannot apply unknown table change: " + change);
      }
    }

    return pendingUpdate;
  }

  /** 执行核心逻辑。 */
  public static UpdateSchema applySchemaChanges(
      UpdateSchema pendingUpdate, List<TableChange> changes) {
    for (TableChange change : changes) {
      if (change instanceof TableChange.AddColumn) {
        apply(pendingUpdate, (TableChange.AddColumn) change);

      } else if (change instanceof TableChange.UpdateColumnType) {
        TableChange.UpdateColumnType update = (TableChange.UpdateColumnType) change;
        Type newType = SparkSchemaUtil.convert(update.newDataType());
        Preconditions.checkArgument(
            newType.isPrimitiveType(),
            "Cannot update '%s', not a primitive type: %s",
            DOT.join(update.fieldNames()),
            update.newDataType());
        pendingUpdate.updateColumn(DOT.join(update.fieldNames()), newType.asPrimitiveType());

      } else if (change instanceof TableChange.UpdateColumnComment) {
        TableChange.UpdateColumnComment update = (TableChange.UpdateColumnComment) change;
        pendingUpdate.updateColumnDoc(DOT.join(update.fieldNames()), update.newComment());

      } else if (change instanceof TableChange.RenameColumn) {
        TableChange.RenameColumn rename = (TableChange.RenameColumn) change;
        pendingUpdate.renameColumn(DOT.join(rename.fieldNames()), rename.newName());

      } else if (change instanceof TableChange.DeleteColumn) {
        TableChange.DeleteColumn delete = (TableChange.DeleteColumn) change;
        pendingUpdate.deleteColumn(DOT.join(delete.fieldNames()));

      } else if (change instanceof TableChange.UpdateColumnNullability) {
        TableChange.UpdateColumnNullability update = (TableChange.UpdateColumnNullability) change;
        if (update.nullable()) {
          pendingUpdate.makeColumnOptional(DOT.join(update.fieldNames()));
        } else {
          pendingUpdate.requireColumn(DOT.join(update.fieldNames()));
        }

      } else if (change instanceof TableChange.UpdateColumnPosition) {
        apply(pendingUpdate, (TableChange.UpdateColumnPosition) change);

      } else {
        throw new UnsupportedOperationException("Cannot apply unknown table change: " + change);
      }
    }

    return pendingUpdate;
  }

  /** 执行核心逻辑。 */
  private static void apply(UpdateSchema pendingUpdate, TableChange.UpdateColumnPosition update) {
    Preconditions.checkArgument(update.position() != null, "Invalid position: null");

    if (update.position() instanceof TableChange.After) {
      TableChange.After after = (TableChange.After) update.position();
      String referenceField = peerName(update.fieldNames(), after.column());
      pendingUpdate.moveAfter(DOT.join(update.fieldNames()), referenceField);

    } else if (update.position() instanceof TableChange.First) {
      pendingUpdate.moveFirst(DOT.join(update.fieldNames()));

    } else {
      throw new IllegalArgumentException("Unknown position for reorder: " + update.position());
    }
  }

  /** 执行核心逻辑。 */
  private static void apply(UpdateSchema pendingUpdate, TableChange.AddColumn add) {
    Preconditions.checkArgument(
        add.isNullable(),
        "Incompatible change: cannot add required column: %s",
        leafName(add.fieldNames()));
    Type type = SparkSchemaUtil.convert(add.dataType());
    pendingUpdate.addColumn(
        parentName(add.fieldNames()), leafName(add.fieldNames()), type, add.comment());

    if (add.position() instanceof TableChange.After) {
      TableChange.After after = (TableChange.After) add.position();
      String referenceField = peerName(add.fieldNames(), after.column());
      pendingUpdate.moveAfter(DOT.join(add.fieldNames()), referenceField);

    } else if (add.position() instanceof TableChange.First) {
      pendingUpdate.moveFirst(DOT.join(add.fieldNames()));

    } else {
      Preconditions.checkArgument(
          add.position() == null,
          "Cannot add '%s' at unknown position: %s",
          DOT.join(add.fieldNames()),
          add.position());
    }
  }

  /** 转换为icebergtable。 */
  public static org.apache.iceberg.Table toIcebergTable(Table table) {
    Preconditions.checkArgument(
        table instanceof SparkTable, "Table %s is not an Iceberg table", table);
    SparkTable sparkTable = (SparkTable) table;
    return sparkTable.table();
  }

  /** 转换为transforms。 */
  public static Transform[] toTransforms(PartitionSpec spec) {
    Map<Integer, String> quotedNameById = SparkSchemaUtil.indexQuotedNameById(spec.schema());
    List<Transform> transforms =
        PartitionSpecVisitor.visit(
            spec,
            new PartitionSpecVisitor<Transform>() {
              /**
               * 执行该方法的具体逻辑。
               *
               * @param sourceName 参数
               * @param sourceId 参数
               * @return 结果对象
               */
              @Override
              public Transform identity(String sourceName, int sourceId) {
                return Expressions.identity(quotedName(sourceId));
              }

              /**
               * 执行该方法的具体逻辑。
               *
               * @param sourceName 参数
               * @param sourceId 参数
               * @param numBuckets 参数
               * @return 结果对象
               */
              @Override
              public Transform bucket(String sourceName, int sourceId, int numBuckets) {
                return Expressions.bucket(numBuckets, quotedName(sourceId));
              }

              /**
               * 执行该方法的具体逻辑。
               *
               * @param sourceName 参数
               * @param sourceId 参数
               * @param width 参数
               * @return 结果对象
               */
              @Override
              public Transform truncate(String sourceName, int sourceId, int width) {
                return Expressions.apply(
                    "truncate",
                    Expressions.column(quotedName(sourceId)),
                    Expressions.literal(width));
              }

              /**
               * 执行该方法的具体逻辑。
               *
               * @param sourceName 参数
               * @param sourceId 参数
               * @return 结果对象
               */
              @Override
              public Transform year(String sourceName, int sourceId) {
                return Expressions.years(quotedName(sourceId));
              }

              /**
               * 执行该方法的具体逻辑。
               *
               * @param sourceName 参数
               * @param sourceId 参数
               * @return 结果对象
               */
              @Override
              public Transform month(String sourceName, int sourceId) {
                return Expressions.months(quotedName(sourceId));
              }

              /**
               * 执行该方法的具体逻辑。
               *
               * @param sourceName 参数
               * @param sourceId 参数
               * @return 结果对象
               */
              @Override
              public Transform day(String sourceName, int sourceId) {
                return Expressions.days(quotedName(sourceId));
              }

              /**
               * 执行该方法的具体逻辑。
               *
               * @param sourceName 参数
               * @param sourceId 参数
               * @return 结果对象
               */
              @Override
              public Transform hour(String sourceName, int sourceId) {
                return Expressions.hours(quotedName(sourceId));
              }

              /**
               * 执行该方法的具体逻辑。
               *
               * @param fieldId 参数
               * @param sourceName 参数
               * @param sourceId 参数
               * @return 结果对象
               */
              @Override
              public Transform alwaysNull(int fieldId, String sourceName, int sourceId) {
                // do nothing for alwaysNull, it doesn't need to be converted to a transform
                return null;
              }

              /**
               * 执行该方法的具体逻辑。
               *
               * @param fieldId 参数
               * @param sourceName 参数
               * @param sourceId 参数
               * @param transform 参数
               * @return 结果对象
               */
              @Override
              public Transform unknown(
                  int fieldId, String sourceName, int sourceId, String transform) {
                return Expressions.apply(transform, Expressions.column(quotedName(sourceId)));
              }

              /** 执行该方法的具体逻辑。 */
              private String quotedName(int id) {
                return quotedNameById.get(id);
              }
            });

    return transforms.stream().filter(Objects::nonNull).toArray(Transform[]::new);
  }

  /** 转换为namedreference。 */
  public static NamedReference toNamedReference(String name) {
    return Expressions.column(name);
  }

  /** 转换为icebergterm。 */
  public static Term toIcebergTerm(Expression expr) {
    if (expr instanceof Transform) {
      Transform transform = (Transform) expr;
      Preconditions.checkArgument(
          "zorder".equals(transform.name()) || transform.references().length == 1,
          "Cannot convert transform with more than one column reference: %s",
          transform);
      String colName = DOT.join(transform.references()[0].fieldNames());
      switch (transform.name().toLowerCase(Locale.ROOT)) {
        case "identity":
          return org.apache.iceberg.expressions.Expressions.ref(colName);
        case "bucket":
          return org.apache.iceberg.expressions.Expressions.bucket(colName, findWidth(transform));
        case "year":
        case "years":
          return org.apache.iceberg.expressions.Expressions.year(colName);
        case "month":
        case "months":
          return org.apache.iceberg.expressions.Expressions.month(colName);
        case "date":
        case "day":
        case "days":
          return org.apache.iceberg.expressions.Expressions.day(colName);
        case "date_hour":
        case "hour":
        case "hours":
          return org.apache.iceberg.expressions.Expressions.hour(colName);
        case "truncate":
          return org.apache.iceberg.expressions.Expressions.truncate(colName, findWidth(transform));
        case "zorder":
          /** 执行该方法的具体逻辑。 */
          return new Zorder(
              Stream.of(transform.references())
                  .map(ref -> DOT.join(ref.fieldNames()))
                  .map(org.apache.iceberg.expressions.Expressions::ref)
                  .collect(Collectors.toList()));
        default:
          throw new UnsupportedOperationException("Transform is not supported: " + transform);
      }

    } else if (expr instanceof NamedReference) {
      NamedReference ref = (NamedReference) expr;
      return org.apache.iceberg.expressions.Expressions.ref(DOT.join(ref.fieldNames()));

    } else {
      throw new UnsupportedOperationException("Cannot convert unknown expression: " + expr);
    }
  }

  /** 转换为partitionspec。 */
  public static PartitionSpec toPartitionSpec(Schema schema, Transform[] partitioning) {
    if (partitioning == null || partitioning.length == 0) {
      return PartitionSpec.unpartitioned();
    }

    PartitionSpec.Builder builder = PartitionSpec.builderFor(schema);
    for (Transform transform : partitioning) {
      Preconditions.checkArgument(
          transform.references().length == 1,
          "Cannot convert transform with more than one column reference: %s",
          transform);
      String colName = DOT.join(transform.references()[0].fieldNames());
      switch (transform.name().toLowerCase(Locale.ROOT)) {
        case "identity":
          builder.identity(colName);
          break;
        case "bucket":
          builder.bucket(colName, findWidth(transform));
          break;
        case "year":
        case "years":
          builder.year(colName);
          break;
        case "month":
        case "months":
          builder.month(colName);
          break;
        case "date":
        case "day":
        case "days":
          builder.day(colName);
          break;
        case "date_hour":
        case "hour":
        case "hours":
          builder.hour(colName);
          break;
        case "truncate":
          builder.truncate(colName, findWidth(transform));
          break;
        default:
          throw new UnsupportedOperationException("Transform is not supported: " + transform);
      }
    }

    return builder.build();
  }

  /** 查找并返回结果。 */
  @SuppressWarnings("unchecked")
  private static int findWidth(Transform transform) {
    for (Expression expr : transform.arguments()) {
      if (expr instanceof Literal) {
        if (((Literal) expr).dataType() instanceof IntegerType) {
          Literal<Integer> lit = (Literal<Integer>) expr;
          Preconditions.checkArgument(
              lit.value() > 0, "Unsupported width for transform: %s", transform.describe());
          return lit.value();

        } else if (((Literal) expr).dataType() instanceof LongType) {
          Literal<Long> lit = (Literal<Long>) expr;
          Preconditions.checkArgument(
              lit.value() > 0 && lit.value() < Integer.MAX_VALUE,
              "Unsupported width for transform: %s",
              transform.describe());
          if (lit.value() > Integer.MAX_VALUE) {
            /** 执行该方法的具体逻辑。 */
            throw new IllegalArgumentException();
          }
          return lit.value().intValue();
        }
      }
    }

    throw new IllegalArgumentException("Cannot find width for transform: " + transform.describe());
  }

  /** 执行该方法的具体逻辑。 */
  private static String leafName(String[] fieldNames) {
    Preconditions.checkArgument(
        fieldNames.length > 0, "Invalid field name: at least one name is required");
    return fieldNames[fieldNames.length - 1];
  }

  /** 执行该方法的具体逻辑。 */
  private static String peerName(String[] fieldNames, String fieldName) {
    if (fieldNames.length > 1) {
      String[] peerNames = Arrays.copyOf(fieldNames, fieldNames.length);
      peerNames[fieldNames.length - 1] = fieldName;
      return DOT.join(peerNames);
    }
    return fieldName;
  }

  /** 执行该方法的具体逻辑。 */
  private static String parentName(String[] fieldNames) {
    if (fieldNames.length > 1) {
      return DOT.join(Arrays.copyOfRange(fieldNames, 0, fieldNames.length - 1));
    }
    return null;
  }

  /** 返回描述信息。 */
  public static String describe(org.apache.iceberg.expressions.Expression expr) {
    return ExpressionVisitors.visit(expr, DescribeExpressionVisitor.INSTANCE);
  }

  /** 返回描述信息。 */
  public static String describe(Schema schema) {
    return TypeUtil.visit(schema, DescribeSchemaVisitor.INSTANCE);
  }

  /** 返回描述信息。 */
  public static String describe(Type type) {
    return TypeUtil.visit(type, DescribeSchemaVisitor.INSTANCE);
  }

  /** 返回描述信息。 */
  public static String describe(org.apache.iceberg.SortOrder order) {
    return Joiner.on(", ").join(SortOrderVisitor.visit(order, DescribeSortOrderVisitor.INSTANCE));
  }

  /** 执行该方法的具体逻辑。 */
  public static boolean extensionsEnabled(SparkSession spark) {
    String extensions = spark.conf().get("spark.sql.extensions", "");
    return extensions.contains("IcebergSparkSessionExtensions");
  }

  /**
   * Iceberg Spark 集成相关组件。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 DescribeSchemaVisitor。
   *
   * <p>设计意图：访问者模式，按类型分派处理逻辑。
   */
  public static class DescribeSchemaVisitor extends TypeUtil.SchemaVisitor<String> {
    private static final Joiner COMMA = Joiner.on(',');
    private static final DescribeSchemaVisitor INSTANCE = new DescribeSchemaVisitor();

    /** 构造 DescribeSchemaVisitor 实例。 */
    private DescribeSchemaVisitor() {}

    /**
     * 执行该方法的具体逻辑。
     *
     * @param schema 参数
     * @param structResult 参数
     * @return 结果对象
     */
    @Override
    public String schema(Schema schema, String structResult) {
      return structResult;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param struct 参数
     * @param fieldResults 参数
     * @return 结果对象
     */
    @Override
    public String struct(Types.StructType struct, List<String> fieldResults) {
      return "struct<" + COMMA.join(fieldResults) + ">";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param field 参数
     * @param fieldResult 参数
     * @return 结果对象
     */
    @Override
    public String field(Types.NestedField field, String fieldResult) {
      return field.name() + ": " + fieldResult + (field.isRequired() ? " not null" : "");
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param list 参数
     * @param elementResult 参数
     * @return 结果对象
     */
    @Override
    public String list(Types.ListType list, String elementResult) {
      return "list<" + elementResult + ">";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param map 参数
     * @param keyResult 参数
     * @param valueResult 参数
     * @return 结果对象
     */
    @Override
    public String map(Types.MapType map, String keyResult, String valueResult) {
      return "map<" + keyResult + ", " + valueResult + ">";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param primitive 参数
     * @return 结果对象
     */
    @Override
    public String primitive(Type.PrimitiveType primitive) {
      switch (primitive.typeId()) {
        case BOOLEAN:
          return "boolean";
        case INTEGER:
          return "int";
        case LONG:
          return "bigint";
        case FLOAT:
          return "float";
        case DOUBLE:
          return "double";
        case DATE:
          return "date";
        case TIME:
          return "time";
        case TIMESTAMP:
          return "timestamp";
        case STRING:
        case UUID:
          return "string";
        case FIXED:
        case BINARY:
          return "binary";
        case DECIMAL:
          Types.DecimalType decimal = (Types.DecimalType) primitive;
          return "decimal(" + decimal.precision() + "," + decimal.scale() + ")";
      }
      throw new UnsupportedOperationException("Cannot convert type to SQL: " + primitive);
    }
  }

  /**
   * Iceberg Spark 集成相关组件，表示或转换 Spark 表达式。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 DescribeExpressionVisitor。
   *
   * <p>设计意图：访问者模式，按类型分派处理逻辑。
   */
  private static class DescribeExpressionVisitor
      extends ExpressionVisitors.ExpressionVisitor<String> {
    private static final DescribeExpressionVisitor INSTANCE = new DescribeExpressionVisitor();

    /** 构造 DescribeExpressionVisitor 实例。 */
    private DescribeExpressionVisitor() {}

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public String alwaysTrue() {
      return "true";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public String alwaysFalse() {
      return "false";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param result 参数
     * @return 结果对象
     */
    @Override
    public String not(String result) {
      return "NOT (" + result + ")";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param leftResult 参数
     * @param rightResult 参数
     * @return 结果对象
     */
    @Override
    public String and(String leftResult, String rightResult) {
      return "(" + leftResult + " AND " + rightResult + ")";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param leftResult 参数
     * @param rightResult 参数
     * @return 结果对象
     */
    @Override
    public String or(String leftResult, String rightResult) {
      return "(" + leftResult + " OR " + rightResult + ")";
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    public <T> String predicate(BoundPredicate<T> pred) {
      throw new UnsupportedOperationException("Cannot convert bound predicates to SQL");
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    public <T> String predicate(UnboundPredicate<T> pred) {
      switch (pred.op()) {
        case IS_NULL:
          return pred.ref().name() + " IS NULL";
        case NOT_NULL:
          return pred.ref().name() + " IS NOT NULL";
        case IS_NAN:
          return "is_nan(" + pred.ref().name() + ")";
        case NOT_NAN:
          return "not_nan(" + pred.ref().name() + ")";
        case LT:
          return pred.ref().name() + " < " + sqlString(pred.literal());
        case LT_EQ:
          return pred.ref().name() + " <= " + sqlString(pred.literal());
        case GT:
          return pred.ref().name() + " > " + sqlString(pred.literal());
        case GT_EQ:
          return pred.ref().name() + " >= " + sqlString(pred.literal());
        case EQ:
          return pred.ref().name() + " = " + sqlString(pred.literal());
        case NOT_EQ:
          return pred.ref().name() + " != " + sqlString(pred.literal());
        case STARTS_WITH:
          return pred.ref().name() + " LIKE '" + pred.literal() + "%'";
        case NOT_STARTS_WITH:
          return pred.ref().name() + " NOT LIKE '" + pred.literal() + "%'";
        case IN:
          return pred.ref().name() + " IN (" + sqlString(pred.literals()) + ")";
        case NOT_IN:
          return pred.ref().name() + " NOT IN (" + sqlString(pred.literals()) + ")";
        default:
          throw new UnsupportedOperationException("Cannot convert predicate to SQL: " + pred);
      }
    }

    /** 执行该方法的具体逻辑。 */
    private static <T> String sqlString(List<org.apache.iceberg.expressions.Literal<T>> literals) {
      return literals.stream()
          .map(DescribeExpressionVisitor::sqlString)
          .collect(Collectors.joining(", "));
    }

    /** 执行该方法的具体逻辑。 */
    private static String sqlString(org.apache.iceberg.expressions.Literal<?> lit) {
      if (lit.value() instanceof String) {
        return "'" + lit.value() + "'";
      } else if (lit.value() instanceof ByteBuffer) {
        byte[] bytes = ByteBuffers.toByteArray((ByteBuffer) lit.value());
        return "X'" + BaseEncoding.base16().encode(bytes) + "'";
      } else {
        return lit.value().toString();
      }
    }
  }

  /** 执行该方法的具体逻辑。 */
  public static org.apache.iceberg.Table loadIcebergTable(SparkSession spark, String name)
      throws ParseException, NoSuchTableException {
    CatalogAndIdentifier catalogAndIdentifier = catalogAndIdentifier(spark, name);

    TableCatalog catalog = asTableCatalog(catalogAndIdentifier.catalog);
    Table sparkTable = catalog.loadTable(catalogAndIdentifier.identifier);
    return toIcebergTable(sparkTable);
  }

  /** 执行该方法的具体逻辑。 */
  public static Catalog loadIcebergCatalog(SparkSession spark, String catalogName) {
    CatalogPlugin catalogPlugin = spark.sessionState().catalogManager().catalog(catalogName);
    Preconditions.checkArgument(
        catalogPlugin instanceof HasIcebergCatalog,
        String.format(
            "Cannot load Iceberg catalog from catalog %s because it does not contain an Iceberg Catalog. "
                + "Actual Class: %s",
            catalogName, catalogPlugin.getClass().getName()));
    return ((HasIcebergCatalog) catalogPlugin).icebergCatalog();
  }

  /** 执行该方法的具体逻辑。 */
  public static CatalogAndIdentifier catalogAndIdentifier(SparkSession spark, String name)
      throws ParseException {
    return catalogAndIdentifier(
        spark, name, spark.sessionState().catalogManager().currentCatalog());
  }

  /** 执行该方法的具体逻辑。 */
  public static CatalogAndIdentifier catalogAndIdentifier(
      SparkSession spark, String name, CatalogPlugin defaultCatalog) throws ParseException {
    ParserInterface parser = spark.sessionState().sqlParser();
    Seq<String> multiPartIdentifier = parser.parseMultipartIdentifier(name).toIndexedSeq();
    List<String> javaMultiPartIdentifier = JavaConverters.seqAsJavaList(multiPartIdentifier);
    return catalogAndIdentifier(spark, javaMultiPartIdentifier, defaultCatalog);
  }

  /** 执行该方法的具体逻辑。 */
  public static CatalogAndIdentifier catalogAndIdentifier(
      String description, SparkSession spark, String name) {
    return catalogAndIdentifier(
        description, spark, name, spark.sessionState().catalogManager().currentCatalog());
  }

  /** 执行该方法的具体逻辑。 */
  public static CatalogAndIdentifier catalogAndIdentifier(
      String description, SparkSession spark, String name, CatalogPlugin defaultCatalog) {
    try {
      return catalogAndIdentifier(spark, name, defaultCatalog);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Cannot parse " + description + ": " + name, e);
    }
  }

  /** 执行该方法的具体逻辑。 */
  public static CatalogAndIdentifier catalogAndIdentifier(
      SparkSession spark, List<String> nameParts) {
    return catalogAndIdentifier(
        spark, nameParts, spark.sessionState().catalogManager().currentCatalog());
  }

  /** 执行该方法的具体逻辑。 */
  public static CatalogAndIdentifier catalogAndIdentifier(
      SparkSession spark, List<String> nameParts, CatalogPlugin defaultCatalog) {
    CatalogManager catalogManager = spark.sessionState().catalogManager();

    String[] currentNamespace;
    if (defaultCatalog.equals(catalogManager.currentCatalog())) {
      currentNamespace = catalogManager.currentNamespace();
    } else {
      currentNamespace = defaultCatalog.defaultNamespace();
    }

    Pair<CatalogPlugin, Identifier> catalogIdentifier =
        SparkUtil.catalogAndIdentifier(
            nameParts,
            catalogName -> {
              try {
                return catalogManager.catalog(catalogName);
              } catch (Exception e) {
                return null;
              }
            },
            Identifier::of,
            defaultCatalog,
            currentNamespace);
    /** 执行该方法的具体逻辑。 */
    return new CatalogAndIdentifier(catalogIdentifier);
  }

  /** 执行该方法的具体逻辑。 */
  private static TableCatalog asTableCatalog(CatalogPlugin catalog) {
    if (catalog instanceof TableCatalog) {
      return (TableCatalog) catalog;
    }

    throw new IllegalArgumentException(
        String.format(
            "Cannot use catalog %s(%s): not a TableCatalog",
            catalog.name(), catalog.getClass().getName()));
  }

  /**
   * Iceberg Spark 集成相关组件，实现 Spark 目录服务以加载和管理 Iceberg 表。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 CatalogAndIdentifier。
   */
  public static class CatalogAndIdentifier {
    private final CatalogPlugin catalog;
    private final Identifier identifier;

    /** 构造 CatalogAndIdentifier 实例。 */
    public CatalogAndIdentifier(CatalogPlugin catalog, Identifier identifier) {
      this.catalog = catalog;
      this.identifier = identifier;
    }

    /** 构造 CatalogAndIdentifier 实例。 */
    public CatalogAndIdentifier(Pair<CatalogPlugin, Identifier> identifier) {
      this.catalog = identifier.first();
      this.identifier = identifier.second();
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public CatalogPlugin catalog() {
      return catalog;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public Identifier identifier() {
      return identifier;
    }
  }

  /** 执行该方法的具体逻辑。 */
  public static TableIdentifier identifierToTableIdentifier(Identifier identifier) {
    return TableIdentifier.of(Namespace.of(identifier.namespace()), identifier.name());
  }

  /** 执行该方法的具体逻辑。 */
  public static String quotedFullIdentifier(String catalogName, Identifier identifier) {
    List<String> parts =
        ImmutableList.<String>builder()
            .add(catalogName)
            .addAll(Arrays.asList(identifier.namespace()))
            .add(identifier.name())
            .build();

    return CatalogV2Implicits.MultipartIdentifierHelper(
            JavaConverters.asScalaIteratorConverter(parts.iterator()).asScala().toSeq())
        .quoted();
  }

  /**
   * 返回partitions。
   *
   * @deprecated 请改用带 distribute 参数的重载方法。
   */
  @Deprecated
  public static List<SparkPartition> getPartitions(
      SparkSession spark, Path rootPath, String format, Map<String, String> partitionFilter) {
    return getPartitions(spark, rootPath, format, partitionFilter, null);
  }

  /** 返回partitions。 */
  public static List<SparkPartition> getPartitions(
      SparkSession spark,
      Path rootPath,
      String format,
      Map<String, String> partitionFilter,
      PartitionSpec partitionSpec) {
    FileStatusCache fileStatusCache = FileStatusCache.getOrCreate(spark);

    Option<StructType> userSpecifiedSchema =
        partitionSpec == null
            ? Option.empty()
            : Option.apply(
                SparkSchemaUtil.convert(new Schema(partitionSpec.partitionType().fields())));

    InMemoryFileIndex fileIndex =
        new InMemoryFileIndex(
            spark,
            JavaConverters.collectionAsScalaIterableConverter(ImmutableList.of(rootPath))
                .asScala()
                .toSeq(),
            scala.collection.immutable.Map$.MODULE$.<String, String>empty(),
            userSpecifiedSchema,
            fileStatusCache,
            Option.empty(),
            Option.empty());

    org.apache.spark.sql.execution.datasources.PartitionSpec spec = fileIndex.partitionSpec();
    StructType schema = spec.partitionColumns();
    if (schema.isEmpty()) {
      return Lists.newArrayList();
    }

    List<org.apache.spark.sql.catalyst.expressions.Expression> filterExpressions =
        SparkUtil.partitionMapToExpression(schema, partitionFilter);
    Seq<org.apache.spark.sql.catalyst.expressions.Expression> scalaPartitionFilters =
        JavaConverters.asScalaBufferConverter(filterExpressions).asScala().toIndexedSeq();

    List<org.apache.spark.sql.catalyst.expressions.Expression> dataFilters = Lists.newArrayList();
    Seq<org.apache.spark.sql.catalyst.expressions.Expression> scalaDataFilters =
        JavaConverters.asScalaBufferConverter(dataFilters).asScala().toIndexedSeq();

    Seq<PartitionDirectory> filteredPartitions =
        fileIndex.listFiles(scalaPartitionFilters, scalaDataFilters).toIndexedSeq();

    return JavaConverters.seqAsJavaListConverter(filteredPartitions).asJava().stream()
        .map(
            partition -> {
              Map<String, String> values = Maps.newHashMap();
              JavaConverters.asJavaIterableConverter(schema)
                  .asJava()
                  .forEach(
                      field -> {
                        int fieldIndex = schema.fieldIndex(field.name());
                        Object catalystValue = partition.values().get(fieldIndex, field.dataType());
                        Object value =
                            CatalystTypeConverters.convertToScala(catalystValue, field.dataType());
                        values.put(field.name(), String.valueOf(value));
                      });

              FileStatus fileStatus =
                  JavaConverters.seqAsJavaListConverter(partition.files()).asJava().get(0);

              /** 执行该方法的具体逻辑。 */
              return new SparkPartition(
                  values, fileStatus.getPath().getParent().toString(), format);
            })
        .collect(Collectors.toList());
  }

  /** 转换为v1tableidentifier。 */
  public static org.apache.spark.sql.catalyst.TableIdentifier toV1TableIdentifier(
      Identifier identifier) {
    String[] namespace = identifier.namespace();

    Preconditions.checkArgument(
        namespace.length <= 1,
        "Cannot convert %s to a Spark v1 identifier, namespace contains more than 1 part",
        identifier);

    String table = identifier.name();
    Option<String> database = namespace.length == 1 ? Option.apply(namespace[0]) : Option.empty();
    return org.apache.spark.sql.catalyst.TableIdentifier.apply(table, database);
  }

  /**
   * Iceberg Spark 集成相关组件，实现排序相关逻辑。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 DescribeSortOrderVisitor。
   *
   * <p>设计意图：访问者模式，按类型分派处理逻辑。
   */
  private static class DescribeSortOrderVisitor implements SortOrderVisitor<String> {
    private static final DescribeSortOrderVisitor INSTANCE = new DescribeSortOrderVisitor();

    /** 构造 DescribeSortOrderVisitor 实例。 */
    private DescribeSortOrderVisitor() {}

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sourceName 参数
     * @param sourceId 参数
     * @param direction 参数
     * @param nullOrder 参数
     * @return 结果对象
     */
    @Override
    public String field(
        String sourceName,
        int sourceId,
        org.apache.iceberg.SortDirection direction,
        NullOrder nullOrder) {
      return String.format("%s %s %s", sourceName, direction, nullOrder);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sourceName 参数
     * @param sourceId 参数
     * @param numBuckets 参数
     * @param direction 参数
     * @param nullOrder 参数
     * @return 结果对象
     */
    @Override
    public String bucket(
        String sourceName,
        int sourceId,
        int numBuckets,
        org.apache.iceberg.SortDirection direction,
        NullOrder nullOrder) {
      return String.format("bucket(%s, %s) %s %s", numBuckets, sourceName, direction, nullOrder);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sourceName 参数
     * @param sourceId 参数
     * @param width 参数
     * @param direction 参数
     * @param nullOrder 参数
     * @return 结果对象
     */
    @Override
    public String truncate(
        String sourceName,
        int sourceId,
        int width,
        org.apache.iceberg.SortDirection direction,
        NullOrder nullOrder) {
      return String.format("truncate(%s, %s) %s %s", sourceName, width, direction, nullOrder);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sourceName 参数
     * @param sourceId 参数
     * @param direction 参数
     * @param nullOrder 参数
     * @return 结果对象
     */
    @Override
    public String year(
        String sourceName,
        int sourceId,
        org.apache.iceberg.SortDirection direction,
        NullOrder nullOrder) {
      return String.format("years(%s) %s %s", sourceName, direction, nullOrder);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sourceName 参数
     * @param sourceId 参数
     * @param direction 参数
     * @param nullOrder 参数
     * @return 结果对象
     */
    @Override
    public String month(
        String sourceName,
        int sourceId,
        org.apache.iceberg.SortDirection direction,
        NullOrder nullOrder) {
      return String.format("months(%s) %s %s", sourceName, direction, nullOrder);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sourceName 参数
     * @param sourceId 参数
     * @param direction 参数
     * @param nullOrder 参数
     * @return 结果对象
     */
    @Override
    public String day(
        String sourceName,
        int sourceId,
        org.apache.iceberg.SortDirection direction,
        NullOrder nullOrder) {
      return String.format("days(%s) %s %s", sourceName, direction, nullOrder);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sourceName 参数
     * @param sourceId 参数
     * @param direction 参数
     * @param nullOrder 参数
     * @return 结果对象
     */
    @Override
    public String hour(
        String sourceName,
        int sourceId,
        org.apache.iceberg.SortDirection direction,
        NullOrder nullOrder) {
      return String.format("hours(%s) %s %s", sourceName, direction, nullOrder);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sourceName 参数
     * @param sourceId 参数
     * @param transform 参数
     * @param direction 参数
     * @param nullOrder 参数
     * @return 结果对象
     */
    @Override
    public String unknown(
        String sourceName,
        int sourceId,
        String transform,
        org.apache.iceberg.SortDirection direction,
        NullOrder nullOrder) {
      return String.format("%s(%s) %s %s", transform, sourceName, direction, nullOrder);
    }
  }
}
