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

import static com.enigmabridge.ebuilder.processor.BuilderMethods.isPropertySetMethod;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.enigmabridge.ebuilder.processor.BuilderFactory.TypeInference.EXPLICIT_TYPES;

import com.enigmabridge.ebuilder.EBuilder;
import com.enigmabridge.ebuilder.processor.util.*;
import com.enigmabridge.ebuilder.processor.util.feature.GuavaLibrary;
import com.enigmabridge.ebuilder.processor.util.feature.SourceLevel;
import com.enigmabridge.ebuilder.processor.Metadata.Property;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import com.enigmabridge.ebuilder.processor.PropertyCodeGenerator.Type;

import java.io.Serializable;
import java.util.*;

/**
 * Code generation for the &#64;{@link EBuilder} annotation.
 */
public class CodeGenerator {

  /** Write the source code for a generated builder. */
  void writeABuilderSource(SourceBuilder code, Metadata metadata) {
    if (!metadata.hasBuilder()) {
      writeStubSource(code, metadata);
      return;
    }

    addABuilderTypeDeclaration(code, metadata);
    code.addLine(" {");

    addConstantDeclarations(metadata, code);
    if (any(metadata.getProperties(), IS_REQUIRED)) {
      addPropertyEnum(metadata, code);
    }

    addFieldDeclarations(code, metadata);
    addAbstractMethods(code, metadata);

    addAccessors(metadata, code);
    addMergeFromValueMethod(code, metadata);
    addMergeFromBuilderMethod(code, metadata);
    addMergeFromSuperTypes(code, metadata);
    addClearMethod(code, metadata);
    addPropertiesSetMethods(code, metadata);

    code.addLine("}");
  }

  void writeBuilderSource(SourceBuilder code, Metadata metadata) {
    if (!metadata.hasBuilder()) {
      writeStubSource(code, metadata);
      return;
    }

    addBuilderTypeDeclaration(code, metadata);
    code.addLine(" {");

    addConstantDeclarations(metadata, code);
    addStaticFromMethod(code, metadata);
    addBuilderConstructor(code, metadata);
    addAbstractMethodsImpl(code, metadata);

    // Moved to another builder.
    addBuildMethod(code, metadata);
    addBuildPartialMethod(code, metadata);

    addValueType(code, metadata);
    addPartialType(code, metadata);
    for (Function<Metadata, Excerpt> nestedClass : metadata.getNestedClasses()) {
      code.add(nestedClass.apply(metadata));
    }
    addStaticMethods(code, metadata);
    code.addLine("}");
  }

  private void addABuilderTypeDeclaration(SourceBuilder code, Metadata metadata) {
    code.addLine("/**")
        .addLine(" * Auto-generated superclass of %s,", metadata.getBuilder().javadocLink())
        .addLine(" * derived from the API of %s.", metadata.getType().javadocLink())
        .addLine(" */")
        .add(Excerpts.generated(getClass()));
    for (Excerpt annotation : metadata.getGeneratedBuilderAnnotations()) {
      code.add(annotation);
    }
    code.add("abstract class %s",
            metadata.getGeneratedABuilderParametrized().declaration()
    );
    final Optional<ParameterizedType> optABuilderAncestor = metadata.getOptionalABuilderAncestor();
    if (optABuilderAncestor.isPresent()){
      code.add(" extends %s", optABuilderAncestor.get().fullDeclaration());
    }
    if (metadata.isBuilderSerializable()) {
      code.add(" implements %s", Serializable.class);
    }
  }

  private void addBuilderTypeDeclaration(SourceBuilder code, Metadata metadata) {
    code.addLine("/**")
        .addLine(" * Auto-generated superclass of %s,", metadata.getBuilder().javadocLink())
        .addLine(" * derived from the API of %s.", metadata.getType().javadocLink())
        .addLine(" */")
        .add(Excerpts.generated(getClass()));
    for (Excerpt annotation : metadata.getGeneratedBuilderAnnotations()) {
      code.add(annotation);
    }

    //EntB_Builder<EntB, EntB_Builder>
    final Optional<ParameterizedType> optionalABuilderExtension = metadata.getOptionalABuilderExtension();
    Excerpt extendsWhat = optionalABuilderExtension.isPresent() ?
            optionalABuilderExtension.get().fullDeclaration() :
            metadata.getGeneratedABuilderParametrizedSpec().declaration();

    code.add("abstract class %s extends %s",
            metadata.getGeneratedBuilder().declaration(),
            extendsWhat
    );
  }

