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
    void migrateObjectFormRefs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              deploy_job:
                script: make deploy
                only:
                  refs: [main@group/project, /^release-.*$/, merge_requests]
              """,
            """
              deploy_job:
                script: make deploy
                rules:
                  - if: $CI_COMMIT_BRANCH == 'main' && $CI_PROJECT_PATH == 'group/project'
                  - if: $CI_COMMIT_BRANCH =~ /^release-.*$/
                  - if: $CI_PIPELINE_SOURCE == 'merge_request_event'
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateObjectFormVariablesWithDefaultRefs() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              deploy_job:
                script: make deploy
                only:
                  variables:
                    - $RELEASE == "true"
                    - $STAGING
              """,
            """
              deploy_job:
                script: make deploy
                rules:
                  - if: $CI_COMMIT_BRANCH && ($RELEASE == "true" || $STAGING)
                  - if: $CI_COMMIT_TAG && ($RELEASE == "true" || $STAGING)
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateObjectFormRefsAndVariables() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              deploy_job:
                script: make deploy
                only:
                  refs:
                    - main@group/project
                    - schedules
                  variables:
                    - '$CI_COMMIT_MESSAGE =~ /run-e2e/'
              """,
            """
              deploy_job:
                script: make deploy
                rules:
                  - if: $CI_COMMIT_BRANCH == 'main' && $CI_PROJECT_PATH == 'group/project' && $CI_COMMIT_MESSAGE =~ /run-e2e/
                  - if: $CI_PIPELINE_SOURCE == 'schedule' && $CI_COMMIT_MESSAGE =~ /run-e2e/
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateObjectFormKubernetes() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              deploy_job:
                script: make deploy
                only:
                  refs: [main]
                  kubernetes: active
              review_job:
                script: make review
                only:
                  kubernetes: active
              """,
            """
              deploy_job:
                script: make deploy
                rules:
                  - if: $CI_COMMIT_BRANCH == 'main' && $CI_KUBERNETES_ACTIVE
              review_job:
                script: make review
                rules:
                  - if: $CI_COMMIT_BRANCH && $CI_KUBERNETES_ACTIVE
                  - if: $CI_COMMIT_TAG && $CI_KUBERNETES_ACTIVE
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateObjectFormExcept() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              build_job:
                script: make build
                except:
                  refs: [tags, main@group/project]
                  variables:
                    - $SKIP_BUILD
                    - "!$CI_COMMIT_REF_PROTECTED"
                  kubernetes: active
              """,
            """
              build_job:
                script: make build
                rules:
                  - if: $CI_COMMIT_TAG
                    when: never
                  - if: $CI_COMMIT_BRANCH == 'main' && $CI_PROJECT_PATH == 'group/project'
                    when: never
                  - if: $SKIP_BUILD
                    when: never
                  - if: "!$CI_COMMIT_REF_PROTECTED"
                    when: never
                  - if: $CI_KUBERNETES_ACTIVE
                    when: never
                  - when: always
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void migrateObjectFormsOfOnlyAndExcept() {
        rewriteRun(
          //language=yaml
          yaml(
            """
              deploy_job:
                script: make deploy
                only:
                  refs: [main, merge_requests]
                  variables: [$RELEASE == "true"]
                except:
                  refs: [tags]
                  variables: [$SKIP]
              test_job:
                script: make test
                only:
                  refs: [branches]
                except:
                  - main
              """,
            """
              deploy_job:
                script: make deploy
                rules:
                  - if: $CI_COMMIT_TAG
                    when: never
                  - if: $SKIP
                    when: never
                  - if: $CI_COMMIT_BRANCH == 'main' && $RELEASE == "true"
                  - if: $CI_PIPELINE_SOURCE == 'merge_request_event' && $RELEASE == "true"
              test_job:
                script: make test
                rules:
                  - if: $CI_COMMIT_BRANCH == 'main'
                    when: never
                  - if: $CI_COMMIT_BRANCH
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void rulesAlreadyPresent() {
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
        void noOnlyOrExcept() {
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
        void unknownObjectFormKey() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  build_job:
                    script: make build
                    only:
                      refs: [main]
                      unknown: [value]
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void emptyObjectFormRefs() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  build_job:
                    script: make build
                    only:
                      refs: []
                      variables: [$DEPLOY]
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void kubernetesOtherThanActive() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  deploy_job:
                    script: make deploy
                    only:
                      kubernetes: inactive
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void aliasedObjectFormRefs() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  .refs:
                    only: &refs
                      - main
                  build_job:
                    script: make build
                    only:
                      refs: *refs
                      variables: [$DEPLOY]
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void variablesWithEscapes() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  build_job:
                    script: make build
                    only:
                      variables:
                        - "$CI_COMMIT_MESSAGE =~ /\\\\[deploy\\\\]/"
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void anchorInsideObjectForm() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  build_job:
                    script: make build
                    only:
                      refs: [main]
                      variables: &deploy_vars
                        - $DEPLOY
                  test_job:
                    script: make test
                    only:
                      variables: *deploy_vars
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void objectFormCombinedWithExtendedExcept() {
            rewriteRun(
              //language=yaml
              yaml(
                """
                  .no_tags:
                    except:
                      refs: [tags]
                  deploy_job:
                    extends: .no_tags
                    script: make deploy
                    only:
                      refs: [main]
                      variables: [$DEPLOY]
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void anchoredOnly() {
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
        void anchoredExceptRef() {
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
        void exceptCombinedWithExtendedOnly() {
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
        void exceptCombinedWithMergedOnly() {
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
        void onlyCombinedWithIndirectlyExtendedExcept() {
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
        void extendingTemplateFromInclude() {
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
        void conditionsCombinedFromSiblingTemplates() {
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
        void conditionsCombinedThroughNestedMerge() {
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
        void localTemplateAlsoExtendingTemplateFromInclude() {
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
        void otherYamlFiles() {
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
}
