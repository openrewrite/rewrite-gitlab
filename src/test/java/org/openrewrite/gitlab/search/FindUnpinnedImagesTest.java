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
package org.openrewrite.gitlab.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class FindUnpinnedImagesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindUnpinnedImages());
    }

    @DocumentExample
    @Test
    void findLatestAndUntaggedImages() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build:
                image: maven:latest
                script: mvn verify
              test:
                image: node
                script: npm test
              """,
            """
              build:
                image: ~~>maven:latest
                script: mvn verify
              test:
                image: ~~>node
                script: npm test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void ignorePinnedImages() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build:
                image: maven:3.9-eclipse-temurin-21
                script: mvn verify
              test:
                image: node@sha256:0d1ef1fa2ffb4c4a3cd8a5fb3a1a8a2b5bd0ac0f7da2c4f0e0b1c62a9e5f1d3a
                script: npm test
              lint:
                image: registry.example.com:5000/tools/linter:1.2.3
                script: lint
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void findImageNameInMapping() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build:
                image:
                  name: registry.example.com:5000/tools/builder
                  entrypoint: [""]
                script: build
              """,
            """
              build:
                image:
                  name: ~~>registry.example.com:5000/tools/builder
                  entrypoint: [""]
                script: build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void findServices() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              test:
                services:
                  - postgres
                  - redis:7.2
                  - name: mysql:latest
                    alias: db
                script: test
              """,
            """
              test:
                services:
                  - ~~>postgres
                  - redis:7.2
                  - name: ~~>mysql:latest
                    alias: db
                script: test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void findInDefaultAndGlobalImage() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              image: ruby
              default:
                image: python:latest
              """,
            """
              image: ~~>ruby
              default:
                image: ~~>python:latest
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void ignoreImagesWhoseTagMayComeFromVariable() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build:
                image: $CI_REGISTRY_IMAGE
                script: build
              test:
                image: $CI_REGISTRY/group/app:$CI_COMMIT_SHA
                script: test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void ignoreKeysNamedImageOutsideJobs() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              variables:
                image: node
              build:
                variables:
                  image: node
                script: build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void ignoreOtherFiles() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build:
                image: node
              """,
            source -> source.path("docker-compose.yml")
          )
        );
    }
}
