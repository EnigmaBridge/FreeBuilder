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

import com.enigmabridge.ebuilder.processor.excerpt.CheckedMap;
import com.enigmabridge.ebuilder.processor.util.*;
import com.enigmabridge.ebuilder.processor.util.feature.FunctionPackage;
import com.enigmabridge.ebuilder.processor.util.feature.GuavaLibrary;
import com.enigmabridge.ebuilder.processor.util.feature.SourceLevel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.enigmabridge.ebuilder.processor.util.Block;
import com.enigmabridge.ebuilder.processor.util.Excerpts;
import com.enigmabridge.ebuilder.processor.util.ParameterizedType;
import com.enigmabridge.ebuilder.processor.util.PreconditionExcerpts;
import com.enigmabridge.ebuilder.processor.util.QualifiedName;
import com.enigmabridge.ebuilder.processor.util.SourceBuilder;
import com.enigmabridge.ebuilder.processor.util.StaticExcerpt;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator.Factory} providing append-only semantics for {@link Map}
 * properties.
 */
public class MapPropertyFactory implements PropertyCodeGenerator.Factory {

  @Override
  public Optional<? extends PropertyCodeGenerator> create(PropertyCodeGenerator.Config config) {
    DeclaredType type = ModelUtils.maybeDeclared(config.getProperty().getType()).orNull();
    if (type == null || !Util.erasesToAnyOf(type, Map.class, ImmutableMap.class)) {
      return Optional.absent();
    }
    TypeMirror keyType = Util.upperBound(config.getElements(), type.getTypeArguments().get(0));
    TypeMirror valueType = Util.upperBound(config.getElements(), type.getTypeArguments().get(1));
    Optional<TypeMirror> unboxedKeyType = ModelUtils.maybeUnbox(keyType, config.getTypes());
    Optional<TypeMirror> unboxedValueType = ModelUtils.maybeUnbox(valueType, config.getTypes());
    boolean overridesPutMethod = hasPutMethodOverride(
        config, unboxedKeyType.or(keyType), unboxedValueType.or(valueType));
    return Optional.of(new CodeGenerator(
        config.getMetadata(),
        config.getProperty(),
        overridesPutMethod,
        keyType,
        unboxedKeyType,
        valueType,
        unboxedValueType));
  }

  private static boolean hasPutMethodOverride(
          PropertyCodeGenerator.Config config, TypeMirror keyType, TypeMirror valueType) {
    return ModelUtils.overrides(
        config.getBuilder(),
        config.getTypes(),
        BuilderMethods.putMethod(config.getProperty()),
        keyType,
        valueType);
  }

  @VisibleForTesting
  static class CodeGenerator extends PropertyCodeGenerator {

    private static final ParameterizedType COLLECTION =
        QualifiedName.of(Collection.class).withParameters("E");

    private final boolean overridesPutMethod;
    private final TypeMirror keyType;
    private final Optional<TypeMirror> unboxedKeyType;
    private final TypeMirror valueType;
    private final Optional<TypeMirror> unboxedValueType;

    CodeGenerator(
        Metadata metadata,
        Metadata.Property property,
        boolean overridesPutMethod,
        TypeMirror keyType,
        Optional<TypeMirror> unboxedKeyType,
        TypeMirror valueType,
        Optional<TypeMirror> unboxedValueType) {
      super(metadata, property);
      this.overridesPutMethod = overridesPutMethod;
      this.keyType = keyType;
      this.unboxedKeyType = unboxedKeyType;
      this.valueType = valueType;
      this.unboxedValueType = unboxedValueType;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.addLine("final %1$s<%2$s, %3$s> %4$s = new %1$s%5$s();",
          LinkedHashMap.class,
          keyType,
          valueType,
          property.getName(),
          SourceLevel.diamondOperator(Excerpts.add("%s, %s", keyType, valueType)));
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code) {
      addPut(code, metadata);
      addPutAll(code, metadata);
      addRemove(code, metadata);
      addMutate(code, metadata);
      addClear(code, metadata);
      addGetter(code, metadata);
    }

