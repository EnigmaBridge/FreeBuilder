package com.enigmabridge.ebuilder;

@EBuilder
public interface DefaultedPropertiesType {
  String getFirstName();
  String getSurname();

  class Builder extends DefaultedPropertiesType_Builder {
    public Builder() {
      setFirstName("Joe");
      setSurname("Bloggs");
    }
  }
}
