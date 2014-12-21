/*
 * Copyright 2014 Google Inc. All rights reserved.
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

import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.inject.Inject;
import java.lang.annotation.Retention;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Functional test which uses Acai as clients do from a JUnit rule.
 */
@RunWith(JUnit4.class)
public class AcaiFunctionalTest {
  @Rule public Acai acai = new Acai(TestModule.class);

  @Inject @TestBindingAnnotation private String injectedValue;

  @Before
  public void checkAcaiRunsBeforeTestSetup() {
    assertThat(injectedValue).isNotNull();
    assertThat(Service.beforeSuiteCount).is(1);
  }

  @Test
  public void checkAcaiWorksAsJUnitRule() {
    assertThat(injectedValue).isEqualTo("injected-value");
    assertThat(Service.beforeSuiteCount).is(1);
    assertThat(Service.beforeTestCount).is(1);
    assertThat(Service.afterTestCount).is(0);
  }

  private static class TestModule extends AbstractModule {
    @Override protected void configure() {
      bindConstant().annotatedWith(TestBindingAnnotation.class).to("injected-value");
      install(new TestingServiceModule() {
        @Override protected void configureTestingServices() {
          bindTestingService(Service.class);
        }
      });
    }
  }

  private static class Service implements TestingService {
    static int beforeSuiteCount = 0;
    static int beforeTestCount = 0;
    static int afterTestCount = 0;

    @BeforeSuite public void incrementBeforeSuiteCount() {
      beforeSuiteCount++;
    }

    @BeforeTest public void incrementBeforeTestCount() {
      beforeTestCount++;
    }

    @AfterTest public void incrementAfterTestCount() {
      afterTestCount++;
    }
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  private @interface TestBindingAnnotation {
  }
}
