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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.gitlab.table.GitLabIncludes;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.yaml.Assertions.yaml;

class FindIncludesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindIncludes());
    }

    @DocumentExample
    @Test
    void findComponentsWithVersionAndInputs() {
        rewriteRun(
          spec -> spec.dataTable(GitLabIncludes.Row.class, rows -> assertThat(rows).containsExactly(
            new GitLabIncludes.Row(".gitlab-ci.yml", "component", "$CI_SERVER_FQDN/group/renovate", "3",
              "java_version=21\nstage=build"),
            new GitLabIncludes.Row(".gitlab-ci.yml", "component", "$CI_SERVER_FQDN/group/sast", null, null)
          )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@3
                  inputs:
                    java_version: 21
                    stage: build
                - component: $CI_SERVER_FQDN/group/sast
              """,
            """
              include:
                - ~~>component: $CI_SERVER_FQDN/group/renovate@3
                  inputs:
                    java_version: 21
                    stage: build
                - ~~>component: $CI_SERVER_FQDN/group/sast
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void findOtherIncludeTypes() {
        rewriteRun(
          spec -> spec.dataTable(GitLabIncludes.Row.class, rows -> assertThat(rows).containsExactly(
            new GitLabIncludes.Row(".gitlab-ci.yml", "template", "Jobs/SAST.gitlab-ci.yml", null, null),
            new GitLabIncludes.Row(".gitlab-ci.yml", "remote", "https://example.com/ci/build.yml", null, null),
            new GitLabIncludes.Row(".gitlab-ci.yml", "project", "group/templates:/build.yml", "v1.2.0", null),
            new GitLabIncludes.Row(".gitlab-ci.yml", "project", "group/templates:/test.yml", "v1.2.0", null),
            new GitLabIncludes.Row(".gitlab-ci.yml", "local", "/ci/deploy.yml", null, "environment=production")
          )),
          //language=yaml
          yaml(
            """
              include:
                - template: Jobs/SAST.gitlab-ci.yml
                - remote: https://example.com/ci/build.yml
                - project: group/templates
                  ref: v1.2.0
                  file:
                    - /build.yml
                    - /test.yml
                - local: /ci/deploy.yml
                  inputs:
                    environment: production
              """,
            """
              include:
                - ~~>template: Jobs/SAST.gitlab-ci.yml
                - ~~>remote: https://example.com/ci/build.yml
                - ~~>project: group/templates
                  ref: v1.2.0
                  file:
                    - /build.yml
                    - /test.yml
                - ~~>local: /ci/deploy.yml
                  inputs:
                    environment: production
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void findListOfStrings() {
        rewriteRun(
          spec -> spec.dataTable(GitLabIncludes.Row.class, rows -> assertThat(rows).containsExactly(
            new GitLabIncludes.Row(".gitlab-ci.yml", "local", "/ci/build.yml", null, null),
            new GitLabIncludes.Row(".gitlab-ci.yml", "remote", "https://example.com/ci/test.yml", null, null)
          )),
          //language=yaml
          yaml(
            """
              include:
                - /ci/build.yml
                - https://example.com/ci/test.yml
              """,
            """
              include:
                - ~~>/ci/build.yml
                - ~~>https://example.com/ci/test.yml
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void findSingleString() {
        rewriteRun(
          spec -> spec.dataTable(GitLabIncludes.Row.class, rows -> assertThat(rows).containsExactly(
            new GitLabIncludes.Row(".gitlab-ci.yml", "local", ".defaults.yml", null, null)
          )),
          //language=yaml
          yaml(
            """
              include: .defaults.yml
              """,
            """
              include: ~~>.defaults.yml
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void findSingleMapping() {
        rewriteRun(
          spec -> spec.dataTable(GitLabIncludes.Row.class, rows -> assertThat(rows).containsExactly(
            new GitLabIncludes.Row(".gitlab-ci.yml", "project", "group/templates:/build.yml", null, null)
          )),
          //language=yaml
          yaml(
            """
              include:
                project: group/templates
                file: /build.yml
              """,
            """
              include:
                ~~>project: group/templates
                file: /build.yml
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void resolveAliasedInputs() {
        rewriteRun(
          spec -> spec.dataTable(GitLabIncludes.Row.class, rows -> assertThat(rows).containsExactly(
            new GitLabIncludes.Row(".gitlab-ci.yml", "component", "$CI_SERVER_FQDN/group/build", "2",
              "java_version=21\nstages=[build, test]"),
            new GitLabIncludes.Row(".gitlab-ci.yml", "component", "$CI_SERVER_FQDN/group/test", "2",
              "java_version=21\nstages=*stages")
          )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/build@2
                  inputs:
                    java_version: &java 21
                    stages: &stages [build, test]
                - component: $CI_SERVER_FQDN/group/test@2
                  inputs:
                    java_version: *java
                    stages: *stages
              """,
            """
              include:
                - ~~>component: $CI_SERVER_FQDN/group/build@2
                  inputs:
                    java_version: &java 21
                    stages: &stages [build, test]
                - ~~>component: $CI_SERVER_FQDN/group/test@2
                  inputs:
                    java_version: *java
                    stages: *stages
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void renderNonScalarInputsInFlowStyle() {
        rewriteRun(
          spec -> spec.dataTable(GitLabIncludes.Row.class, rows -> assertThat(rows).containsExactly(
            new GitLabIncludes.Row(".gitlab-ci.yml", "component", "$CI_SERVER_FQDN/group/deploy", "1.0.0",
              "environments=[staging, production]\nvariables={REGION: eu, DEBUG: false}")
          )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/deploy@1.0.0
                  inputs:
                    environments:
                      - staging
                      - production
                    variables:
                      REGION: eu
                      DEBUG: false
              """,
            """
              include:
                - ~~>component: $CI_SERVER_FQDN/group/deploy@1.0.0
                  inputs:
                    environments:
                      - staging
                      - production
                    variables:
                      REGION: eu
                      DEBUG: false
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void otherFile() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/renovate@3
                  """,
                source -> source.path("ci/build.yml")
              )
            );
        }

        @Test
        void childPipelineInclude() {
            //language=yaml
            rewriteRun(
              yaml(
                """
                  deploy:
                    trigger:
                      include:
                        - local: /ci/child.yml
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }
    }
}
