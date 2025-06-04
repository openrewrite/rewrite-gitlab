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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class ChangeComponentTest implements RewriteTest {
    @DocumentExample
    @Test
    void updateTemplate() {
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
              "\\$CI_SERVER_FQDN/components/opentofu/full-pipeline",
              ".+",
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
    void matchedOldVersion() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "\\$CI_SERVER_FQDN/components/opentofu/full-pipeline",
              "1.+",
              "$CI_SERVER_FQDN/components/opentofu/full-pipeline",
              "2.0"
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/components/opentofu/full-pipeline@1.0
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/components/opentofu/full-pipeline@2.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void mismatchedOldVersion() {
        rewriteRun(spec -> spec.recipe(
            new ChangeComponent(
              "\\$CI_SERVER_FQDN/components/opentofu/full-pipeline",
              "1.+",
              "$CI_SERVER_FQDN/components/opentofu/full-pipeline",
              "2.0"
            )),
          //language=yaml
          yaml(
            """
              include:
                - component: $CI_SERVER_FQDN/components/opentofu/full-pipeline@2.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }
}
