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

import static com.enigmabridge.ebuilder.processor.util.ModelUtils.*;
import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Iterables.tryFind;
import static com.google.common.collect.Maps.newLinkedHashMap;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.util.ElementFilter.constructorsIn;
import static javax.lang.model.util.ElementFilter.typesIn;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;
import static com.enigmabridge.ebuilder.processor.BuilderFactory.NO_ARGS_CONSTRUCTOR;
import static com.enigmabridge.ebuilder.processor.GwtSupport.gwtMetadata;
import static com.enigmabridge.ebuilder.processor.MethodFinder.methodsOn;

import com.enigmabridge.ebuilder.EBuilder;
import com.enigmabridge.ebuilder.processor.util.IsInvalidTypeVisitor;
import com.enigmabridge.ebuilder.processor.Metadata.Property;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import com.enigmabridge.ebuilder.processor.PropertyCodeGenerator.Config;
import com.enigmabridge.ebuilder.processor.util.ParameterizedType;
import com.enigmabridge.ebuilder.processor.util.QualifiedName;

import java.beans.Introspector;
import java.io.Serializable;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Messager;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

/**
 * Analyses a {@link EBuilder EBuilder}
 * type, returning metadata about it in a format amenable to code generation.
 *
 * <p>Any deviations from the EBuilder spec in the user's class will result in errors being
 * issued, but unless code generation is totally impossible, metadata will still be returned.
 * This allows the user to extend an existing type without worrying that a mistake will cause
 * compiler errors in all dependent code&mdash;which would make it very hard to find the real
 * error.
 */
class Analyser {

  /**
   * Thrown when a @EBuilder type cannot have a Builder type generated, for instance if
   * it is private.
   */
  public static class CannotGenerateCodeException extends Exception { }

  /**
   * Factories of {@link PropertyCodeGenerator} instances. Note: order is important; the default
   * factory should always be last.
   */
  private static final List<PropertyCodeGenerator.Factory> PROPERTY_FACTORIES = ImmutableList.of(
      new NullablePropertyFactory(), // Must be first, as no other factory supports nulls
      new ListPropertyFactory(),
      new SetPropertyFactory(),
      new MapPropertyFactory(),
      new MultisetPropertyFactory(),
      new ListMultimapPropertyFactory(),
      new SetMultimapPropertyFactory(),
      new OptionalPropertyFactory(),
      new BuildablePropertyFactory(),
      new DefaultPropertyFactory()); // Must be last, as it will always return a CodeGenerator

  private static final String BUILDER_SIMPLE_NAME_TEMPLATE = "%s_Builder";
  private static final String ABUILDER_SIMPLE_NAME_TEMPLATE = "%s_ABuilder";
  private static final String USER_BUILDER_NAME = "Builder";
  private static final String USER_ABUILDER_NAME = "ABuilder";
  private static final String USER_DEFAULT_VALUES_NAME = "defaultValues";
  private static final String BUILDER_ANNOTATION = EBuilder.class.getCanonicalName();

  private static final Pattern GETTER_PATTERN = Pattern.compile("^(get|is)(.+)");
  private static final String GET_PREFIX = "get";
  private static final String IS_PREFIX = "is";

  private final Elements elements;
  private final Messager messager;
  private final MethodIntrospector methodIntrospector;
  private final Types types;

  Analyser(
      Elements elements, Messager messager, MethodIntrospector methodIntrospector, Types types) {
    this.elements = elements;
    this.messager = messager;
    this.methodIntrospector = methodIntrospector;
    this.types = types;
  }

  private void err(TypeElement type, String fmt, Object... args){
    messager.printMessage(ERROR, String.format(fmt, args), type);
  }

  private void log(TypeElement type, String fmt, Object... args){
    //messager.printMessage(NOTE, String.format(fmt, args), type);
  }

