package com.enigmabridge.ebuilder;

import java.util.List;

@EBuilder
public interface ListPropertyType {
  List<String> getNames();

  class Builder extends ListPropertyType_Builder {}
}
