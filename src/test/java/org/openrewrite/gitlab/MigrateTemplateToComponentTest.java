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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.yaml.Assertions.yaml;

class MigrateTemplateToComponentTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateTemplateToComponent(
          "Terraform/Base.latest.gitlab-ci.yml",
          "$CI_SERVER_FQDN/components/opentofu/job-templates",
          "~latest",
          List.of("version: ~latest", "opentofu_version: 1.6.0")));
    }

    @DocumentExample
    @Test
    void migrateTemplateToComponent() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              include:
                - template: Terraform/Base.latest.gitlab-ci.yml
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/components/opentofu/job-templates@~latest
                  inputs:
                    version: ~latest
                    opentofu_version: 1.6.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void leaveSiblingIncludesAlone() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              include:
                - template: Gradle.gitlab-ci.yml
                - template: Terraform/Base.latest.gitlab-ci.yml
              """,
            """
              include:
                - template: Gradle.gitlab-ci.yml
                - component: $CI_SERVER_FQDN/components/opentofu/job-templates@~latest
                  inputs:
                    version: ~latest
                    opentofu_version: 1.6.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void noopWhenTemplateAbsent() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              include:
                - template: Gradle.gitlab-ci.yml
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void matchTemplateWithRefSuffix() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              include:
                - template: Terraform/Base.latest.gitlab-ci.yml@main
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/components/opentofu/job-templates@~latest
                  inputs:
                    version: ~latest
                    opentofu_version: 1.6.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void addComponentWithoutInputs() {
        //language=yaml
        rewriteRun(
          spec -> spec.recipe(new MigrateTemplateToComponent(
            "Terraform/Base.latest.gitlab-ci.yml",
            "$CI_SERVER_FQDN/components/opentofu/job-templates",
            "~latest",
            null)),
          yaml(
            """
              include:
                - template: Terraform/Base.latest.gitlab-ci.yml
              """,
            """
              include:
                - component: $CI_SERVER_FQDN/components/opentofu/job-templates@~latest
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }
}