  /**
   * Returns a {@link Metadata} metadata object for {@code type}.
   *
   * @throws CannotGenerateCodeException if code cannot be generated, e.g. if the type is private
   */
  Metadata analyse(TypeElement type) throws CannotGenerateCodeException {
    PackageElement pkg = elements.getPackageOf(type);
    verifyType(type, pkg);
    ImmutableSet<ExecutableElement> ownMethods = MethodFinder.methodsOn(type, elements, false);
    ImmutableSet<ExecutableElement> methods = MethodFinder.methodsOn(type, elements);

    QualifiedName generatedABuilder = QualifiedName.of(
        pkg.getQualifiedName().toString(), generatedABuilderSimpleName(type));
    QualifiedName generatedBuilder = QualifiedName.of(
        pkg.getQualifiedName().toString(), generatedBuilderSimpleName(type));

    Optional<TypeElement> abuilder = tryFindABuilder(generatedABuilder, type, USER_ABUILDER_NAME);
    Optional<TypeElement> builder = tryFindBuilder(generatedBuilder, type, USER_BUILDER_NAME);

    // <T> parameters on annotated type
    List<? extends TypeParameterElement> typeParameters = type.getTypeParameters();
    log(type, "type %s", type);
    log(type, "typeParams %s", typeParameters);

    // If abstract builder is overridden by the user code, use that one, otherwise use our abstract builder.
    Optional<ParameterizedType> generatedABuilderExt = Optional.absent();
    if (abuilder.isPresent()){
      // TODO: fix for generics...
      //List<? extends TypeParameterElement> abuilderTypeParameters = abuilder.get().getTypeParameters();

      // A builder types - specific
      ArrayList abuilderExtParamsSpec = new ArrayList(Arrays.asList(type, generatedBuilder));
      // abuilderExtParamsSpec.addAll(abuilderTypeParameters);

      log(type, "ABuilderExt name: %s", generatedABuilderExt);
      generatedABuilderExt = parameterized(abuilder, abuilderExtParamsSpec);
    }

    QualifiedName valueType = generatedBuilder.nestedType("Value");
    QualifiedName partialType = generatedBuilder.nestedType("Partial");
    QualifiedName propertyType = generatedABuilder.nestedType("Property");

    // A builder types: T extends EntA, B extends EntA_Builder
    ArrayList abuilderParams = new ArrayList(Arrays.asList("T extends " + type, "B extends " + generatedABuilder));
    abuilderParams.addAll(typeParameters);
    log(type, "ABuilder typeParams %s", typeParameters);

    // A builder types - specific
    ArrayList abuilderParamsSpec = new ArrayList(Arrays.asList(type, generatedBuilder));
    abuilderParamsSpec.addAll(typeParameters);
    log(type, "ABuilderSpec typeParams %s", abuilderParamsSpec);

    Map<ExecutableElement, Metadata.Property> properties = findProperties(type, methods);
    Metadata.Builder metadataBuilder = new Metadata.Builder()
        .setType(QualifiedName.of(type).withParameters(typeParameters))
        .setInterfaceType(type.getKind().isInterface())
        .setABuilder(parameterized(abuilder, typeParameters))
        .setBuilder(parameterized(builder, typeParameters))
        .setBuilderFactory(builderFactory(builder))
        .setGeneratedBuilder(generatedBuilder.withParameters(typeParameters))
        .setGeneratedABuilder(generatedABuilder.withParameters(typeParameters))
        .setGeneratedABuilderParametrized(generatedABuilder.withParameters(abuilderParams))
        .setGeneratedABuilderParametrizedSpec(generatedABuilder.withParameters(abuilderParamsSpec))
        .setOptionalABuilderExtension(generatedABuilderExt)
        .setValueType(valueType.withParameters(typeParameters))
        .setPartialType(partialType.withParameters(typeParameters))
        .setPropertyEnum(propertyType.withParameters())
        .addVisibleNestedTypes(valueType)
        .addVisibleNestedTypes(partialType)
        .addVisibleNestedTypes(propertyType)
        .addAllVisibleNestedTypes(visibleTypesIn(type))  // Because we inherit from type
        .putAllStandardMethodUnderrides(findUnderriddenMethods(methods))
        .setBuilderSerializable(shouldBuilderBeSerializable(builder))
        .addAllProperties(properties.values())
        .addAllOwnProperties(findProperties(type, ownMethods).values())
        .setTypeGen("T")
        .setBuildGen("B");

    // Super class has EBuilder also?
    analyseSuperclass(type, metadataBuilder, methods, properties);

    // All super types implementing builder
    metadataBuilder.addAllSuperBuilderTypes(superBuilders(type));

    Metadata baseMetadata = metadataBuilder.build();
    metadataBuilder.mergeFrom(gwtMetadata(type, baseMetadata));
    if (builder.isPresent()) {
      metadataBuilder
          .clearProperties()
          .addAllProperties(codeGenerators(properties, baseMetadata, builder.get()));

      // mergeFrom from super types
      metadataBuilder.putAllSuperTypeProperties(processSuperTypeProperties(type, baseMetadata, builder));
    }
    return metadataBuilder.build();
  }

  private Optional<DeclaredType> getDeclaredSuperclass(TypeElement type){
    final TypeMirror superClass = type.getSuperclass();
    final TypeKind scType = superClass.getKind();
    if (scType == TypeKind.NONE){
      return Optional.absent();
    }

    // Can be either DECLARED or ERROR
    if (scType != TypeKind.DECLARED){
      return Optional.absent();
    }

    return Optional.of((DeclaredType) superClass);
  }

