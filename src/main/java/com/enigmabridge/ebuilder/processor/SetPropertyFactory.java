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
import com.google.common.collect.ImmutableSet;

import com.enigmabridge.ebuilder.processor.PropertyCodeGenerator.Config;
import com.enigmabridge.ebuilder.processor.excerpt.CheckedSet;
import com.enigmabridge.ebuilder.processor.util.Block;
import com.enigmabridge.ebuilder.processor.util.Excerpts;
import com.enigmabridge.ebuilder.processor.util.ParameterizedType;
import com.enigmabridge.ebuilder.processor.util.QualifiedName;
import com.enigmabridge.ebuilder.processor.util.SourceBuilder;
import com.enigmabridge.ebuilder.processor.util.StaticExcerpt;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator.Factory} providing append-only semantics for {@link Set}
 * properties.
 */
public class SetPropertyFactory implements PropertyCodeGenerator.Factory {

  @Override
  public Optional<CodeGenerator> create(Config config) {
    DeclaredType type = maybeDeclared(config.getProperty().getType()).orNull();
    if (type == null || !erasesToAnyOf(type, Set.class, ImmutableSet.class)) {
      return Optional.absent();
    }

    TypeMirror elementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
    Optional<TypeMirror> unboxedType = maybeUnbox(elementType, config.getTypes());
    boolean overridesAddMethod = hasAddMethodOverride(config, unboxedType.or(elementType));
    return Optional.of(new CodeGenerator(
        config.getMetadata(), config.getProperty(), elementType, unboxedType, overridesAddMethod));
  }

  private static boolean hasAddMethodOverride(Config config, TypeMirror elementType) {
    return overrides(
        config.getBuilder(),
        config.getTypes(),
        BuilderMethods.addMethod(config.getProperty()),
        elementType);
  }

  @VisibleForTesting
  static class CodeGenerator extends PropertyCodeGenerator {

    private static final ParameterizedType COLLECTION =
        QualifiedName.of(Collection.class).withParameters("E");
    private final TypeMirror elementType;
    private final Optional<TypeMirror> unboxedType;
    private final boolean overridesAddMethod;

    CodeGenerator(
        Metadata metadata,
        Metadata.Property property,
        TypeMirror elementType,
        Optional<TypeMirror> unboxedType,
        boolean overridesAddMethod) {
      super(metadata, property);
      this.elementType = elementType;
      this.unboxedType = unboxedType;
      this.overridesAddMethod = overridesAddMethod;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("private %s<%s> %s = %s.of();",
            Set.class, elementType, property.getName(), ImmutableSet.class);
      } else {
        code.addLine("private final %1$s<%2$s> %3$s = new %1$s%4$s();",
            LinkedHashSet.class, elementType, property.getName(), SourceLevel.diamondOperator(elementType));
      }
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code) {
      addAdd(code, metadata);
      addVarargsAdd(code, metadata);
      addAddAll(code, metadata);
      addRemove(code, metadata);
      addMutator(code, metadata);
      addClear(code, metadata);
      addGetter(code, metadata);
    }

