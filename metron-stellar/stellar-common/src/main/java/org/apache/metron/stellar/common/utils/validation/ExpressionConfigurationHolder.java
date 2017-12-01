/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.metron.stellar.common.utils.validation;

import java.lang.annotation.AnnotationTypeMismatchException;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.NullArgumentException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.metron.stellar.common.utils.validation.annotations.StellarConfiguration;
import org.apache.metron.stellar.common.utils.validation.annotations.StellarConfigurationList;
import org.apache.metron.stellar.common.utils.validation.annotations.StellarExpressionField;
import org.apache.metron.stellar.common.utils.validation.annotations.StellarExpressionList;
import org.apache.metron.stellar.common.utils.validation.annotations.StellarExpressionMap;

public class ExpressionConfigurationHolder implements StellarConfiguredStatementVisitor {

  private Object holderObject;
  private String name;
  private String parentName;
  private String fullName;
  private List<ExpressionConfigurationHolder> children = new LinkedList<>();
  private List<Field> expressionList;
  private List<Field> expressionListList;
  private List<Field> expresionMapList;

  public ExpressionConfigurationHolder(String parentName, String name, Object holderObject) {
    if (holderObject == null) {
      throw new NullArgumentException("holderObject");
    }
    if (StringUtils.isEmpty(name)) {
      throw new NullArgumentException("name");
    }
    this.name = name;
    this.parentName = parentName;

    this.fullName = StringUtils.isEmpty(parentName) ? this.name
        : String.format("%s/%s", this.parentName, this.name);

    this.holderObject = holderObject;
  }

  public Object getHolderObject() {
    return holderObject;
  }

  public String getName() {
    return name;
  }

  public String getParentName() {
    return parentName;
  }

  public String getFullName() {
    return fullName;
  }

  @Override
  public void visit(StatementVisitor visitor, ErrorConsumer errorConsumer) {
    visitExpressions(visitor, errorConsumer);
    visitLists(visitor, errorConsumer);
    visitMaps(visitor, errorConsumer);
    visitChilden(visitor, errorConsumer);
  }

  private void visitExpressions(StatementVisitor visitor,
      ErrorConsumer errorConsumer) {
    expressionList.forEach((f) -> {
      String thisFullName = String
          .format("%s/%s", getFullName(), f.getAnnotation(StellarExpressionField.class).name());
      try {
        Object thisExpressionObject =  FieldUtils.readField(f, holderObject, true);
        if (thisExpressionObject == null || StringUtils.isEmpty(thisExpressionObject.toString())) {
          return;
        }
        visitExpression(thisFullName,thisExpressionObject.toString(), visitor,
            errorConsumer);
      } catch (IllegalAccessException e) {
        errorConsumer.consume(thisFullName, e);
      }
    });
  }

  private void visitExpression(String expressionName, String expression,
      StatementVisitor visitor, ErrorConsumer errorConsumer) {
    if (StringUtils.isEmpty(expression)) {
      return;
    }
    try {
      visitor.visit(expressionName, expression);
    } catch (Exception e) {
      errorConsumer.consume(expressionName, e);
    }
  }

  private void visitLists(StatementVisitor visitor, ErrorConsumer errorConsumer) {
    expressionListList.forEach((l) -> {
      String thisFullName = String
          .format("%s/%s", getFullName(), l.getAnnotation(StellarExpressionList.class).name());
      try {
        Object possibleIterable = FieldUtils.readField(l, holderObject, true);
        if (possibleIterable == null) {
          return;
        }
        Iterable it = (Iterable)possibleIterable;
        int index = 0;
        for (Object expressionObject : it) {
          String expressionFullName = String.format("%s/%s", thisFullName, index);
          visitExpression(expressionFullName, expressionObject.toString(), visitor, errorConsumer);
        }
      } catch (IllegalAccessException e) {
        errorConsumer.consume(thisFullName, e);
      }
    });
  }

