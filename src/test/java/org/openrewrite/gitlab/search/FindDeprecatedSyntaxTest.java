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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class FindDeprecatedSyntaxTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource("/META-INF/rewrite/deprecated-syntax.yml",
          "org.openrewrite.gitlab.search.FindDeprecatedSyntax");
    }

    @DocumentExample
    @Test
    void findOnlyAndExcept() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              build_job:
                script: make build
                only:
                  - main
                except:
                  - tags
              """,
            """
              build_job:
                script: make build
                ~~>only:
                  - main
                ~~>except:
                  - tags
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }
}
