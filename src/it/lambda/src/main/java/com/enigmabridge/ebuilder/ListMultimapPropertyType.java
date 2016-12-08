package com.enigmabridge.ebuilder;

import com.google.common.collect.ListMultimap;

@EBuilder
public interface ListMultimapPropertyType {
  ListMultimap<Integer, String> getNumbers();

  class Builder extends ListMultimapPropertyType_Builder {}
}
