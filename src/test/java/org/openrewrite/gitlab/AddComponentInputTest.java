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

class AddComponentInputTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddComponentInput("$CI_SERVER_FQDN/group/maven-build", null, "java_version", "21"));
    }

    @DocumentExample
    @Test
    void addToExistingInputs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    stage: build
                - component: $CI_SERVER_FQDN/group/renovate@1
                  inputs:
                    schedule: weekly
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    stage: build
                    java_version: 21
                - component: $CI_SERVER_FQDN/group/renovate@1
                  inputs:
                    schedule: weekly
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void addInputsWhereNoneExist() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              include:
                - local: build.yml
                - component: $CI_SERVER_FQDN/group/maven-build@1
              stages:
                - build
              """,
            """
              include:
                - local: build.yml
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: 21
              stages:
                - build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void matchSiblingIndentation() {
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
            """
              include:
              - component: $CI_SERVER_FQDN/group/maven-build@1
                inputs:
                    stage: build
                    java_version: 21
              - component: $CI_SERVER_FQDN/group/maven-build@2
                inputs:
                  java_version: 21
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void addStringValue() {
        rewriteRun(
          spec -> spec.recipe(new AddComponentInput(
            "$CI_SERVER_FQDN/group/maven-build", null, "image", "eclipse-temurin:21")),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    stage: build
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    stage: build
                    image: eclipse-temurin:21
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void matchVersionPattern() {
        rewriteRun(
          spec -> spec.recipe(new AddComponentInput("$CI_SERVER_FQDN/group/maven-build", "~1", "java_version", "21")),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1.2.0
                  inputs:
                    job_name: build-1
                - component: $CI_SERVER_FQDN/group/maven-build@2.0.0
                  inputs:
                    job_name: build-2
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1.2.0
                  inputs:
                    job_name: build-1
                    java_version: 21
                - component: $CI_SERVER_FQDN/group/maven-build@2.0.0
                  inputs:
                    job_name: build-2
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void keepAnchorOnSiblingInput() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    stage: &stage build
              variables:
                STAGE: *stage
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    stage: &stage build
                    java_version: 21
              variables:
                STAGE: *stage
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void inputAlreadySet() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build@1
                      inputs:
                        java_version: 17
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void aliasedValueAlreadySet() {
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
                        stage: build
                    - component: $CI_SERVER_FQDN/group/gradle-build@1
                      inputs: *build_inputs
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void inputsFromMergeKey() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  .inputs: &build_inputs
                    java_version: 17
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build@1
                      inputs:
                        <<: *build_inputs
                        stage: build
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
                    - local: maven-build.yml
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
                  """,
                source -> source.path("other.yml")
              )
            );
        }
    }
}
