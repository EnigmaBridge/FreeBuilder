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

import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.tryFind;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.util.ElementFilter.typesIn;
import static com.enigmabridge.ebuilder.processor.BuilderFactory.TypeInference.INFERRED_TYPES;
import static com.enigmabridge.ebuilder.processor.BuilderMethods.getBuilderMethod;
import static com.enigmabridge.ebuilder.processor.BuilderMethods.mutator;
import static com.enigmabridge.ebuilder.processor.BuilderMethods.setter;
import static com.enigmabridge.ebuilder.processor.util.ModelUtils.asElement;
import static com.enigmabridge.ebuilder.processor.util.ModelUtils.findAnnotationMirror;
import static com.enigmabridge.ebuilder.processor.util.ModelUtils.maybeDeclared;

import com.enigmabridge.ebuilder.processor.util.Block;
import com.enigmabridge.ebuilder.processor.util.feature.FunctionPackage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;

import com.enigmabridge.ebuilder.processor.Metadata.Property;
import com.enigmabridge.ebuilder.processor.PropertyCodeGenerator.Config;
import com.enigmabridge.ebuilder.processor.util.ParameterizedType;
import com.enigmabridge.ebuilder.processor.util.SourceBuilder;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * {@link PropertyCodeGenerator.Factory} for <b>buildable</b> types: that is, types with a Builder
 * class providing a similar API to proto or &#64;EBuilder:<ul>
 * <li> a public constructor, or static builder()/newBuilder() method;
 * <li> build(), buildPartial() and clear() methods; and
 * <li> a mergeWith(Value) method.
 * </ul>
 */
public class BuildablePropertyFactory implements PropertyCodeGenerator.Factory {

  /** How to merge the values from one Builder into another. */
  private enum MergeBuilderMethod {
    MERGE_DIRECTLY, BUILD_PARTIAL_AND_MERGE
  }

  @Override
  public Optional<? extends PropertyCodeGenerator> create(Config config) {
    DeclaredType type = maybeDeclared(config.getProperty().getType()).orNull();
    if (type == null) {
      return Optional.absent();
    }
    TypeElement element = asElement(type);

    // Find the builder
    Optional<TypeElement> builder =
        tryFind(typesIn(element.getEnclosedElements()), IS_BUILDER_TYPE);
    if (!builder.isPresent()) {
      return Optional.absent();
    }

    // Verify the builder can be constructed
    Optional<BuilderFactory> builderFactory = BuilderFactory.from(builder.get());
    if (!builderFactory.isPresent()) {
      return Optional.absent();
    }

    MergeBuilderMethod mergeFromBuilderMethod;
    if (findAnnotationMirror(element, "EBuilder").isPresent()) {
      /*
       * If the element is annotated @EBuilder, assume the necessary methods will be added. We
       * can't check directly as the builder superclass may not have been generated yet. To be
       * strictly correct, we should delay a round if an error type leaves us unsure about this kind
       * of API-changing decision, and then we would work with _any_ Builder-generating API. We
       * would need to drop out part of our own builder superclass, to prevent chains of dependent
       * buildable types leading to quadratic compilation times (not to mention cycles), and leave a
       * dangling super-superclass to pick up next round. As an optimization, though, we would
       * probably skip this for @EBuilder-types anyway, to avoid extra types whenever possible,
       * which leaves a lot of complicated code supporting a currently non-existent edge case.
       */
      mergeFromBuilderMethod = MergeBuilderMethod.MERGE_DIRECTLY;
    } else {
      List<ExecutableElement> methods = FluentIterable
          .from(config.getElements().getAllMembers(builder.get()))
          .filter(ExecutableElement.class)
          .filter(new IsCallableMethod())
          .toList();

      // Check there is a build() method
      if (!any(methods, new IsBuildMethod("build", type, config.getTypes()))) {
        return Optional.absent();
      }

      // Check there is a buildPartial() method
      if (!any(methods, new IsBuildMethod("buildPartial", type, config.getTypes()))) {
        return Optional.absent();
      }

      // Check there is a clear() method
      if (!any(methods, new IsClearMethod())) {
        return Optional.absent();
      }

      // Check there is a mergeFrom(Value) method
      if (!any(methods, new IsMergeFromMethod(type, config.getTypes()))) {
        return Optional.absent();
      }

      // Check whether there is a mergeFrom(Builder) method
      if (any(methods, new IsMergeFromMethod(builder.get().asType(), config.getTypes()))) {
        mergeFromBuilderMethod = MergeBuilderMethod.MERGE_DIRECTLY;
      } else {
        mergeFromBuilderMethod = MergeBuilderMethod.BUILD_PARTIAL_AND_MERGE;
      }
    }

    return Optional.of(new CodeGenerator(
        config.getMetadata(),
        config.getProperty(),
        ParameterizedType.from(builder.get()),
        builderFactory.get(),
        mergeFromBuilderMethod));
  }

  @VisibleForTesting static class CodeGenerator extends PropertyCodeGenerator {

    final ParameterizedType builderType;
    final BuilderFactory builderFactory;
    final MergeBuilderMethod mergeFromBuilderMethod;

    CodeGenerator(
        Metadata metadata,
        Property property,
        ParameterizedType builderType,
        BuilderFactory builderFactory,
        MergeBuilderMethod mergeFromBuilderMethod) {
      super(metadata, property);
      this.builderType = builderType;
      this.builderFactory = builderFactory;
      this.mergeFromBuilderMethod = mergeFromBuilderMethod;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.addLine("private final %s %s = %s;",
          builderType, property.getName(), builderFactory.newBuilder(builderType, INFERRED_TYPES));
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code) {
      addSetter(code, metadata);
      addSetterTakingBuilder(code, metadata);
      addMutate(code, metadata);
      addGetter(code, metadata);
    }

