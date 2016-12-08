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

import static com.enigmabridge.ebuilder.processor.BuilderMethods.getter;
import static com.enigmabridge.ebuilder.processor.BuilderMethods.putAllMethod;
import static com.enigmabridge.ebuilder.processor.Util.erasesToAnyOf;
import static com.enigmabridge.ebuilder.processor.Util.upperBound;

import com.enigmabridge.ebuilder.processor.excerpt.CheckedSetMultimap;
import com.enigmabridge.ebuilder.processor.util.Block;
import com.enigmabridge.ebuilder.processor.util.ModelUtils;
import com.enigmabridge.ebuilder.processor.util.SourceBuilder;
import com.enigmabridge.ebuilder.processor.util.feature.FunctionPackage;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

import com.enigmabridge.ebuilder.processor.PropertyCodeGenerator.Config;
import com.enigmabridge.ebuilder.processor.util.ParameterizedType;
import com.enigmabridge.ebuilder.processor.util.StaticExcerpt;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator.Factory} providing append-only semantics for {@link SetMultimap}
 * properties.
 */
public class SetMultimapPropertyFactory implements PropertyCodeGenerator.Factory {

  @Override
  public Optional<CodeGenerator> create(Config config) {
    DeclaredType type = ModelUtils.maybeDeclared(config.getProperty().getType()).orNull();
    if (type == null || !erasesToAnyOf(type, SetMultimap.class, ImmutableSetMultimap.class)) {
      return Optional.absent();
    }

    TypeMirror keyType = upperBound(config.getElements(), type.getTypeArguments().get(0));
    TypeMirror valueType = upperBound(config.getElements(), type.getTypeArguments().get(1));
    Optional<TypeMirror> unboxedKeyType = ModelUtils.maybeUnbox(keyType, config.getTypes());
    Optional<TypeMirror> unboxedValueType = ModelUtils.maybeUnbox(valueType, config.getTypes());
    boolean overridesPutMethod =
        hasPutMethodOverride(config, unboxedKeyType.or(keyType), unboxedValueType.or(valueType));
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
      Config config, TypeMirror keyType, TypeMirror valueType) {
    return ModelUtils.overrides(
        config.getBuilder(),
        config.getTypes(),
        BuilderMethods.putMethod(config.getProperty()),
        keyType,
        valueType);
  }

  private static class CodeGenerator extends PropertyCodeGenerator {

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
        TypeMirror valueType, Optional<TypeMirror> unboxedValueType) {
      super(metadata, property);
      this.overridesPutMethod = overridesPutMethod;
      this.keyType = keyType;
      this.unboxedKeyType = unboxedKeyType;
      this.valueType = valueType;
      this.unboxedValueType = unboxedValueType;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.addLine("final %1$s<%2$s, %3$s> %4$s = %1$s.create();",
          LinkedHashMultimap.class, keyType, valueType, property.getName());
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code) {
      addPut(code, metadata);
      addSingleKeyPutAll(code, metadata);
      addMultimapPutAll(code, metadata);
      addRemove(code, metadata);
      addRemoveAll(code, metadata);
      addMutate(code, metadata);
      addClear(code, metadata);
      addGetter(code, metadata);
    }

