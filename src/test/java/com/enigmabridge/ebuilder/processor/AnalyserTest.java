/*
 * Copyright 2014 Google Inc. All rights reserved.
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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Maps.uniqueIndex;
import static com.google.common.truth.Truth.assertThat;
import static com.enigmabridge.ebuilder.processor.BuilderFactory.BUILDER_METHOD;
import static com.enigmabridge.ebuilder.processor.BuilderFactory.NEW_BUILDER_METHOD;
import static com.enigmabridge.ebuilder.processor.BuilderFactory.NO_ARGS_CONSTRUCTOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.enigmabridge.ebuilder.processor.util.Excerpt;
import com.enigmabridge.ebuilder.processor.util.QualifiedName;
import com.enigmabridge.ebuilder.processor.util.SourceStringBuilder;
import com.enigmabridge.ebuilder.processor.util.testing.FakeMessager;
import com.enigmabridge.ebuilder.processor.util.testing.ModelRule;
import com.google.common.annotations.GwtCompatible;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.google.common.collect.Maps;
import com.google.common.truth.Truth;
import com.enigmabridge.ebuilder.processor.PropertyCodeGenerator.Type;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

import javax.annotation.Generated;
import javax.lang.model.element.TypeElement;

/** Unit tests for {@link Analyser}. */
@RunWith(JUnit4.class)
public class AnalyserTest {

  @Rule public final ModelRule model = new ModelRule();
  private final FakeMessager messager = new FakeMessager();

  private Analyser analyser;

  @Before
  public void setup() {
    analyser = new Analyser(
        model.elementUtils(),
        messager,
        MethodIntrospector.instance(model.environment()),
        model.typeUtils());
  }

  @Test
  public void emptyDataType() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    QualifiedName expectedBuilder = QualifiedName.of("com.example", "DataType_Builder");
    QualifiedName partialType = expectedBuilder.nestedType("Partial");
    QualifiedName propertyType = expectedBuilder.nestedType("Property");
    QualifiedName valueType = expectedBuilder.nestedType("Value");
    Metadata expectedMetadata = new Metadata.Builder()
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(true)
        .setGeneratedBuilder(expectedBuilder.withParameters())
        .setInterfaceType(false)
        .setPartialType(partialType.withParameters())
        .setPropertyEnum(propertyType.withParameters())
        .setType(QualifiedName.of("com.example", "DataType").withParameters())
        .setValueType(valueType.withParameters())
        .addVisibleNestedTypes(partialType)
        .addVisibleNestedTypes(propertyType)
        .addVisibleNestedTypes(valueType)
        .build();

