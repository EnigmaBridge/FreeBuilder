package com.enigmabridge.ebuilder.processor;

import com.enigmabridge.ebuilder.processor.Metadata.Property;

/** Utility methods for method names used in builders. */
public class BuilderMethods {

  public static String getter(Property property) {
    return property.getGetterName();
  }

  public static String setter(Property property) {
    if (property.isUsingBeanConvention()) {
      return "set" + property.getCapitalizedName();
    } else {
      return property.getName();
    }
  }

  public static String nullableSetter(Property property) {
    if (property.isUsingBeanConvention()) {
      return "setNullable" + property.getCapitalizedName();
    } else {
      return "nullable" + property.getCapitalizedName();
    }
  }

  public static String getBuilderMethod(Property property) {
    if (property.isUsingBeanConvention()) {
      return "get" + property.getCapitalizedName() + "Builder";
    } else {
      return property.getName() + "Builder";
    }
  }

  public static String addMethod(Metadata.Property property) {
    return "add" + property.getCapitalizedName();
  }

  public static String addAllMethod(Metadata.Property property) {
    return "addAll" + property.getCapitalizedName();
  }

  public static String addCopiesMethod(Metadata.Property property) {
    return "addCopiesTo" + property.getCapitalizedName();
  }

  public static String putMethod(Metadata.Property property) {
    return "put" + property.getCapitalizedName();
  }

  public static String putAllMethod(Metadata.Property property) {
    return "putAll" + property.getCapitalizedName();
  }

  public static String removeMethod(Metadata.Property property) {
    return "remove" + property.getCapitalizedName();
  }

  public static String removeAllMethod(Metadata.Property property) {
    return "removeAll" + property.getCapitalizedName();
  }

  public static String setCountMethod(Metadata.Property property) {
    return "setCountOf" + property.getCapitalizedName();
  }

  public static String mapper(Metadata.Property property) {
    return "map" + property.getCapitalizedName();
  }

  public static String mutator(Metadata.Property property) {
    return "mutate" + property.getCapitalizedName();
  }

  public static String clearMethod(Metadata.Property property) {
    return "clear" + property.getCapitalizedName();
  }

  public static String isPropertySetMethod(Metadata.Property property) {
    return "isProperty" + property.getCapitalizedName() + "Set";
  }

  private BuilderMethods() {}
}