    private void addPut(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds a {@code key}-{@code value} mapping to the multimap to be returned")
          .addLine(" * from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * If the multimap already contains this mapping, then {@code %s}",
              BuilderMethods.putMethod(property))
          .addLine(" * has no effect (only the previously added mapping is retained).")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedKeyType.isPresent() || !unboxedValueType.isPresent()) {
        code.add(" * @throws NullPointerException if ");
        if (unboxedKeyType.isPresent()) {
          code.add("{@code value}");
        } else if (unboxedValueType.isPresent()) {
          code.add("{@code key}");
        } else {
          code.add("either {@code key} or {@code value}");
        }
        code.add(" is null\n");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s key, %s value) {",
              metadata.getBuildGen(),
              BuilderMethods.putMethod(property),
              unboxedKeyType.or(keyType),
              unboxedValueType.or(valueType));
      if (!unboxedKeyType.isPresent()) {
        code.addLine("  %s.checkNotNull(key);", Preconditions.class);
      }
      if (!unboxedValueType.isPresent()) {
        code.addLine("  %s.checkNotNull(value);", Preconditions.class);
      }
      code.addLine("  this.%s.put(key, value);", property.getName())
          .addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addSingleKeyPutAll(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds a collection of {@code values} with the same {@code key} to the")
          .addLine(" * multimap to be returned from %s, ignoring duplicate values",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * (only the first duplicate value is added).")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (unboxedKeyType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code values} is null or contains a"
            + " null element");
      } else {
        code.addLine(" * @throws NullPointerException if either {@code key} or {@code values} is")
            .addLine(" *     null, or if {@code values} contains a null element");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s key, %s<? extends %s> values) {",
              metadata.getBuildGen(),
              putAllMethod(property),
              unboxedKeyType.or(keyType),
              Iterable.class,
              valueType)
          .addLine("  for (%s value : values) {", unboxedValueType.or(valueType))
          .addLine("    %s(key, value);", BuilderMethods.putMethod(property))
          .addLine("  }")
          .addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addMultimapPutAll(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Adds each mapping in {@code multimap} to the multimap to be returned from")
          .addLine(" * %s, ignoring duplicate mappings",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * (only the first duplicate mapping is added).")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code multimap} is null or contains a")
          .addLine(" *     null key or value")
          .addLine(" */");
      addAccessorAnnotations(code);
      code.addLine("public %s %s(%s<? extends %s, ? extends %s> multimap) {",
              metadata.getBuildGen(),
              putAllMethod(property),
              Multimap.class,
              keyType,
              valueType)
          .addLine("  for (%s<? extends %s, ? extends %s<? extends %s>> entry",
              Entry.class, keyType, Collection.class, valueType)
          .addLine("      : multimap.asMap().entrySet()) {")
          .addLine("    %s(entry.getKey(), entry.getValue());",
              putAllMethod(property), property.getCapitalizedName())
          .addLine("  }")
          .addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addRemove(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Removes a single key-value pair with the key {@code key} and the value"
              + " {@code value}")
          .addLine(" * from the multimap to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedKeyType.isPresent() || !unboxedValueType.isPresent()) {
        code.add(" * @throws NullPointerException if ");
        if (unboxedKeyType.isPresent()) {
          code.add("{@code value}");
        } else if (unboxedValueType.isPresent()) {
          code.add("{@code key}");
        } else {
          code.add("either {@code key} or {@code value}");
        }
        code.add(" is null\n");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s key, %s value) {",
              metadata.getBuildGen(),
              BuilderMethods.removeMethod(property),
              unboxedKeyType.or(keyType),
              unboxedValueType.or(valueType));
      if (!unboxedKeyType.isPresent()) {
        code.addLine("  %s.checkNotNull(key);", Preconditions.class);
      }
      if (!unboxedValueType.isPresent()) {
        code.addLine("  %s.checkNotNull(value);", Preconditions.class);
      }
      code.addLine("  this.%s.remove(key, value);", property.getName())
          .addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addRemoveAll(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Removes all values associated with the key {@code key} from the multimap to")
          .addLine(" * be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedKeyType.isPresent()) {
        code.add(" * @throws NullPointerException if {@code key} is null\n");
      }
      code.addLine(" */")
          .addLine("public %s %s(%s key) {",
              metadata.getBuildGen(),
              BuilderMethods.removeAllMethod(property),
              unboxedKeyType.or(keyType));
      if (!unboxedKeyType.isPresent()) {
        code.addLine("  %s.checkNotNull(key);", Preconditions.class);
      }
      code.addLine("  this.%s.removeAll(key);", property.getName())
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
          .addLine(" * Applies {@code mutator} to the multimap to be returned from %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * <p>This method mutates the multimap in-place. {@code mutator} is a void")
          .addLine(" * consumer, so any value returned from a lambda will be ignored.")
          .addLine(" *")
          .addLine(" * @return this {@code Builder} object")
          .addLine(" * @throws NullPointerException if {@code mutator} is null")
          .addLine(" */")
          .addLine("public %s %s(%s<%s<%s, %s>> mutator) {",
              metadata.getBuildGen(),
              BuilderMethods.mutator(property),
              consumer.getQualifiedName(),
              SetMultimap.class,
              keyType,
              valueType);
      if (overridesPutMethod) {
        code.addLine("  mutator.accept(new CheckedSetMultimap<>(%s, this::%s));",
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
          .addLine(" * Removes all of the mappings from the multimap to be returned from")
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
          .addLine(" * Returns an unmodifiable view of the multimap that will be returned by")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Changes to this builder will be reflected in the view.")
          .addLine(" */")
          .addLine("public %s<%s, %s> %s() {",
              SetMultimap.class,
              keyType,
              valueType,
              getter(property))
          .addLine("  return %s.unmodifiableSetMultimap(%s);",
              Multimaps.class, property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.addLine("%s = %s.copyOf(%s.%s);",
              finalField, ImmutableSetMultimap.class, builder, property.getName());
    }

    @Override
    public void addMergeFromValue(Block code, String value) {
      code.addLine("%s(%s.%s());", putAllMethod(property), value, property.getGetterName());
    }

    @Override
    public void addMergeFromSuperValue(Block code, String value) {
      addMergeFromValue(code, value);
    }

    @Override
    public void addMergeFromBuilder(Block code, String builder) {
      code.addLine("%s(((%s) %s).%s);",
          putAllMethod(property),
          metadata.getGeneratedABuilder(),
          builder,
          property.getName());
    }

    @Override
    public void addMergeFromSuperBuilder(Block code, String builder) {
      code.addLine("%s(%s.%s());",
          putAllMethod(property),
          builder,
          getter(property));
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s(%s);", builder, putAllMethod(property), variable);
    }

    @Override
    public void addClearField(Block code) {
      code.addLine("%s.clear();", property.getName());
    }

    @Override
    public Set<StaticExcerpt> getStaticExcerpts() {
      ImmutableSet.Builder<StaticExcerpt> staticMethods = ImmutableSet.builder();
      if (overridesPutMethod) {
        staticMethods.addAll(CheckedSetMultimap.excerpts());
      }
      return staticMethods.build();
    }
  }
}
