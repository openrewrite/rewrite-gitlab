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
    void upgradeComponentInputAndImage() {
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
                image: eclipse-temurin:17-jdk
                script: ./mvnw verify
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/maven-build@1
                  inputs:
                    java_version: 21
                - component: $CI_SERVER_FQDN/group/renovate@2
              build:
                image: eclipse-temurin:21-jdk
                script: ./mvnw verify
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void upgradeImageFamilies() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              image: eclipse-temurin:17
              default:
                image: registry.example.com/library/eclipse-temurin:17-jdk-alpine
              corretto:
                image: amazoncorretto:17-alpine
                script: java -version
              maven:
                image: maven:3.9.6-eclipse-temurin-17
                script: mvn verify
              maven-corretto:
                image:
                  name: maven:3-amazoncorretto-17-debian
                script: mvn verify
              gradle:
                image: gradle:8.10-jdk17
                script: gradle build
              gradle-alpine:
                image: gradle:jdk11-alpine
                script: gradle build
              """,
            """
              image: eclipse-temurin:21
              default:
                image: registry.example.com/library/eclipse-temurin:21-jdk-alpine
              corretto:
                image: amazoncorretto:21-alpine
                script: java -version
              maven:
                image: maven:3.9.6-eclipse-temurin-21
                script: mvn verify
              maven-corretto:
                image:
                  name: maven:3-amazoncorretto-21-debian
                script: mvn verify
              gradle:
                image: gradle:8.10-jdk21
                script: gradle build
              gradle-alpine:
                image: gradle:jdk21-alpine
                script: gradle build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void upgradeAnchoredImageOnce() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              .jdk: &jdk maven:3-eclipse-temurin-17
              build:
                image: *jdk
                script: mvn verify
              test:
                image: *jdk
                script: mvn test
              """,
            """
              .jdk: &jdk maven:3-eclipse-temurin-21
              build:
                image: *jdk
                script: mvn verify
              test:
                image: *jdk
                script: mvn test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void alreadyOnSameOrHigherVersion() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  build:
                    image: eclipse-temurin:21-jdk
                    script: ./mvnw verify
                  test:
                    image: maven:3-eclipse-temurin-25
                    script: mvn test
                  lint:
                    image: gradle:jdk25
                    script: gradle check
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void fullyPinnedJavaVersion() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  build:
                    image: eclipse-temurin:17.0.9_9-jdk
                    script: ./mvnw verify
                  test:
                    image: amazoncorretto:17.0.9
                    script: ./mvnw test
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void tagsWithoutJavaVersion() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  build:
                    image: maven:3.9.6
                    script: mvn verify
                  test:
                    image: eclipse-temurin
                    script: ./mvnw test
                  lint:
                    image: node:18
                    script: npm run lint
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
