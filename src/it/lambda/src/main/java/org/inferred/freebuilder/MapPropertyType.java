package org.inferred.freebuilder;

import java.util.Map;

@EBuilder
public interface MapPropertyType {
  Map<Integer, String> getNumbers();

  class Builder extends MapPropertyType_Builder {}
}
