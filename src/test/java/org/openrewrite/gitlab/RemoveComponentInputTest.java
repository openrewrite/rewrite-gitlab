/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.gitlab;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class RemoveComponentInputTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RemoveComponentInput("$CI_SERVER_FQDN/group/maven-build", null, "java_version"));
    }

    @DocumentExample
    @Test
    void removeInput() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: 17
                    stage: build
                - component: $CI_SERVER_FQDN/group/gradle-build@1
                  inputs:
                    java_version: 17
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    stage: build
                - component: $CI_SERVER_FQDN/group/gradle-build@1
                  inputs:
                    java_version: 17
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void removeEmptiedInputs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: 17
                - inputs:
                    java_version: 17
                  component: $CI_SERVER_FQDN/group/maven-build@2
              stages:
                - build
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                - component: $CI_SERVER_FQDN/group/maven-build@2
              stages:
                - build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void matchVersionPattern() {
        rewriteRun(
          spec -> spec.recipe(new RemoveComponentInput("$CI_SERVER_FQDN/group/maven-build", "1.x", "java_version")),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1.2.0
                  inputs:
                    java_version: 17
                    job_name: build-1
                - component: $CI_SERVER_FQDN/group/maven-build@2.0.0
                  inputs:
                    java_version: 17
                    job_name: build-2
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1.2.0
                  inputs:
                    job_name: build-1
                - component: $CI_SERVER_FQDN/group/maven-build@2.0.0
                  inputs:
                    java_version: 17
                    job_name: build-2
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void everyComponent() {
        rewriteRun(
          spec -> spec.recipe(new RemoveComponentInput(null, null, "java_version")),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: 17
                    stage: build
                - component: $CI_SERVER_FQDN/group/gradle-build@1
                  inputs:
                    stage: build
                    java_version: 17
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    stage: build
                - component: $CI_SERVER_FQDN/group/gradle-build@1
                  inputs:
                    stage: build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void removeAliasedValue() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              variables:
                JAVA_VERSION: &java_version 17
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: *java_version
                    stage: build
              """,
            """
              variables:
                JAVA_VERSION: &java_version 17
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    stage: build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void removeUnusedAnchor() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: &java_version 17
                    stage: build
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    stage: build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void anchoredValueAliasedElsewhere() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build@1
                      inputs:
                        java_version: &java_version 17
                  variables:
                    JAVA_VERSION: *java_version
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void aliasedInputs() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build@1
                      inputs: &build_inputs
                        java_version: 17
                    - component: $CI_SERVER_FQDN/group/gradle-build@1
                      inputs: *build_inputs
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void inputNotSet() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build@1
                      inputs:
                        stage: build
                    - component: $CI_SERVER_FQDN/group/maven-build@2
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void otherComponent() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build-extra@1
                      inputs:
                        java_version: 17
                    - local: maven-build.yml
                      inputs:
                        java_version: 17
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void otherFile() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build@1
                      inputs:
                        java_version: 17
                  """,
                source -> source.path("other.yml")
              )
            );
        }
    }
}
