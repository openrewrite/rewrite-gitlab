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
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class ChangeComponentInputTest implements RewriteTest {

    @DocumentExample
    @Test
    void changeValue() {
        rewriteRun(
          spec -> spec.recipe(new ChangeComponentInput(
            "$CI_SERVER_FQDN/group/maven-build", null, "java_version", null, "21")),
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
                    java_version: 21
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
    void changeStringValue() {
        rewriteRun(
          spec -> spec.recipe(new ChangeComponentInput(
            "$CI_SERVER_FQDN/group/maven-build", null, "image", null, "eclipse-temurin:21")),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    image: "eclipse-temurin:17"
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    image: "eclipse-temurin:21"
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void matchVersionPattern() {
        rewriteRun(
          spec -> spec.recipe(new ChangeComponentInput(
            "$CI_SERVER_FQDN/group/maven-build", "1.x", "java_version", null, "21")),
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
                    java_version: 21
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
    void everyComponentThatTakesTheInput() {
        rewriteRun(
          spec -> spec.recipe(new ChangeComponentInput(null, null, "java_version", null, "21")),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: 17
                - component: $CI_SERVER_FQDN/group/gradle-build@2
                  inputs:
                    java_version: 17
                - component: $CI_SERVER_FQDN/group/renovate@1
                  inputs:
                    schedule: weekly
                - component: $CI_SERVER_FQDN/group/sast@1
                - local: java_version.yml
                  inputs:
                    java_version: 17
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: 21
                - component: $CI_SERVER_FQDN/group/gradle-build@2
                  inputs:
                    java_version: 21
                - component: $CI_SERVER_FQDN/group/renovate@1
                  inputs:
                    schedule: weekly
                - component: $CI_SERVER_FQDN/group/sast@1
                - local: java_version.yml
                  inputs:
                    java_version: 17
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void renameInput() {
        rewriteRun(
          spec -> spec.recipe(new ChangeComponentInput(
            "$CI_SERVER_FQDN/group/maven-build", null, "java_version", "jdk_version", null)),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: 17
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    jdk_version: 17
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void renameAndChangeValue() {
        rewriteRun(
          spec -> spec.recipe(new ChangeComponentInput(
            "$CI_SERVER_FQDN/group/maven-build", null, "java_version", "jdk_version", "21")),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: 17
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    jdk_version: 21
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void keepAnchorOnChangedValue() {
        rewriteRun(
          spec -> spec.recipe(new ChangeComponentInput(
            "$CI_SERVER_FQDN/group/maven-build", null, "java_version", null, "21")),
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
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: &java_version 21
              variables:
                JAVA_VERSION: *java_version
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void renameAliasedInput() {
        rewriteRun(
          spec -> spec.recipe(new ChangeComponentInput(
            "$CI_SERVER_FQDN/group/maven-build", null, "java_version", "jdk_version", null)),
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
            """
              variables:
                JAVA_VERSION: &java_version 17
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    jdk_version: *java_version
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void inputNotSet() {
            rewriteRun(
              spec -> spec.recipe(new ChangeComponentInput(
                "$CI_SERVER_FQDN/group/maven-build", null, "java_version", null, "21")),
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
              spec -> spec.recipe(new ChangeComponentInput(
                "$CI_SERVER_FQDN/group/maven-build", null, "java_version", null, "21")),
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build-extra@1
                      inputs:
                        java_version: 17
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void otherVersion() {
            rewriteRun(
              spec -> spec.recipe(new ChangeComponentInput(
                "$CI_SERVER_FQDN/group/maven-build", "~1", "java_version", null, "21")),
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build@2.0.0
                      inputs:
                        java_version: 17
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void sameValue() {
            rewriteRun(
              spec -> spec.recipe(new ChangeComponentInput(
                "$CI_SERVER_FQDN/group/maven-build", null, "java_version", null, "21")),
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build@1
                      inputs:
                        java_version: 21
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void aliasedValue() {
            rewriteRun(
              spec -> spec.recipe(new ChangeComponentInput(
                "$CI_SERVER_FQDN/group/maven-build", null, "java_version", null, "21")),
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
              spec -> spec.recipe(new ChangeComponentInput(
                "$CI_SERVER_FQDN/group/maven-build", null, "java_version", null, "21")),
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
        void newNameAlreadySet() {
            rewriteRun(
              spec -> spec.recipe(new ChangeComponentInput(
                "$CI_SERVER_FQDN/group/maven-build", null, "java_version", "jdk_version", null)),
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build@1
                      inputs:
                        java_version: 17
                        jdk_version: 21
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void otherFile() {
            rewriteRun(
              spec -> spec.recipe(new ChangeComponentInput(null, null, "java_version", null, "21")),
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
