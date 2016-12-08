/*
 * Copyright 2015 Google Inc. All rights reserved.
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

import com.google.common.base.Joiner;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.enigmabridge.ebuilder.processor.Metadata.Property;
import com.enigmabridge.ebuilder.processor.util.QualifiedName;
import com.enigmabridge.ebuilder.processor.util.SourceBuilder;
import com.enigmabridge.ebuilder.processor.util.SourceStringBuilder;
import com.enigmabridge.ebuilder.processor.util.feature.Feature;
import com.enigmabridge.ebuilder.processor.util.feature.GuavaLibrary;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.lang.model.type.TypeVariable;

import static com.google.common.truth.Truth.assertThat;
import static com.enigmabridge.ebuilder.processor.util.TypeVariableImpl.newTypeVariable;
import static com.enigmabridge.ebuilder.processor.util.feature.SourceLevel.JAVA_7;
import static com.enigmabridge.ebuilder.processor.util.feature.SourceLevel.JAVA_8;

@RunWith(JUnit4.class)
public class GenericTypeSourceTest {

  @Test
  public void testJ6() {
    Metadata metadata = createMetadata();

    assertThat(generateSource(metadata, GuavaLibrary.AVAILABLE)).isEqualTo(Joiner.on('\n').join(
        "/**",
        " * Auto-generated superclass of {@link Person.Builder},",
        " * derived from the API of {@link Person}.",
        " */",
        "@Generated(\"org.inferred.freebuilder.processor.CodeGenerator\")",
        "abstract class Person_Builder<A, B> {",
        "",
        "  /**",
        "   * Creates a new builder using {@code value} as a template.",
        "   */",
        "  public static <A, B> Person.Builder<A, B> from(Person<A, B> value) {",
        "    return new Person.Builder<A, B>().mergeFrom(value);",
        "  }",
        "",
        "  private static final Joiner COMMA_JOINER = Joiner.on(\", \").skipNulls();",
        "",
        "  private enum Property {",
        "    NAME(\"name\"),",
        "    AGE(\"age\"),",
        "    ;",
        "",
        "    private final String name;",
        "",
        "    private Property(String name) {",
        "      this.name = name;",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return name;",
        "    }",
        "  }",
        "",
        "  private A name;",
        "  private B age;",
        "  private final EnumSet<Person_Builder.Property> _unsetProperties =",
        "      EnumSet.allOf(Person_Builder.Property.class);",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getName()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code name} is null",
        "   */",
        "  public Person.Builder<A, B> setName(A name) {",
        "    this.name = Preconditions.checkNotNull(name);",
        "    _unsetProperties.remove(Person_Builder.Property.NAME);",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Returns the value that will be returned by {@link Person#getName()}.",
        "   *",
        "   * @throws IllegalStateException if the field has not been set",
        "   */",
        "  public A getName() {",
        "    Preconditions.checkState(",
        "        !_unsetProperties.contains(Person_Builder.Property.NAME), \"name not set\");",
        "    return name;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getAge()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code age} is null",
        "   */",
        "  public Person.Builder<A, B> setAge(B age) {",
        "    this.age = Preconditions.checkNotNull(age);",
        "    _unsetProperties.remove(Person_Builder.Property.AGE);",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Returns the value that will be returned by {@link Person#getAge()}.",
        "   *",
        "   * @throws IllegalStateException if the field has not been set",
        "   */",
        "  public B getAge() {",
        "    Preconditions.checkState(",
        "        !_unsetProperties.contains(Person_Builder.Property.AGE), \"age not set\");",
        "    return age;",
        "  }",
        "",
        "  /**",
        "   * Sets all property values using the given {@code Person} as a template.",
        "   */",
        "  public Person.Builder<A, B> mergeFrom(Person<A, B> value) {",
        "    Person_Builder<A, B> _defaults = new Person.Builder<A, B>();",
        "    if (_defaults._unsetProperties.contains(Person_Builder.Property.NAME)",
        "        || !value.getName().equals(_defaults.getName())) {",
        "      setName(value.getName());",
        "    }",
        "    if (_defaults._unsetProperties.contains(Person_Builder.Property.AGE)",
        "        || !value.getAge().equals(_defaults.getAge())) {",
        "      setAge(value.getAge());",
        "    }",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Copies values from the given {@code Builder}.",
        "   * Does not affect any properties not set on the input.",
        "   */",
        "  public Person.Builder<A, B> mergeFrom(Person.Builder<A, B> template) {",
        "    // Upcast to access private fields; otherwise, oddly, we get an access violation.",
        "    Person_Builder<A, B> base = (Person_Builder<A, B>) template;",
        "    Person_Builder<A, B> _defaults = new Person.Builder<A, B>();",
        "    if (!base._unsetProperties.contains(Person_Builder.Property.NAME)",
        "        && (_defaults._unsetProperties.contains(Person_Builder.Property.NAME)",
        "            || !template.getName().equals(_defaults.getName()))) {",
        "      setName(template.getName());",
        "    }",
        "    if (!base._unsetProperties.contains(Person_Builder.Property.AGE)",
        "        && (_defaults._unsetProperties.contains(Person_Builder.Property.AGE)",
        "            || !template.getAge().equals(_defaults.getAge()))) {",
        "      setAge(template.getAge());",
        "    }",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Resets the state of this builder.",
        "   */",
        "  public Person.Builder<A, B> clear() {",
        "    Person_Builder<A, B> _defaults = new Person.Builder<A, B>();",
        "    name = _defaults.name;",
        "    age = _defaults.age;",
        "    _unsetProperties.clear();",
        "    _unsetProperties.addAll(_defaults._unsetProperties);",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Returns true if the required property corresponding to",
        "   * {@link Person#getName()} is set.",
        "   */",
        "  public boolean isPropertyNameSet() {",
        "    return _unsetProperties.contains(Person_Builder.Property.NAME);",
        "  }",
        "",
        "  /**",
        "   * Returns true if the required property corresponding to",
        "   * {@link Person#getAge()} is set.",
        "   */",
        "  public boolean isPropertyAgeSet() {",
        "    return _unsetProperties.contains(Person_Builder.Property.AGE);",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created {@link Person} based on the contents of the {@code Builder}.",
        "   *",
        "   * @throws IllegalStateException if any field has not been set",
        "   */",
        "  public Person<A, B> build() {",
        "    Preconditions.checkState(_unsetProperties.isEmpty(),"
            + " \"Not set: %s\", _unsetProperties);",
        "    return new Person_Builder.Value<A, B>(this);",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created partial {@link Person}",
        "   * based on the contents of the {@code Builder}.",
        "   * State checking will not be performed.",
        "   * Unset properties will throw an {@link UnsupportedOperationException}",
        "   * when accessed via the partial object.",
        "   *",
        "   * <p>Partials should only ever be used in tests.",
        "   */",
        "  @VisibleForTesting()",
        "  public Person<A, B> buildPartial() {",
        "    return new Person_Builder.Partial<A, B>(this);",
        "  }",
        "",
        "  private static final class Value<A, B> extends Person<A, B> {",
        "    private final A name;",
        "    private final B age;",
        "",
        "    private Value(Person_Builder<A, B> builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "    }",
        "",
        "    @Override",
        "    public A getName() {",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    public B getAge() {",
        "      return age;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Value)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Value<?, ?> other = (Person_Builder.Value<?, ?>) obj;",
        "      if (!name.equals(other.name)) {",
        "        return false;",
        "      }",
        "      if (!age.equals(other.age)) {",
        "        return false;",
        "      }",
        "      return true;",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Arrays.hashCode(new Object[] {name, age});",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"Person{\" + \"name=\" + name + \", \" + \"age=\" + age + \"}\";",
        "    }",
        "  }",
        "",
        "  private static final class Partial<A, B> extends Person<A, B> {",
        "    private final A name;",
        "    private final B age;",
        "    private final EnumSet<Person_Builder.Property> _unsetProperties;",
        "",
        "    Partial(Person_Builder<A, B> builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "      this._unsetProperties = builder._unsetProperties.clone();",
        "    }",
        "",
        "    @Override",
        "    public A getName() {",
        "      if (_unsetProperties.contains(Person_Builder.Property.NAME)) {",
        "        throw new UnsupportedOperationException(\"name not set\");",
        "      }",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    public B getAge() {",
        "      if (_unsetProperties.contains(Person_Builder.Property.AGE)) {",
        "        throw new UnsupportedOperationException(\"age not set\");",
        "      }",
        "      return age;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Partial)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Partial<?, ?> other = (Person_Builder.Partial<?, ?>) obj;",
        "      if (name != other.name && (name == null || !name.equals(other.name))) {",
        "        return false;",
        "      }",
        "      if (age != other.age && (age == null || !age.equals(other.age))) {",
        "        return false;",
        "      }",
        "      return _unsetProperties.equals(other._unsetProperties);",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Arrays.hashCode(new Object[] {name, age, _unsetProperties});",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"partial Person{\"",
        "          + COMMA_JOINER.join(",
        "              (!_unsetProperties.contains(Person_Builder.Property.NAME) "
            + "? \"name=\" + name : null),",
        "              (!_unsetProperties.contains(Person_Builder.Property.AGE) "
            + "? \"age=\" + age : null))",
        "          + \"}\";",
        "    }",
        "  }",
        "}\n"));
  }

  @Test
  public void testJ7() {
    Metadata metadata = createMetadata();

    String source = generateSource(metadata, JAVA_7, GuavaLibrary.AVAILABLE);
    assertThat(source).isEqualTo(Joiner.on('\n').join(
        "/**",
        " * Auto-generated superclass of {@link Person.Builder},",
        " * derived from the API of {@link Person}.",
        " */",
        "@Generated(\"org.inferred.freebuilder.processor.CodeGenerator\")",
        "abstract class Person_Builder<A, B> {",
        "",
        "  /**",
        "   * Creates a new builder using {@code value} as a template.",
        "   */",
        "  public static <A, B> Person.Builder<A, B> from(Person<A, B> value) {",
        "    return new Person.Builder<A, B>().mergeFrom(value);",
        "  }",
        "",
        "  private static final Joiner COMMA_JOINER = Joiner.on(\", \").skipNulls();",
        "",
        "  private enum Property {",
        "    NAME(\"name\"),",
        "    AGE(\"age\"),",
        "    ;",
        "",
        "    private final String name;",
        "",
        "    private Property(String name) {",
        "      this.name = name;",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return name;",
        "    }",
        "  }",
        "",
        "  private A name;",
        "  private B age;",
        "  private final EnumSet<Person_Builder.Property> _unsetProperties =",
        "      EnumSet.allOf(Person_Builder.Property.class);",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getName()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code name} is null",
        "   */",
        "  public Person.Builder<A, B> setName(A name) {",
        "    this.name = Preconditions.checkNotNull(name);",
        "    _unsetProperties.remove(Person_Builder.Property.NAME);",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Returns the value that will be returned by {@link Person#getName()}.",
        "   *",
        "   * @throws IllegalStateException if the field has not been set",
        "   */",
        "  public A getName() {",
        "    Preconditions.checkState(",
        "        !_unsetProperties.contains(Person_Builder.Property.NAME), \"name not set\");",
        "    return name;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getAge()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code age} is null",
        "   */",
        "  public Person.Builder<A, B> setAge(B age) {",
        "    this.age = Preconditions.checkNotNull(age);",
        "    _unsetProperties.remove(Person_Builder.Property.AGE);",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Returns the value that will be returned by {@link Person#getAge()}.",
        "   *",
        "   * @throws IllegalStateException if the field has not been set",
        "   */",
        "  public B getAge() {",
        "    Preconditions.checkState(",
        "        !_unsetProperties.contains(Person_Builder.Property.AGE), \"age not set\");",
        "    return age;",
        "  }",
        "",
        "  /**",
        "   * Sets all property values using the given {@code Person} as a template.",
        "   */",
        "  public Person.Builder<A, B> mergeFrom(Person<A, B> value) {",
        "    Person_Builder<A, B> _defaults = new Person.Builder<>();",
        "    if (_defaults._unsetProperties.contains(Person_Builder.Property.NAME)",
        "        || !value.getName().equals(_defaults.getName())) {",
        "      setName(value.getName());",
        "    }",
        "    if (_defaults._unsetProperties.contains(Person_Builder.Property.AGE)",
        "        || !value.getAge().equals(_defaults.getAge())) {",
        "      setAge(value.getAge());",
        "    }",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Copies values from the given {@code Builder}.",
        "   * Does not affect any properties not set on the input.",
        "   */",
        "  public Person.Builder<A, B> mergeFrom(Person.Builder<A, B> template) {",
        "    // Upcast to access private fields; otherwise, oddly, we get an access violation.",
        "    Person_Builder<A, B> base = (Person_Builder<A, B>) template;",
        "    Person_Builder<A, B> _defaults = new Person.Builder<>();",
        "    if (!base._unsetProperties.contains(Person_Builder.Property.NAME)",
        "        && (_defaults._unsetProperties.contains(Person_Builder.Property.NAME)",
        "            || !template.getName().equals(_defaults.getName()))) {",
        "      setName(template.getName());",
        "    }",
        "    if (!base._unsetProperties.contains(Person_Builder.Property.AGE)",
        "        && (_defaults._unsetProperties.contains(Person_Builder.Property.AGE)",
        "            || !template.getAge().equals(_defaults.getAge()))) {",
        "      setAge(template.getAge());",
        "    }",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Resets the state of this builder.",
        "   */",
        "  public Person.Builder<A, B> clear() {",
        "    Person_Builder<A, B> _defaults = new Person.Builder<>();",
        "    name = _defaults.name;",
        "    age = _defaults.age;",
        "    _unsetProperties.clear();",
        "    _unsetProperties.addAll(_defaults._unsetProperties);",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Returns true if the required property corresponding to",
        "   * {@link Person#getName()} is set.",
        "   */",
        "  public boolean isPropertyNameSet() {",
        "    return _unsetProperties.contains(Person_Builder.Property.NAME);",
        "  }",
        "",
        "  /**",
        "   * Returns true if the required property corresponding to",
        "   * {@link Person#getAge()} is set.",
        "   */",
        "  public boolean isPropertyAgeSet() {",
        "    return _unsetProperties.contains(Person_Builder.Property.AGE);",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created {@link Person} based on the contents of the {@code Builder}.",
        "   *",
        "   * @throws IllegalStateException if any field has not been set",
        "   */",
        "  public Person<A, B> build() {",
        "    Preconditions.checkState(_unsetProperties.isEmpty(),"
            + " \"Not set: %s\", _unsetProperties);",
        "    return new Person_Builder.Value<>(this);",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created partial {@link Person}",
        "   * based on the contents of the {@code Builder}.",
        "   * State checking will not be performed.",
        "   * Unset properties will throw an {@link UnsupportedOperationException}",
        "   * when accessed via the partial object.",
        "   *",
        "   * <p>Partials should only ever be used in tests.",
        "   */",
        "  @VisibleForTesting()",
        "  public Person<A, B> buildPartial() {",
        "    return new Person_Builder.Partial<>(this);",
        "  }",
        "",
        "  private static final class Value<A, B> extends Person<A, B> {",
        "    private final A name;",
        "    private final B age;",
        "",
        "    private Value(Person_Builder<A, B> builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "    }",
        "",
        "    @Override",
        "    public A getName() {",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    public B getAge() {",
        "      return age;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Value)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Value<?, ?> other = (Person_Builder.Value<?, ?>) obj;",
        "      return Objects.equals(name, other.name) && Objects.equals(age, other.age);",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Objects.hash(name, age);",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"Person{\" + \"name=\" + name + \", \" + \"age=\" + age + \"}\";",
        "    }",
        "  }",
        "",
        "  private static final class Partial<A, B> extends Person<A, B> {",
        "    private final A name;",
        "    private final B age;",
        "    private final EnumSet<Person_Builder.Property> _unsetProperties;",
        "",
        "    Partial(Person_Builder<A, B> builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "      this._unsetProperties = builder._unsetProperties.clone();",
        "    }",
        "",
        "    @Override",
        "    public A getName() {",
        "      if (_unsetProperties.contains(Person_Builder.Property.NAME)) {",
        "        throw new UnsupportedOperationException(\"name not set\");",
        "      }",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    public B getAge() {",
        "      if (_unsetProperties.contains(Person_Builder.Property.AGE)) {",
        "        throw new UnsupportedOperationException(\"age not set\");",
        "      }",
        "      return age;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Partial)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Partial<?, ?> other = (Person_Builder.Partial<?, ?>) obj;",
        "      return Objects.equals(name, other.name)",
        "          && Objects.equals(age, other.age)",
        "          && Objects.equals(_unsetProperties, other._unsetProperties);",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Objects.hash(name, age, _unsetProperties);",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"partial Person{\"",
        "          + COMMA_JOINER.join(",
        "              (!_unsetProperties.contains(Person_Builder.Property.NAME) "
            + "? \"name=\" + name : null),",
        "              (!_unsetProperties.contains(Person_Builder.Property.AGE) "
            + "? \"age=\" + age : null))",
        "          + \"}\";",
        "    }",
        "  }",
        "}\n"));
  }

  @Test
  public void testJ8() {
    Metadata metadata = createMetadata();

    String source = generateSource(metadata, JAVA_8, GuavaLibrary.AVAILABLE);
    assertThat(source).isEqualTo(Joiner.on('\n').join(
        "/**",
        " * Auto-generated superclass of {@link Person.Builder},",
        " * derived from the API of {@link Person}.",
        " */",
        "abstract class Person_Builder<A, B> {",
        "",
        "  /**",
        "   * Creates a new builder using {@code value} as a template.",
        "   */",
        "  public static <A, B> Person.Builder<A, B> from(Person<A, B> value) {",
        "    return new Person.Builder<A, B>().mergeFrom(value);",
        "  }",
        "",
        "  private static final Joiner COMMA_JOINER = Joiner.on(\", \").skipNulls();",
        "",
        "  private enum Property {",
        "    NAME(\"name\"),",
        "    AGE(\"age\"),",
        "    ;",
        "",
        "    private final String name;",
        "",
        "    private Property(String name) {",
        "      this.name = name;",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return name;",
        "    }",
        "  }",
        "",
        "  private A name;",
        "  private B age;",
        "  private final EnumSet<Person_Builder.Property> _unsetProperties =",
        "      EnumSet.allOf(Person_Builder.Property.class);",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getName()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code name} is null",
        "   */",
        "  public Person.Builder<A, B> setName(A name) {",
        "    this.name = Preconditions.checkNotNull(name);",
        "    _unsetProperties.remove(Person_Builder.Property.NAME);",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Replaces the value to be returned by {@link Person#getName()}",
        "   * by applying {@code mapper} to it and using the result.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code mapper} is null or returns null",
        "   * @throws IllegalStateException if the field has not been set",
        "   */",
        "  public Person.Builder<A, B> mapName(UnaryOperator<A> mapper) {",
        "    Preconditions.checkNotNull(mapper);",
        "    return setName(mapper.apply(getName()));",
        "  }",
        "",
        "  /**",
        "   * Returns the value that will be returned by {@link Person#getName()}.",
        "   *",
        "   * @throws IllegalStateException if the field has not been set",
        "   */",
        "  public A getName() {",
        "    Preconditions.checkState(",
        "        !_unsetProperties.contains(Person_Builder.Property.NAME), \"name not set\");",
        "    return name;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getAge()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code age} is null",
        "   */",
        "  public Person.Builder<A, B> setAge(B age) {",
        "    this.age = Preconditions.checkNotNull(age);",
        "    _unsetProperties.remove(Person_Builder.Property.AGE);",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Replaces the value to be returned by {@link Person#getAge()}",
        "   * by applying {@code mapper} to it and using the result.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code mapper} is null or returns null",
        "   * @throws IllegalStateException if the field has not been set",
        "   */",
        "  public Person.Builder<A, B> mapAge(UnaryOperator<B> mapper) {",
        "    Preconditions.checkNotNull(mapper);",
        "    return setAge(mapper.apply(getAge()));",
        "  }",
        "",
        "  /**",
        "   * Returns the value that will be returned by {@link Person#getAge()}.",
        "   *",
        "   * @throws IllegalStateException if the field has not been set",
        "   */",
        "  public B getAge() {",
        "    Preconditions.checkState(",
        "        !_unsetProperties.contains(Person_Builder.Property.AGE), \"age not set\");",
        "    return age;",
        "  }",
        "",
        "  /**",
        "   * Sets all property values using the given {@code Person} as a template.",
        "   */",
        "  public Person.Builder<A, B> mergeFrom(Person<A, B> value) {",
        "    Person_Builder<A, B> _defaults = new Person.Builder<>();",
        "    if (_defaults._unsetProperties.contains(Person_Builder.Property.NAME)",
        "        || !value.getName().equals(_defaults.getName())) {",
        "      setName(value.getName());",
        "    }",
        "    if (_defaults._unsetProperties.contains(Person_Builder.Property.AGE)",
        "        || !value.getAge().equals(_defaults.getAge())) {",
        "      setAge(value.getAge());",
        "    }",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Copies values from the given {@code Builder}.",
        "   * Does not affect any properties not set on the input.",
        "   */",
        "  public Person.Builder<A, B> mergeFrom(Person.Builder<A, B> template) {",
        "    // Upcast to access private fields; otherwise, oddly, we get an access violation.",
        "    Person_Builder<A, B> base = (Person_Builder<A, B>) template;",
        "    Person_Builder<A, B> _defaults = new Person.Builder<>();",
        "    if (!base._unsetProperties.contains(Person_Builder.Property.NAME)",
        "        && (_defaults._unsetProperties.contains(Person_Builder.Property.NAME)",
        "            || !template.getName().equals(_defaults.getName()))) {",
        "      setName(template.getName());",
        "    }",
        "    if (!base._unsetProperties.contains(Person_Builder.Property.AGE)",
        "        && (_defaults._unsetProperties.contains(Person_Builder.Property.AGE)",
        "            || !template.getAge().equals(_defaults.getAge()))) {",
        "      setAge(template.getAge());",
        "    }",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Resets the state of this builder.",
        "   */",
        "  public Person.Builder<A, B> clear() {",
        "    Person_Builder<A, B> _defaults = new Person.Builder<>();",
        "    name = _defaults.name;",
        "    age = _defaults.age;",
        "    _unsetProperties.clear();",
        "    _unsetProperties.addAll(_defaults._unsetProperties);",
        "    return (Person.Builder<A, B>) this;",
        "  }",
        "",
        "  /**",
        "   * Returns true if the required property corresponding to",
        "   * {@link Person#getName()} is set.",
        "   */",
        "  public boolean isPropertyNameSet() {",
        "    return _unsetProperties.contains(Person_Builder.Property.NAME);",
        "  }",
        "",
        "  /**",
        "   * Returns true if the required property corresponding to",
        "   * {@link Person#getAge()} is set.",
        "   */",
        "  public boolean isPropertyAgeSet() {",
        "    return _unsetProperties.contains(Person_Builder.Property.AGE);",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created {@link Person} based on the contents of the {@code Builder}.",
        "   *",
        "   * @throws IllegalStateException if any field has not been set",
        "   */",
        "  public Person<A, B> build() {",
        "    Preconditions.checkState(_unsetProperties.isEmpty(),"
            + " \"Not set: %s\", _unsetProperties);",
        "    return new Person_Builder.Value<>(this);",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created partial {@link Person}",
        "   * based on the contents of the {@code Builder}.",
        "   * State checking will not be performed.",
        "   * Unset properties will throw an {@link UnsupportedOperationException}",
        "   * when accessed via the partial object.",
        "   *",
        "   * <p>Partials should only ever be used in tests.",
        "   */",
        "  @VisibleForTesting()",
        "  public Person<A, B> buildPartial() {",
        "    return new Person_Builder.Partial<>(this);",
        "  }",
        "",
        "  private static final class Value<A, B> extends Person<A, B> {",
        "    private final A name;",
        "    private final B age;",
        "",
        "    private Value(Person_Builder<A, B> builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "    }",
        "",
        "    @Override",
        "    public A getName() {",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    public B getAge() {",
        "      return age;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Value)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Value<?, ?> other = (Person_Builder.Value<?, ?>) obj;",
        "      return Objects.equals(name, other.name) && Objects.equals(age, other.age);",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Objects.hash(name, age);",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"Person{\" + \"name=\" + name + \", \" + \"age=\" + age + \"}\";",
        "    }",
        "  }",
        "",
        "  private static final class Partial<A, B> extends Person<A, B> {",
        "    private final A name;",
        "    private final B age;",
        "    private final EnumSet<Person_Builder.Property> _unsetProperties;",
        "",
        "    Partial(Person_Builder<A, B> builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "      this._unsetProperties = builder._unsetProperties.clone();",
        "    }",
        "",
        "    @Override",
        "    public A getName() {",
        "      if (_unsetProperties.contains(Person_Builder.Property.NAME)) {",
        "        throw new UnsupportedOperationException(\"name not set\");",
        "      }",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    public B getAge() {",
        "      if (_unsetProperties.contains(Person_Builder.Property.AGE)) {",
        "        throw new UnsupportedOperationException(\"age not set\");",
        "      }",
        "      return age;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Partial)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Partial<?, ?> other = (Person_Builder.Partial<?, ?>) obj;",
        "      return Objects.equals(name, other.name)",
        "          && Objects.equals(age, other.age)",
        "          && Objects.equals(_unsetProperties, other._unsetProperties);",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Objects.hash(name, age, _unsetProperties);",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"partial Person{\"",
        "          + COMMA_JOINER.join(",
        "              (!_unsetProperties.contains(Person_Builder.Property.NAME) "
            + "? \"name=\" + name : null),",
        "              (!_unsetProperties.contains(Person_Builder.Property.AGE) "
            + "? \"age=\" + age : null))",
        "          + \"}\";",
        "    }",
        "  }",
        "}\n"));
  }

  private static String generateSource(Metadata metadata, Feature<?>... features) {
    SourceBuilder sourceBuilder = SourceStringBuilder.simple(features);
    new CodeGenerator().writeBuilderSource(sourceBuilder, metadata);
    try {
      return new Formatter().formatSource(sourceBuilder.toString());
    } catch (FormatterException e) {
      throw new RuntimeException(e);
    }
  }

  private static Metadata createMetadata() {
    QualifiedName person = QualifiedName.of("com.example", "Person");
    QualifiedName generatedBuilder = QualifiedName.of("com.example", "Person_Builder");
    TypeVariable typeVariableA = newTypeVariable("A");
    Property name = new Property.Builder()
        .setAllCapsName("NAME")
        .setCapitalizedName("Name")
        .setFullyCheckedCast(true)
        .setGetterName("getName")
        .setName("name")
        .setType(typeVariableA)
        .setUsingBeanConvention(true)
        .build();
    TypeVariable typeVariableB = newTypeVariable("B");
    Property age = new Property.Builder()
        .setAllCapsName("AGE")
        .setCapitalizedName("Age")
        .setFullyCheckedCast(true)
        .setGetterName("getAge")
        .setName("age")
        .setType(typeVariableB)
        .setUsingBeanConvention(true)
        .build();
    Metadata metadata = new Metadata.Builder()
        .setBuilder(person.nestedType("Builder").withParameters("A", "B"))
        .setBuilderFactory(BuilderFactory.NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setGeneratedBuilder(generatedBuilder.withParameters("A", "B"))
        .setInterfaceType(false)
        .setPartialType(generatedBuilder.nestedType("Partial").withParameters("A", "B"))
        .addProperties(name, age)
        .setPropertyEnum(generatedBuilder.nestedType("Property").withParameters())
        .setType(person.withParameters("A", "B"))
        .setValueType(generatedBuilder.nestedType("Value").withParameters("A", "B"))
        .build();
    return metadata.toBuilder()
        .clearProperties()
        .addProperties(name.toBuilder()
            .setCodeGenerator(new DefaultPropertyFactory.CodeGenerator(metadata, name, false))
            .build())
        .addProperties(age.toBuilder()
            .setCodeGenerator(new DefaultPropertyFactory.CodeGenerator(metadata, age, false))
            .build())
        .build();
  }

}
