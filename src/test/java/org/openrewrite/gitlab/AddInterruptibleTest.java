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

class AddInterruptibleTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddInterruptible(null, null));
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
                interruptible: true
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
                image: ruby:3.0
              """,
            """
              default:
                image: ruby:3.0
                interruptible: true
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void addToSpecificJob() {
        //language=yaml
        rewriteRun(
          spec ->
            spec.recipe(new AddInterruptible("build_job", null)),
          yaml(
            """
              build_job:
                script: make build
              """,
            """
              build_job:
                script: make build
                interruptible: true
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
                interruptible: true
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }
}