  private ImmutableMap<ParameterizedType, ImmutableList<Property>> processSuperTypeProperties(
          TypeElement type,
          Metadata baseMetadata,
          Optional<TypeElement> builder) throws CannotGenerateCodeException {
    Map<ParameterizedType, ImmutableList<Property>> toRet =
            new HashMap<ParameterizedType, ImmutableList<Property>>();

    final ImmutableSet<TypeElement> superTypes = MethodFinder.getSupertypes(type);
    for (TypeElement superType : superTypes) {
      if (superType.equals(type)) {
        continue;
      }

      final ImmutableSet<ExecutableElement> superMethods = methodsOn(superType, elements);
      final Map<ExecutableElement, Property> superPropertiesRet =
              findProperties(superType, superMethods);
      if (superPropertiesRet.isEmpty()) {
        continue;
      }

      ParameterizedType pType = QualifiedName.of(superType).withParameters(
              superType.getTypeParameters());

      // Code builder dance
      if (builder.isPresent()) {
        final Metadata metadataSuperType = analyse(superType);
        final Metadata.Builder metadataBld = Metadata.Builder.from(metadataSuperType);
        metadataBld.setBuilderFactory(Optional.<BuilderFactory>absent());

        for (Map.Entry<ExecutableElement, Property> entry : superPropertiesRet.entrySet()) {
          Config config = new ConfigImpl(
                  builder.get(),
                  metadataBld.build(),
                  entry.getValue(),
                  entry.getKey(),
                  ImmutableSet.<String>of());

          entry.setValue(new Property.Builder()
                  .mergeFrom(entry.getValue())
                  .setCodeGenerator(createCodeGenerator(config))
                  .build());
        }
      }

      toRet.put(pType, ImmutableList.copyOf(superPropertiesRet.values()));
    }

    return ImmutableMap.copyOf(toRet);
  }

  private ImmutableSet<ParameterizedType> superBuilders(TypeElement type)
          throws CannotGenerateCodeException {
    Set<ParameterizedType> toRet = new HashSet<ParameterizedType>();
    PackageElement pkg = elements.getPackageOf(type);

    final ImmutableSet<TypeElement> superTypes = MethodFinder.getSupertypes(type);
    for (TypeElement superType : superTypes) {
      if (superType.equals(type)) {
        continue;
      }

      final Optional<AnnotationMirror> freeBuilderMirror =
              findAnnotationMirror(superType, EBuilder.class);

      if (freeBuilderMirror.isPresent()) {
        ParameterizedType pType = QualifiedName.of(superType).withParameters(
                superType.getTypeParameters());
        toRet.add(pType);
      }
    }

    return ImmutableSet.copyOf(toRet);
  }

  private void analyseSuperclass(TypeElement type, Metadata.Builder metadataBuilder,
                                 ImmutableSet<ExecutableElement> methods, Map<ExecutableElement, Metadata.Property> properties)
  {
    final Optional<DeclaredType> optSuperDecl = getDeclaredSuperclass(type);
    if (!optSuperDecl.isPresent()){
      return;
    }

    final TypeMirror superClass = type.getSuperclass();
    final DeclaredType scDecType = optSuperDecl.get();
    final Element scElem = scDecType.asElement();
    final ElementKind scKind = scElem.getKind();
    log(type, "Type %s has super class type %s, kind: %s", type, scElem, scKind);

    final Optional<AnnotationMirror> freeBuilderMirror =
            findAnnotationMirror(scElem, BUILDER_ANNOTATION);
    if (!freeBuilderMirror.isPresent()){
      metadataBuilder.setOptionalABuilderAncestor(Optional.<ParameterizedType>absent());
      return;
    }

    final PackageElement pkg = elements.getPackageOf(type);
    log(type, "Super class %s has free builder annotation, package: %s", scElem, pkg);

    final TypeElement scTElem = (TypeElement) scElem;

    // If ABuilder is overridden in the superclass, extend that one.
    QualifiedName generatedABuilder = QualifiedName.of(
            pkg.getQualifiedName().toString(), generatedABuilderSimpleName(scTElem));

    Optional<TypeElement> abuilder = tryFindABuilder(generatedABuilder, scTElem, USER_ABUILDER_NAME);
    List<? extends TypeParameterElement> typeParameters = scTElem.getTypeParameters();

    List abuilderParams = Arrays.asList(metadataBuilder.getTypeGen(), metadataBuilder.getBuildGen());
    abuilderParams.addAll(typeParameters);

    Optional<ParameterizedType> ancestorBuilder = Optional.absent();
    if (abuilder.isPresent()){
      ancestorBuilder = parameterized(abuilder, abuilderParams);
    } else {
      ancestorBuilder = Optional.of(generatedABuilder.withParameters(abuilderParams));
    }

    metadataBuilder.setOptionalABuilderAncestor(ancestorBuilder);
  }

  private static Set<QualifiedName> visibleTypesIn(TypeElement type) {
    ImmutableSet.Builder<QualifiedName> visibleTypes = ImmutableSet.builder();
    for (TypeElement nestedType : typesIn(type.getEnclosedElements())) {
      visibleTypes.add(QualifiedName.of(nestedType));
    }
    visibleTypes.addAll(visibleTypesIn(maybeType(type.getEnclosingElement())));
    visibleTypes.addAll(visibleTypesIn(maybeAsTypeElement(type.getSuperclass())));
    return visibleTypes.build();
  }

