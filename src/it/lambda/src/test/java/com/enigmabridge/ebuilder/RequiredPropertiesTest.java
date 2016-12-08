package com.enigmabridge.ebuilder;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.function.UnaryOperator;

public class RequiredPropertiesTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testMap() {
    RequiredPropertiesType value = new RequiredPropertiesType.Builder()
        .setFirstName("joe")
        .setSurname("bloggs")
        .mapFirstName(CAPITALIZE)
        .mapSurname(CAPITALIZE)
        .build();
    assertEquals("Joe", value.getFirstName());
    assertEquals("Bloggs", value.getSurname());
  }

  private static final UnaryOperator<String> CAPITALIZE = s ->
      s.substring(0, 1).toUpperCase() + s.substring(1, s.length());
}
