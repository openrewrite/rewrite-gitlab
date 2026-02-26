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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class MigrateExceptToRulesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateExceptToRules());
    }

    @DocumentExample
    @Test
    void migrateSimpleExcept() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                script: make build
                except:
                  - tags
              """,
            """
              build_job:
                script: make build
                rules:
                  - if: $CI_COMMIT_TAG
                    when: never
                  - when: always
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateMultipleExceptRefs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              deploy_job:
                script: make deploy
                except:
                  - schedules
                  - triggers
              """,
            """
              deploy_job:
                script: make deploy
                rules:
                  - if: $CI_PIPELINE_SOURCE == 'schedule'
                    when: never
                  - if: $CI_PIPELINE_SOURCE == 'trigger'
                    when: never
                  - when: always
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateExceptBranchName() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              test_job:
                script: make test
                except:
                  - main
              """,
            """
              test_job:
                script: make test
                rules:
                  - if: $CI_COMMIT_BRANCH == 'main'
                    when: never
                  - when: always
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void noopWhenRulesAlreadyPresent() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                script: make build
                rules:
                  - if: $CI_COMMIT_BRANCH == "main"
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void noopWhenBothOnlyAndExcept() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                script: make build
                only:
                  - branches
                except:
                  - main
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void noopWhenNotPresent() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                script: make build
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void skipComplexObjectForm() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                script: make build
                except:
                  refs:
                    - main
                  variables:
                    - $DEPLOY
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void noopForNonGitlabCiFiles() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                script: make build
                except:
                  - tags
              """,
            source -> source.path("other-config.yml")
          )
        );
    }
}
