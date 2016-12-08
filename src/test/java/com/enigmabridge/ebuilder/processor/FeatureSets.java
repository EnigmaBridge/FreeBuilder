package com.enigmabridge.ebuilder.processor;

import com.enigmabridge.ebuilder.processor.util.feature.FeatureSet;
import com.enigmabridge.ebuilder.processor.util.feature.FunctionPackage;
import com.enigmabridge.ebuilder.processor.util.feature.GuavaLibrary;
import com.enigmabridge.ebuilder.processor.util.feature.SourceLevel;
import com.google.common.collect.ImmutableList;

import com.enigmabridge.ebuilder.processor.util.feature.StaticFeatureSet;

import java.util.List;

final class FeatureSets {

  /** For tests valid in any environment. */
  public static final List<FeatureSet> ALL = ImmutableList.of(
      new StaticFeatureSet(SourceLevel.JAVA_6),
      new StaticFeatureSet(SourceLevel.JAVA_7),
      new StaticFeatureSet(SourceLevel.JAVA_7, FunctionPackage.AVAILABLE),
      new StaticFeatureSet(SourceLevel.JAVA_6, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(SourceLevel.JAVA_7, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(SourceLevel.JAVA_7, FunctionPackage.AVAILABLE, GuavaLibrary.AVAILABLE));

  /** For mapper and mutate method tests. */
  public static final List<FeatureSet> WITH_LAMBDAS = ImmutableList.of(
      new StaticFeatureSet(SourceLevel.JAVA_7, FunctionPackage.AVAILABLE),
      new StaticFeatureSet(SourceLevel.JAVA_7, FunctionPackage.AVAILABLE, GuavaLibrary.AVAILABLE));

  /** For tests using Guava types. */
  public static final List<FeatureSet> WITH_GUAVA = ImmutableList.of(
      new StaticFeatureSet(SourceLevel.JAVA_6, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(SourceLevel.JAVA_7, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(SourceLevel.JAVA_7, FunctionPackage.AVAILABLE, GuavaLibrary.AVAILABLE));

  /** For mutate method tests using Guava types. */
  public static final List<FeatureSet> WITH_GUAVA_AND_LAMBDAS = ImmutableList.of(
      new StaticFeatureSet(SourceLevel.JAVA_7, FunctionPackage.AVAILABLE, GuavaLibrary.AVAILABLE));

  private FeatureSets() {}
}
