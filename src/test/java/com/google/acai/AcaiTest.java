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
import com.google.inject.Provides;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Retention;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.runners.MethodSorters.NAME_ASCENDING;

@RunWith(JUnit4.class)
@FixMethodOrder(NAME_ASCENDING)
public class AcaiTest {
  @Rule public Acai acai = new Acai(TestModule.class);

  @Inject @ExampleBindingAnnotation private String injectedValue;
  @Inject @IsInitialized private boolean isInitialized;

  @Before
  public void checkAcaiRunsBeforeTestSetup() {
    assertThat(injectedValue).isNotNull();
    assertThat(TestingServiceA.beforeSuiteCount).is(1);
  }

  @After
  public void noopAfter() {
    // Does nothing, exists purely to check Acai is not
    // negatively impacted by its presence.
  }

  @Test
  public void orderedTest1_injectionHappenedAfterBeforeSuiteExecuted() {
    assertThat(isInitialized).is(true);
  }

  @Test
  public void orderedTest2_testingServiceMethodsExecuted() {
    assertThat(TestingServiceA.beforeSuiteCount).is(1);
    assertThat(TestingServiceA.beforeTestCount).is(2);
    assertThat(TestingServiceA.afterTestCount).is(1);

    assertThat(TestingServiceB.beforeSuiteCount).is(1);
    assertThat(TestingServiceB.beforeTestCount).is(2);
    assertThat(TestingServiceB.afterTestCount).is(1);
  }

  @Test
  public void orderedTest3_canBeInjected() {
    assertThat(injectedValue).isEqualTo("injected-value");
  }


  private static class TestModule extends AbstractModule {
    @Override protected void configure() {
      bindConstant().annotatedWith(ExampleBindingAnnotation.class).to("injected-value");
      bind(InitializingTestingService.class).in(Singleton.class);
      install(new TestingServiceModule() {
        @Override protected void configureTestingServices() {
          bindTestingService(TestingServiceA.class);
          bindTestingService(TestingServiceB.class);
          bindTestingService(InitializingTestingService.class);
        }
      });
    }

    @Provides
    @IsInitialized
    private boolean provideIsInitialized(InitializingTestingService service) {
      return service.initialized;
    }
  }

  private static class TestingServiceA implements TestingService {
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

  private static class TestingServiceB implements TestingService {
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

  private static class InitializingTestingService implements TestingService {
    boolean initialized = false;

    @BeforeSuite public void incrementBeforeSuiteCount() {
      initialized = true;
    }
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  private @interface ExampleBindingAnnotation {
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  private @interface IsInitialized {
  }
}
