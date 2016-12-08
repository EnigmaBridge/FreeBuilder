package com.enigmabridge.ebuilder;

import com.google.common.collect.SetMultimap;

@EBuilder
public interface SetMultimapPropertyType {
  SetMultimap<Integer, String> getNumbers();

  class Builder extends SetMultimapPropertyType_Builder {}
}