  private static Set<QualifiedName> visibleTypesIn(Optional<TypeElement> type) {
    if (!type.isPresent()) {
      return ImmutableSet.of();
    } else {
      return visibleTypesIn(type.get());
    }
  }

  /** Basic sanity-checking to ensure we can fulfil the &#64;EBuilder contract for this type. */
  private void verifyType(TypeElement type, PackageElement pkg) throws CannotGenerateCodeException {
    if (pkg.isUnnamed()) {
      messager.printMessage(ERROR, "@EBuilder does not support types in unnamed packages", type);
      throw new CannotGenerateCodeException();
    }
    switch (type.getNestingKind()) {
      case TOP_LEVEL:
        break;

      case MEMBER:
        if (!type.getModifiers().contains(Modifier.STATIC)) {
          messager.printMessage(
              ERROR,
              "Inner classes cannot be @EBuilder types (did you forget the static keyword?)",
              type);
          throw new CannotGenerateCodeException();
        }

        if (type.getModifiers().contains(Modifier.PRIVATE)) {
          messager.printMessage(ERROR, "@EBuilder types cannot be private", type);
          throw new CannotGenerateCodeException();
        }

        for (Element e = type.getEnclosingElement(); e != null; e = e.getEnclosingElement()) {
          if (e.getModifiers().contains(Modifier.PRIVATE)) {
            messager.printMessage(
                ERROR,
                "@EBuilder types cannot be private, but enclosing type "
                    + e.getSimpleName() + " is inaccessible",
                type);
            throw new CannotGenerateCodeException();
          }
        }
        break;

      default:
        messager.printMessage(
            ERROR, "Only top-level or static nested types can be @EBuilder types", type);
        throw new CannotGenerateCodeException();
    }
    switch (type.getKind()) {
      case ANNOTATION_TYPE:
        messager.printMessage(ERROR, "@EBuilder does not support annotation types", type);
        throw new CannotGenerateCodeException();

      case CLASS:
        verifyTypeIsConstructible(type);
        break;

      case ENUM:
        messager.printMessage(ERROR, "@EBuilder does not support enum types", type);
        throw new CannotGenerateCodeException();

      case INTERFACE:
        // Nothing extra needs to be checked on an interface
        break;

      default:
        throw new AssertionError("Unexpected element kind " + type.getKind());
    }
  }

  /** Issues an error if {@code type} does not have a package-visible no-args constructor. */
  private void verifyTypeIsConstructible(TypeElement type)
      throws CannotGenerateCodeException {
    List<ExecutableElement> constructors = constructorsIn(type.getEnclosedElements());
    if (constructors.isEmpty()) {
      return;
    }
    for (ExecutableElement constructor : constructors) {
      if (constructor.getParameters().isEmpty()) {
        if (constructor.getModifiers().contains(Modifier.PRIVATE)) {
          messager.printMessage(
              ERROR,
              "@EBuilder types must have a package-visible no-args constructor",
              constructor);
          throw new CannotGenerateCodeException();
        }
        return;
      }
    }
    messager.printMessage(
        ERROR, "@EBuilder types must have a package-visible no-args constructor", type);
    throw new CannotGenerateCodeException();
  }

  /** Find any standard methods the user has 'underridden' in their type. */
  private Map<Metadata.StandardMethod, Metadata.UnderrideLevel> findUnderriddenMethods(
      Iterable<ExecutableElement> methods) {
    Map<Metadata.StandardMethod, ExecutableElement> standardMethods =
        new LinkedHashMap<Metadata.StandardMethod, ExecutableElement>();
    for (ExecutableElement method : methods) {
      Optional<Metadata.StandardMethod> standardMethod = maybeStandardMethod(method);
      if (standardMethod.isPresent() && isUnderride(method)) {
        standardMethods.put(standardMethod.get(), method);
      }
    }
    if (standardMethods.containsKey(Metadata.StandardMethod.EQUALS)
        != standardMethods.containsKey(Metadata.StandardMethod.HASH_CODE)) {
      ExecutableElement underriddenMethod = standardMethods.containsKey(Metadata.StandardMethod.EQUALS)
          ? standardMethods.get(Metadata.StandardMethod.EQUALS)
          : standardMethods.get(Metadata.StandardMethod.HASH_CODE);
      messager.printMessage(ERROR,
          "hashCode and equals must be implemented together on @EBuilder types",
          underriddenMethod);
    }
    ImmutableMap.Builder<Metadata.StandardMethod, Metadata.UnderrideLevel> result = ImmutableMap.builder();
    for (Metadata.StandardMethod standardMethod : standardMethods.keySet()) {
      if (standardMethods.get(standardMethod).getModifiers().contains(Modifier.FINAL)) {
        result.put(standardMethod, Metadata.UnderrideLevel.FINAL);
      } else {
        result.put(standardMethod, Metadata.UnderrideLevel.OVERRIDEABLE);
      }
    }
    return result.build();
  }

  private static boolean isUnderride(ExecutableElement method) {
    return !method.getModifiers().contains(Modifier.ABSTRACT);
  }

