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

class OrderKeywordsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new OrderKeywords());
    }

    @DocumentExample
    @Test
    void orderGlobalAndJobKeywords() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build:
                script:
                  - make build
                image: gcc:14
                stage: build
                artifacts:
                  paths:
                    - out/

              stages:
                - build
              """,
            """
              stages:
                - build

              build:
                stage: build
                image: gcc:14
                script:
                  - make build
                artifacts:
                  paths:
                    - out/
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void globalKeywordsInRecommendedOrder() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              default:
                image: alpine
              workflow:
                rules:
                  - if: $CI_COMMIT_BRANCH
              variables:
                FOO: bar
              stages:
                - test
              include:
                - local: ci/common.yml
              test:
                script: echo test
              """,
            """
              include:
                - local: ci/common.yml
              stages:
                - test
              variables:
                FOO: bar
              workflow:
                rules:
                  - if: $CI_COMMIT_BRANCH
              default:
                image: alpine
              test:
                script: echo test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void orderDefaultSection() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              default:
                retry: 2
                image: ruby:3.3
                before_script:
                  - bundle install
              """,
            """
              default:
                image: ruby:3.3
                retry: 2
                before_script:
                  - bundle install
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void commentsMoveWithTheirKeyword() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              # Pipeline for the project

              build:
                # Compile everything
                script: make
                stage: build

              # Stages run in this order
              stages:
                - build
              """,
            """
              # Pipeline for the project

              # Stages run in this order
              stages:
                - build

              build:
                stage: build
                # Compile everything
                script: make
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void trailingCommentsStayOnTheirLine() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build: # Compile everything
                image: gcc:14
                script: make # Build all targets
                stage: build # Runs first
              test:
                script: make test
              """,
            """
              build: # Compile everything
                stage: build # Runs first
                image: gcc:14
                script: make # Build all targets
              test:
                script: make test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void trailingCommentStaysOnItsLineWithoutFinalNewline() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build:
                script: make # Build all targets
                stage: build # Runs first""",
            """
              build:
                stage: build # Runs first
                script: make # Build all targets""",
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void keepWindowsLineEndings() {
        rewriteRun(
          yaml(
            "build:\r\n  image: gcc:14\r\n  stage: build",
            "build:\r\n  stage: build\r\n  image: gcc:14",
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void blockScalarsKeepTheirLineBreak() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build:
                after_script: echo done
                script: |
                  make
                stage: build
              test:
                script:
                  - |
                    make test
                stage: test
                artifacts:
                  paths:
                    - report.xml
              """,
            """
              build:
                stage: build
                script: |
                  make
                after_script: echo done
              test:
                stage: test
                script:
                  - |
                    make test
                artifacts:
                  paths:
                    - report.xml
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void unknownKeysKeepRelativeOrderAfterKnownKeywords() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build:
                zeta: 1
                script: make
                alpha: 2
                stage: build
              """,
            """
              build:
                stage: build
                script: make
                zeta: 1
                alpha: 2
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void mergeKeyComesFirst() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              .defaults: &defaults
                image: alpine
              test:
                script: echo test
                <<: *defaults
              """,
            """
              .defaults: &defaults
                image: alpine
              test:
                <<: *defaults
                script: echo test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void aliasMayFollowItsAnchorWithinAJob() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build:
                script: &build make
                after_script: *build
                stage: build
              """,
            """
              build:
                stage: build
                script: &build make
                after_script: *build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void orderConfigurationAfterHeader() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              spec:
                inputs:
                  stage:
                    default: test
              ---
              test:
                script: echo test
                stage: $[[ inputs.stage ]]
              """,
            """
              spec:
                inputs:
                  stage:
                    default: test
              ---
              test:
                stage: $[[ inputs.stage ]]
                script: echo test
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void jobsKeepTheirOrder() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  test:
                    stage: test
                    script: make test
                  build:
                    stage: build
                    script: make build
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void deprecatedGlobalKeywordsAreNotJobs() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  cache:
                    key: $CI_COMMIT_REF_SLUG
                    when: always
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void flowMappings() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  build: {script: make, stage: build}
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void rootSequence() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  - script: make
                    stage: build
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void mergeKeyOverridesPrecedingKey() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  .defaults: &defaults
                    image: alpine
                  test:
                    image: ubuntu
                    script: echo test
                    <<: *defaults
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void nestedMergeKeyOverridesPrecedingKey() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  .base: &base
                    image: alpine
                  .defaults: &defaults
                    <<: *base
                    tags: [docker]
                  test:
                    image: ubuntu
                    <<: [*defaults]
                    script: echo test
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void aliasWouldPrecedeItsAnchor() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  .shared_variables: &shared_variables
                    FOO: bar
                  variables: *shared_variables
                  test:
                    script: echo test
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void variableNames() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  variables:
                    script: x
                    stage: y
                  test:
                    variables:
                      script: x
                      stage: y
                    script: echo test
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void otherYamlFiles() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  build:
                    script: make
                    stage: build
                  """,
                source -> source.path("config.yml")
              )
            );
        }
    }
}