    private void addAdd(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds {@code element} to the set to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * If the set already contains {@code element}, then {@code %s}",
              BuilderMethods.addMethod(property))
          .addLine(" * has no effect (only the previously added element is retained).")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code element} is null");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s element) {",
              metadata.getBuilder(),
              BuilderMethods.addMethod(property),
              unboxedType.or(elementType));
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("  if (this.%s instanceof %s) {", property.getName(), ImmutableSet.class)
            .addLine("    this.%1$s = new %2$s%3$s(this.%1$s);",
                property.getName(), LinkedHashSet.class, SourceLevel.diamondOperator(elementType))
            .addLine("  }");
      }
      if (unboxedType.isPresent()) {
        code.addLine("  this.%s.add(element);", property.getName());
      } else {
        code.add(PreconditionExcerpts.checkNotNullPreamble("element"))
            .addLine("  this.%s.add(%s);", property.getName(), PreconditionExcerpts.checkNotNullInline("element"));
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addVarargsAdd(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds each element of {@code elements} to the set to be returned from")
          .addLine(" * %s, ignoring duplicate elements",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * (only the first duplicate element is added).")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code elements} is null or contains a")
            .addLine(" *     null element");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s... elements) {",
              metadata.getBuilder(),
              BuilderMethods.addMethod(property),
              unboxedType.or(elementType));
      Optional<Class<?>> arrayUtils = code.feature(GuavaLibrary.GUAVA).arrayUtils(unboxedType.or(elementType));
      if (arrayUtils.isPresent()) {
        code.addLine("  return %s(%s.asList(elements));", BuilderMethods.addAllMethod(property), arrayUtils.get());
      } else {
        // Primitive type, Guava not available
        code.addLine("  for (%s element : elements) {", elementType)
            .addLine("    %s(element);", BuilderMethods.addMethod(property))
            .addLine("  }")
            .addLine("  return (%s) this;", metadata.getBuilder());
      }
      code.addLine("}");
    }

    private void addAddAll(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds each element of {@code elements} to the set to be returned from")
          .addLine(" * %s, ignoring duplicate elements",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * (only the first duplicate element is added).")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code elements} is null or contains a")
          .addLine(" *     null element")
          .addLine(" */");
      addAccessorAnnotations(code);
      code.addLine("public %s %s(%s<? extends %s> elements) {",
              metadata.getBuilder(),
              BuilderMethods.addAllMethod(property),
              Iterable.class,
              elementType)
          .add(Excerpts.forEach(unboxedType.or(elementType), "elements", BuilderMethods.addMethod(property)))
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addRemove(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Removes {@code element} from the set to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Does nothing if {@code element} is not a member of the set.")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code element} is null");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s element) {",
              metadata.getBuilder(),
              BuilderMethods.removeMethod(property),
              unboxedType.or(elementType));
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("  if (this.%s instanceof %s) {", property.getName(), ImmutableSet.class)
            .addLine("    this.%1$s = new %2$s%3$s(this.%1$s);",
                property.getName(), LinkedHashSet.class, SourceLevel.diamondOperator(elementType))
            .addLine("  }");
      }
      if (unboxedType.isPresent()) {
        code.addLine("  this.%s.remove(element);", property.getName());
      } else {
        code.add(PreconditionExcerpts.checkNotNullPreamble("element"))
            .addLine("  this.%s.remove(%s);", property.getName(), PreconditionExcerpts.checkNotNullInline("element"));
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addMutator(SourceBuilder code, Metadata metadata) {
      Optional<ParameterizedType> consumer = code.feature(FunctionPackage.FUNCTION_PACKAGE).consumer();
      if (consumer.isPresent()) {
        code.addLine("")
            .addLine("/**")
            .addLine(" * Applies {@code mutator} to the set to be returned from %s.",
                metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
            .addLine(" *")
            .addLine(" * <p>This method mutates the set in-place. {@code mutator} is a void")
            .addLine(" * consumer, so any value returned from a lambda will be ignored. Take care")
            .addLine(" * not to call pure functions, like %s.",
                COLLECTION.javadocNoArgMethodLink("stream"))
            .addLine(" *")
            .addLine(" * @return this {@code Builder} object")
            .addLine(" * @throws NullPointerException if {@code mutator} is null")
            .addLine(" */")
            .addLine("public %s %s(%s<? super %s<%s>> mutator) {",
                metadata.getBuilder(),
                BuilderMethods.mutator(property),
                consumer.get().getQualifiedName(),
                Set.class,
                elementType);
        if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
          code.addLine("  if (%s instanceof %s) {", property.getName(), ImmutableSet.class)
              .addLine("    %1$s = new %2$s%3$s(%1$s);",
                  property.getName(), LinkedHashSet.class, SourceLevel.diamondOperator(elementType))
              .addLine("  }");
        }
        if (overridesAddMethod) {
          code.addLine("  mutator.accept(new CheckedSet<%s>(%s, this::%s));",
                  elementType, property.getName(), BuilderMethods.addMethod(property));
        } else {
          code.addLine("  // If %s is overridden, this method will be updated to delegate to it",
                  BuilderMethods.addMethod(property))
              .addLine("  mutator.accept(%s);", property.getName());
        }
        code.addLine("  return (%s) this;", metadata.getBuilder())
            .addLine("}");
      }
    }

    private void addClear(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Clears the set to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s() {", metadata.getBuilder(), BuilderMethods.clearMethod(property));
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("if (%s instanceof %s) {", property.getName(), ImmutableSet.class)
            .addLine("  %s = %s.of();", property.getName(), ImmutableSet.class)
            .addLine("} else {");
      }
      code.addLine("%s.clear();", property.getName());
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("}");
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addGetter(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns an unmodifiable view of the set that will be returned by")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Changes to this builder will be reflected in the view.")
          .addLine(" */")
          .addLine("public %s<%s> %s() {", Set.class, elementType, BuilderMethods.getter(property));
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("  if (%s instanceof %s) {", property.getName(), ImmutableSet.class)
            .addLine("    %1$s = new %2$s%3$s(%1$s);",
                property.getName(), LinkedHashSet.class, SourceLevel.diamondOperator(elementType))
            .addLine("  }");
      }
      code.addLine("  return %s.unmodifiableSet(%s);", Collections.class, property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.add("%s = ", finalField);
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.add("%s.copyOf", ImmutableSet.class);
      } else {
        code.add("immutableSet");
      }
      code.add("(%s.%s);\n", builder, property.getName());
    }

    @Override
    public void addMergeFromValue(Block code, String value) {
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("if (%s instanceof %s && %s == %s.<%s>of()) {",
                value, metadata.getValueType(), property.getName(), ImmutableSet.class, elementType)
            .addLine("  %s = %s.%s();", property.getName(), value, property.getGetterName())
            .addLine("} else {");
      }
      code.addLine("%s(%s.%s());", BuilderMethods.addAllMethod(property), value, property.getGetterName());
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("}");
      }
    }

    @Override
    public void addMergeFromBuilder(Block code, String builder) {
      code.addLine("%s(((%s) %s).%s);",
          BuilderMethods.addAllMethod(property),
          metadata.getGeneratedABuilder(),
          builder,
          property.getName());
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s(%s);", builder, BuilderMethods.addAllMethod(property), variable);
    }

    @Override
    public void addClearField(Block code) {
      code.addLine("%s();", BuilderMethods.clearMethod(property));
    }

    @Override
    public Set<StaticExcerpt> getStaticExcerpts() {
      ImmutableSet.Builder<StaticExcerpt> staticMethods = ImmutableSet.builder();
      staticMethods.add(IMMUTABLE_SET);
      if (overridesAddMethod) {
        staticMethods.addAll(CheckedSet.excerpts());
      }
      return staticMethods.build();
    }
  }

  private static final StaticExcerpt IMMUTABLE_SET = new StaticExcerpt(METHOD, "immutableSet") {
    @Override
    public void addTo(SourceBuilder code) {
      if (!code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("")
            .addLine("private static <E> %1$s<E> immutableSet(%1$s<E> elements) {",
                Set.class, Class.class)
            .addLine("  switch (elements.size()) {")
            .addLine("  case 0:")
            .addLine("    return %s.emptySet();", Collections.class)
            .addLine("  case 1:")
            .addLine("    return %s.singleton(elements.iterator().next());", Collections.class)
            .addLine("  default:")
            .addLine("    return %s.unmodifiableSet(new %s%s(elements));",
                Collections.class, LinkedHashSet.class, SourceLevel.diamondOperator("E"))
            .addLine("  }")
            .addLine("}");
      }
    }
  };
}
