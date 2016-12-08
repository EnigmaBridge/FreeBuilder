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

import static com.enigmabridge.ebuilder.processor.BuilderMethods.addAllMethod;
import static com.enigmabridge.ebuilder.processor.BuilderMethods.addMethod;
import static com.enigmabridge.ebuilder.processor.BuilderMethods.clearMethod;
import static com.enigmabridge.ebuilder.processor.BuilderMethods.getter;
import static com.enigmabridge.ebuilder.processor.BuilderMethods.mutator;
import static com.enigmabridge.ebuilder.processor.Util.erasesToAnyOf;
import static com.enigmabridge.ebuilder.processor.Util.upperBound;
import static com.enigmabridge.ebuilder.processor.util.ModelUtils.maybeDeclared;
import static com.enigmabridge.ebuilder.processor.util.ModelUtils.maybeUnbox;
import static com.enigmabridge.ebuilder.processor.util.ModelUtils.overrides;
import static com.enigmabridge.ebuilder.processor.util.StaticExcerpt.Type.METHOD;

import com.enigmabridge.ebuilder.processor.util.*;
import com.enigmabridge.ebuilder.processor.util.feature.FunctionPackage;
import com.enigmabridge.ebuilder.processor.util.feature.GuavaLibrary;
import com.enigmabridge.ebuilder.processor.util.feature.SourceLevel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import com.enigmabridge.ebuilder.processor.PropertyCodeGenerator.Config;
import com.enigmabridge.ebuilder.processor.excerpt.CheckedList;
import com.enigmabridge.ebuilder.processor.util.Block;
import com.enigmabridge.ebuilder.processor.util.Excerpt;
import com.enigmabridge.ebuilder.processor.util.Excerpts;
import com.enigmabridge.ebuilder.processor.util.ParameterizedType;
import com.enigmabridge.ebuilder.processor.util.QualifiedName;
import com.enigmabridge.ebuilder.processor.util.SourceBuilder;
import com.enigmabridge.ebuilder.processor.util.StaticExcerpt;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator.Factory} providing append-only semantics for {@link List}
 * properties.
 */
public class ListPropertyFactory implements PropertyCodeGenerator.Factory {

  @Override
  public Optional<? extends PropertyCodeGenerator> create(Config config) {
    DeclaredType type = maybeDeclared(config.getProperty().getType()).orNull();
    if (type == null || !erasesToAnyOf(type, Collection.class, List.class, ImmutableList.class)) {
      return Optional.absent();
    }

    TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
    Optional<TypeMirror> unboxedType = maybeUnbox(elementType, config.getTypes());
    boolean overridesAddMethod = hasAddMethodOverride(config, unboxedType.or(elementType));
    return Optional.of(new CodeGenerator(
        config.getMetadata(),
        config.getProperty(),
        overridesAddMethod,
        elementType,
        unboxedType));
  }

  private static boolean hasAddMethodOverride(Config config, TypeMirror keyType) {
    return overrides(
        config.getBuilder(),
        config.getTypes(),
        addMethod(config.getProperty()),
        keyType);
  }

  @VisibleForTesting static class CodeGenerator extends PropertyCodeGenerator {

    private static final ParameterizedType COLLECTION =
        QualifiedName.of(Collection.class).withParameters("E");

    private final boolean overridesAddMethod;
    private final TypeMirror elementType;
    private final Optional<TypeMirror> unboxedType;

    @VisibleForTesting
    CodeGenerator(
        Metadata metadata,
        Metadata.Property property,
        boolean overridesAddMethod,
        TypeMirror elementType,
        Optional<TypeMirror> unboxedType) {
      super(metadata, property);
      this.overridesAddMethod = overridesAddMethod;
      this.elementType = elementType;
      this.unboxedType = unboxedType;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("%s<%s> %s = %s.of();",
            List.class,
            elementType,
            property.getName(),
            ImmutableList.class);
      } else {
        code.addLine("final %1$s<%2$s> %3$s = new %1$s%4$s();",
            ArrayList.class,
            elementType,
            property.getName(),
            SourceLevel.diamondOperator(elementType));
      }
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code) {
      addAdd(code, metadata);
      addVarargsAdd(code, metadata);
      addAddAll(code, metadata);
      addMutate(code, metadata);
      addClear(code, metadata);
      addGetter(code, metadata);
    }