  /**
   * Looks for a type called Builder, and verifies it extends the autogenerated superclass. Issues
   * an error if the wrong type is being subclassed&mdash;a typical copy-and-paste error when
   * renaming an existing &#64;EBuilder type, or using one as a template.
   */
  private Optional<TypeElement> tryFindBuilder(
      final QualifiedName generatedBuilder, TypeElement type) {
    return tryFindBuilder(generatedBuilder, type, USER_BUILDER_NAME);
  }

  private Optional<TypeElement> tryFindBuilder(
      final QualifiedName generatedBuilder, final TypeElement type, final String builderName) {
    Optional<TypeElement> userClass =
        tryFind(typesIn(type.getEnclosedElements()), new Predicate<Element>() {
          @Override public boolean apply(Element input) {
            return input.getSimpleName().contentEquals(builderName);
          }
        });
    if (!userClass.isPresent()) {
      if (type.getKind() == INTERFACE) {
        messager.printMessage(
            NOTE,
            "Add \"class Builder extends "
                + generatedBuilder.getSimpleName()
                + " {}\" to your interface to enable the @EBuilder API",
            type);
      } else {
        messager.printMessage(
            NOTE,
            "Add \"public static class Builder extends "
                + generatedBuilder.getSimpleName()
                + " {}\" to your class to enable the @EBuilder API",
            type);
      }
      return Optional.absent();
    }

    boolean extendsSuperclass =
        new IsSubclassOfGeneratedTypeVisitor(generatedBuilder, type.getTypeParameters())
            .visit(userClass.get().getSuperclass());
    if (!extendsSuperclass) {
      err(userClass.get(), "%s extends the wrong type (should be %s, but is %s)",
              builderName, generatedBuilder.getSimpleName(), type.getSimpleName());
      return Optional.absent();
    }

    return userClass;
  }

  private Optional<TypeElement> tryFindABuilder(
      final QualifiedName generatedBuilder, final TypeElement type, final String builderName) {
    Optional<TypeElement> userClass =
        tryFind(typesIn(type.getEnclosedElements()), new Predicate<Element>() {
          @Override public boolean apply(Element input) {
            return input.getSimpleName().contentEquals(builderName);
          }
        });

    if (!userClass.isPresent()) {
      return Optional.absent();
    }

    final TypeMirror scType = userClass.get().getSuperclass();
    final TypeKind scKind = scType.getKind();

    // Error - not yet generated... thus check only the prefix. What can we do...
    if (scKind == TypeKind.ERROR){
      String prefix = generatedBuilder.getSimpleName()+"<";
      if (scType.toString().startsWith(prefix)){
        log(userClass.get(),"Matching %s with %s", generatedBuilder, scType);
        return userClass;

      } else {
        err(userClass.get(), "%s extends the wrong type (should be %s, but is %s), err-type",
                builderName, generatedBuilder.getSimpleName(), type.getSimpleName());
        return Optional.absent();
      }
    }

    boolean extendsSuperclass =
        new IsSubclassOfGeneratedTypeVisitor(generatedBuilder, type.getTypeParameters())
            .visit(scType);
    if (!extendsSuperclass) {
      err(userClass.get(), "%s extends the wrong type (should be %s, but is %s)",
              builderName, generatedBuilder.getSimpleName(), type.getSimpleName());
      return Optional.absent();
    }

    return userClass;
  }

  private Optional<BuilderFactory> builderFactory(Optional<TypeElement> builder) {
    if (!builder.isPresent()) {
      return Optional.of(NO_ARGS_CONSTRUCTOR);
    }
    if (!builder.get().getModifiers().contains(Modifier.STATIC)) {
      messager.printMessage(ERROR, "Builder must be static on @EBuilder types", builder.get());
      return Optional.absent();
    }
    return BuilderFactory.from(builder.get());
  }

  private Map<ExecutableElement, Metadata.Property> findProperties(
      TypeElement type, Iterable<ExecutableElement> methods) {
    Map<ExecutableElement, Metadata.Property> propertiesByMethod = newLinkedHashMap();
    Optional<JacksonSupport> jacksonSupport = JacksonSupport.create(type);
    for (ExecutableElement method : methods) {
      Metadata.Property property = asPropertyOrNull(type, method, jacksonSupport);
      if (property != null) {
        propertiesByMethod.put(method, property);
      }
    }
    return propertiesByMethod;
  }

  private List<Metadata.Property> codeGenerators(
      Map<ExecutableElement, Metadata.Property> properties,
      Metadata metadata,
      TypeElement builder) {
    ImmutableList.Builder<Metadata.Property> codeGenerators = ImmutableList.builder();
    Set<String> methodsInvokedInBuilderConstructor = getMethodsInvokedInBuilderConstructor(builder);
    for (Map.Entry<ExecutableElement, Metadata.Property> entry : properties.entrySet()) {
      Config config = new ConfigImpl(
          builder,
          metadata,
          entry.getValue(),
          entry.getKey(),
          methodsInvokedInBuilderConstructor);
      codeGenerators.add(new Metadata.Property.Builder()
          .mergeFrom(entry.getValue())
          .setCodeGenerator(createCodeGenerator(config))
          .build());
    }
    return codeGenerators.build();
  }