  private static void addStaticFromMethod(SourceBuilder code, Metadata metadata) {
    BuilderFactory builderFactory = metadata.getBuilderFactory().orNull();
    if (builderFactory == null) {
      return;
    }
    code.addLine("")
        .addLine("/**")
        .addLine(" * Creates a new builder using {@code value} as a template.")
        .addLine(" */")
        .addLine("public static %s %s from(%s value) {",
            metadata.getBuilder().declarationParameters(),
            metadata.getBuilder(),
            metadata.getType())
        .addLine("  return (%s)%s.mergeFrom(value);",
            metadata.getBuilder(),
            builderFactory.newBuilder(metadata.getBuilder(), EXPLICIT_TYPES))
        .addLine("}");
  }

  private static void addConstantDeclarations(Metadata metadata, SourceBuilder body) {
    if (body.feature(GuavaLibrary.GUAVA).isAvailable() && metadata.getProperties().size() > 1) {
      body.addLine("")
          .addLine("private static final %1$s COMMA_JOINER = %1$s.on(\", \").skipNulls();",
              Joiner.class);
    }
  }

  private static void addFieldDeclarations(SourceBuilder code, Metadata metadata) {
    code.addLine("");
    for (Metadata.Property property : metadata.getProperties()) {
      PropertyCodeGenerator codeGenerator = property.getCodeGenerator();
      codeGenerator.addBuilderFieldDeclaration(code);
    }
    // Unset properties
    if (any(metadata.getProperties(), IS_REQUIRED)) {
      code.addLine("final %s<%s> _unsetProperties =",
              EnumSet.class, metadata.getPropertyEnum())
          .addLine("    %s.allOf(%s.class);", EnumSet.class, metadata.getPropertyEnum());
    }
  }

  private static void addBuilderConstructor(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Default constructor with default values.")
        .addLine(" */")
        .addLine("public %s() {", metadata.getGeneratedBuilder().getSimpleName())
        .addLine("  initBuilder();")
        .addLine("}");
  }

  private static void addAbstractMethods(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Abstract build method, returns immutable value class.")
        .addLine(" * Implemented in side (non-inherited) builder.")
        .addLine(" * ")
        .addLine(" * @return this {@code %s} object", metadata.getTypeGen())
        .addLine(" */")
        .addLine("public abstract %s build();", metadata.getTypeGen());

    code.addLine("")
        .addLine("/**")
        .addLine(" * Abstract method returns the actual builder instance with the ")
        .addLine(" * high type. This is returned in each value setter so the return ")
        .addLine(" * value is compatible with top-level builder. Used in builder inheritance.")
        .addLine(" * ")
        .addLine(" * @return this {@code %s} object", metadata.getBuildGen())
        .addLine(" */")
        .addLine("protected abstract %s getThisBuilder();", metadata.getBuildGen());

    code.addLine("")
        .addLine("/**")
        .addLine(" * Abstract method returns a new builder instance of the  ")
        .addLine(" * high type. It is used to detect default values on runtime for merging purposes.")
        .addLine(" * ")
        .addLine(" * @return this {@code %s} object", metadata.getBuildGen())
        .addLine(" */")
        .addLine("protected abstract %s getNewBuilder();", metadata.getBuildGen());

    code.addLine("")
        .addLine("/**")
        .addLine(" * Method for setting default values to builder properties.")
        .addLine(" * By default it is empty or calls super.")
        .addLine(" * In order to set inheritable default values, override this method in .")
        .addLine(" * the abstract builder. For local default values inherit in specific builder.")
        .addLine(" */")
        .addLine("protected void defaultValues(){");
    if (metadata.getOptionalABuilderAncestor().isPresent()){
      code.addLine("  super.defaultValues();");
    }
    code.addLine("}");

  }

  private static void addAbstractMethodsImpl(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns this builder object as a top-level {@link %s} builder ", metadata.getBuilder())
        .addLine(" * class extending this abstract class {@link %s}.", metadata.getGeneratedBuilder())
        .addLine(" */")
        .addLine("@Override")
        .addLine("protected %s getThisBuilder() {", metadata.getBuilder())
        .addLine("  return (%s)this;", metadata.getBuilder())
        .addLine("}")
        .addLine("");

    BuilderFactory builderFactory = metadata.getBuilderFactory().orNull();
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns a new builder instance as a top-level {@link %s} builder ", metadata.getBuilder())
        .addLine(" * class extending this abstract class {@link %s}.", metadata.getGeneratedBuilder())
        .addLine(" * Used for default value generation.")
        .addLine(" */")
        .addLine("@Override")
        .addLine("protected %s getNewBuilder() {", metadata.getBuilder());

    if (builderFactory == null) {
      code.addLine("  throw new IllegalStateException(\"Abstract builder cannot be instantianted\");");
    } else {
      code.addLine("  return %s;", builderFactory.newBuilder(metadata.getBuilder(), EXPLICIT_TYPES));
    }
    code.addLine("}");

    code.addLine("")
        .addLine("/**")
        .addLine(" * Initializes the builder object with default values.")
        .addLine(" * Returns the builder type - usable in upper builder factory.")
        .addLine(" */")
        .addLine("@SuppressWarnings(\"unchecked\")")
        .addLine("protected <BB extends %s> BB initBuilder() {", metadata.getGeneratedABuilder())
        .addLine("  defaultValues();")
        .addLine("  return (BB) this;")
        .addLine("}");
  }

