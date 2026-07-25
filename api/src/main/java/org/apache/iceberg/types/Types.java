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
package org.apache.iceberg.types;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.types.Type.NestedType;
import org.apache.iceberg.types.Type.PrimitiveType;

/**
 * Iceberg 类型系统具体实现集合：定义所有数据类型的内部类与工厂方法。
 *
 * <p>所属模块：iceberg-api（类型系统的核心实现，被 schema、分区、表达式、序列化等模块依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 13 种原始类型（Boolean/Integer/Long/Float/Double/Date/Time/Timestamp/String/UUID/
 *       Fixed/Binary/Decimal）与 3 种嵌套类型（StructType/ListType/MapType）。
 *   <li>定义 {@link NestedField} 表示 struct 字段、list 元素、map 键值，携带字段 ID、可选性与文档。
 *   <li>提供 {@link #fromPrimitiveString(String)} 把类型字符串（如 "int"、"fixed[16]"、"decimal(10,2)"）
 *       解析为原始类型实例。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>无参数的原始类型采用单例（INSTANCE），减少对象创建并保证相等性；带参数的类型 （TimestampType 的 adjustToUTC、FixedType 的
 *       length、DecimalType 的 precision/scale） 自行覆盖 equals/hashCode。
 *   <li>嵌套类型把元素/键值建模为 {@link NestedField}，统一字段 ID 管理，支撑 schema 演进时的 字段增删与 ID 稳定性。
 *   <li>{@link StructType} 的字段索引（按名/按 ID/按小写名）采用 transient 懒加载缓存， 序列化时不保存缓存，反序列化后按需重建。
 *   <li>{@link DecimalType} 限制 precision <= 38，与 IEEE 754 decimal128 对齐。
 * </ul>
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.Schema} 持有；被 {@link TypeUtil} 遍历与改写； 被 {@link
 * Conversions}、{@link Comparators} 用于值转换与比较；被各引擎集成模块用于类型映射。
 */
public class Types {

  private Types() {}

  private static final ImmutableMap<String, PrimitiveType> TYPES =
      ImmutableMap.<String, PrimitiveType>builder()
          .put(BooleanType.get().toString(), BooleanType.get())
          .put(IntegerType.get().toString(), IntegerType.get())
          .put(LongType.get().toString(), LongType.get())
          .put(FloatType.get().toString(), FloatType.get())
          .put(DoubleType.get().toString(), DoubleType.get())
          .put(DateType.get().toString(), DateType.get())
          .put(TimeType.get().toString(), TimeType.get())
          .put(TimestampType.withZone().toString(), TimestampType.withZone())
          .put(TimestampType.withoutZone().toString(), TimestampType.withoutZone())
          .put(StringType.get().toString(), StringType.get())
          .put(UUIDType.get().toString(), UUIDType.get())
          .put(BinaryType.get().toString(), BinaryType.get())
          .buildOrThrow();

  private static final Pattern FIXED = Pattern.compile("fixed\\[\\s*(\\d+)\\s*\\]");
  private static final Pattern DECIMAL =
      Pattern.compile("decimal\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");

  /**
   * 把类型字符串解析为原始类型实例。
   *
   * <p>逻辑：先小写化后查 TYPES 常量映射（覆盖 boolean/int/long/float/double/date/time/
   * timestamptz/timestamp/string/uuid/binary）；未命中再分别用 FIXED、DECIMAL 正则匹配 fixed[N] 与
   * decimal(P,S)；都不匹配则抛 IllegalArgumentException。
   *
   * @param typeString 类型字符串
   * @return 对应的原始类型实例
   * @throws IllegalArgumentException 无法解析时抛出
   */
  public static PrimitiveType fromPrimitiveString(String typeString) {
    String lowerTypeString = typeString.toLowerCase(Locale.ROOT);
    if (TYPES.containsKey(lowerTypeString)) {
      return TYPES.get(lowerTypeString);
    }

    Matcher fixed = FIXED.matcher(lowerTypeString);
    if (fixed.matches()) {
      return FixedType.ofLength(Integer.parseInt(fixed.group(1)));
    }

    Matcher decimal = DECIMAL.matcher(lowerTypeString);
    if (decimal.matches()) {
      return DecimalType.of(Integer.parseInt(decimal.group(1)), Integer.parseInt(decimal.group(2)));
    }

    throw new IllegalArgumentException("Cannot parse type string to primitive: " + typeString);
  }

