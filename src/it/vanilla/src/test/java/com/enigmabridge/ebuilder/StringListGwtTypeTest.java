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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** FreeBuilder test using {@link StringListGwtType}. */
@RunWith(JUnit4.class)
public class StringListGwtTypeTest {

  private static final String NAME_1 = "name1";
  private static final String NAME_2 = "name2";
  private static final String NAME_3 = "name3";

  @Test
  public void testAllMethodInteractions() {
    StringListGwtType.Builder builder = new StringListGwtType.Builder();
    builder.addNames(NAME_1);
    assertEquals(newArrayList(NAME_1), builder.build().getNames());
    builder.addNames(NAME_2, NAME_3);
    assertEquals(newArrayList(NAME_1, NAME_2, NAME_3), builder.build().getNames());
    builder.clearNames();
    assertEquals(newArrayList(), builder.build().getNames());
    builder.addAllNames(newArrayList(NAME_2, NAME_1));
    assertEquals(newArrayList(NAME_2, NAME_1), builder.build().getNames());
    builder.clear();
    assertEquals(newArrayList(), builder.build().getNames());
  }

  @SafeVarargs
  private static <E> List<E> newArrayList(E... items) {
    List<E> list = new ArrayList<E>();
    for (E item : items) {
      list.add(item);
    }
    return list;
  }
}