  private static void addAccessors(Metadata metadata, SourceBuilder body) {
    for (Metadata.Property property : metadata.getProperties()) {
      property.getCodeGenerator().addBuilderFieldAccessors(body);
    }
  }

  private static void addBuildMethod(SourceBuilder code, Metadata metadata) {
    boolean hasRequiredProperties = any(metadata.getProperties(), IS_REQUIRED);
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns a newly-created %s based on the contents of the {@code %s}.",
            metadata.getType().javadocLink(), metadata.getBuilder().getSimpleName());
    if (hasRequiredProperties) {
      code.addLine(" *")
          .addLine(" * @throws IllegalStateException if any field has not been set");
    }
    code.addLine(" */")
        .addLine("public %s build() {", metadata.getType());
    if (hasRequiredProperties) {
      code.add(PreconditionExcerpts.checkState(
          "_unsetProperties.isEmpty()", "Not set: %s", "_unsetProperties"));
    }
    code.addLine("  return %s(this);", metadata.getValueType().constructor())
        .addLine("}");
  }

  private static void addMergeFromValueMethod(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets all property values using the given {@code %s} as a template.",
            metadata.getType().getQualifiedName())
        .addLine(" */")
        .addLine("public %s mergeFrom(%s value) {", metadata.getBuildGen(), metadata.getTypeGen());
    Block body = new Block(code);
    for (Metadata.Property property : metadata.getProperties()) {
      property.getCodeGenerator().addMergeFromValue(body, "value");
    }
    code.add(body)
//        .addLine("  return (%s) this;", metadata.getBuilder())
        .addLine("  return getThisBuilder();")
        .addLine("}");
  }

  private static void addMergeFromBuilderMethod(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Copies values from the given {@code %s}.",
            metadata.getBuilder().getSimpleName())
        .addLine(" * Does not affect any properties not set on the input.")
        .addLine(" */")
        .addLine("public %1$s mergeFrom(%1$s template) {", metadata.getBuildGen());
    Block body = new Block(code);
    for (Metadata.Property property : metadata.getProperties()) {
      property.getCodeGenerator().addMergeFromBuilder(body, "template");
    }
    code.add(body)
        .addLine("  return getThisBuilder();")