  @SuppressWarnings("unchecked")
  private void visitMaps(StatementVisitor visitor, ErrorConsumer errorConsumer) {
    expresionMapList.forEach((f) -> {
      String thisFullName = String
          .format("%s/%s", getFullName(), f.getAnnotation(StellarExpressionMap.class).name());
      try {
        // if we have configured a qualifying field as a type of flag we need to check it
        // for example, if StellarFoo.class.isAssignableFrom(this.foo.getClass()) then
        // bar is a StellarExpressionMap
        if (!StringUtils.isEmpty(f.getAnnotation(StellarExpressionMap.class).qualify_with_field())) {
          String fieldName = f.getAnnotation(StellarExpressionMap.class).qualify_with_field();
          Class type = f.getAnnotation(StellarExpressionMap.class).qualify_with_field_type();
          Object theObject = FieldUtils.readField(holderObject, fieldName, true);
          if (theObject == null) {
            errorConsumer.consume(thisFullName, new IncompleteAnnotationException(StellarExpressionMap.class,"fieldName"));
            return;
          }
          if (!type.isAssignableFrom(theObject.getClass())) {
            return;
          }
        }

        Map map = (Map) FieldUtils.readField(f, holderObject, true);

        // some maps actually nest the config, so check and dig to get the real map
        String[] innerKeys = f.getAnnotation(StellarExpressionMap.class).inner_map_keys();
        if (innerKeys.length != 0) {
          for (String key : innerKeys) {
            if (StringUtils.isEmpty(key)) {
              return;
            }
            Object innerObject = map.get(key);
            if (innerObject == null) {
              return;
            }
            fullName = String.format("%s/%s",fullName,key);
            if(!Map.class.isAssignableFrom(innerObject.getClass())) {
              errorConsumer.consume(fullName, new Exception("The annotation specified an inner map that was not a map"));
            }
            map = (Map)innerObject;
          }
        }

        map.forEach((k, v) -> {
          String mapKeyFullName = String.format("%s/%s", thisFullName, k.toString());
          if (Map.class.isAssignableFrom(v.getClass())) {
            Map innerMap = (Map)v;
            innerMap.forEach((ik,iv) -> {
              if (iv == null) {
                return;
              }
              visitExpression(String.format("%s/%s",mapKeyFullName,ik.toString()),iv.toString(), visitor, errorConsumer);
            });
            return;
          }
          visitExpression(mapKeyFullName, v.toString(), visitor, errorConsumer);
        });
      } catch (IllegalAccessException e) {
        errorConsumer.consume(thisFullName, e);
      }
    });
  }

  private void visitChilden(StatementVisitor visitor,
      ErrorConsumer errorConsumer) {
    children.forEach((c) -> c.visit(visitor,errorConsumer));
  }

  public void discover() throws IllegalAccessException {
    expressionList = FieldUtils
        .getFieldsListWithAnnotation(holderObject.getClass(), StellarExpressionField.class);
    expressionListList = FieldUtils
        .getFieldsListWithAnnotation(holderObject.getClass(), StellarExpressionList.class);
    expresionMapList = FieldUtils
        .getFieldsListWithAnnotation(holderObject.getClass(), StellarExpressionMap.class);
    List<Field> holderList = FieldUtils
        .getFieldsListWithAnnotation(holderObject.getClass(), StellarConfiguration.class);
    List<Field> holderListList = FieldUtils
        .getFieldsListWithAnnotation(holderObject.getClass(), StellarConfigurationList.class);

    for (Field f : holderList) {
      Object potentialChild = FieldUtils.readField(f, holderObject,true);
      if (potentialChild == null) {
        break;
      }
      ExpressionConfigurationHolder child = new ExpressionConfigurationHolder(getFullName(), f.getName(),
          potentialChild);

      child.discover();
      children.add(child);
    }
    for (Field f : holderListList) {
      String thisFullName = String
          .format("%s/%s", getFullName(), f.getAnnotation(StellarConfigurationList.class).name());
      Object potentialChild = FieldUtils.readField(f, holderObject,true);
      if (potentialChild == null) {
        break;
      }
      if (!Iterable.class.isAssignableFrom(potentialChild.getClass())) {
        break;
      }
      Iterable it = (Iterable) FieldUtils.readField(f, holderObject, true);
      int index = 0;
      for (Object thisHolderObject : it) {
        if (thisHolderObject == null) {
          break;
        }
        ExpressionConfigurationHolder child = new ExpressionConfigurationHolder(thisFullName, String.valueOf(index),
            thisHolderObject);
        index++;
        child.discover();
        children.add(child);
      }
    }
  }
}