  private Set<String> getMethodsInvokedInBuilderConstructor(TypeElement builder) {
    List<ExecutableElement> constructors = constructorsIn(builder.getEnclosedElements());
    Set<Name> result = null;
    for (ExecutableElement constructor : constructors) {
      if (result == null) {
        result = methodIntrospector.getOwnMethodInvocations(constructor);
      } else {
        result = Sets.intersection(result, methodIntrospector.getOwnMethodInvocations(constructor));
      }
    }
    return ImmutableSet.copyOf(transform(result, toStringFunction()));
  }

  /**
   * Introspects {@code method}, as found on {@code valueType}.
   *
   * @return a {@link Metadata.Property} metadata object, or null if the method is not a valid getter
   */
  private Metadata.Property asPropertyOrNull(
      TypeElement valueType,
      ExecutableElement method,
      Optional<JacksonSupport> jacksonSupport) {
    MatchResult getterNameMatchResult = getterNameMatchResult(valueType, method);
    if (getterNameMatchResult == null) {
      return null;
    }
    String getterName = getterNameMatchResult.group(0);

    TypeMirror propertyType = getReturnType(valueType, method);
    String camelCaseName = Introspector.decapitalize(getterNameMatchResult.group(2));
    Metadata.Property.Builder resultBuilder = new Metadata.Property.Builder()
            .setType(propertyType)
            .setName(camelCaseName)
            .setCapitalizedName(getterNameMatchResult.group(2))
            .setAllCapsName(camelCaseToAllCaps(camelCaseName))
            .setGetterName(getterName)
            .setFullyCheckedCast(CAST_IS_FULLY_CHECKED.visit(propertyType));
    if (jacksonSupport.isPresent()) {
      jacksonSupport.get().addJacksonAnnotations(resultBuilder, method);
    }
    if (propertyType.getKind().isPrimitive()) {
      PrimitiveType unboxedType = types.getPrimitiveType(propertyType.getKind());
      TypeMirror boxedType = types.erasure(types.boxedClass(unboxedType).asType());
      resultBuilder.setBoxedType(boxedType);
    }
    return resultBuilder.build();
  }

  /**
   * Determines the return type of {@code method}, if called on an instance of type {@code type}.
   *
   * <p>For instance, in this example, myY.getProperty() returns List&lt;T&gt;, not T:<pre><code>
   *    interface X&lt;T&gt; {
   *      T getProperty();
   *    }
   *    &#64;EBuilder interface Y&lt;T&gt; extends X&lt;List&lt;T&gt;&gt; { }</pre></code>
   *
   * <p>(Unfortunately, a bug in Eclipse prevents us handling these cases correctly at the moment.
   * javac works fine.)
   */
  private TypeMirror getReturnType(TypeElement type, ExecutableElement method) {
    try {
      ExecutableType executableType = (ExecutableType)
          types.asMemberOf((DeclaredType) type.asType(), method);
      return executableType.getReturnType();
    } catch (IllegalArgumentException e) {
      // Eclipse incorrectly throws an IllegalArgumentException here:
      //    "element is not valid for the containing declared type"
      // As a workaround for the common case, fall back to the declared return type.
      return method.getReturnType();
    }
  }

  private static PropertyCodeGenerator createCodeGenerator(Config config) {
    for (PropertyCodeGenerator.Factory factory : PROPERTY_FACTORIES) {
      Optional<? extends PropertyCodeGenerator> codeGenerator = factory.create(config);
      if (codeGenerator.isPresent()) {
        return codeGenerator.get();
      }
    }
    throw new AssertionError("DefaultPropertyFactory not registered");
  }

  private class ConfigImpl implements Config {

    private final TypeElement builder;
    private final Metadata metadata;
    private final Metadata.Property property;
    private final ExecutableElement getterMethod;
    private final Set<String> methodsInvokedInBuilderConstructor;

    ConfigImpl(
        TypeElement builder,
        Metadata metadata,
        Metadata.Property property,
        ExecutableElement getterMethod,
        Set<String> methodsInvokedInBuilderConstructor) {
      this.builder = builder;
      this.metadata = metadata;
      this.property = property;
      this.getterMethod = getterMethod;
      this.methodsInvokedInBuilderConstructor = methodsInvokedInBuilderConstructor;
    }

    @Override
    public TypeElement getBuilder() {
      return builder;
    }

    @Override
    public Metadata getMetadata() {
      return metadata;
    }

    @Override
    public Metadata.Property getProperty() {
      return property;
    }

    @Override
    public List<? extends AnnotationMirror> getAnnotations() {
      return getterMethod.getAnnotationMirrors();
    }

    @Override
    public Set<String> getMethodsInvokedInBuilderConstructor() {
      return methodsInvokedInBuilderConstructor;
    }

