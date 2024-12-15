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
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class ChangeTemplateTest implements RewriteTest {
    @DocumentExample
    @Test
    void updateTemplate() {
        rewriteRun(spec -> spec.recipe(
            new ChangeTemplate(
              "Terraform/Base.gitlab-ci.yml",
              "OpenTofu/Base.gitlab-ci.yml"
            )),
          //language=yaml
          yaml(
            """
              include:
                - template: Terraform/Base.gitlab-ci.yml
              """,
            """
              include:
                - template: OpenTofu/Base.gitlab-ci.yml
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }
}
