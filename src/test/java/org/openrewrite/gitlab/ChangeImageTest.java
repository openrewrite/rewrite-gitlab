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

class ChangeImageTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ChangeImage("eclipse-temurin", "17*", null, "21-jdk"));
    }

    @DocumentExample
    @Test
    void changeTagInJobDefaultAndGlobalImage() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              image: eclipse-temurin:17
              default:
                image: eclipse-temurin:17-jdk
              build:
                image: eclipse-temurin:17-jdk-alpine
                script: ./mvnw verify
              test:
                image: eclipse-temurin:21-jdk
                script: ./mvnw test
              """,
            """
              image: eclipse-temurin:21-jdk
              default:
                image: eclipse-temurin:21-jdk
              build:
                image: eclipse-temurin:21-jdk
                script: ./mvnw verify
              test:
                image: eclipse-temurin:21-jdk
                script: ./mvnw test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void changeImageName() {
        rewriteRun(
          spec -> spec.recipe(new ChangeImage("openjdk", null, "eclipse-temurin", null)),
          //language=yaml
          yaml(
            """
              build:
                image: openjdk:17
                script: ./mvnw verify
              """,
            """
              build:
                image: eclipse-temurin:17
                script: ./mvnw verify
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void changeImageNameInMapping() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build:
                image:
                  name: eclipse-temurin:17-jdk
                  entrypoint: [""]
                script: ./mvnw verify
              test:
                image: { name: "eclipse-temurin:17", entrypoint: [""] }
                script: ./mvnw test
              """,
            """
              build:
                image:
                  name: eclipse-temurin:21-jdk
                  entrypoint: [""]
                script: ./mvnw verify
              test:
                image: { name: "eclipse-temurin:21-jdk", entrypoint: [""] }
                script: ./mvnw test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void changeServices() {
        rewriteRun(
          spec -> spec.recipe(new ChangeImage("postgres", null, null, "16")),
          //language=yaml
          yaml(
            """
              default:
                services:
                  - postgres:15
              test:
                services:
                  - redis:7.2
                  - name: postgres:15
                    alias: db
                script: test
              """,
            """
              default:
                services:
                  - postgres:16
              test:
                services:
                  - redis:7.2
                  - name: postgres:16
                    alias: db
                script: test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void changeServicesInFlowSequence() {
        rewriteRun(
          spec -> spec.recipe(new ChangeImage("postgres", null, null, "16")),
          //language=yaml
          yaml(
            """
              test:
                services: [postgres:15, redis:7.2]
                script: test
              """,
            """
              test:
                services: [postgres:16, redis:7.2]
                script: test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void changeAnchoredScalarOnce() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              .jdk: &jdk eclipse-temurin:17-jdk
              build:
                image: *jdk
                script: ./mvnw verify
              test:
                image: *jdk
                script: ./mvnw test
              """,
            """
              .jdk: &jdk eclipse-temurin:21-jdk
              build:
                image: *jdk
                script: ./mvnw verify
              test:
                image: *jdk
                script: ./mvnw test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void changeAnchoredImageOfHiddenJob() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              .defaults: &defaults
                image: &jdk eclipse-temurin:17-jdk
                tags: [docker]
              build:
                <<: *defaults
                script: ./mvnw verify
              test:
                image: *jdk
                script: ./mvnw test
              """,
            """
              .defaults: &defaults
                image: &jdk eclipse-temurin:21-jdk
                tags: [docker]
              build:
                <<: *defaults
                script: ./mvnw verify
              test:
                image: *jdk
                script: ./mvnw test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void changeAnchoredImageMapping() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              .jdk: &jdk
                name: eclipse-temurin:17-jdk
                entrypoint: [""]
              build:
                image: *jdk
                script: ./mvnw verify
              """,
            """
              .jdk: &jdk
                name: eclipse-temurin:21-jdk
                entrypoint: [""]
              build:
                image: *jdk
                script: ./mvnw verify
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void changeAnchorAlsoAliasedOutsideImages() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              .jdk: &jdk eclipse-temurin:17-jdk
              variables:
                BASE_IMAGE: *jdk
              build:
                image: *jdk
                script: docker build --build-arg BASE_IMAGE .
              """,
            """
              .jdk: &jdk eclipse-temurin:21-jdk
              variables:
                BASE_IMAGE: *jdk
              build:
                image: *jdk
                script: docker build --build-arg BASE_IMAGE .
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void changeAnchorDefinedOutsideImages() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              variables:
                BASE_IMAGE: &jdk eclipse-temurin:17-jdk
              .images:
                temurin: &temurin eclipse-temurin:17
              build:
                image: *jdk
                script: docker build --build-arg BASE_IMAGE .
              run:
                image: *temurin
                script: java -jar app.jar
              """,
            """
              variables:
                BASE_IMAGE: &jdk eclipse-temurin:21-jdk
              .images:
                temurin: &temurin eclipse-temurin:21-jdk
              build:
                image: *jdk
                script: docker build --build-arg BASE_IMAGE .
              run:
                image: *temurin
                script: java -jar app.jar
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void dropDigestWhenTagChanges() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build:
                image: eclipse-temurin:17-jdk@sha256:0d1ef1fa2ffb4c4a3cd8a5fb3a1a8a2b5bd0ac0f7da2c4f0e0b1c62a9e5f1d3a
                script: ./mvnw verify
              """,
            """
              build:
                image: eclipse-temurin:21-jdk
                script: ./mvnw verify
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void keepDigestWhenOnlyNameChanges() {
        rewriteRun(
          spec -> spec.recipe(new ChangeImage("eclipse-temurin", null, "registry.example.com/mirror/eclipse-temurin", null)),
          //language=yaml
          yaml(
            """
              build:
                image: eclipse-temurin:17-jdk@sha256:0d1ef1fa2ffb4c4a3cd8a5fb3a1a8a2b5bd0ac0f7da2c4f0e0b1c62a9e5f1d3a
                script: ./mvnw verify
              """,
            """
              build:
                image: registry.example.com/mirror/eclipse-temurin:17-jdk@sha256:0d1ef1fa2ffb4c4a3cd8a5fb3a1a8a2b5bd0ac0f7da2c4f0e0b1c62a9e5f1d3a
                script: ./mvnw verify
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void matchAnyRegistryWithGlob() {
        rewriteRun(
          spec -> spec.recipe(new ChangeImage("*/eclipse-temurin", "17*", null, "21-jdk")),
          //language=yaml
          yaml(
            """
              build:
                image: registry.example.com:5000/library/eclipse-temurin:17-jdk
                script: ./mvnw verify
              test:
                image: $CI_DEPENDENCY_PROXY_GROUP_IMAGE_PREFIX/eclipse-temurin:17
                script: ./mvnw test
              lint:
                image: eclipse-temurin:17
                script: ./mvnw checkstyle:check
              """,
            """
              build:
                image: registry.example.com:5000/library/eclipse-temurin:21-jdk
                script: ./mvnw verify
              test:
                image: $CI_DEPENDENCY_PROXY_GROUP_IMAGE_PREFIX/eclipse-temurin:21-jdk
                script: ./mvnw test
              lint:
                image: eclipse-temurin:17
                script: ./mvnw checkstyle:check
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void matchUntaggedImageAsLatest() {
        rewriteRun(
          spec -> spec.recipe(new ChangeImage("node", "latest", null, "22")),
          //language=yaml
          yaml(
            """
              build:
                image: node
                script: npm ci
              test:
                image: node:latest
                script: npm test
              """,
            """
              build:
                image: node:22
                script: npm ci
              test:
                image: node:22
                script: npm test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void otherTagOrRegistry() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  build:
                    image: eclipse-temurin:11-jdk
                    script: ./mvnw verify
                  test:
                    image: registry.example.com/eclipse-temurin:17-jdk
                    script: ./mvnw test
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void referencesWithVariables() {
            rewriteRun(
              spec -> spec.recipe(new ChangeImage("*", null, null, "21-jdk")),
              //language=yaml
              yaml(
                """
                  build:
                    image: $CI_REGISTRY_IMAGE
                    script: build
                  test:
                    image: eclipse-temurin:$JAVA_VERSION
                    script: test
                  lint:
                    image: $CI_REGISTRY/group/$TOOL:1.0
                    script: lint
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void keysNamedImageOutsideJobs() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  variables:
                    image: eclipse-temurin:17
                  build:
                    variables:
                      image: eclipse-temurin:17
                    script: build
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void compactFlowMappingParsedAsScalar() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  build:
                    image: {name: eclipse-temurin:17}
                    script: ./mvnw verify
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void otherFiles() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  build:
                    image: eclipse-temurin:17
                  """,
                source -> source.path("docker-compose.yml")
              )
            );
        }
    }
}
