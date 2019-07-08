/*
 * Copyright (C) 2013 - 2019 Oracle and/or its affiliates. All rights reserved.
 */
package oracle.pgql.lang.ddl.createpropertygraph;

public class Property {

  /**
   * The column name. In the future, we may want to support arbitrary expressions.
   */
  String columnName;

  /**
   * The property name.
   */
  String propertyName;

  /**
   * The constructor.
   */
  public Property(String columnName, String propertyName) {
    this.columnName = columnName;
    this.propertyName = propertyName;
  }

  public String getColumnName() {
    return columnName;
  }

  public void setColumnName(String columnName) {
    this.columnName = columnName;
  }

  public String getPropertyName() {
    return propertyName;
  }

  public void setPropertyName(String propertyName) {
    this.propertyName = propertyName;
  }

  @Override
  public String toString() {
    return columnName + " AS " + propertyName;
  }

  @Override
  public int hashCode() {
    return 31;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Property other = (Property) obj;
    if (columnName == null) {
      if (other.columnName != null)
        return false;
    } else if (!columnName.equals(other.columnName))
      return false;
    if (propertyName == null) {
      if (other.propertyName != null)
        return false;
    } else if (!propertyName.equals(other.propertyName))
      return false;
    return true;
  }
}
