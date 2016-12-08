package com.enigmabridge.ebuilder;

import java.util.Set;

@EBuilder
public interface SetPropertyType {
  Set<String> getNames();

  class Builder extends SetPropertyType_Builder {}
}
