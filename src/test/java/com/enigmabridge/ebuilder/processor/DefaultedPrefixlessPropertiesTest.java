/*
 * Copyright 2016 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.enigmabridge.ebuilder.processor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.enigmabridge.ebuilder.FreeBuilder;
import com.enigmabridge.ebuilder.processor.util.feature.FeatureSet;
import com.enigmabridge.ebuilder.processor.util.testing.BehaviorTestRunner.Shared;
import com.enigmabridge.ebuilder.processor.util.testing.BehaviorTester;
import com.enigmabridge.ebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import com.enigmabridge.ebuilder.processor.util.testing.SourceBuilder;
import com.enigmabridge.ebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import javax.tools.JavaFileObject;
import java.util.List;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class DefaultedPrefixlessPropertiesTest {

  @Parameters(name = "{0}, {1}")
  @SuppressWarnings("unchecked")
  public static Iterable<Object[]> featureSets() {
    return () -> Lists
        .cartesianProduct(FeatureSets.ALL, ImmutableList.of(OPTIMIZED_BUILDER, SLOW_BUILDER))
        .stream()
        .map(List::toArray)
        .iterator();
  }

  /**
   * Test that defaults work correctly when we can detect them at compile-time (javac only).
   */
  private static final JavaFileObject OPTIMIZED_BUILDER = new SourceBuilder()
      .named("Optimized")
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  int propertyA();")
      .addLine("  boolean propertyB();")
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {")
      .addLine("    public Builder() {")
      .addLine("      propertyA(0);")
      .addLine("      propertyB(false);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  /**
   * Test that defaults work correctly when we cannot detect them at compile-time.
   */
  private static final JavaFileObject SLOW_BUILDER = new SourceBuilder()
      .named("Slow")
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  int propertyA();")
      .addLine("  boolean propertyB();")
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {")
      .addLine("    public Builder() {")
      .addLine("      if (true) {  // Disable optimization in javac")
      .addLine("        propertyA(0);")
      .addLine("        propertyB(false);")
      .addLine("      }")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;
  @Parameter(value = 0) public FeatureSet features;
  @Parameter(value = 1) public JavaFileObject dataType;

  @Test
  public void testMergeFromBuilder_defaultsDoNotOverride() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .propertyB(true)")
            .addLine("    .mergeFrom(new DataType.Builder())")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.propertyA());")
            .addLine("assertTrue(value.propertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue_defaultsDoNotOverride() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .propertyB(true)")
            .addLine("    .mergeFrom(new DataType.Builder().build())")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.propertyA());")
            .addLine("assertTrue(value.propertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromBuilder_nonDefaultsUsed() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyB(true)")
            .addLine("    .mergeFrom(new DataType.Builder())")
            .addLine("        .propertyA(13)")
            .addLine("    .build();")
            .addLine("assertEquals(13, value.propertyA());")
            .addLine("assertTrue(value.propertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue_nonDefaultsUsed() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyB(true)")
            .addLine("    .mergeFrom(new DataType.Builder()")
            .addLine("        .propertyA(13)")
            .addLine("        .build())")
            .addLine("    .build();")
            .addLine("assertEquals(13, value.propertyA());")
            .addLine("assertTrue(value.propertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromBuilder_nonDefaultsOverride() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .propertyB(true)")
            .addLine("    .mergeFrom(new DataType.Builder())")
            .addLine("        .propertyA(13)")
            .addLine("    .build();")
            .addLine("assertEquals(13, value.propertyA());")
            .addLine("assertTrue(value.propertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue_nonDefaultsOverride() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .propertyB(true)")
            .addLine("    .mergeFrom(new DataType.Builder()")
            .addLine("        .propertyA(13)")
            .addLine("        .build())")
            .addLine("    .build();")
            .addLine("assertEquals(13, value.propertyA());")
            .addLine("assertTrue(value.propertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .propertyB(true)")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(0, value.propertyA());")
            .addLine("assertFalse(value.propertyB());")
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder().addImport("com.example.DataType");
  }

}