    private void addSetter(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code %s} is null", property.getName())
          .addLine(" */");
      addAccessorAnnotations(code);
      code.addLine("public %s %s(%s %s) {",
              metadata.getBuildGen(),
              setter(property),
              property.getType(),
              property.getName())
          .addLine("  this.%s.clear();", property.getName())
          .addLine("  this.%1$s.mergeFrom(%2$s.checkNotNull(%1$s));",
              property.getName(), Preconditions.class)
          .addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addSetterTakingBuilder(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets the value to be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code builder} is null")
          .addLine(" */")
          .addLine("public %s %s(%s builder) {",
              metadata.getBuilder(),
              setter(property),
              builderType)
          .addLine("  return %s(builder.build());", setter(property))
          .addLine("}");
    }

    private void addMutate(SourceBuilder code, Metadata metadata) {
      ParameterizedType consumer = code.feature(FunctionPackage.FUNCTION_PACKAGE).consumer().orNull();
      if (consumer == null) {
        return;
      }
      code.addLine("")
          .addLine("/**")
          .addLine(" * Applies {@code mutator} to the builder for the value that will be")
          .addLine(" * returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * <p>This method mutates the builder in-place. {@code mutator} is a void")
          .addLine(" * consumer, so any value returned from a lambda will be ignored.")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code mutator} is null")
          .addLine(" */")
          .addLine("public %s %s(%s<%s> mutator) {",
              metadata.getBuildGen(),
              mutator(property),
              consumer.getQualifiedName(),
              builderType)
          .addLine("  mutator.accept(%s);", property.getName())
          .addLine("  return getThisBuilder();")
          //.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }

    private void addGetter(SourceBuilder code, Metadata metadata) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns a builder for the value that will be returned by %s.",
              metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" */")
          .addLine("public %s %s() {", builderType, getBuilderMethod(property))
          .addLine("  return %s;", property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.addLine("%s = %s.%s.build();", finalField, builder, property.getName());
    }

    @Override
    public void addPartialFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.addLine("%s = %s.%s.buildPartial();", finalField, builder, property.getName());
    }

    @Override
    public void addMergeFromValue(Block code, String value) {
      String propertyName = property.getName();
      if (propertyName.equals(value)) {
        propertyName = "this." + propertyName;  // see issue #78
      }
      code.addLine("%s.mergeFrom(%s.%s());", propertyName, value, property.getGetterName());
    }

    @Override
    public void addMergeFromSuperValue(Block code, String value) {
      addMergeFromValue(code, value);
    }

    @Override
    public void addMergeFromBuilder(Block code, String builder) {
      String propertyName = property.getName();
      if (propertyName.equals(builder)) {
        propertyName = "this." + propertyName;  // see issue #78
      }
      code.add("%s.mergeFrom(%s.%s()", propertyName, builder, getBuilderMethod(property));
      if (mergeFromBuilderMethod == MergeBuilderMethod.BUILD_PARTIAL_AND_MERGE) {
        code.add(".buildPartial()");
      }
      code.add(");\n");
    }

    @Override
    public void addMergeFromSuperBuilder(Block code, String builder) {
      addMergeFromBuilder(code, builder);
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s(%s);", builder, setter(property), variable);
    }

    @Override
    public void addClearField(Block code) {
      code.addLine("%s.clear();", property.getName());
    }
  }

  private static final class IsCallableMethod implements Predicate<ExecutableElement> {
    @Override
    public boolean apply(ExecutableElement element) {
      boolean isMethod = (element.getKind() == ElementKind.METHOD);
      boolean isPublic = element.getModifiers().contains(Modifier.PUBLIC);
      boolean isNotStatic = !element.getModifiers().contains(Modifier.STATIC);
      boolean declaresNoExceptions = element.getThrownTypes().isEmpty();
      return isMethod && isPublic && isNotStatic && declaresNoExceptions;
    }
  }

  private static final class IsBuildMethod implements Predicate<ExecutableElement> {
    final String methodName;
    final TypeMirror builtType;
    final Types types;

    IsBuildMethod(String methodName, TypeMirror builtType, Types types) {
      this.methodName = methodName;
      this.builtType = builtType;
      this.types = types;
    }

    @Override public boolean apply(ExecutableElement element) {
      if (!element.getParameters().isEmpty()) {
        return false;
      }
      if (!element.getSimpleName().contentEquals(methodName)) {
        return false;
      }
      if (!types.isSubtype(element.getReturnType(), builtType)) {
        return false;
      }
      return true;
    }
  }

  private static final class IsClearMethod implements Predicate<ExecutableElement> {
    @Override public boolean apply(ExecutableElement element) {
      if (!element.getParameters().isEmpty()) {
        return false;
      }
      if (!element.getSimpleName().contentEquals("clear")) {
        return false;
      }
      return true;
    }
  }

  private static final class IsMergeFromMethod implements Predicate<ExecutableElement> {
    final TypeMirror builderType;
    final Types types;

    IsMergeFromMethod(TypeMirror sourceType, Types types) {
      this.builderType = sourceType;
      this.types = types;
    }

    @Override public boolean apply(ExecutableElement element) {
      if (element.getParameters().size() != 1) {
        return false;
      }
      if (!element.getSimpleName().contentEquals("mergeFrom")) {
        return false;
      }
      if (!types.isSubtype(builderType, element.getParameters().get(0).asType())) {
        return false;
      }
      return true;
    }
  }

  private static final Predicate<Element> IS_BUILDER_TYPE = new Predicate<Element>() {
    @Override public boolean apply(Element element) {
      return element.getSimpleName().contentEquals("Builder")
          && element.getModifiers().contains(PUBLIC);
    }
  };

}
