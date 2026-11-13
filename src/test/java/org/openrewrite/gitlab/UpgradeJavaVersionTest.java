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

class UpgradeJavaVersionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeJavaVersion(21));
    }

    @DocumentExample
    @Test
    void upgradeComponentInput() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: 17
                - component: $CI_SERVER_FQDN/group/renovate@2
              build:
                script: ./mvnw verify
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: 21
                - component: $CI_SERVER_FQDN/group/renovate@2
              build:
                script: ./mvnw verify
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void upgradeQuotedJavaVersionInput() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: "17"
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: "21"
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void upgradeAnchoredJavaVersionInputOnce() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: &java 17
                - component: $CI_SERVER_FQDN/group/gradle-build@1
                  inputs:
                    java_version: *java
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: &java 21
                - component: $CI_SERVER_FQDN/group/gradle-build@1
                  inputs:
                    java_version: *java
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void javaVersionInputSameOrHigher() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build@1
                      inputs:
                        java_version: 21
                    - component: $CI_SERVER_FQDN/group/gradle-build@1
                      inputs:
                        java_version: 25
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void javaVersionInputNotAMajor() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/maven-build@1
                      inputs:
                        java_version: 17.0.9
                    - component: $CI_SERVER_FQDN/group/gradle-build@1
                      inputs:
                        java_version: $JAVA_VERSION
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void images() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  image: eclipse-temurin:17
                  build:
                    image: maven:3-eclipse-temurin-17
                    services:
                      - eclipse-temurin:17-jre
                    script: mvn verify
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void componentWithoutJavaVersionInput() {
            //language=yaml
            rewriteRun(
              yaml(
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
    }
}
