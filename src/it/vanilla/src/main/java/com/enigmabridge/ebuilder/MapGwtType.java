/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.enigmabridge.ebuilder;

import com.google.common.annotations.GwtCompatible;

import java.io.Serializable;
import java.util.Map;

/** Simple GWT-compatible EBuilder type with a map of strings to doubles.  */
@EBuilder
@GwtCompatible(serializable = true)
public interface MapGwtType extends Serializable {
  Map<String, Double> getDistances();

  /** Builder for {@link StringListGwtType}. */
  class Builder extends MapGwtType_Builder { }
}
