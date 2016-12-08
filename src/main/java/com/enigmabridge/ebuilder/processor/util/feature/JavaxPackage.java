package com.enigmabridge.ebuilder.processor.util.feature;

import static com.enigmabridge.ebuilder.processor.util.feature.SourceLevel.JAVA_8;
import static com.enigmabridge.ebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import com.google.common.base.Optional;

import com.enigmabridge.ebuilder.processor.util.QualifiedName;
import com.enigmabridge.ebuilder.processor.util.SourceBuilder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;

/**
 * Types in the javax package, if available. Linked to the {@link SourceLevel} by default in tests.
 */
public enum JavaxPackage implements Feature<JavaxPackage> {

  AVAILABLE("javax"), UNAVAILABLE("No javax");

  /**
   * Constant to pass to {@link SourceBuilder#feature(FeatureType)} to get the current status of
   * {@link JavaxPackage}.
   */
  public static final FeatureType<JavaxPackage> JAVAX =
      new FeatureType<JavaxPackage>() {

        @Override
        protected JavaxPackage testDefault(FeatureSet features) {
          boolean isJava8OrHigher = features.get(SOURCE_LEVEL).compareTo(JAVA_8) >= 0;
          return isJava8OrHigher ? UNAVAILABLE : AVAILABLE;
        }

        @Override
        protected JavaxPackage forEnvironment(ProcessingEnvironment env, FeatureSet features) {
          return hasType(env.getElementUtils(), GENERATED) ? AVAILABLE : UNAVAILABLE;
        }
      };

  private static final QualifiedName GENERATED = QualifiedName.of("javax.annotation", "Generated");

  private final String humanReadableFormat;

  JavaxPackage(String humanReadableFormat) {
    this.humanReadableFormat = humanReadableFormat;
  }

  /**
   * Parameterized type for {@code java.util.function.Consumer<T>}, if available.
   */
  public Optional<QualifiedName> generated() {
    return ifAvailable(GENERATED);
  }

  @Override
  public String toString() {
    return humanReadableFormat;
  }

  private static boolean hasType(Elements elements, QualifiedName type) {
    return elements.getTypeElement(type.toString()) != null;
  }

  private <T> Optional<T> ifAvailable(T value) {
    return (this == AVAILABLE) ? Optional.of(value) : Optional.<T>absent();
  }
}
