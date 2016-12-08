package com.enigmabridge.ebuilder.processor;

import com.google.common.collect.ImmutableList;
import com.enigmabridge.ebuilder.processor.util.feature.FeatureSet;
import com.enigmabridge.ebuilder.processor.util.feature.GuavaLibrary;
import com.enigmabridge.ebuilder.processor.util.feature.StaticFeatureSet;

import java.util.List;

import static com.enigmabridge.ebuilder.processor.util.feature.SourceLevel.*;

final class FeatureSets {

  /** For tests valid in any environment. */
  public static final List<FeatureSet> ALL = ImmutableList.of(
      new StaticFeatureSet(JAVA_6),
      new StaticFeatureSet(JAVA_7),
      new StaticFeatureSet(JAVA_8),
      new StaticFeatureSet(JAVA_6, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(JAVA_7, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(JAVA_8, GuavaLibrary.AVAILABLE));

  /** For mapper and mutate method tests. */
  public static final List<FeatureSet> WITH_LAMBDAS = ImmutableList.of(
      new StaticFeatureSet(JAVA_8),
      new StaticFeatureSet(JAVA_8, GuavaLibrary.AVAILABLE));

  /** For tests using Guava types. */
  public static final List<FeatureSet> WITH_GUAVA = ImmutableList.of(
      new StaticFeatureSet(JAVA_6, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(JAVA_7, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(JAVA_8, GuavaLibrary.AVAILABLE));

  /** For mutate method tests using Guava types. */
  public static final List<FeatureSet> WITH_GUAVA_AND_LAMBDAS = ImmutableList.of(
      new StaticFeatureSet(JAVA_8, GuavaLibrary.AVAILABLE));

  private FeatureSets() {}
}