    private void addAdd(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds {@code element} to the list to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code element} is null");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s element) {",
              metadata.getBuildGen(), addMethod(property), unboxedType.or(elementType));
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("  if (this.%s instanceof %s) {", property.getName(), ImmutableList.class)
            .addLine("    this.%1$s = new %2$s%3$s(this.%1$s);",
                property.getName(),
                ArrayList.class,
                SourceLevel.diamondOperator(elementType))
            .addLine("  }");
      }
      if (unboxedType.isPresent()) {
        code.addLine("  this.%s.add(element);", property.getName());
      } else {
        code.add(PreconditionExcerpts.checkNotNullPreamble("element"))
            .addLine("  this.%s.add(%s);", property.getName(), PreconditionExcerpts.checkNotNullInline("element"));
      }
      code.addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addVarargsAdd(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds each element of {@code elements} to the list to be returned from")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code elements} is null or contains a")
            .addLine(" *     null element");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s... elements) {",
              metadata.getBuildGen(),
              addMethod(property),
              unboxedType.or(elementType));
      Optional<Class<?>> arrayUtils = code.feature(GuavaLibrary.GUAVA).arrayUtils(unboxedType.or(elementType));
      if (arrayUtils.isPresent()) {
        code.addLine("  return %s(%s.asList(elements));", addAllMethod(property), arrayUtils.get());
      } else {
        // Primitive type, Guava not available
        code.addLine("  %1$s.ensureCapacity(%1$s.size() + elements.length);", property.getName())
            .addLine("  for (%s element : elements) {", unboxedType.get())
            .addLine("    %s(element);", addMethod(property))
            .addLine("  }")
            .addLine("  return getThisBuilder();");
            //.addLine("  return (%s) this;", metadata.getBuilder());
      }
      code.addLine("}");
    }

    private void addAddAll(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds each element of {@code elements} to the list to be returned from")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code elements} is null or contains a")
          .addLine(" *     null element")
          .addLine(" */");
      addAccessorAnnotations(code);
      code.addLine("public %s %s(%s<? extends %s> elements) {",
              metadata.getBuildGen(),
              addAllMethod(property),
              Iterable.class,
              elementType);
      code.addLine("  if (elements instanceof %s) {", Collection.class)
          .addLine("    int elementsSize = ((%s<?>) elements).size();", Collection.class);
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("    if (elementsSize != 0) {")
            .addLine("      if (%s instanceof %s) {", property.getName(), ImmutableList.class)
            .addLine("        %1$s = new %2$s%3$s(%1$s);",
                property.getName(), ArrayList.class, SourceLevel.diamondOperator(elementType))
            .addLine("      }")
            .add("      ((%s<?>) %s)", ArrayList.class, property.getName());
      } else {
        code.add("    %s", property.getName());
      }
      code.add(".ensureCapacity(%s.size() + elementsSize);%n", property.getName())
          .addLine("  }");
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("  }");
      }
      code.add(Excerpts.forEach(unboxedType.or(elementType), "elements", addMethod(property)))
          .addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addMutate(SourceBuilder code, Metadata metadata) {
      ParameterizedType consumer = code.feature(FunctionPackage.FUNCTION_PACKAGE).consumer().orNull();
      if (consumer == null) {
        return;
      }
      code.addLine("")
          .addLine("/**")
          .addLine(" * Applies {@code mutator} to the list to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * <p>This method mutates the list in-place. {@code mutator} is a void")
          .addLine(" * consumer, so any value returned from a lambda will be ignored. Take care")
          .addLine(" * not to call pure functions, like %s.",
              COLLECTION.javadocNoArgMethodLink("stream"))
          .addLine(" *")
          .addLine(" * @return this {@code Builder} object")
          .addLine(" * @throws NullPointerException if {@code mutator} is null")
          .addLine(" */")
          .addLine("public %s %s(%s<? super %s<%s>> mutator) {",
              metadata.getBuildGen(),
              mutator(property),
              consumer.getQualifiedName(),
              List.class,
              elementType);
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("  if (this.%s instanceof %s) {", property.getName(), ImmutableList.class)
            .addLine("    this.%1$s = new %2$s%3$s(this.%1$s);",
                property.getName(),
                ArrayList.class,
                SourceLevel.diamondOperator(elementType))
            .addLine("  }");
      }
      if (overridesAddMethod) {
        code.addLine("  mutator.accept(new CheckedList<>(%s, this::%s));",
            property.getName(), addMethod(property));
      } else {
        code.addLine("  // If %s is overridden, this method will be updated to delegate to it",
                addMethod(property))
            .addLine("  mutator.accept(%s);", property.getName());
      }
      code.addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addClear(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Clears the list to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s() {", metadata.getBuildGen(), clearMethod(property));
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("  if (%s instanceof %s) {", property.getName(), ImmutableList.class)
            .addLine("    %s = %s.of();", property.getName(), ImmutableList.class)
            .addLine("  } else {");
      }
      code.addLine("    %s.clear();", property.getName());
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("  }");
      }
      code.addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addGetter(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns an unmodifiable view of the list that will be returned by")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Changes to this builder will be reflected in the view.")
          .addLine(" */")
          .addLine("public %s<%s> %s() {", List.class, elementType, getter(property));
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("  if (%s instanceof %s) {", property.getName(), ImmutableList.class)
            .addLine("    %1$s = new %2$s%3$s(%1$s);",
                property.getName(), ArrayList.class, SourceLevel.diamondOperator(elementType))
            .addLine("  }");
      }
      code.addLine("  return %s.unmodifiableList(%s);", Collections.class, property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("%s = %s.copyOf(%s.%s);",
            finalField, ImmutableList.class, builder, property.getName());
      } else {
        code.addLine("%s = immutableList(%s.%s, %s.class);",
            finalField, builder, property.getName(), elementType);
      }
    }

    @Override
    public void addMergeFromValue(Block code, String value) {
      boolean useGuavablock = code.feature(GuavaLibrary.GUAVA).isAvailable() && false;
      if (useGuavablock) {
        code.addLine("if (%s instanceof %s && %s == %s.<%s>of()) {",
                value,
                metadata.getValueType(),
                property.getName(),
                ImmutableList.class,
                elementType)
            .addLine("  %s = %s.%s();", property.getName(), value, property.getGetterName())
            .addLine("} else {");
      }
      code.addLine("%s(%s.%s());", addAllMethod(property), value, property.getGetterName());
      if (useGuavablock) {
        code.addLine("}");
      }
    }

    @Override
    public void addMergeFromBuilder(Block code, String builder) {
      Excerpt base = Declarations.upcastToGeneratedBuilder(code, metadata, builder);
      code.addLine("%s(%s.%s);", addAllMethod(property), base, property.getName());
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s(%s);", builder, addAllMethod(property), variable);
    }

    @Override
    public void addClearField(Block code) {
      code.addLine("%s();", clearMethod(property));
    }

    @Override
    public Set<StaticExcerpt> getStaticExcerpts() {
      ImmutableSet.Builder<StaticExcerpt> methods = ImmutableSet.builder();
      methods.add(IMMUTABLE_LIST);
      if (overridesAddMethod) {
        methods.addAll(CheckedList.excerpts());
      }
      return methods.build();
    }
  }

  private static final StaticExcerpt IMMUTABLE_LIST = new StaticExcerpt(METHOD, "immutableList") {
    @Override
    public void addTo(SourceBuilder code) {
      if (!code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("")
            .addLine("@%s(\"unchecked\")", SuppressWarnings.class)
            .addLine("private static <E> %1$s<E> immutableList(%1$s<E> elements, %2$s<E> type) {",
                List.class, Class.class)
            .addLine("  switch (elements.size()) {")
            .addLine("  case 0:")
            .addLine("    return %s.emptyList();", Collections.class)
            .addLine("  case 1:")
            .addLine("    return %s.singletonList(elements.get(0));", Collections.class)
            .addLine("  default:")
            .addLine("    return %s.unmodifiableList(%s.asList(elements.toArray(",
                Collections.class, Arrays.class)
            .addLine("        (E[]) %s.newInstance(type, elements.size()))));", Array.class)
            .addLine("  }")
            .addLine("}");
      }
    }
  };
}
