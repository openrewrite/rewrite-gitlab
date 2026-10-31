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

class MigrateToRulesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateToRules());
    }

    @DocumentExample
    @Test
    void migrateSimpleBranchRef() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                script: make build
                only:
                  - main
              """,
            """
              build_job:
                script: make build
                rules:
                  - if: $CI_COMMIT_BRANCH == 'main'
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateMultipleRefs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              deploy_job:
                script: make deploy
                only:
                  - main
                  - tags
                  - merge_requests
              """,
            """
              deploy_job:
                script: make deploy
                rules:
                  - if: $CI_COMMIT_BRANCH == 'main'
                  - if: $CI_COMMIT_TAG
                  - if: $CI_PIPELINE_SOURCE == 'merge_request_event'
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateKeywordRefs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              test_job:
                script: make test
                only:
                  - branches
                  - tags
              """,
            """
              test_job:
                script: make test
                rules:
                  - if: $CI_COMMIT_BRANCH
                  - if: $CI_COMMIT_TAG
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateRegexRef() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              release_job:
                script: make release
                only:
                  - /^release-.*$/
              """,
            """
              release_job:
                script: make release
                rules:
                  - if: $CI_COMMIT_BRANCH =~ /^release-.*$/
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateProjectQualifiedRefs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              deploy_job:
                script: make deploy
                only:
                  - main@group/project
                  - /^release-.*$/@group/subgroup/project
                  - tags@group/project
              """,
            """
              deploy_job:
                script: make deploy
                rules:
                  - if: $CI_COMMIT_BRANCH == 'main' && $CI_PROJECT_PATH == 'group/project'
                  - if: $CI_COMMIT_BRANCH =~ /^release-.*$/ && $CI_PROJECT_PATH == 'group/subgroup/project'
                  - if: $CI_COMMIT_TAG && $CI_PROJECT_PATH == 'group/project'
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateRegexRefContainingAt() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              release_job:
                script: make release
                only:
                  - /^user@host$/
              """,
            """
              release_job:
                script: make release
                rules:
                  - if: $CI_COMMIT_BRANCH =~ /^user@host$/
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migratePipelineSourceRefs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              scheduled_job:
                script: make scheduled
                only:
                  - schedules
                  - web
                  - api
              """,
            """
              scheduled_job:
                script: make scheduled
                rules:
                  - if: $CI_PIPELINE_SOURCE == 'schedule'
                  - if: $CI_PIPELINE_SOURCE == 'web'
                  - if: $CI_PIPELINE_SOURCE == 'api'
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

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
    void migrateCombinedOnlyAndExcept() {
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
            """
              build_job:
                script: make build
                rules:
                  - if: $CI_COMMIT_BRANCH == 'main'
                    when: never
                  - if: $CI_COMMIT_BRANCH
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
    void skipComplexOnlyForm() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                script: make build
                only:
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
    void skipComplexExceptForm() {
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
    void multipleJobs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                script: make build
                only:
                  - main
              test_job:
                script: make test
                only:
                  - tags
              """,
            """
              build_job:
                script: make build
                rules:
                  - if: $CI_COMMIT_BRANCH == 'main'
              test_job:
                script: make test
                rules:
                  - if: $CI_COMMIT_TAG
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateFourSpaceIndentOnly() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                  script: make build
                  only:
                      - main
                      - tags
              """,
            """
              build_job:
                  script: make build
                  rules:
                      - if: $CI_COMMIT_BRANCH == 'main'
                      - if: $CI_COMMIT_TAG
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateFourSpaceIndentExcept() {
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
    void migrateFourSpaceIndentCombined() {
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
            """
              build_job:
                  script: make build
                  rules:
                      - if: $CI_COMMIT_BRANCH == 'main'
                        when: never
                      - if: $CI_COMMIT_BRANCH
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void skipAnchoredOnly() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                script: make build
                only: &main_only
                  - main
              test_job:
                script: make test
                only: *main_only
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void skipAnchoredExceptRef() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                script: make build
                except:
                  - &release_branch release
              variables:
                RELEASE_BRANCH: *release_branch
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void skipExceptCombinedWithExtendedOnly() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              .deploy:
                script: make deploy
                only:
                  - main
              deploy_job:
                extends: .deploy
                except:
                  - tags
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void skipExceptCombinedWithMergedOnly() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              .deploy: &deploy
                script: make deploy
                only:
                  - main
              deploy_job:
                <<: *deploy
                except:
                  - tags
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void skipOnlyCombinedWithIndirectlyExtendedExcept() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              .base:
                except:
                  - tags
              .deploy:
                extends: .base
                script: make deploy
              deploy_job:
                extends: [.deploy]
                only:
                  - main
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void skipWhenExtendingTemplateFromInclude() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              include:
                - remote: https://example.com/templates.yml
              deploy_job:
                extends: .remote_deploy
                only:
                  - main
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void skipConditionsCombinedFromSiblingTemplates() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              .main_only:
                only:
                  - main
              .no_tags:
                except:
                  - tags
              deploy_job:
                extends: [.main_only, .no_tags]
                script: make deploy
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void skipConditionsCombinedThroughNestedMerge() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              .templates:
                main_only: &main_only
                  only:
                    - main
                deploy: &deploy
                  <<: *main_only
                  except:
                    - tags
              deploy_job:
                <<: *deploy
                script: make deploy
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void skipLocalTemplateWhenAlsoExtendingTemplateFromInclude() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              include:
                - remote: https://example.com/templates.yml
              .main_only:
                only:
                  - main
              deploy_job:
                extends: [.main_only, .remote_deploy]
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateExternalAndChatRefs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              external_job:
                script: make external
                only:
                  - external
                  - external_pull_requests
                  - chat
              """,
            """
              external_job:
                script: make external
                rules:
                  - if: $CI_PIPELINE_SOURCE == 'external'
                  - if: $CI_PIPELINE_SOURCE == 'external_pull_request_event'
                  - if: $CI_PIPELINE_SOURCE == 'chat'
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateJobExtendingTemplateWithoutConditions() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              .defaults: &defaults
                image: alpine
              .deploy:
                script: make deploy
              deploy_job:
                <<: *defaults
                extends: .deploy
                only:
                  - main
              """,
            """
              .defaults: &defaults
                image: alpine
              .deploy:
                script: make deploy
              deploy_job:
                <<: *defaults
                extends: .deploy
                rules:
                  - if: $CI_COMMIT_BRANCH == 'main'
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateTemplateExtendedByJobWithoutConditions() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              .deploy:
                only:
                  - main
              deploy_job:
                extends: .deploy
                script: make deploy
              """,
            """
              .deploy:
                rules:
                  - if: $CI_COMMIT_BRANCH == 'main'
              deploy_job:
                extends: .deploy
                script: make deploy
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
                only:
                  - main
              """,
            source -> source.path("other-config.yml")
          )
        );
    }
}