  /** 布尔类型，单例。 */
  public static class BooleanType extends PrimitiveType {
    private static final BooleanType INSTANCE = new BooleanType();

    public static BooleanType get() {
      return INSTANCE;
    }

    @Override
    public TypeID typeId() {
      return TypeID.BOOLEAN;
    }

    @Override
    public String toString() {
      return "boolean";
    }
  }

  /** 32 位有符号整数类型，单例。 */
  public static class IntegerType extends PrimitiveType {
    private static final IntegerType INSTANCE = new IntegerType();

    public static IntegerType get() {
      return INSTANCE;
    }

    @Override
    public TypeID typeId() {
      return TypeID.INTEGER;
    }

    @Override
    public String toString() {
      return "int";
    }
  }

  /** 64 位有符号长整型类型，单例。 */
  public static class LongType extends PrimitiveType {
    private static final LongType INSTANCE = new LongType();

    public static LongType get() {
      return INSTANCE;
    }

    @Override
    public TypeID typeId() {
      return TypeID.LONG;
    }

    @Override
    public String toString() {
      return "long";
    }
  }

  /** 单精度浮点类型，单例。 */
  public static class FloatType extends PrimitiveType {
    private static final FloatType INSTANCE = new FloatType();

    public static FloatType get() {
      return INSTANCE;
    }

    @Override
    public TypeID typeId() {
      return TypeID.FLOAT;
    }

    @Override
    public String toString() {
      return "float";
    }
  }

  /** 双精度浮点类型，单例。 */
  public static class DoubleType extends PrimitiveType {
    private static final DoubleType INSTANCE = new DoubleType();

    public static DoubleType get() {
      return INSTANCE;
    }

    @Override
    public TypeID typeId() {
      return TypeID.DOUBLE;
    }

    @Override
    public String toString() {
      return "double";
    }
  }

  /**
   * 日期类型：自 1970-01-01 起的天数，以 int 存储，单例。
   *
   * <p>设计要点：与 SQL DATE 语义一致，不含时区与时间分量。
   */
  public static class DateType extends PrimitiveType {
    private static final DateType INSTANCE = new DateType();

    public static DateType get() {
      return INSTANCE;
    }

    @Override
    public TypeID typeId() {
      return TypeID.DATE;
    }

    @Override
    public String toString() {
      return "date";
    }
  }

  /**
   * 时间类型：自午夜起的微秒数，以 long 存储，单例。
   *
   * <p>设计要点：与 SQL TIME WITHOUT TIME ZONE 语义一致，不含日期与时区。
   */
  public static class TimeType extends PrimitiveType {
    private static final TimeType INSTANCE = new TimeType();

    public static TimeType get() {
      return INSTANCE;
    }

    private TimeType() {}

    @Override
    public TypeID typeId() {
      return TypeID.TIME;
    }

    @Override
    public String toString() {
      return "time";
    }
  }

  /**
   * 时间戳类型：自 epoch 起的微秒数，以 long 存储。
   *
   * <p>设计要点：区分带时区（adjustToUTC=true，toString="timestamptz"）与不带时区
   * （adjustToUTC=false，toString="timestamp"）两个单例；因 adjustToUTC 参与相等性判断， 故覆盖 equals/hashCode。
   */
  public static class TimestampType extends PrimitiveType {
    private static final TimestampType INSTANCE_WITH_ZONE = new TimestampType(true);
    private static final TimestampType INSTANCE_WITHOUT_ZONE = new TimestampType(false);

