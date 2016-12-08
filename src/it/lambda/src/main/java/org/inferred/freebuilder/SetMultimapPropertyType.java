package org.inferred.freebuilder;

import com.google.common.collect.SetMultimap;

@EBuilder
public interface SetMultimapPropertyType {
  SetMultimap<Integer, String> getNumbers();

  class Builder extends SetMultimapPropertyType_Builder {}
}