    private void addPut(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Associates {@code key} with {@code value} in the map to be returned from")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * If the map previously contained a mapping for the key,")
          .addLine(" * the old value is replaced by the specified value.")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedKeyType.isPresent() || !unboxedValueType.isPresent()) {
        code.add(" * @throws NullPointerException if ");
        if (unboxedKeyType.isPresent()) {
          code.add("{@code value} is");
        } else if (unboxedValueType.isPresent()) {
          code.add("{@code key} is");
        } else {
          code.add("either {@code key} or {@code value} are");
        }
        code.add(" null\n");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s key, %s value) {",
              metadata.getBuildGen(),
              BuilderMethods.putMethod(property),
              unboxedKeyType.or(keyType),
              unboxedValueType.or(valueType));
      if (!unboxedKeyType.isPresent()) {
        code.add(PreconditionExcerpts.checkNotNull("key"));
      }
      if (!unboxedValueType.isPresent()) {
        code.add(PreconditionExcerpts.checkNotNull("value"));
      }
      code.addLine("  %s.put(key, value);", property.getName())
          .addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addPutAll(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Copies all of the mappings from {@code map} to the map to be returned from")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code map} is null or contains a")
          .addLine(" *     null key or value")
          .addLine(" */");
      addAccessorAnnotations(code);
      code.addLine("public %s %s(%s<? extends %s, ? extends %s> map) {",
              metadata.getBuildGen(),
              BuilderMethods.putAllMethod(property),
              Map.class,
              keyType,
              valueType)
          .addLine("  for (%s<? extends %s, ? extends %s> entry : map.entrySet()) {",
              Map.Entry.class, keyType, valueType)
          .addLine("    %s(entry.getKey(), entry.getValue());", BuilderMethods.putMethod(property))
          .addLine("  }")
          .addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addRemove(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Removes the mapping for {@code key} from the map to be returned from")
          .addLine(" * %s, if one is present.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedKeyType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code key} is null");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s key) {",
              metadata.getBuildGen(),
              BuilderMethods.removeMethod(property),
              unboxedKeyType.or(keyType));
      if (!unboxedKeyType.isPresent()) {
        code.add(PreconditionExcerpts.checkNotNull("key"));
      }
      code.addLine("  %s.remove(key);", property.getName())
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
          .addLine(" * Invokes {@code mutator} with the map to be returned from")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * <p>This method mutates the map in-place. {@code mutator} is a void")
          .addLine(" * consumer, so any value returned from a lambda will be ignored. Take care")
          .addLine(" * not to call pure functions, like %s.",
              COLLECTION.javadocNoArgMethodLink("stream"))
          .addLine(" *")
          .addLine(" * @return this {@code Builder} object")
          .addLine(" * @throws NullPointerException if {@code mutator} is null")
          .addLine(" */")
          .addLine("public %s %s(%s<? super %s<%s, %s>> mutator) {",
              metadata.getBuildGen(),
              BuilderMethods.mutator(property),
              consumer.getQualifiedName(),
              Map.class,
              keyType,
              valueType);
      if (overridesPutMethod) {
        code.addLine("  mutator.accept(new CheckedMap<>(%s, this::%s));",
            property.getName(), BuilderMethods.putMethod(property));
      } else {
        code.addLine("  // If %s is overridden, this method will be updated to delegate to it",
                BuilderMethods.putMethod(property))
            .addLine("  mutator.accept(%s);", property.getName());
      }
      code.addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addClear(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Removes all of the mappings from the map to be returned from ")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s() {", metadata.getBuildGen(), BuilderMethods.clearMethod(property))
          .addLine("  %s.clear();", property.getName())
          .addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addGetter(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns an unmodifiable view of the map that will be returned by")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Changes to this builder will be reflected in the view.")
          .addLine(" */")
          .addLine("public %s<%s, %s> %s() {", Map.class, keyType, valueType, BuilderMethods.getter(property))
          .addLine("  return %s.unmodifiableMap(%s);", Collections.class, property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.add("%s = ", finalField);
      if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.add("%s.copyOf", ImmutableMap.class);
      } else {
        code.add("immutableMap");
      }
      code.add("(%s.%s);\n", builder, property.getName());
    }

    @Override
    public void addMergeFromValue(Block code, String value) {
      code.addLine("%s(%s.%s());", BuilderMethods.putAllMethod(property), value, property.getGetterName());
    }

    @Override
    public void addMergeFromBuilder(Block code, String builder) {
      code.addLine("%s(((%s) %s).%s);",
          BuilderMethods.putAllMethod(property),
          metadata.getGeneratedABuilder(),
          builder,
          property.getName());
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s(%s);", builder, BuilderMethods.putAllMethod(property), variable);
    }

    @Override
    public void addClearField(Block code) {
      code.addLine("%s.clear();", property.getName());
    }

    @Override
    public Set<StaticExcerpt> getStaticExcerpts() {
      ImmutableSet.Builder<StaticExcerpt> result = ImmutableSet.builder();
      result.add(IMMUTABLE_MAP);
      if (overridesPutMethod) {
        result.addAll(CheckedMap.excerpts());
      }
      return result.build();
    }
  }

  private static final StaticExcerpt IMMUTABLE_MAP = new StaticExcerpt(StaticExcerpt.Type.METHOD, "immutableMap") {
    @Override
    public void addTo(SourceBuilder code) {
      if (!code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        code.addLine("")
            .addLine("private static <K, V> %1$s<K, V> immutableMap(%1$s<K, V> entries) {",
                Map.class)
            .addLine("  switch (entries.size()) {")
            .addLine("  case 0:")
            .addLine("    return %s.emptyMap();", Collections.class)
            .addLine("  case 1:")
            .addLine("    %s<K, V> entry = entries.entrySet().iterator().next();", Map.Entry.class)
            .addLine("    return %s.singletonMap(entry.getKey(), entry.getValue());",
                Collections.class)
            .addLine("  default:")
            .addLine("    return %s.unmodifiableMap(new %s%s(entries));",
                Collections.class, LinkedHashMap.class, SourceLevel.diamondOperator("K, V"))
            .addLine("  }")
            .addLine("}");
      }
    }
  };
}