    public static TimestampType withZone() {
      return INSTANCE_WITH_ZONE;
    }

    public static TimestampType withoutZone() {
      return INSTANCE_WITHOUT_ZONE;
    }

    private final boolean adjustToUTC;

    private TimestampType(boolean adjustToUTC) {
      this.adjustToUTC = adjustToUTC;
    }

    public boolean shouldAdjustToUTC() {
      return adjustToUTC;
    }

    @Override
    public TypeID typeId() {
      return TypeID.TIMESTAMP;
    }

    @Override
    public String toString() {
      if (shouldAdjustToUTC()) {
        return "timestamptz";
      } else {
        return "timestamp";
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof TimestampType)) {
        return false;
      }

      TimestampType timestampType = (TimestampType) o;
      return adjustToUTC == timestampType.adjustToUTC;
    }

    @Override
    public int hashCode() {
      return Objects.hash(TimestampType.class, adjustToUTC);
    }
  }

  /** 字符串类型，单例。 */
  public static class StringType extends PrimitiveType {
    private static final StringType INSTANCE = new StringType();

    public static StringType get() {
      return INSTANCE;
    }

    @Override
    public TypeID typeId() {
      return TypeID.STRING;
    }

    @Override
    public String toString() {
      return "string";
    }
  }

  /** UUID 类型，单例。 */
  public static class UUIDType extends PrimitiveType {
    private static final UUIDType INSTANCE = new UUIDType();

    public static UUIDType get() {
      return INSTANCE;
    }

    @Override
    public TypeID typeId() {
      return TypeID.UUID;
    }

    @Override
    public String toString() {
      return "uuid";
    }
  }

  /**
   * 定长二进制类型：长度固定的 byte 数组。
   *
   * <p>设计要点：因 length 参与相等性判断，覆盖 equals/hashCode；toString 为 "fixed[N]"。
   */
  public static class FixedType extends PrimitiveType {
    public static FixedType ofLength(int length) {
      return new FixedType(length);
    }

    private final int length;

    private FixedType(int length) {
      this.length = length;
    }

    public int length() {
      return length;
    }

    @Override
    public TypeID typeId() {
      return TypeID.FIXED;
    }

    @Override
    public String toString() {
      return String.format("fixed[%d]", length);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof FixedType)) {
        return false;
      }

      FixedType fixedType = (FixedType) o;
      return length == fixedType.length;
    }

    @Override
    public int hashCode() {
      return Objects.hash(FixedType.class, length);
    }
  }

  /** 变长二进制类型，单例。 */
  public static class BinaryType extends PrimitiveType {
    private static final BinaryType INSTANCE = new BinaryType();

    public static BinaryType get() {
      return INSTANCE;
    }

    @Override
    public TypeID typeId() {
      return TypeID.BINARY;
    }

    @Override
    public String toString() {
      return "binary";
    }
  }

  /**
   * Decimal 类型：固定精度与标度的十进制数。
   *
   * <p>设计要点：precision 限制 <= 38（与 decimal128 对齐）；precision 与 scale 参与相等性判断， 故覆盖
   * equals/hashCode；toString 为 "decimal(P,S)"。
   */
  public static class DecimalType extends PrimitiveType {
    public static DecimalType of(int precision, int scale) {
      return new DecimalType(precision, scale);
    }

    private final int scale;
    private final int precision;

    private DecimalType(int precision, int scale) {
      Preconditions.checkArgument(
          precision <= 38,
          "Decimals with precision larger than 38 are not supported: %s",
          precision);
      this.scale = scale;
      this.precision = precision;
    }

    public int scale() {
      return scale;
    }

    public int precision() {
      return precision;
    }

    @Override
    public TypeID typeId() {
      return TypeID.DECIMAL;
    }

    @Override
    public String toString() {
      return String.format("decimal(%d, %d)", precision, scale);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof DecimalType)) {
        return false;
      }

      DecimalType that = (DecimalType) o;
      if (scale != that.scale) {
        return false;
      }
      return precision == that.precision;
    }

    @Override
    public int hashCode() {
      return Objects.hash(DecimalType.class, scale, precision);
    }
  }

  /**
   * 嵌套字段：struct 字段、list 元素、map 键值的统一表示。
   *
   * <p>设计要点：字段 ID 在 schema 演进中保持稳定（即使字段重命名），是 Iceberg schema 演进的核心； isOptional 区分可选（允许 null）与必填；doc
   * 携带可选文档说明。不可变值对象，asOptional/asRequired 返回新实例而非原地修改。
   */
  public static class NestedField implements Serializable {
    public static NestedField optional(int id, String name, Type type) {
      return new NestedField(true, id, name, type, null);
    }

    public static NestedField optional(int id, String name, Type type, String doc) {
      return new NestedField(true, id, name, type, doc);
    }

    public static NestedField required(int id, String name, Type type) {
      return new NestedField(false, id, name, type, null);
    }

    public static NestedField required(int id, String name, Type type, String doc) {
      return new NestedField(false, id, name, type, doc);
    }

    public static NestedField of(int id, boolean isOptional, String name, Type type) {
      return new NestedField(isOptional, id, name, type, null);
    }

    public static NestedField of(int id, boolean isOptional, String name, Type type, String doc) {
      return new NestedField(isOptional, id, name, type, doc);
    }

    private final boolean isOptional;
    private final int id;
    private final String name;
    private final Type type;
    private final String doc;

    private NestedField(boolean isOptional, int id, String name, Type type, String doc) {
      Preconditions.checkNotNull(name, "Name cannot be null");
      Preconditions.checkNotNull(type, "Type cannot be null");
      this.isOptional = isOptional;
      this.id = id;
      this.name = name;
      this.type = type;
      this.doc = doc;
    }

    public boolean isOptional() {
      return isOptional;
    }

    public NestedField asOptional() {
      if (isOptional) {
        return this;
      }
      return new NestedField(true, id, name, type, doc);
    }

    public boolean isRequired() {
      return !isOptional;
    }

    public NestedField asRequired() {
      if (!isOptional) {
        return this;
      }
      return new NestedField(false, id, name, type, doc);
    }

    public int fieldId() {
      return id;
    }

    public String name() {
      return name;
    }

    public Type type() {
      return type;
    }

    public String doc() {
      return doc;
    }

    @Override
    public String toString() {
      return String.format("%d: %s: %s %s", id, name, isOptional ? "optional" : "required", type)
          + (doc != null ? " (" + doc + ")" : "");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof NestedField)) {
        return false;
      }

      NestedField that = (NestedField) o;
      if (isOptional != that.isOptional) {
        return false;
      } else if (id != that.id) {
        return false;
      } else if (!name.equals(that.name)) {
        return false;
      } else if (!Objects.equals(doc, that.doc)) {
        return false;
      }
      return type.equals(that.type);
    }

    @Override
    public int hashCode() {
      return Objects.hash(NestedField.class, id, isOptional, name, type);
    }
  }

  /**
   * Struct 类型：有序字段集合，是表 schema 的顶层结构。
   *
   * <p>设计要点：字段以数组存储保持顺序；按名/按 ID/按小写名的索引采用 transient 懒加载缓存， 序列化时不保存缓存。equals 基于 fields 数组内容。
   */
  public static class StructType extends NestedType {
    private static final Joiner FIELD_SEP = Joiner.on(", ");

    public static StructType of(NestedField... fields) {
      return of(Arrays.asList(fields));
    }

    public static StructType of(List<NestedField> fields) {
      return new StructType(fields);
    }

    private final NestedField[] fields;

    // lazy values
    private transient List<NestedField> fieldList = null;
    private transient Map<String, NestedField> fieldsByName = null;
    private transient Map<String, NestedField> fieldsByLowerCaseName = null;
    private transient Map<Integer, NestedField> fieldsById = null;

    private StructType(List<NestedField> fields) {
      Preconditions.checkNotNull(fields, "Field list cannot be null");
      this.fields = new NestedField[fields.size()];
      for (int i = 0; i < this.fields.length; i += 1) {
        this.fields[i] = fields.get(i);
      }
    }

    @Override
    public List<NestedField> fields() {
      return lazyFieldList();
    }

    public NestedField field(String name) {
      return lazyFieldsByName().get(name);
    }

    @Override
    public NestedField field(int id) {
      return lazyFieldsById().get(id);
    }

    public NestedField caseInsensitiveField(String name) {
      return lazyFieldsByLowerCaseName().get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public Type fieldType(String name) {
      NestedField field = field(name);
      if (field != null) {
        return field.type();
      }
      return null;
    }

    @Override
    public TypeID typeId() {
      return TypeID.STRUCT;
    }

    @Override
    public boolean isStructType() {
      return true;
    }

    @Override
    public Types.StructType asStructType() {
      return this;
    }

    @Override
    public String toString() {
      return String.format("struct<%s>", FIELD_SEP.join(fields));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof StructType)) {
        return false;
      }

      StructType that = (StructType) o;
      return Arrays.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
      return Objects.hash(NestedField.class, Arrays.hashCode(fields));
    }

    private List<NestedField> lazyFieldList() {
      if (fieldList == null) {
        this.fieldList = ImmutableList.copyOf(fields);
      }
      return fieldList;
    }

    private Map<String, NestedField> lazyFieldsByName() {
      if (fieldsByName == null) {
        ImmutableMap.Builder<String, NestedField> byNameBuilder = ImmutableMap.builder();
        for (NestedField field : fields) {
          byNameBuilder.put(field.name(), field);
        }
        fieldsByName = byNameBuilder.build();
      }
      return fieldsByName;
    }

    private Map<String, NestedField> lazyFieldsByLowerCaseName() {
      if (fieldsByLowerCaseName == null) {
        ImmutableMap.Builder<String, NestedField> byLowerCaseNameBuilder = ImmutableMap.builder();
        for (NestedField field : fields) {
          byLowerCaseNameBuilder.put(field.name().toLowerCase(Locale.ROOT), field);
        }
        fieldsByLowerCaseName = byLowerCaseNameBuilder.build();
      }
      return fieldsByLowerCaseName;
    }

    private Map<Integer, NestedField> lazyFieldsById() {
      if (fieldsById == null) {
        ImmutableMap.Builder<Integer, NestedField> byIdBuilder = ImmutableMap.builder();
        for (NestedField field : fields) {
          byIdBuilder.put(field.fieldId(), field);
        }
        this.fieldsById = byIdBuilder.build();
      }
      return fieldsById;
    }
  }

  /**
   * List 类型：元素列表，元素建模为名为 "element" 的 {@link NestedField}。
   *
   * <p>设计要点：元素字段带独立 ID，支持 schema 演进；区分元素可选/必填。
   */
  public static class ListType extends NestedType {
    public static ListType ofOptional(int elementId, Type elementType) {
      Preconditions.checkNotNull(elementType, "Element type cannot be null");
      return new ListType(NestedField.optional(elementId, "element", elementType));
    }

    public static ListType ofRequired(int elementId, Type elementType) {
      Preconditions.checkNotNull(elementType, "Element type cannot be null");
      return new ListType(NestedField.required(elementId, "element", elementType));
    }

    private final NestedField elementField;
    private transient List<NestedField> fields = null;

    private ListType(NestedField elementField) {
      this.elementField = elementField;
    }

    public Type elementType() {
      return elementField.type();
    }

    @Override
    public Type fieldType(String name) {
      if ("element".equals(name)) {
        return elementType();
      }
      return null;
    }

    @Override
    public NestedField field(int id) {
      if (elementField.fieldId() == id) {
        return elementField;
      }
      return null;
    }

    @Override
    public List<NestedField> fields() {
      return lazyFieldList();
    }

    public int elementId() {
      return elementField.fieldId();
    }

    public boolean isElementRequired() {
      return !elementField.isOptional;
    }

    public boolean isElementOptional() {
      return elementField.isOptional;
    }

    @Override
    public TypeID typeId() {
      return TypeID.LIST;
    }

    @Override
    public boolean isListType() {
      return true;
    }

    @Override
    public Types.ListType asListType() {
      return this;
    }

    @Override
    public String toString() {
      return String.format("list<%s>", elementField.type());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof ListType)) {
        return false;
      }

      ListType listType = (ListType) o;
      return elementField.equals(listType.elementField);
    }

    @Override
    public int hashCode() {
      return Objects.hash(ListType.class, elementField);
    }

    private List<NestedField> lazyFieldList() {
      if (fields == null) {
        this.fields = ImmutableList.of(elementField);
      }
      return fields;
    }
  }

  /**
   * Map 类型：键值对集合，键值分别建模为名为 "key"/"value" 的 {@link NestedField}。
   *
   * <p>设计要点：键始终必填（不允许 null），值可可选/必填；键值字段带独立 ID。
   */
  public static class MapType extends NestedType {
    public static MapType ofOptional(int keyId, int valueId, Type keyType, Type valueType) {
      Preconditions.checkNotNull(valueType, "Value type cannot be null");
      return new MapType(
          NestedField.required(keyId, "key", keyType),
          NestedField.optional(valueId, "value", valueType));
    }

    public static MapType ofRequired(int keyId, int valueId, Type keyType, Type valueType) {
      Preconditions.checkNotNull(valueType, "Value type cannot be null");
      return new MapType(
          NestedField.required(keyId, "key", keyType),
          NestedField.required(valueId, "value", valueType));
    }

    private final NestedField keyField;
    private final NestedField valueField;
    private transient List<NestedField> fields = null;

    private MapType(NestedField keyField, NestedField valueField) {
      this.keyField = keyField;
      this.valueField = valueField;
    }

    public Type keyType() {
      return keyField.type();
    }

    public Type valueType() {
      return valueField.type();
    }

    @Override
    public Type fieldType(String name) {
      if ("key".equals(name)) {
        return keyField.type();
      } else if ("value".equals(name)) {
        return valueField.type();
      }
      return null;
    }

    @Override
    public NestedField field(int id) {
      if (keyField.fieldId() == id) {
        return keyField;
      } else if (valueField.fieldId() == id) {
        return valueField;
      }
      return null;
    }

    @Override
    public List<NestedField> fields() {
      return lazyFieldList();
    }

    public int keyId() {
      return keyField.fieldId();
    }

    public int valueId() {
      return valueField.fieldId();
    }

    public boolean isValueRequired() {
      return !valueField.isOptional;
    }

    public boolean isValueOptional() {
      return valueField.isOptional;
    }

    @Override
    public TypeID typeId() {
      return TypeID.MAP;
    }

    @Override
    public boolean isMapType() {
      return true;
    }

    @Override
    public Types.MapType asMapType() {
      return this;
    }

    @Override
    public String toString() {
      return String.format("map<%s, %s>", keyField.type(), valueField.type());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof MapType)) {
        return false;
      }

      MapType mapType = (MapType) o;
      if (!keyField.equals(mapType.keyField)) {
        return false;
      }
      return valueField.equals(mapType.valueField);
    }

    @Override
    public int hashCode() {
      return Objects.hash(MapType.class, keyField, valueField);
    }

    private List<NestedField> lazyFieldList() {
      if (fields == null) {
        this.fields = ImmutableList.of(keyField, valueField);
      }
      return fields;
    }
  }
}
