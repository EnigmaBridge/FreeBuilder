package org.inferred.freebuilder;

import java.util.List;

@EBuilder
public interface ListPropertyType {
  List<String> getNames();

  class Builder extends ListPropertyType_Builder {}
}
