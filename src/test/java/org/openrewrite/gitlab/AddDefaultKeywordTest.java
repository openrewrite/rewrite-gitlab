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

class AddDefaultKeywordTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddDefaultKeyword("image", "ruby:3.0", null));
    }

    @DocumentExample
    @Test
    void addToDefault() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              """,
            """
              default:
                image: ruby:3.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void mergeIntoExistingDefault() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              default:
                interruptible: true
              """,
            """
              default:
                interruptible: true
                image: ruby:3.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void acceptTheirsPreservesExisting() {
        //language=yaml
        rewriteRun(
          spec ->
            spec.recipe(new AddDefaultKeyword("image", "ruby:3.0", true)),
          yaml(
            """
              default:
                image: node:18
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
              default:
                image: ruby:3.0
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }
}