//        .addLine("  return (%s) this;", metadata.getBuilder())
        .addLine("}");
  }

  private static void addMergeFromSuperTypes(SourceBuilder code, Metadata metadata) {
    for(Map.Entry<ParameterizedType, ImmutableList<Metadata.Property>> e : metadata.getSuperTypeProperties().entrySet()){
      final ParameterizedType type = e.getKey();
      final ImmutableList<Metadata.Property> properties = e.getValue();

      // mergeFrom - value
      code.addLine("")
          .addLine("/**")
          .addLine(" * Sets all property values using the given {@code %s} as a template.", type.getQualifiedName())
          .addLine(" */")
          .addLine("public %s mergeFromSuper(%s value) {", metadata.getBuildGen(), type.getQualifiedName());
      Block body = new Block(code);
      for (Metadata.Property property : properties) {
        property.getCodeGenerator().addMergeFromValue(body, "value");
      }
      code.add(body)
          .addLine("  return getThisBuilder();")
          .addLine("}");

      // has builder ?
      if (!metadata.getSuperBuilderTypes().contains(type)){
        continue;
      }

      // mergeFrom - builder
      final QualifiedName builder = type.getQualifiedName().nestedType("Builder");
      code.addLine("")
          .addLine("/**")
          .addLine(" * Copies values from the given {@code %s}.", builder.getSimpleName())
          .addLine(" * Does not affect any properties not set on the input.")
          .addLine(" */")
          .addLine("public %s mergeFromSuper(%s template) {", metadata.getBuildGen(), builder);
      Block fromBuilderBody = new Block(code);
      for (Metadata.Property property : properties) {
        property.getCodeGenerator().addMergeFromBuilder(fromBuilderBody, "template");
      }
      code.add(fromBuilderBody)
          .addLine("  return getThisBuilder();")
          .addLine("}");
    }
  }

  private static void addClearMethod(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Resets the state of this builder.")
        .addLine(" */")
        .addLine("public %s clear() {", metadata.getBuildGen());
    Block body = new Block(code);
    List<PropertyCodeGenerator> codeGenerators =
        Lists.transform(metadata.getProperties(), Metadata.GET_CODE_GENERATOR);
    for (PropertyCodeGenerator codeGenerator : codeGenerators) {
      codeGenerator.addClearField(body);
    }
    code.add(body);
    if (any(metadata.getProperties(), IS_REQUIRED)) {
      Optional<Excerpt> defaults = Declarations.freshBuilder(body, metadata);
      if (defaults.isPresent()) {
        code.addLine("  _unsetProperties.clear();")
            .addLine("  _unsetProperties.addAll(%s._unsetProperties);", defaults.get());
      }
    }
    code.addLine("  return getThisBuilder();")
      //.addLine("  return (%s) this;", metadata.getBuilder())
        .addLine("}");
  }

  private static void addPropertiesSetMethods(SourceBuilder code, Metadata metadata) {
    if (!any(metadata.getProperties(), IS_REQUIRED)) {
      return;

    }

    for (Property property : metadata.getProperties()) {
      if (!IS_REQUIRED.apply(property)) {
        continue;
      }

      code.addLine("")
              .addLine("/**")
              .addLine(" * Returns true if the required property corresponding to")
              .addLine(" * %s is set. ", metadata.getType().javadocNoArgMethodLink(
                      property.getGetterName()))
              .addLine(" */")
              .addLine("public boolean %s() {", isPropertySetMethod(property))
              .addLine("  return _unsetProperties.contains(%s.%s);",
                      metadata.getPropertyEnum(), property.getAllCapsName())
              .addLine("}");
    }
  }

  private static void addBuildPartialMethod(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns a newly-created partial %s", metadata.getType().javadocLink())
        .addLine(" * based on the contents of the {@code %s}.",
            metadata.getBuilder().getSimpleName())
        .addLine(" * State checking will not be performed.");
    if (any(metadata.getProperties(), IS_REQUIRED)) {
      code.addLine(" * Unset properties will throw an {@link %s}",
              UnsupportedOperationException.class)
          .addLine(" * when accessed via the partial object.");
    }
    code.addLine(" *")
        .addLine(" * <p>Partials should only ever be used in tests.")
        .addLine(" */");
    if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
      code.addLine("@%s()", VisibleForTesting.class);
    }
    code.addLine("public %s buildPartial() {", metadata.getType())
        .addLine("  return %s(this);", metadata.getPartialType().constructor())
        .addLine("}");
  }

  private static void addPropertyEnum(Metadata metadata, SourceBuilder code) {
    code.addLine("")
        .addLine("enum %s {", metadata.getPropertyEnum().getSimpleName());
    for (Metadata.Property property : metadata.getProperties()) {
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("  %s(\"%s\"),", property.getAllCapsName(), property.getName());
      }
    }
    code.addLine("  ;")
        .addLine("")
        .addLine("  final %s name;", String.class)
        .addLine("")
        .addLine("  %s(%s name) {",
            metadata.getPropertyEnum().getSimpleName(), String.class)
        .addLine("    this.name = name;")
        .addLine("  }")
        .addLine("")
        .addLine("  @%s public %s toString() {", Override.class, String.class)
        .addLine("    return name;")
        .addLine("  }")
        .addLine("}");
  }

  private static void addValueType(SourceBuilder code, Metadata metadata) {
    code.addLine("");
    for (Excerpt annotation : metadata.getValueTypeAnnotations()) {
      code.add(annotation);
    }
    code.addLine("%s static final class %s %s {",
        metadata.getValueTypeVisibility(),
        metadata.getValueType().declaration(),
        extending(metadata.getType(), metadata.isInterfaceType()));
    // Fields
    for (Metadata.Property property : metadata.getProperties()) {
      property.getCodeGenerator().addValueFieldDeclaration(code, property.getName());
    }
    // Constructor
    code.addLine("")
        .addLine("  private %s(%s builder) {",
            metadata.getValueType().getSimpleName(),
            metadata.getGeneratedABuilder());
    for (Metadata.Property property : metadata.getProperties()) {
      property.getCodeGenerator()
          .addFinalFieldAssignment(code, "this." + property.getName(), "builder");
    }
    code.addLine("  }");
    // Getters
    for (Metadata.Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("  @%s", Override.class);
      property.getCodeGenerator().addGetterAnnotations(code);
      code.addLine("  public %s %s() {", property.getType(), property.getGetterName());
      code.add("    return ");
      property.getCodeGenerator().addReadValueFragment(code, property.getName());
      code.add(";\n");
      code.addLine("  }");
    }
    // Equals
    switch (metadata.standardMethodUnderride(Metadata.StandardMethod.EQUALS)) {
      case ABSENT:
        addValueTypeEquals(code, metadata);
        break;

      case OVERRIDEABLE:
        // Partial-respecting override if a non-final user implementation exists.
        code.addLine("")
            .addLine("  @%s", Override.class)
            .addLine("  public boolean equals(Object obj) {")
            .addLine("    return (!(obj instanceof %s) && super.equals(obj));",
                metadata.getPartialType().getQualifiedName())
            .addLine("  }");
        break;

      case FINAL:
        // Cannot override if a final user implementation exists.
        break;
    }
    // Hash code
    if (metadata.standardMethodUnderride(Metadata.StandardMethod.HASH_CODE) == Metadata.UnderrideLevel.ABSENT) {
      String properties = Joiner.on(", ").join(getNames(metadata.getProperties()));
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public int hashCode() {");
      if (code.feature(SourceLevel.SOURCE_LEVEL).javaUtilObjects().isPresent()) {
        code.addLine("    return %s.hash(%s);",
            code.feature(SourceLevel.SOURCE_LEVEL).javaUtilObjects().get(), properties);
      } else {
        code.addLine("    return %s.hashCode(new Object[] { %s });", Arrays.class, properties);
      }
      code.addLine("  }");
    }
    // toString
    if (metadata.standardMethodUnderride(Metadata.StandardMethod.TO_STRING) == Metadata.UnderrideLevel.ABSENT) {
      addValueTypeToString(code, metadata);
    }
    code.addLine("}");
  }

  private static void addValueTypeEquals(SourceBuilder code, Metadata metadata) {
    // Default implementation if no user implementation exists.
    code.addLine("")
        .addLine("  @%s", Override.class)
        .addLine("  public boolean equals(Object obj) {")
        .addLine("    if (!(obj instanceof %s)) {", metadata.getValueType().getQualifiedName())
        .addLine("      return false;")
        .addLine("    }")
        .addLine("    %1$s other = (%1$s) obj;", metadata.getValueType().withWildcards());
    if (metadata.getProperties().isEmpty()) {
      code.addLine("    return true;");
    } else if (code.feature(SourceLevel.SOURCE_LEVEL).javaUtilObjects().isPresent()) {
      String prefix = "    return ";
      for (Metadata.Property property : metadata.getProperties()) {
        code.add(prefix);
        code.add("%1$s.equals(%2$s, other.%2$s)",
            code.feature(SourceLevel.SOURCE_LEVEL).javaUtilObjects().get(), property.getName());
        prefix = "\n        && ";
      }
      code.add(";\n");
    } else {
      for (Metadata.Property property : metadata.getProperties()) {
        switch (property.getType().getKind()) {
          case FLOAT:
          case DOUBLE:
            code.addLine("    if (%s.doubleToLongBits(%s)", Double.class, property.getName())
                .addLine("        != %s.doubleToLongBits(other.%s)) {",
                    Double.class, property.getName());
            break;

          default:
            if (property.getType().getKind().isPrimitive()) {
              code.addLine("    if (%1$s != other.%1$s) {", property.getName());
            } else if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
              code.addLine("    if (%1$s != other.%1$s", property.getName())
                  .addLine("        && (%1$s == null || !%1$s.equals(other.%1$s))) {",
                      property.getName());
            } else {
              code.addLine("    if (!%1$s.equals(other.%1$s)) {", property.getName());
            }
        }
        code.addLine("      return false;")
            .addLine("    }");
      }
      code.addLine("    return true;");
    }
    code.addLine("  }");
  }

  private static void addValueTypeToString(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("  @%s", Override.class)
        .addLine("  public %s toString() {", String.class);
    switch (metadata.getProperties().size()) {
      case 0: {
        code.addLine("    return \"%s{}\";", metadata.getType().getSimpleName());
        break;
      }

      case 1: {
        code.add("    return \"%s{", metadata.getType().getSimpleName());
        Metadata.Property property = getOnlyElement(metadata.getProperties());
        if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
          code.add("\" + (%1$s != null ? \"%1$s=\" + %1$s : \"\") + \"}\";\n",
              property.getName());
        } else {
          code.add("%1$s=\" + %1$s + \"}\";\n", property.getName());
        }
        break;
      }

      default: {
        if (!any(metadata.getProperties(), IS_OPTIONAL)) {
          // If none of the properties are optional, use string concatenation for performance.
          code.addLine("    return \"%s{\"", metadata.getType().getSimpleName());
          Metadata.Property lastProperty = getLast(metadata.getProperties());
          for (Metadata.Property property : metadata.getProperties()) {
            code.add("        + \"%1$s=\" + %1$s", property.getName());
            if (property != lastProperty) {
              code.add(" + \", \"\n");
            } else {
              code.add(" + \"}\";\n");
            }
          }
        } else if (code.feature(GuavaLibrary.GUAVA).isAvailable()) {
          // If Guava is available, use COMMA_JOINER for readability.
          code.addLine("    return \"%s{\"", metadata.getType().getSimpleName())
              .addLine("        + COMMA_JOINER.join(");
          Metadata.Property lastProperty = getLast(metadata.getProperties());
          for (Metadata.Property property : metadata.getProperties()) {
            code.add("            ");
            if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
              code.add("(%s != null ? ", property.getName());
            }
            code.add("\"%1$s=\" + %1$s", property.getName());
            if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
              code.add(" : null)");
            }
            if (property != lastProperty) {
              code.add(",\n");
            } else {
              code.add(")\n");
            }
          }
          code.addLine("        + \"}\";");
        } else {
          // Use StringBuilder if no better choice is available.
          writeToStringWithBuilder(code, metadata, false);
        }
        break;
      }
    }
    code.addLine("  }");
  }

  private static void addPartialType(SourceBuilder code, Metadata metadata) {
    boolean hasRequiredProperties = any(metadata.getProperties(), IS_REQUIRED);
    code.addLine("")
        .addLine("private static final class %s %s {",
            metadata.getPartialType().declaration(),
            extending(metadata.getType(), metadata.isInterfaceType()));
    // Fields
    for (Metadata.Property property : metadata.getProperties()) {
      property.getCodeGenerator().addValueFieldDeclaration(code, property.getName());
    }
    if (hasRequiredProperties) {
      code.addLine("  private final %s<%s> _unsetProperties;",
          EnumSet.class, metadata.getPropertyEnum());
    }
    // Constructor
    code.addLine("")
        .addLine("  %s(%s builder) {",
            metadata.getPartialType().getSimpleName(),
            metadata.getGeneratedABuilder());
    for (Metadata.Property property : metadata.getProperties()) {
      property.getCodeGenerator()
          .addPartialFieldAssignment(code, "this." + property.getName(), "builder");
    }
    if (hasRequiredProperties) {
      code.addLine("    this._unsetProperties = builder._unsetProperties.clone();");
    }
    code.addLine("  }");
    // Getters
    for (Metadata.Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("  @%s", Override.class);
      property.getCodeGenerator().addGetterAnnotations(code);
      code.addLine("  public %s %s() {", property.getType(), property.getGetterName());
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("    if (_unsetProperties.contains(%s.%s)) {",
                metadata.getPropertyEnum(), property.getAllCapsName())
            .addLine("      throw new %s(\"%s not set\");",
                UnsupportedOperationException.class, property.getName())
            .addLine("    }");
      }
      code.add("    return ");
      property.getCodeGenerator().addReadValueFragment(code, property.getName());
      code.add(";\n");
      code.addLine("  }");
    }
    // Equals
    if (metadata.standardMethodUnderride(Metadata.StandardMethod.EQUALS) != Metadata.UnderrideLevel.FINAL) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public boolean equals(Object obj) {")
          .addLine("    if (!(obj instanceof %s)) {", metadata.getPartialType().getQualifiedName())
          .addLine("      return false;")
          .addLine("    }")
          .addLine("    %1$s other = (%1$s) obj;", metadata.getPartialType().withWildcards());
      if (metadata.getProperties().isEmpty()) {
        code.addLine("    return true;");
      } else if (code.feature(SourceLevel.SOURCE_LEVEL).javaUtilObjects().isPresent()) {
        String prefix = "    return ";
        for (Metadata.Property property : metadata.getProperties()) {
          code.add(prefix);
          code.add("%1$s.equals(%2$s, other.%2$s)",
              code.feature(SourceLevel.SOURCE_LEVEL).javaUtilObjects().get(), property.getName());
          prefix = "\n        && ";
        }
        if (hasRequiredProperties) {
          code.add(prefix);
          code.add("%1$s.equals(_unsetProperties, other._unsetProperties)",
              code.feature(SourceLevel.SOURCE_LEVEL).javaUtilObjects().get());
        }
        code.add(";\n");
      } else {
        for (Metadata.Property property : metadata.getProperties()) {
          switch (property.getType().getKind()) {
            case FLOAT:
            case DOUBLE:
              code.addLine("    if (%s.doubleToLongBits(%s)", Double.class, property.getName())
                  .addLine("        != %s.doubleToLongBits(other.%s)) {",
                      Double.class, property.getName());
              break;

            default:
              if (property.getType().getKind().isPrimitive()) {
                code.addLine("    if (%1$s != other.%1$s) {", property.getName());
              } else if (property.getCodeGenerator().getType() == Type.HAS_DEFAULT) {
                code.addLine("    if (!%1$s.equals(other.%1$s)) {", property.getName());
              } else {
                code.addLine("    if (%1$s != other.%1$s", property.getName())
                    .addLine("        && (%1$s == null || !%1$s.equals(other.%1$s))) {",
                        property.getName());
              }
          }
          code.addLine("      return false;")
              .addLine("    }");
        }
        if (hasRequiredProperties) {
          code.addLine("    return _unsetProperties.equals(other._unsetProperties);");
        } else {
          code.addLine("    return true;");
        }
      }
      code.addLine("  }");
    }
    // Hash code
    if (metadata.standardMethodUnderride(Metadata.StandardMethod.HASH_CODE) != Metadata.UnderrideLevel.FINAL) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public int hashCode() {");

      List<String> namesList = getNames(metadata.getProperties());
      if (hasRequiredProperties) {
        namesList =
            ImmutableList.<String>builder().addAll(namesList).add("_unsetProperties").build();
      }
      String properties = Joiner.on(", ").join(namesList);

      if (code.feature(SourceLevel.SOURCE_LEVEL).javaUtilObjects().isPresent()) {
        code.addLine("    return %s.hash(%s);",
            code.feature(SourceLevel.SOURCE_LEVEL).javaUtilObjects().get(), properties);
      } else {
        code.addLine("    return %s.hashCode(new Object[] { %s });", Arrays.class, properties);
      }
      code.addLine("  }");
    }
    // toString
    if (metadata.standardMethodUnderride(Metadata.StandardMethod.TO_STRING) != Metadata.UnderrideLevel.FINAL) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public %s toString() {", String.class);
      if (metadata.getProperties().size() > 1 && !code.feature(GuavaLibrary.GUAVA).isAvailable()) {
        writeToStringWithBuilder(code, metadata, true);
      } else {
        writePartialToStringWithConcatenation(code, metadata);
      }
      code.addLine("  }");
    }
    code.addLine("}");
  }

  private static void writeToStringWithBuilder(
      SourceBuilder code, Metadata metadata, boolean isPartial) {
    code.addLine("%1$s result = new %1$s(\"%2$s%3$s{\");",
        StringBuilder.class, isPartial ? "partial " : "", metadata.getType().getSimpleName());
    boolean noDefaults = !any(metadata.getProperties(), HAS_DEFAULT);
    if (noDefaults) {
      // We need to keep track of whether to output a separator
      code.addLine("String separator = \"\";");
    }
    boolean seenDefault = false;
    Metadata.Property first = metadata.getProperties().get(0);
    Metadata.Property last = Iterables.getLast(metadata.getProperties());
    for (Metadata.Property property : metadata.getProperties()) {
      boolean hadSeenDefault = seenDefault;
      switch (property.getCodeGenerator().getType()) {
        case HAS_DEFAULT:
          seenDefault = true;
          break;

        case OPTIONAL:
          code.addLine("if (%s != null) {", property.getName());
          break;

        case REQUIRED:
          if (isPartial) {
            code.addLine("if (!_unsetProperties.contains(%s.%s)) {",
                    metadata.getPropertyEnum(), property.getAllCapsName());
          }
          break;
      }
      if (noDefaults && property != first) {
        code.addLine("result.append(separator);");
      } else if (!noDefaults && hadSeenDefault) {
        code.addLine("result.append(\", \");");
      }
      code.addLine("result.append(\"%1$s=\").append(%1$s);", property.getName());
      if (!noDefaults && !seenDefault) {
        code.addLine("result.append(\", \");");
      } else if (noDefaults && property != last) {
        code.addLine("separator = \", \";");
      }
      switch (property.getCodeGenerator().getType()) {
        case HAS_DEFAULT:
          break;

        case OPTIONAL:
          code.addLine("}");
          break;

        case REQUIRED:
          if (isPartial) {
            code.addLine("}");
          }
          break;
      }
    }
    code.addLine("result.append(\"}\");")
        .addLine("return result.toString();");
  }

  private static void writePartialToStringWithConcatenation(SourceBuilder code, Metadata metadata) {
    code.add("    return \"partial %s{", metadata.getType().getSimpleName());
    switch (metadata.getProperties().size()) {
      case 0: {
        code.add("}\";\n");
        break;
      }

      case 1: {
        Metadata.Property property = getOnlyElement(metadata.getProperties());
        switch (property.getCodeGenerator().getType()) {
          case HAS_DEFAULT:
            code.add("%1$s=\" + %1$s + \"}\";\n", property.getName());
            break;

          case OPTIONAL:
            code.add("\"\n")
                .addLine("        + (%1$s != null ? \"%1$s=\" + %1$s : \"\")",
                    property.getName())
                .addLine("        + \"}\";");
            break;

          case REQUIRED:
            code.add("\"\n")
                .addLine("        + (!_unsetProperties.contains(%s.%s)",
                    metadata.getPropertyEnum(), property.getAllCapsName())
                .addLine("            ? \"%1$s=\" + %1$s : \"\")", property.getName())
                .addLine("        + \"}\";");
            break;
        }
        break;
      }

      default: {
        code.add("\"\n")
            .add("        + COMMA_JOINER.join(\n");
        Metadata.Property lastProperty = getLast(metadata.getProperties());
        for (Metadata.Property property : metadata.getProperties()) {
          code.add("            ");
          switch (property.getCodeGenerator().getType()) {
            case HAS_DEFAULT:
              code.add("\"%1$s=\" + %1$s", property.getName());
              break;

            case OPTIONAL:
              code.add("(%1$s != null ? \"%1$s=\" + %1$s : null)", property.getName());
              break;

            case REQUIRED:
              code.add("(!_unsetProperties.contains(%s.%s)\n",
                      metadata.getPropertyEnum(), property.getAllCapsName())
                  .add("                ? \"%1$s=\" + %1$s : null)", property.getName());
              break;
          }
          if (property != lastProperty) {
            code.add(",\n");
          } else {
            code.add(")\n");
          }
        }
        code.addLine("        + \"}\";");
        break;
      }
    }
  }

  private static void addStaticMethods(SourceBuilder code, Metadata metadata) {
    SortedSet<Excerpt> staticMethods = new TreeSet<Excerpt>();
    for (Metadata.Property property : metadata.getProperties()) {
      staticMethods.addAll(property.getCodeGenerator().getStaticExcerpts());
    }
    for (Excerpt staticMethod : staticMethods) {
      code.add(staticMethod);
    }
  }

  private void writeStubSource(SourceBuilder code, Metadata metadata) {
    code.addLine("/**")
        .addLine(" * Placeholder. Create {@code %s.Builder} and subclass this type.",
            metadata.getType())
        .addLine(" */")
        .add(Excerpts.generated(getClass()))
        .addLine("abstract class %s {}", metadata.getGeneratedABuilder().declaration());
  }

  /** Returns an {@link Excerpt} of "implements/extends {@code type}". */
  private static Excerpt extending(final Object type, final boolean isInterface) {
    return Excerpts.add(isInterface ? "implements %s" : "extends %s", type);
  }

  private static ImmutableList<String> getNames(Iterable<Metadata.Property> properties) {
    ImmutableList.Builder<String> result = ImmutableList.builder();
    for (Metadata.Property property : properties) {
      result.add(property.getName());
    }
    return result.build();
  }

  private static final Predicate<Metadata.Property> IS_REQUIRED = new Predicate<Metadata.Property>() {
    @Override public boolean apply(Metadata.Property property) {
      return property.getCodeGenerator().getType() == Type.REQUIRED;
    }
  };

  private static final Predicate<Metadata.Property> IS_OPTIONAL = new Predicate<Metadata.Property>() {
    @Override public boolean apply(Metadata.Property property) {
      return property.getCodeGenerator().getType() == Type.OPTIONAL;
    }
  };

  private static final Predicate<Metadata.Property> HAS_DEFAULT = new Predicate<Metadata.Property>() {
    @Override public boolean apply(Metadata.Property property) {
      return property.getCodeGenerator().getType() == Type.HAS_DEFAULT;
    }
  };
}
