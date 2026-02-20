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
package org.openrewrite.gitlab.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class FindDeprecatedOnlyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindDeprecatedOnly());
    }

    @DocumentExample
    @Test
    void findOnly() {
        //language=yaml
        rewriteRun(
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
                ~~>only:
                  - main
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void noMatchWhenNotPresent() {
        //language=yaml
        rewriteRun(
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
}
