/*
 * Copyright 2016 Google Inc. All rights reserved.
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
package com.enigmabridge.ebuilder.processor.util.testing;

import com.enigmabridge.ebuilder.processor.util.QualifiedName;
import com.google.common.base.Joiner;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

public class InMemoryJavaFile extends InMemoryFile implements JavaFileObject {

  private final QualifiedName qualifiedName;
  private final Kind kind;

  public InMemoryJavaFile(QualifiedName qualifiedName, Kind kind) {
    super(name(qualifiedName));
    this.qualifiedName = qualifiedName;
    this.kind = kind;
  }

  @Override
  public Kind getKind() {
    return kind;
  }

  @Override
  public boolean isNameCompatible(String simpleName, Kind kind) {
    return qualifiedName.getSimpleName().equals(simpleName) && this.kind == kind;
  }

  @Override
  public NestingKind getNestingKind() {
    return qualifiedName.isTopLevel() ? NestingKind.TOP_LEVEL : NestingKind.MEMBER;
  }

  @Override
  public Modifier getAccessLevel() {
    return null;
  }

  private static String name(QualifiedName qualifiedName) {
    StringBuilder name = new StringBuilder();
    if (!qualifiedName.getPackage().isEmpty()) {
      name.append(qualifiedName.getPackage().replace('.', '/')).append('/');
    }
    Joiner.on('$').appendTo(name, qualifiedName.getSimpleNames());
    name.append(".java");
    return name.toString();
  }
}
