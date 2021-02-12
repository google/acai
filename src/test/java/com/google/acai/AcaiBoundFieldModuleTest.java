/*
 * Copyright 2021 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.acai;

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.testing.fieldbinder.Bind;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Qualifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;

@RunWith(JUnit4.class)
public class AcaiBoundFieldModuleTest {

  private static final String DEFAULT = "default";
  private static final String SPECIAL = "special";

  public static class TestEnv extends AbstractModule {
    @Override
    protected void configure() {
      install(AcaiBoundFieldModule.of(Provided.class));
    }
  }

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface MyAnnotation {}

  static class Provided {
    @Bind(lazy = true)
    @MyAnnotation
    String value = DEFAULT;
  }

  private static class Target {
    @Inject Provided provided;
    @Inject @MyAnnotation Provider<String> valueProvider;
  }

  @Test
  public void bindingsAreTestScoped() throws Throwable {
    // Creating the rule in here since we want to test the scoping.
    Acai acai = new Acai(TestEnv.class);
    Target target = new Target();

    acai.apply(
            new Statement() {
              @Override
              public void evaluate() {
                assertThat(target.valueProvider.get()).isEqualTo(DEFAULT);
                target.provided.value = SPECIAL;
                assertThat(target.valueProvider.get()).isEqualTo(SPECIAL);
              }
            },
            null,
            target)
        .evaluate();

    acai.apply(
            new Statement() {
              @Override
              public void evaluate() {
                assertThat(target.valueProvider.get()).isEqualTo(DEFAULT);
              }
            },
            null,
            target)
        .evaluate();
  }
}
