/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class ChangeComponentTest implements RewriteTest {
    @DocumentExample
    @Test
    void updateComponent() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "$CI_SERVER_FQDN/group/renovate",
              "0.10.0",
              "$CI_SERVER_FQDN/group/dependency-updates",
              "1.0.0"
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@0.10.0
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/dependency-updates@1.0.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void updateComponentVersion() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "some-component",
              "1.0",
              null,
              "2.0"
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: some-component@1.0
              """,
            """
              include:
                - component: some-component@2.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void keepVersionWhenOnlyPathChanges() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "$CI_SERVER_FQDN/group/renovate",
              null,
              "$CI_SERVER_FQDN/group/dependency-updates",
              null
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@1.2.3
                - component: $CI_SERVER_FQDN/group/renovate@main
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/dependency-updates@1.2.3
                - component: $CI_SERVER_FQDN/group/dependency-updates@main
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void escapedComponentPath() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "\\$CI_SERVER_FQDN/components/opentofu/full-pipeline",
              "0.10.0",
              "$CI_SERVER_FQDN/components/opentofu/validate-plan-apply",
              "0.10.0"
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/components/opentofu/full-pipeline@0.10.0
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/components/opentofu/validate-plan-apply@0.10.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void anyOldVersion() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "$CI_SERVER_FQDN/group/renovate",
              null,
              null,
              "2.0.0"
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@e3b0c44298fc1c149afbf4c8996fb92427ae41e4
                - component: $CI_SERVER_FQDN/group/maven-build@1.2.3
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@2.0.0
                - component: $CI_SERVER_FQDN/group/maven-build@1.2.3
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void exactMajorVersion() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "$CI_SERVER_FQDN/group/renovate",
              "1",
              null,
              "2"
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@1
                - component: $CI_SERVER_FQDN/group/renovate@1.2.3
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@2
                - component: $CI_SERVER_FQDN/group/renovate@1.2.3
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void xRangeVersion() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "$CI_SERVER_FQDN/group/renovate",
              "1.x",
              null,
              "2.0.0"
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@1
                - component: $CI_SERVER_FQDN/group/renovate@2.1.0
                - component: $CI_SERVER_FQDN/group/renovate@main
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@2.0.0
                - component: $CI_SERVER_FQDN/group/renovate@2.1.0
                - component: $CI_SERVER_FQDN/group/renovate@main
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void tildeRangeVersion() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "$CI_SERVER_FQDN/group/renovate",
              "~1",
              null,
              "2.0.0"
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@1.2.3
                - component: $CI_SERVER_FQDN/group/renovate@2.1.0
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@2.0.0
                - component: $CI_SERVER_FQDN/group/renovate@2.1.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void matchedOldVersion() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "some-component",
              "1.+",
              "other-component",
              "2.0"
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: some-component@1.0
              """,
            """
              include:
                - component: other-component@2.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void branchVersion() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "$CI_SERVER_FQDN/group/renovate",
              "main",
              null,
              "1.0.0"
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@main
                - component: $CI_SERVER_FQDN/group/renovate@maintenance
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/group/renovate@1.0.0
                - component: $CI_SERVER_FQDN/group/renovate@maintenance
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void latestVersion() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "$CI_SERVER_FQDN/group/renovate",
              "~latest",
              null,
              "1.0.0"
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: "$CI_SERVER_FQDN/group/renovate@~latest"
              """,
            """
              include:
                - component: "$CI_SERVER_FQDN/group/renovate@1.0.0"
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void mismatchedOldVersion() {
            rewriteRun(spec -> spec.recipe(
                new ChangeComponent(
                  "some-component",
                  "1.+",
                  "other-component",
                  "2.0"
                )),
              //language=yaml
              yaml(
                """
                  include:
                    - component: some-component@2.0
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void mismatchedComponent() {
            rewriteRun(spec -> spec.recipe(
                new ChangeComponent(
                  "some-component",
                  "1.+",
                  "other-component",
                  "2.0"
                )),
              //language=yaml
              yaml(
                """
                  include:
                    - component: other-component@1.0
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void pathIsNotARegex() {
            rewriteRun(spec -> spec.recipe(
                new ChangeComponent(
                  "$CI_SERVER_FQDN/group/renovate.ci",
                  null,
                  "$CI_SERVER_FQDN/group/dependency-updates",
                  null
                )),
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/renovate-ci@1.0.0
                    - component: $CI_SERVER_FQDN/group/renovate.ci/extra@1.0.0
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void versionAlreadyIncluded() {
            rewriteRun(spec -> spec.recipe(
                new ChangeComponent(
                  "$CI_SERVER_FQDN/group/renovate",
                  "1",
                  null,
                  "2"
                )),
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/renovate@1
                    - component: $CI_SERVER_FQDN/group/renovate@2
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void newPathAlreadyIncluded() {
            rewriteRun(spec -> spec.recipe(
                new ChangeComponent(
                  "$CI_SERVER_FQDN/group/renovate",
                  null,
                  "$CI_SERVER_FQDN/group/dependency-updates",
                  null
                )),
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/renovate@1.0.0
                    - component: $CI_SERVER_FQDN/group/dependency-updates@2.0.0
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void changesConvergeOnSameVersion() {
            rewriteRun(spec -> spec.recipe(
                new ChangeComponent(
                  "$CI_SERVER_FQDN/group/renovate",
                  null,
                  null,
                  "2.0.0"
                )),
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/renovate@1.0.0
                    - component: $CI_SERVER_FQDN/group/renovate@1.1.0
                  """,
                source -> source.path(".gitlab-ci.yml")
              )
            );
        }

        @Test
        void otherFile() {
            rewriteRun(spec -> spec.recipe(
                new ChangeComponent(
                  "$CI_SERVER_FQDN/group/renovate",
                  null,
                  null,
                  "2.0.0"
                )),
              //language=yaml
              yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/group/renovate@1.0.0
                  """,
                source -> source.path("other.yml")
              )
            );
        }
    }
}
