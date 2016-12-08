package org.inferred.freebuilder;

@EBuilder
public interface RequiredPropertiesType {
  String getFirstName();
  String getSurname();

  class Builder extends RequiredPropertiesType_Builder {}
}
