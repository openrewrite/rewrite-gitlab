/*
 * Copyright 2025 the original author or authors.
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

class AddTimeoutTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddTimeout("1 hour", null, null));
    }

    @DocumentExample
    @Test
    void addToDefault() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              """,
            """
              default:
                timeout: 1 hour
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void mergeIntoExistingDefault() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              default:
                image: ruby:3.0
              """,
            """
              default:
                image: ruby:3.0
                timeout: 1 hour
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void addToSpecificJob() {
        //language=yaml
        rewriteRun(
          spec ->
            spec.recipe(new AddTimeout("30 minutes", "build_job", null)),
          yaml(
            """
              build_job:
                script: make build
              """,
            """
              build_job:
                timeout: 30 minutes
                script: make build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void addDefaultBeforeJobs() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              stages:
                - build

              build_job:
                stage: build
                script: make build
              """,
            """
              stages:
                - build
              default:
                timeout: 1 hour

              build_job:
                stage: build
                script: make build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void addToJobAfterKeywordsThatComeBefore() {
        //language=yaml
        rewriteRun(
          spec ->
            spec.recipe(new AddTimeout("30 minutes", "build_job", null)),
          yaml(
            """
              .defaults: &defaults
                image: ruby:3.0

              build_job:
                <<: *defaults
                stage: build
                script: make build
                artifacts:
                  paths:
                    - build/
              """,
            """
              .defaults: &defaults
                image: ruby:3.0

              build_job:
                <<: *defaults
                stage: build
                timeout: 30 minutes
                script: make build
                artifacts:
                  paths:
                    - build/
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void addToHiddenJob() {
        //language=yaml
        rewriteRun(
          spec ->
            spec.recipe(new AddTimeout("30 minutes", ".build_template", null)),
          yaml(
            """
              .build_template:
                script: make build
              """,
            """
              .build_template:
                timeout: 30 minutes
                script: make build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void addToJobAfterSpecHeader() {
        //language=yaml
        rewriteRun(
          spec ->
            spec.recipe(new AddTimeout("30 minutes", "build_job", null)),
          yaml(
            """
              spec:
                inputs:
                  stage:
                    default: build
              ---
              build_job:
                script: make build
              """,
            """
              spec:
                inputs:
                  stage:
                    default: build
              ---
              build_job:
                timeout: 30 minutes
                script: make build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void addDefaultAfterSpecHeader() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              spec:
                inputs:
                  stage:
                    default: build
              ---
              build_job:
                script: make build
              """,
            """
              spec:
                inputs:
                  stage:
                    default: build
              ---
              default:
                timeout: 1 hour
              build_job:
                script: make build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void appendAfterBlockScalar() {
        //language=yaml
        rewriteRun(
          spec ->
            spec.recipe(new AddTimeout("30 minutes", "build_job", null)),
          yaml(
            """
              build_job:
                rules:
                  - if: |
                      $CI_COMMIT_BRANCH == "main"
                script: make build
              """,
            """
              build_job:
                rules:
                  - if: |
                      $CI_COMMIT_BRANCH == "main"
                script: make build
                timeout: 30 minutes
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void noopWhenAlreadyPresent() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  default:
                    timeout: 1 hour
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void noopWhenJobMissing() {
            //language=yaml
            rewriteRun(
              spec ->
                spec.recipe(new AddTimeout("30 minutes", "deploy_job", null)),
              yaml(
                """
                  stages:
                    - build

                  build_job:
                    script: make build
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void noopWhenJobOnlyInSpecHeader() {
            //language=yaml
            rewriteRun(
              spec ->
                spec.recipe(new AddTimeout("30 minutes", "spec", null)),
              yaml(
                """
                  spec:
                    inputs:
                      stage:
                        default: build
                  ---
                  build_job:
                    script: make build
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void noopWhenJobIsAlias() {
            //language=yaml
            rewriteRun(
              spec ->
                spec.recipe(new AddTimeout("30 minutes", "test_job", null)),
              yaml(
                """
                  build_job: &build
                    script: make build
                  test_job: *build
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void noopWhenJobIsEmpty() {
            //language=yaml
            rewriteRun(
              spec ->
                spec.recipe(new AddTimeout("30 minutes", "build_job", null)),
              yaml(
                """
                  build_job:
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }
    }
}
