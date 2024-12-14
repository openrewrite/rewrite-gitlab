/*
 * Copyright 2024 the original author or authors.
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

class AddComponentTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(
          new AddComponent(
            "$CI_SERVER_FQDN/components/opentofu/full-pipeline",
            "0.10.0",
            List.of("version: 0.10.0", "opentofu_version: 1.6.1")));
    }

    @DocumentExample
    @Test
    void addToExistingList() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              include:
                - template: Gradle.gitlab-ci.yml
              """,
            """
              include:
                - template: Gradle.gitlab-ci.yml
                - component: $CI_SERVER_FQDN/components/opentofu/full-pipeline@0.10.0
                  inputs:
                    version: 0.10.0
                    opentofu_version: 1.6.1
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void addNewWhereNoneExist() {
        //language=yaml
        rewriteRun(
          yaml(
            "",
            """
              include:
                - component: $CI_SERVER_FQDN/components/opentofu/full-pipeline@0.10.0
                  inputs:
                    version: 0.10.0
                    opentofu_version: 1.6.1
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void noopWhenAlreadyPresent() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              include:
                - template: Gradle.gitlab-ci.yml
                - component: $CI_SERVER_FQDN/components/opentofu/full-pipeline@0.10.0
                  inputs:
                    version: 0.10.0
                    opentofu_version: 1.6.1
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }
}