    assertEquals(expectedMetadata, metadata);
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[NOTE] Add \"public static class Builder extends DataType_Builder {}\" to your class "
                + "to enable the @EBuilder API"));
  }

  @Test
  public void emptyInterface() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public interface DataType {",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    QualifiedName expectedBuilder = QualifiedName.of("com.example", "DataType_Builder");
    QualifiedName partialType = expectedBuilder.nestedType("Partial");
    QualifiedName propertyType = expectedBuilder.nestedType("Property");
    QualifiedName valueType = expectedBuilder.nestedType("Value");
    Metadata expectedMetadata = new Metadata.Builder()
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(true)
        .setGeneratedBuilder(expectedBuilder.withParameters())
        .setInterfaceType(true)
        .setPartialType(partialType.withParameters())
        .setPropertyEnum(propertyType.withParameters())
        .setType(QualifiedName.of("com.example", "DataType").withParameters())
        .setValueType(valueType.withParameters())
        .addVisibleNestedTypes(partialType)
        .addVisibleNestedTypes(propertyType)
        .addVisibleNestedTypes(valueType)
        .build();

    assertEquals(expectedMetadata, metadata);
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[NOTE] Add \"class Builder extends DataType_Builder {}\" to your interface "
                + "to enable the @EBuilder API"));
  }

  @Test
  public void nestedDataType() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse((TypeElement) model.newElementWithMarker(
        "package com.example;",
        "public class OuterClass {",
        "  ---> public static class DataType {",
        "  }",
        "}"));
    assertEquals("com.example.OuterClass.DataType",
        dataType.getType().getQualifiedName().toString());
    assertEquals(QualifiedName.of("com.example", "OuterClass_DataType_Builder").withParameters(),
        dataType.getGeneratedABuilder());
  }

  @Test
  public void twiceNestedDataType() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse((TypeElement) model.newElementWithMarker(
        "package com.example;",
        "public class OuterClass {",
        "  public static class InnerClass {",
        "    ---> public static class DataType {",
        "    }",
        "  }",
        "}"));
    assertEquals("com.example.OuterClass.InnerClass.DataType",
        dataType.getType().getQualifiedName().toString());
    assertEquals(
        QualifiedName.of("com.example", "OuterClass_InnerClass_DataType_Builder").withParameters(),
        dataType.getGeneratedABuilder());
  }

  @Test
  public void builderSubclass() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder { }",
        "}"));
    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        dataType.getGeneratedABuilder());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    Truth.assertThat(dataType.getBuilderFactory()).hasValue(NO_ARGS_CONSTRUCTOR);
    assertFalse(dataType.isBuilderSerializable());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void serializableBuilderSubclass() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder ",
        "      extends DataType_Builder implements java.io.Serializable { }",
        "}"));
    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        dataType.getGeneratedABuilder());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    assertTrue(dataType.isBuilderSerializable());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void builderSubclass_publicBuilderMethod() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder { }",
        "  public static Builder builder() { return new Builder(); }",
        "}"));
    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        dataType.getGeneratedABuilder());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    Truth.assertThat(dataType.getBuilderFactory()).hasValue(BUILDER_METHOD);
    assertFalse(dataType.isBuilderSerializable());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void builderSubclass_publicNewBuilderMethod() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder { }",
        "  public static Builder newBuilder() { return new Builder(); }",
        "}"));
    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        dataType.getGeneratedABuilder());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    Truth.assertThat(dataType.getBuilderFactory()).hasValue(NEW_BUILDER_METHOD);
    assertFalse(dataType.isBuilderSerializable());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void twoGetters() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract int getAge();",
        "}"));
    Map<String, Metadata.Property> properties = Maps.uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals(model.typeMirror(int.class), properties.get("age").getType());
    assertEquals(model.typeMirror(Integer.class), properties.get("age").getBoxedType());
    assertEquals("AGE", properties.get("age").getAllCapsName());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertNull(properties.get("name").getBoxedType());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
  }

  @Test
  public void complexGetterNames() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getCustomURLTemplate();",
        "  public abstract String getTop50Sites();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Metadata.Property> properties = Maps.uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("customURLTemplate", "top50Sites");
    assertEquals("CUSTOM_URL_TEMPLATE", properties.get("customURLTemplate").getAllCapsName());
    assertEquals("TOP50_SITES", properties.get("top50Sites").getAllCapsName());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void twoGetters_interface() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "interface DataType {",
        "  String getName();",
        "  int getAge();",
        "  class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Metadata.Property> properties = Maps.uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals(model.typeMirror(int.class), properties.get("age").getType());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  public void booleanGetter() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract boolean isAvailable();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Metadata.Property> properties = Maps.uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("available");
    assertEquals(model.typeMirror(boolean.class), properties.get("available").getType());
    assertEquals("Available", properties.get("available").getCapitalizedName());
    assertEquals("isAvailable", properties.get("available").getGetterName());
    assertEquals("AVAILABLE", properties.get("available").getAllCapsName());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void finalGetter() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public final String getName() {",
        "    return null;",
        "  }",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
  }

  @Test
  public void defaultCodeGenerator() throws Analyser.CannotGenerateCodeException {
    Metadata metadata = analyser.analyse(model.newType(
        "package com.example;",
        "interface DataType {",
        "  String getName();",
        "  class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Metadata.Property> properties = Maps.uniqueIndex(metadata.getProperties(), GET_NAME);
    assertEquals(
        DefaultPropertyFactory.CodeGenerator.class,
        properties.get("name").getCodeGenerator().getClass());
  }

  @Test
  public void nonAbstractGetter() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public String getName() {",
        "    return null;",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void nonAbstractMethodNamedIssue() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public boolean issue() {",
        "    return true;",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void voidGetter() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract void getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("getName");
    assertThat(messager.getMessagesByElement().get("getName"))
        .containsExactly("[ERROR] Getter methods must not be void on @EBuilder types");
  }

  @Test
  public void nonBooleanIsMethod() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String isName();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("isName");
    assertThat(messager.getMessagesByElement().get("isName")).containsExactly(
        "[ERROR] Getter methods starting with 'is' must return a boolean on @EBuilder types");
  }

  @Test
  public void getterWithArgument() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName(boolean capitalized);",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("getName");
    assertThat(messager.getMessagesByElement().get("getName"))
        .containsExactly("[ERROR] Getter methods cannot take parameters on @EBuilder types");
  }

  @Test
  public void abstractButNotGetter() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String name();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("name");
    assertThat(messager.getMessagesByElement().get("name"))
        .containsExactly("[ERROR] Only getter methods (starting with 'get' or 'is') may be declared"
            + " abstract on @EBuilder types");
  }

  @Test
  public void abstractMethodNamedGet() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String get();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("get");
    assertThat(messager.getMessagesByElement().get("get"))
        .containsExactly("[ERROR] Only getter methods (starting with 'get' or 'is') may be declared"
            + " abstract on @EBuilder types");
  }

  @Test
  public void abstractMethodNamedGetter() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getter();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("getter");
    assertThat(messager.getMessagesByElement().get("getter"))
        .containsExactly("[ERROR] Getter methods cannot have a lowercase character immediately"
            + " after the 'get' prefix on @EBuilder types (did you mean 'getTer'?)");
  }

  @Test
  public void abstractMethodNamedIssue() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String issue();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("issue");
    assertThat(messager.getMessagesByElement().get("issue"))
        .containsExactly("[ERROR] Getter methods cannot have a lowercase character immediately"
            + " after the 'is' prefix on @EBuilder types (did you mean 'isSue'?)");
  }

  @Test
  public void abstractMethodWithNonAsciiName() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getürkt();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("getürkt");
    assertThat(messager.getMessagesByElement().get("getürkt"))
        .containsExactly("[ERROR] Getter methods cannot have a lowercase character immediately"
            + " after the 'get' prefix on @EBuilder types (did you mean 'getÜrkt'?)");
  }

  @Test
  public void abstractMethodNamedIs() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract boolean is();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("is");
    assertThat(messager.getMessagesByElement().get("is"))
        .containsExactly("[ERROR] Only getter methods (starting with 'get' or 'is') may be declared"
            + " abstract on @EBuilder types");
  }

  @Test
  public void mixedValidAndInvalidGetters() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract void getNothing();",
        "  public abstract int getAge();",
        "  public abstract float isDoubleBarrelled();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Metadata.Property> properties = Maps.uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals("AGE", properties.get("age").getAllCapsName());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
    assertThat(messager.getMessagesByElement().keys())
        .containsExactly("getNothing", "isDoubleBarrelled");
    assertThat(messager.getMessagesByElement().get("getNothing"))
        .containsExactly("[ERROR] Getter methods must not be void on @EBuilder types");
    assertThat(messager.getMessagesByElement().get("isDoubleBarrelled"))
        .containsExactly("[ERROR] Getter methods starting with 'is' must return a boolean"
            + " on @EBuilder types");
  }

  @Test
  public void noDefaults() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract int getAge();",
        "  public static class Builder extends DataType_Builder {",
        "    public Builder() {",
        "    }",
        "  }",
        "}"));
    Map<String, Metadata.Property> properties = Maps.uniqueIndex(dataType.getProperties(), GET_NAME);
    assertEquals(Type.REQUIRED, properties.get("name").getCodeGenerator().getType());
    assertEquals(Type.REQUIRED, properties.get("age").getCodeGenerator().getType());
  }

  @Test
  public void implementsInterface() throws Analyser.CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "public interface IDataType {",
        "  public abstract String getName();",
        "  public abstract int getAge();",
        "}");
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType implements IDataType {",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Metadata.Property> properties = Maps.uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");
  }

  @Test
  public void implementsGenericInterface() throws Analyser.CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "public interface IDataType<T> {",
        "  public abstract T getProperty();",
        "}");
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType implements IDataType<String> {",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Metadata.Property> properties = Maps.uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("property");
    assertEquals("java.lang.String", properties.get("property").getType().toString());
  }

  @Test
  public void notGwtSerializable() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "@" + GwtCompatible.class.getName() + "(serializable = false)",
        "public interface DataType {",
        "  class Builder extends DataType_Builder {}",
        "}"));
    Truth.assertThat(dataType.getGeneratedBuilderAnnotations()).hasSize(1);
    assertThat(asSource(dataType.getGeneratedBuilderAnnotations().get(0)))
        .isEqualTo("@GwtCompatible");
    assertThat(dataType.getValueTypeVisibility()).isEqualTo(Metadata.Visibility.PRIVATE);
    Truth.assertThat(dataType.getValueTypeAnnotations()).isEmpty();
    assertThat(dataType.getNestedClasses()).isEmpty();
    Truth.assertThat(dataType.getVisibleNestedTypes()).containsNoneOf(
        QualifiedName.of("com.example", "DataType", "Value_CustomFieldSerializer"),
        QualifiedName.of("com.example", "DataType", "GwtWhitelist"));
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void gwtSerializable() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "@" + GwtCompatible.class.getName() + "(serializable = true)",
        "public interface DataType {",
        "  class Builder extends DataType_Builder {}",
        "}"));
    Truth.assertThat(dataType.getGeneratedBuilderAnnotations()).hasSize(1);
    assertThat(asSource(dataType.getGeneratedBuilderAnnotations().get(0)))
        .isEqualTo("@GwtCompatible");
    assertThat(dataType.getValueTypeVisibility()).isEqualTo(Metadata.Visibility.PACKAGE);
    Truth.assertThat(dataType.getValueTypeAnnotations()).hasSize(1);
    assertThat(asSource(dataType.getValueTypeAnnotations().get(0)))
        .isEqualTo("@GwtCompatible(serializable = true)");
    assertThat(dataType.getNestedClasses()).hasSize(2);
    Truth.assertThat(dataType.getVisibleNestedTypes()).containsAllOf(
        QualifiedName.of("com.example", "DataType_Builder", "Value_CustomFieldSerializer"),
        QualifiedName.of("com.example", "DataType_Builder", "GwtWhitelist"));
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void underriddenEquals() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        Metadata.StandardMethod.EQUALS, Metadata.UnderrideLevel.OVERRIDEABLE));
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("equals", ImmutableList.of(
            "[ERROR] hashCode and equals must be implemented together on @EBuilder types"));
  }

  @Test
  public void underriddenHashCode() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        Metadata.StandardMethod.HASH_CODE, Metadata.UnderrideLevel.OVERRIDEABLE));
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("hashCode", ImmutableList.of(
            "[ERROR] hashCode and equals must be implemented together on @EBuilder types"));
  }

  @Test
  public void underriddenHashCodeAndEquals() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  @Override public boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        Metadata.StandardMethod.EQUALS, Metadata.UnderrideLevel.OVERRIDEABLE,
        Metadata.StandardMethod.HASH_CODE, Metadata.UnderrideLevel.OVERRIDEABLE));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void underriddenToString() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public String toString() {",
        "    return \"DataType{}\";",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        Metadata.StandardMethod.TO_STRING, Metadata.UnderrideLevel.OVERRIDEABLE));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void underriddenTriad() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  @Override public int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  @Override public String toString() {",
        "    return \"DataType{}\";",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        Metadata.StandardMethod.EQUALS, Metadata.UnderrideLevel.OVERRIDEABLE,
        Metadata.StandardMethod.HASH_CODE, Metadata.UnderrideLevel.OVERRIDEABLE,
        Metadata.StandardMethod.TO_STRING, Metadata.UnderrideLevel.OVERRIDEABLE));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void finalEquals() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        Metadata.StandardMethod.EQUALS, Metadata.UnderrideLevel.FINAL));
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("equals", ImmutableList.of(
            "[ERROR] hashCode and equals must be implemented together on @EBuilder types"));
  }

  @Test
  public void finalHashCode() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        Metadata.StandardMethod.HASH_CODE, Metadata.UnderrideLevel.FINAL));
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("hashCode", ImmutableList.of(
            "[ERROR] hashCode and equals must be implemented together on @EBuilder types"));
  }

  @Test
  public void finalHashCodeAndEquals() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  @Override public final boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        Metadata.StandardMethod.EQUALS, Metadata.UnderrideLevel.FINAL,
        Metadata.StandardMethod.HASH_CODE, Metadata.UnderrideLevel.FINAL));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void finalToString() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final String toString() {",
        "    return \"DataType{}\";",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        Metadata.StandardMethod.TO_STRING, Metadata.UnderrideLevel.FINAL));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void finalTriad() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  @Override public final int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  @Override public final String toString() {",
        "    return \"DataType{}\";",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        Metadata.StandardMethod.EQUALS, Metadata.UnderrideLevel.FINAL,
        Metadata.StandardMethod.HASH_CODE, Metadata.UnderrideLevel.FINAL,
        Metadata.StandardMethod.TO_STRING, Metadata.UnderrideLevel.FINAL));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void abstractEquals() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  /** Some comment about value-based equality. */",
        "  @Override public abstract boolean equals(Object obj);",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEmpty();
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void abstractHashCode() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  /** Some comment about value-based equality. */",
        "  @Override public abstract int hashCode();",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEmpty();
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void abstractToString() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  /** Some comment about how this is a useful toString implementation. */",
        "  @Override public abstract String toString();",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEmpty();
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void privateNestedType() {
    TypeElement privateType = (TypeElement) model.newElementWithMarker(
        "package com.example;",
        "public class DataType {",
        "  ---> private static class PrivateType {",
        "  }",
        "}");

    try {
      analyser.analyse(privateType);
      fail("Expected CannotGenerateCodeException");
    } catch (Analyser.CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("PrivateType", ImmutableList.of(
            "[ERROR] @EBuilder types cannot be private"));
  }

  @Test
  public void indirectlyPrivateNestedType() {
    TypeElement nestedType = (TypeElement) model.newElementWithMarker(
        "package com.example;",
        "public class DataType {",
        "  private static class PrivateType {",
        "    ---> static class NestedType {",
        "    }",
        "  }",
        "}");

    try {
      analyser.analyse(nestedType);
      fail("Expected CannotGenerateCodeException");
    } catch (Analyser.CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("NestedType", ImmutableList.of(
            "[ERROR] @EBuilder types cannot be private, "
                + "but enclosing type PrivateType is inaccessible"));
  }

  @Test
  public void innerType() {
    TypeElement innerType = (TypeElement) model.newElementWithMarker(
        "package com.example;",
        "public class DataType {",
        "  ---> public class InnerType {",
        "  }",
        "}");

    try {
      analyser.analyse(innerType);
      fail("Expected CannotGenerateCodeException");
    } catch (Analyser.CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("InnerType", ImmutableList.of(
            "[ERROR] Inner classes cannot be @EBuilder types "
                + "(did you forget the static keyword?)"));
  }

  @Test
  public void nonStaticBuilder() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Metadata.Property> properties = Maps.uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name");
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertNull(properties.get("name").getBoxedType());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
    Truth.assertThat(dataType.getBuilderFactory()).isAbsent();
    assertThat(messager.getMessagesByElement().asMap()).containsEntry(
        "Builder", ImmutableList.of("[ERROR] Builder must be static on @EBuilder types"));
  }

  @Test
  public void genericType() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType<A, B> {",
        "  public abstract A getName();",
        "  public abstract B getAge();",
        "  public static class Builder<A, B> extends DataType_Builder<A, B> {}",
        "}"));
    assertEquals("com.example.DataType.Builder<A, B>", dataType.getBuilder().toString());
    assertEquals(Optional.of(BuilderFactory.NO_ARGS_CONSTRUCTOR), dataType.getBuilderFactory());
    assertEquals("com.example.DataType_Builder<A, B>", dataType.getGeneratedABuilder().toString());
    assertEquals("com.example.DataType_Builder.Partial<A, B>",
        dataType.getPartialType().toString());
    assertEquals("com.example.DataType_Builder.Property", dataType.getPropertyEnum().toString());
    assertEquals("com.example.DataType<A, B>", dataType.getType().toString());
    assertEquals("com.example.DataType_Builder.Value<A, B>", dataType.getValueType().toString());
    Map<String, Metadata.Property> properties = Maps.uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals("B", properties.get("age").getType().toString());
    assertNull(properties.get("age").getBoxedType());
    assertEquals("AGE", properties.get("age").getAllCapsName());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("A", properties.get("name").getType().toString());
    assertNull(properties.get("name").getBoxedType());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
  }

  /** @see <a href="https://github.com/google/FreeBuilder/issues/111">Issue 111</a> */
  @Test
  public void genericType_rebuilt() throws Analyser.CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "abstract class DataType_Builder<A, B> {}");
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType<A, B> {",
        "  public abstract A getName();",
        "  public abstract B getAge();",
        "  public static class Builder<A, B> extends DataType_Builder<A, B> {}",
        "}"));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
    assertEquals("com.example.DataType.Builder<A, B>", dataType.getBuilder().toString());
    assertEquals(Optional.of(BuilderFactory.NO_ARGS_CONSTRUCTOR), dataType.getBuilderFactory());
    assertEquals("com.example.DataType_Builder<A, B>", dataType.getGeneratedABuilder().toString());
    assertEquals("com.example.DataType_Builder.Partial<A, B>",
        dataType.getPartialType().toString());
    assertEquals("com.example.DataType_Builder.Property", dataType.getPropertyEnum().toString());
    assertEquals("com.example.DataType<A, B>", dataType.getType().toString());
    assertEquals("com.example.DataType_Builder.Value<A, B>", dataType.getValueType().toString());
    Map<String, Metadata.Property> properties = Maps.uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals("B", properties.get("age").getType().toString());
    assertNull(properties.get("age").getBoxedType());
    assertEquals("AGE", properties.get("age").getAllCapsName());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("A", properties.get("name").getType().toString());
    assertNull(properties.get("name").getBoxedType());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
  }

  @Test
  public void wrongBuilderSuperclass_errorType() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public interface DataType {",
        "  class Builder extends SomeOther_Builder { }",
        "}");

    analyser.analyse(dataType);

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("Builder", ImmutableList.of(
            "[ERROR] Builder extends the wrong type (should be DataType_Builder)"));
  }

  @Test
  public void wrongBuilderSuperclass_actualType() throws Analyser.CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "@" + Generated.class.getCanonicalName() + "(\"EBuilder FTW!\")",
        "class SomeOther_Builder { }");
    TypeElement dataType = model.newType(
        "package com.example;",
        "public interface DataType {",
        "  class Builder extends SomeOther_Builder { }",
        "}");

    analyser.analyse(dataType);

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("Builder", ImmutableList.of(
            "[ERROR] Builder extends the wrong type (should be DataType_Builder)"));
  }

  @Test
  public void explicitPackageScopeNoArgsConstructor() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  DataType() { }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    TypeElement concreteBuilder = model.typeElement("com.example.DataType.Builder");
    QualifiedName expectedBuilder = QualifiedName.of("com.example", "DataType_Builder");
    QualifiedName partialType = expectedBuilder.nestedType("Partial");
    QualifiedName propertyType = expectedBuilder.nestedType("Property");
    QualifiedName valueType = expectedBuilder.nestedType("Value");
    Metadata expectedMetadata = new Metadata.Builder()
        .setBuilder(QualifiedName.of("com.example", "DataType", "Builder").withParameters())
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setGeneratedBuilder(expectedBuilder.withParameters())
        .setInterfaceType(false)
        .setPartialType(partialType.withParameters())
        .setPropertyEnum(propertyType.withParameters())
        .setType(QualifiedName.of("com.example", "DataType").withParameters())
        .setValueType(valueType.withParameters())
        .addVisibleNestedTypes(QualifiedName.of(concreteBuilder))
        .addVisibleNestedTypes(partialType)
        .addVisibleNestedTypes(propertyType)
        .addVisibleNestedTypes(valueType)
        .build();

    assertEquals(expectedMetadata, metadata);
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void multipleConstructors() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  DataType(int i) { }",
        "  DataType() { }",
        "  DataType(String s) { }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    TypeElement concreteBuilder = model.typeElement("com.example.DataType.Builder");
    QualifiedName expectedBuilder = QualifiedName.of("com.example", "DataType_Builder");
    QualifiedName partialType = expectedBuilder.nestedType("Partial");
    QualifiedName propertyType = expectedBuilder.nestedType("Property");
    QualifiedName valueType = expectedBuilder.nestedType("Value");
    Metadata expectedMetadata = new Metadata.Builder()
        .setBuilder(QualifiedName.of("com.example", "DataType", "Builder").withParameters())
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setGeneratedBuilder(expectedBuilder.withParameters())
        .setInterfaceType(false)
        .setPartialType(partialType.withParameters())
        .setPropertyEnum(propertyType.withParameters())
        .setType(QualifiedName.of("com.example", "DataType").withParameters())
        .setValueType(valueType.withParameters())
        .addVisibleNestedTypes(QualifiedName.of(concreteBuilder))
        .addVisibleNestedTypes(partialType)
        .addVisibleNestedTypes(propertyType)
        .addVisibleNestedTypes(valueType)
        .build();

    assertEquals(expectedMetadata, metadata);
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void explicitPrivateScopeNoArgsConstructor() {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  private DataType() { }",
        "}");

    try {
      analyser.analyse(dataType);
      fail("Expected CannotGenerateCodeException");
    } catch (Analyser.CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("<init>", ImmutableList.of(
            "[ERROR] @EBuilder types must have a package-visible no-args constructor"));
  }

  @Test
  public void noNoArgsConstructor() {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  private DataType(int x) { }",
        "}");

    try {
      analyser.analyse(dataType);
      fail("Expected CannotGenerateCodeException");
    } catch (Analyser.CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[ERROR] @EBuilder types must have a package-visible no-args constructor"));
  }

  @Test
  public void freeEnumBuilder() {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public enum DataType {}");

    try {
      analyser.analyse(dataType);
      fail("Expected CannotGenerateCodeException");
    } catch (Analyser.CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[ERROR] @EBuilder does not support enum types"));
  }

  @Test
  public void unnamedPackage() {
    TypeElement dataType = model.newType(
        "public class DataType {}");

    try {
      analyser.analyse(dataType);
      fail("Expected CannotGenerateCodeException");
    } catch (Analyser.CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[ERROR] @EBuilder does not support types in unnamed packages"));
  }

  @Test
  public void freeAnnotationBuilder() {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public @interface DataType {}");

    try {
      analyser.analyse(dataType);
      fail("Expected CannotGenerateCodeException");
    } catch (Analyser.CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[ERROR] @EBuilder does not support annotation types"));
  }

  @Test
  public void isFullyCheckedCast_nonGenericType() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Metadata.Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_erasedType() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Metadata.Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_wildcardType() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<?> getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Metadata.Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_genericType() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<String> getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Metadata.Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isFalse();
  }

  @Test
  public void isFullyCheckedCast_lowerBoundWildcard() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<? extends Number> getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Metadata.Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isFalse();
  }

  @Test
  public void isFullyCheckedCast_objectLowerBoundWildcard() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<? extends Object> getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Metadata.Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_oneWildcard() throws Analyser.CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract java.util.Map<?, String> getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Metadata.Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isFalse();
  }

  @Test
  public void typeNotNamedBuilderIgnored() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public interface DataType {",
        "  class Bulider extends DataType_Builder {}",
        "}");

    analyser.analyse(dataType);

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[NOTE] Add \"class Builder extends DataType_Builder {}\" to your interface "
                + "to enable the @EBuilder API"));
  }

  @Test
  public void valueTypeNestedClassesAddedToVisibleList() throws Analyser.CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  DataType(int i) { }",
        "  DataType() { }",
        "  DataType(String s) { }",
        "  public static class Builder extends DataType_Builder {}",
        "  public interface Objects {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    Truth.assertThat(metadata.getVisibleNestedTypes()).containsExactly(
        QualifiedName.of("com.example", "DataType", "Builder"),
        QualifiedName.of("com.example", "DataType", "Objects"),
        QualifiedName.of("com.example", "DataType_Builder", "Partial"),
        QualifiedName.of("com.example", "DataType_Builder", "Property"),
        QualifiedName.of("com.example", "DataType_Builder", "Value"));
  }

  @Test
  public void valueTypeSuperclassesNestedClassesAddedToVisibleList()
      throws Analyser.CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "public class SuperType {",
        "  public interface Objects {}",
        "}");
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType extends SuperType {",
        "  DataType(int i) { }",
        "  DataType() { }",
        "  DataType(String s) { }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    Truth.assertThat(metadata.getVisibleNestedTypes()).containsExactly(
        QualifiedName.of("com.example", "SuperType", "Objects"),
        QualifiedName.of("com.example", "DataType", "Builder"),
        QualifiedName.of("com.example", "DataType_Builder", "Partial"),
        QualifiedName.of("com.example", "DataType_Builder", "Property"),
        QualifiedName.of("com.example", "DataType_Builder", "Value"));
  }

  private static String asSource(Excerpt annotation) {
    return SourceStringBuilder.simple().add(annotation).toString().trim();
  }

  private static final Function<Metadata.Property, String> GET_NAME = new Function<Metadata.Property, String>() {
    @Override
    public String apply(Metadata.Property propery) {
      return propery.getName();
    }
  };
}
