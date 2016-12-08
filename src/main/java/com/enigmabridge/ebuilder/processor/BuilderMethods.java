package com.enigmabridge.ebuilder.processor;

/** Utility methods for method names used in builders. */
public class BuilderMethods {

  public static String getter(Metadata.Property property) {
    return property.getGetterName();
  }

  public static String setter(Metadata.Property property) {
    return "set" + property.getCapitalizedName();
  }

  public static String nullableSetter(Metadata.Property property) {
    return "setNullable" + property.getCapitalizedName();
  }

  public static String getBuilderMethod(Metadata.Property property) {
    return "get" + property.getCapitalizedName() + "Builder";
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
