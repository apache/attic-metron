/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.metron.stellar.common.utils;

import com.google.common.collect.ImmutableList;
import org.apache.metron.stellar.common.StellarPredicateProcessor;
import org.apache.metron.stellar.common.StellarProcessor;
import org.apache.metron.stellar.dsl.Context;
import org.apache.metron.stellar.dsl.DefaultVariableResolver;
import org.apache.metron.stellar.dsl.MapVariableResolver;
import org.apache.metron.stellar.dsl.StellarFunctions;
import org.apache.metron.stellar.dsl.VariableResolver;
import org.junit.Assert;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Utilities for executing and validating Stellar expressions.
 */
public class StellarProcessorUtils {

  /**
   * Execute and validate a Stellar expression.
   *
   * <p>This is intended for use while unit testing Stellar expressions.  This ensures that the expression
   * validates successfully and produces a result that can be serialized correctly.
   *
   * @param expression The expression to execute.
   * @param variables The variables to expose to the expression.
   * @param context The execution context.
   * @return The result of executing the expression.
   */
  public static Object run(String expression, Map<String, Object> variables, Context context) {

    // validate the expression
    StellarProcessor processor = new StellarProcessor();
    Assert.assertTrue("Invalid expression; expr=" + expression,
            processor.validate(expression, context));

    // execute the expression
    Object ret = processor.parse(
            expression,
            new DefaultVariableResolver(x -> variables.get(x), x -> variables.containsKey(x)),(x,y)-> variables.put(x,y),
            StellarFunctions.FUNCTION_RESOLVER(),
            context);

    // ensure the result can be serialized/deserialized
    byte[] raw = SerDeUtils.toBytes(ret);
    Object actual = SerDeUtils.fromBytes(raw, Object.class);
    Assert.assertEquals(ret, actual);

    return ret;
  }

  /**
   * Execute and validate a Stellar expression.
   *
   * <p>This is intended for use while unit testing Stellar expressions.  This ensures that the expression
   * validates successfully and produces a result that can be serialized correctly.
   *
   * @param expression The expression to execute.
   * @param variables The variables to expose to the expression.
   * @return The result of executing the expression.
   */
  public static Object run(String expression, Map<String, Object> variables) {
    return run(expression, variables, Context.EMPTY_CONTEXT());
  }

  /**
   * Execute and validate a Stellar expression.
   *
   * <p>This is intended for use while unit testing Stellar expressions.  This ensures that the expression
   * validates successfully and produces a result that can be serialized correctly.
   *
   * @param expression The expression to execute.
   * @param context The execution context.
   * @return The result of executing the expression.
   */
  public static Object run(String expression, Context context) {
    return run(expression, Collections.emptyMap(), context);
  }

  public static void validate(String expression, Context context) {
    StellarProcessor processor = new StellarProcessor();
    Assert.assertTrue("Invalid expression; expr=" + expression,
            processor.validate(expression, context));
  }

  public static void validate(String rule) {
    validate(rule, Context.EMPTY_CONTEXT());
  }

  public static boolean runPredicate(String rule, Map resolver) {
    return runPredicate(rule, resolver, Context.EMPTY_CONTEXT());
  }

  public static boolean runPredicate(String rule, Map resolver, Context context) {
    return runPredicate(rule, new MapVariableResolver(resolver), context);
  }

  public static boolean runPredicate(String rule, VariableResolver resolver) {
    return runPredicate(rule, resolver, Context.EMPTY_CONTEXT());
  }

  public static boolean runPredicate(String rule, VariableResolver resolver, Context context) {
    StellarPredicateProcessor processor = new StellarPredicateProcessor();
    Assert.assertTrue(rule + " not valid.", processor.validate(rule));
    return processor.parse(rule, resolver, StellarFunctions.FUNCTION_RESOLVER(), context);
  }

  public static void runWithArguments(String function, Object argument, Object expected) {
    runWithArguments(function, ImmutableList.of(argument), expected);
  }

  public static void runWithArguments(String function, List<Object> arguments, Object expected) {
    Supplier<Stream<Map.Entry<String, Object>>> kvStream = () -> StreamSupport
            .stream(new XRange(arguments.size()), false)
            .map(i -> new AbstractMap.SimpleImmutableEntry<>("var" + i, arguments.get(i)));

    String args = kvStream.get().map(kv -> kv.getKey()).collect(Collectors.joining(","));
    Map<String, Object> variables = kvStream.get().collect(Collectors.toMap(kv -> kv.getKey(), kv -> kv.getValue()));
    String stellarStatement = function + "(" + args + ")";
    String reason = stellarStatement + " != " + expected + " with variables: " + variables;

    if (expected instanceof Double) {
      Assert.assertEquals(reason, (Double) expected, (Double) run(stellarStatement, variables), 1e-6);
    } else {
      Assert.assertEquals(reason, expected, run(stellarStatement, variables));
    }
  }

  public static class XRange extends Spliterators.AbstractIntSpliterator {
    int end;
    int i = 0;

    public XRange(int start, int end) {
      super(end - start, 0);
      i = start;
      this.end = end;
    }

    public XRange(int end) {
      this(0, end);
    }

    @Override
    public boolean tryAdvance(IntConsumer action) {
      boolean isDone = i >= end;
      if (isDone) {
        return false;
      } else {
        action.accept(i);
        i++;
        return true;
      }
    }

    /**
     * {@inheritDoc}
     *
     * @param action to {@code IntConsumer} and passed to {@link #tryAdvance(IntConsumer)};
     *     otherwise the action is adapted to an instance of {@code IntConsumer}, by boxing the
     *     argument of {@code IntConsumer}, and then passed to {@link #tryAdvance(IntConsumer)}.
     */
    @Override
    public boolean tryAdvance(Consumer<? super Integer> action) {
      boolean isDone = i >= end;
      if (isDone) {
        return false;
      } else {
        action.accept(i);
        i++;
        return true;
      }
    }
  }
}