    @Override
    public Elements getElements() {
      return elements;
    }

    @Override
    public Types getTypes() {
      return types;
    }
  }

  /**
   * Visitor that returns true if a cast to the visited type is guaranteed to be fully checked at
   * runtime. This is true for any type that is non-generic, raw, or parameterized with unbounded
   * wildcards, such as {@code Integer}, {@code List} or {@code Map<?, ?>}.
   */
  private static final SimpleTypeVisitor6<Boolean, ?> CAST_IS_FULLY_CHECKED =
      new SimpleTypeVisitor6<Boolean, Void>() {
        @Override
        public Boolean visitArray(ArrayType t, Void p) {
          return visit(t.getComponentType());
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, Void p) {
          for (TypeMirror argument : t.getTypeArguments()) {
            if (!IS_UNBOUNDED_WILDCARD.visit(argument)) {
              return false;
            }
          }
          return true;
        }

        @Override protected Boolean defaultAction(TypeMirror e, Void p) {
          return true;
        }
      };

  /**
   * Visitor that returns true if the visited type is an unbounded wildcard, i.e. {@code <?>}.
   */
  private static final SimpleTypeVisitor6<Boolean, ?> IS_UNBOUNDED_WILDCARD =
      new SimpleTypeVisitor6<Boolean, Void>() {
        @Override public Boolean visitWildcard(WildcardType t, Void p) {
          return t.getExtendsBound() == null
              || t.getExtendsBound().toString().equals("java.lang.Object");
        }

        @Override protected Boolean defaultAction(TypeMirror e, Void p) {
          return false;
        }
      };

