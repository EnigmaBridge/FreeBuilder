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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.enigmabridge.ebuilder.processor.BuilderMethods.getter;
import static com.enigmabridge.ebuilder.processor.BuilderMethods.mapper;
import static com.enigmabridge.ebuilder.processor.BuilderMethods.setter;

import com.enigmabridge.ebuilder.processor.util.Block;
import com.enigmabridge.ebuilder.processor.util.Excerpt;
import com.enigmabridge.ebuilder.processor.util.PreconditionExcerpts;
import com.enigmabridge.ebuilder.processor.util.SourceBuilder;
import com.enigmabridge.ebuilder.processor.util.feature.FunctionPackage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import com.enigmabridge.ebuilder.processor.PropertyCodeGenerator.Config;
import com.enigmabridge.ebuilder.processor.util.ParameterizedType;

import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** {@link PropertyCodeGenerator.Factory} providing reference semantics for Nullable properties. */
public class NullablePropertyFactory implements PropertyCodeGenerator.Factory {

  @Override
  public Optional<CodeGenerator> create(Config config) {
    Metadata.Property property = config.getProperty();
    boolean isPrimitive = property.getType().getKind().isPrimitive();
    Set<TypeElement> nullableAnnotations = nullablesIn(config.getAnnotations());
    if (isPrimitive || nullableAnnotations.isEmpty()) {
      return Optional.absent();
    }
    return Optional.of(new CodeGenerator(config.getMetadata(), property, nullableAnnotations));
  }

  private static Set<TypeElement> nullablesIn(Iterable<? extends AnnotationMirror> annotations) {
    ImmutableSet.Builder<TypeElement> nullableAnnotations = ImmutableSet.builder();
    for (AnnotationMirror mirror : annotations) {
      if (mirror.getElementValues().isEmpty()) {
        TypeElement type = (TypeElement) mirror.getAnnotationType().asElement();
        if (type.getSimpleName().contentEquals("Nullable")) {
          nullableAnnotations.add(type);
        }
      }
    }
    return nullableAnnotations.build();
  }

  @VisibleForTesting static class CodeGenerator extends PropertyCodeGenerator {

    private final Set<TypeElement> nullables;

    CodeGenerator(Metadata metadata, Metadata.Property property, Iterable<TypeElement> nullableAnnotations) {
      super(metadata, property);
      this.nullables = ImmutableSet.copyOf(nullableAnnotations);
    }

    @Override
    public Type getType() {
      return Type.OPTIONAL;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      addGetterAnnotations(code);
      code.add("%s %s = null;\n", property.getType(), property.getName());
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code) {
      addSetter(code, metadata);
      addMapper(code, metadata);
      addGetter(code, metadata);
    }

    private void addSetter(SourceBuilder code, final Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */");
      addAccessorAnnotations(code);
      code.add("public %s %s(", metadata.getBuildGen(), setter(property));
      addGetterAnnotations(code);
      code.add("%s %s) {\n", property.getType(), property.getName())
          .addLine("  this.%1$s = %1$s;", property.getName())
          .addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addMapper(SourceBuilder code, final Metadata metadata) {
      ParameterizedType unaryOperator = code.feature(FunctionPackage.FUNCTION_PACKAGE).unaryOperator().orNull();
      if (unaryOperator == null) {
        return;
      }
      TypeMirror typeParam = firstNonNull(property.getBoxedType(), property.getType());
      code.addLine("")
          .addLine("/**")
          .addLine(" * If the value to be returned by %s is not",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * null, replaces it by applying {@code mapper} to it and using the result.")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code mapper} is null")
          .addLine(" */")
          .addLine("public %s %s(%s mapper) {",
              metadata.getBuildGen(),
              mapper(property),
              unaryOperator.withParameters(typeParam))
          .add(PreconditionExcerpts.checkNotNull("mapper"))
          .addLine("  %s %s = %s();",
              property.getType(), property.getName(), getter(property))
          .addLine("  if (%s != null) {", property.getName())
          .addLine("    %s(mapper.apply(%s));", setter(property), property.getName())
          .addLine("  }")
          .addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addGetter(SourceBuilder code, final Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns the value that will be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" */");
      addGetterAnnotations(code);
      code.addLine("public %s %s() {", property.getType(), getter(property))
          .addLine("  return %s;", property.getName())
          .addLine("}");
    }

    @Override
    public void addValueFieldDeclaration(SourceBuilder code, String finalField) {
      addGetterAnnotations(code);
      code.add("private final %s %s;\n", property.getType(), finalField);
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.addLine("%s = %s.%s;", finalField, builder, property.getName());
    }

    @Override
    public void addMergeFromValue(Block code, String value) {
      code.addLine("%s(%s.%s());", setter(property), value, property.getGetterName());
    }

    @Override
    public void addMergeFromSuperValue(Block code, String value) {
      addMergeFromValue(code, value);
    }

    @Override
    public void addMergeFromBuilder(Block code, String builder) {
      code.addLine("%s(%s.%s());", setter(property), builder, getter(property));
    }

    @Override
    public void addMergeFromSuperBuilder(Block code, String builder) {
      addMergeFromBuilder(code, builder);
    }

    @Override
    public void addGetterAnnotations(SourceBuilder code) {
      for (TypeElement nullableAnnotation : nullables) {
        code.add("@%s ", nullableAnnotation);
      }
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s(%s);", builder, setter(property), variable);
    }

    @Override
    public void addClearField(Block code) {
      Optional<Excerpt> defaults = Declarations.freshBuilder(code, metadata);
      if (defaults.isPresent()) {
        code.addLine("%1$s = %2$s.%1$s;", property.getName(), defaults.get());
      } else {
        code.addLine("%s = null;", property.getName());
      }
    }
  }
}
