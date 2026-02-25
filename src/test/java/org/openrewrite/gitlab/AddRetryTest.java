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

import java.util.List;

import static org.openrewrite.yaml.Assertions.yaml;

class AddRetryTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddRetry(2, List.of("runner_system_failure", "stuck_or_timeout_failure"), null, null));
    }

    @DocumentExample
    @Test
    void addRetryWithWhen() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              """,
            """
              default:
                retry:
                  max: 2
                  when:
                    - runner_system_failure
                    - stuck_or_timeout_failure
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void addSimpleRetry() {
        //language=yaml
        rewriteRun(
          spec ->
            spec.recipe(new AddRetry(1, null, null, null)),
          yaml(
            """
              """,
            """
              default:
                retry:
                  max: 1
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
                retry:
                  max: 2
                  when:
                    - runner_system_failure
                    - stuck_or_timeout_failure
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
            spec.recipe(new AddRetry(2, List.of("script_failure"), "build_job", null)),
          yaml(
            """
              build_job:
                script: make build
              """,
            """
              build_job:
                script: make build
                retry:
                  max: 2
                  when:
                    - script_failure
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
                retry:
                  max: 2
                  when:
                    - runner_system_failure
                    - stuck_or_timeout_failure
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }
}