  /**
   * Verifies {@code method} is an abstract getter following the JavaBean convention. Any
   * deviations will be logged as an error.
   *
   * <p>We deviate slightly from the JavaBean convention by insisting that there must be a
   * non-lowercase character immediately following the get/is prefix; this prevents ugly cases like
   * 'get()' or 'getter()'.
   *
   * @return a {@link Matcher} with the getter prefix in group 1 and the property name suffix
   *     in group 2, or {@code null} if {@code method} is not a valid abstract getter method
   */
  private MatchResult getterNameMatchResult(TypeElement valueType, ExecutableElement method) {
    if (maybeStandardMethod(method).isPresent()) {
      return null;
    }
    Set<Modifier> modifiers = method.getModifiers();
    if (!modifiers.contains(Modifier.ABSTRACT)) {
      return null;
    }
    boolean declaredOnValueType = method.getEnclosingElement().equals(valueType);
    String name = method.getSimpleName().toString();
    Matcher getterMatcher = GETTER_PATTERN.matcher(name);
    if (!getterMatcher.matches()) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR,
            "Only getter methods (starting with '" + GET_PREFIX
                + "' or '" + IS_PREFIX + "') may be declared abstract on @EBuilder types",
            method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return null;
    }
    String prefix = getterMatcher.group(1);
    String suffix = getterMatcher.group(2);
    if (hasUpperCase(suffix.codePointAt(0))) {
      if (declaredOnValueType) {
        String message = new StringBuilder()
            .append("Getter methods cannot have a lowercase character immediately after the '")
            .append(prefix)
            .append("' prefix on @EBuilder types (did you mean '")
            .append(prefix)
            .appendCodePoint(Character.toUpperCase(suffix.codePointAt(0)))
            .append(suffix.substring(suffix.offsetByCodePoints(0, 1)))
            .append("'?)")
            .toString();
        messager.printMessage(ERROR, message, method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return null;
    }
    TypeMirror returnType = getReturnType(valueType, method);
    if (returnType.getKind() == TypeKind.VOID) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR, "Getter methods must not be void on @EBuilder types", method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return null;
    }
    if (prefix.equals(IS_PREFIX) && (returnType.getKind() != TypeKind.BOOLEAN)) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR,
            "Getter methods starting with '" + IS_PREFIX
                + "' must return a boolean on @EBuilder types",
            method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return null;
    }
    if (!method.getParameters().isEmpty()) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR, "Getter methods cannot take parameters on @EBuilder types", method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return null;
    }
    if (new IsInvalidTypeVisitor().visit(returnType)) {
      // The compiler should already have issued an error.
      return null;
    }
    return getterMatcher.toMatchResult();
  }

  private void printNoImplementationMessage(TypeElement valueType, ExecutableElement method) {
    messager.printMessage(
        ERROR,
        "No implementation found for non-getter method '" + method + "'; "
            + "cannot generate @EBuilder implementation",
        valueType);
  }

  /**
   * Returns the simple name of the builder class that should be generated for the given type.
   *
   * <p>This is simply the {@link #BUILDER_SIMPLE_NAME_TEMPLATE} with the original type name
   * substituted in. (If the original type is nested, its enclosing classes will be included,
   * separated with underscores, to ensure uniqueness.)
   */
  private String generatedBuilderSimpleName(TypeElement type) {
    String packageName = elements.getPackageOf(type).getQualifiedName().toString();
    String originalName = type.getQualifiedName().toString();
    checkState(originalName.startsWith(packageName + "."));
    String nameWithoutPackage = originalName.substring(packageName.length() + 1);
    return String.format(BUILDER_SIMPLE_NAME_TEMPLATE, nameWithoutPackage.replaceAll("\\.", "_"));
  }
  private String generatedABuilderSimpleName(TypeElement type) {
    String packageName = elements.getPackageOf(type).getQualifiedName().toString();
    String originalName = type.getQualifiedName().toString();
    checkState(originalName.startsWith(packageName + "."));
    String nameWithoutPackage = originalName.substring(packageName.length() + 1);
    return String.format(ABUILDER_SIMPLE_NAME_TEMPLATE, nameWithoutPackage.replaceAll("\\.", "_"));
  }

  private boolean shouldBuilderBeSerializable(Optional<TypeElement> builder) {
    if (!builder.isPresent()) {
      // If there's no user-provided subclass, make the builder serializable.
      return true;
    }
    // If there is a user-provided subclass, only make its generated superclass serializable if
    // it is itself; otherwise, tools may complain about missing a serialVersionUID field.
    return any(builder.get().getInterfaces(), isEqualTo(Serializable.class));
  }

  private static boolean hasUpperCase(int codepoint) {
    return Character.toUpperCase(codepoint) != codepoint;
  }

  /** Returns whether a method is one of the {@link Metadata.StandardMethod}s, and if so, which. */
  private static Optional<Metadata.StandardMethod> maybeStandardMethod(ExecutableElement method) {
    String methodName = method.getSimpleName().toString();
    if (methodName.equals("equals")) {
      if (method.getParameters().size() == 1
          && method.getParameters().get(0).asType().toString().equals("java.lang.Object")) {
        return Optional.of(Metadata.StandardMethod.EQUALS);
      } else {
        return Optional.absent();
      }
    } else if (methodName.equals("hashCode")) {
      if (method.getParameters().isEmpty()) {
        return Optional.of(Metadata.StandardMethod.HASH_CODE);
      } else {
        return Optional.absent();
      }
    } else if (methodName.equals("toString")) {
      if (method.getParameters().isEmpty()) {
        return Optional.of(Metadata.StandardMethod.TO_STRING);
      } else {
        return Optional.absent();
      }
    } else {
      return Optional.absent();
    }
  }

  /**
   * Visitor that returns true if the visited type extends a generated {@code superclass} in the
   * same package.
   */
  private static final class IsSubclassOfGeneratedTypeVisitor extends
      SimpleTypeVisitor6<Boolean, Void> {
    private final QualifiedName superclass;
    private final List<? extends TypeParameterElement> typeParameters;

    private IsSubclassOfGeneratedTypeVisitor(
        QualifiedName superclass, List<? extends TypeParameterElement> typeParameters) {
      super(false);
      this.superclass = superclass;
      this.typeParameters = typeParameters;
    }

    /**
     * Any reference to the as-yet-ungenerated builder should be an unresolved ERROR.
     * Similarly for many copy-and-paste errors
     */
    @Override
    public Boolean visitError(ErrorType t, Void p) {
      if (typeParameters.isEmpty()) {
        // For non-generic types, the ErrorType will have the correct name.
        String simpleName = t.toString();
        return equal(simpleName, superclass.getSimpleName());
      }
      // For generic types, we'll just have to hope for the best.
      // TODO: Revalidate in a subsequent round?
      return true;
    }

    /**
     * However, with some setups (e.g. Eclipse+blaze), the builder may have already been
     * generated and provided via a jar, in which case the reference will be DECLARED and
     * qualified. We still want to generate it.
     */
    @Override
    public Boolean visitDeclared(DeclaredType t, Void p) {
      return asElement(t).getQualifiedName().contentEquals(superclass.toString());
    }
  }

  /** Converts camelCaseConvention to ALL_CAPS_CONVENTION. */
  private static String camelCaseToAllCaps(String camelCase) {
    // The first half of the pattern spots lowercase to uppercase boundaries.
    // The second half spots the end of uppercase sequences, like "URL" in "myURLShortener".
    return camelCase.replaceAll("(?<=[^A-Z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][^A-Z])", "_")
        .toUpperCase();
  }

  private Predicate<TypeMirror> isEqualTo(Class<?> cls) {
    final TypeMirror typeMirror = elements.getTypeElement(cls.getCanonicalName()).asType();
    return new Predicate<TypeMirror>() {
      @Override public boolean apply(TypeMirror input) {
        return types.isSameType(input, typeMirror);
      }
    };
  }

  private static Optional<ParameterizedType> parameterized(
      Optional<TypeElement> type, List<? extends TypeParameterElement> typeParameters) {
    if (!type.isPresent()) {
      return Optional.absent();
    }
    return Optional.of(QualifiedName.of(type.get()).withParameters(typeParameters));
  }
}
