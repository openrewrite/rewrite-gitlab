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

class BestPracticesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource("/META-INF/rewrite/best-practices.yml",
          "org.openrewrite.gitlab.BestPractices");
    }

    @DocumentExample
    @Test
    void appliesBestPractices() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              stages:
                - build
                - test
              """,
            """
              stages:
                - build
                - test
              workflow:
                rules:
                  - if: $CI_PIPELINE_SOURCE == 'merge_request_event'
                  - if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS
                    when: never
                  - if: $CI_COMMIT_BRANCH
              default:
                interruptible: true
                retry:
                  max: 2
                  when:
                    - runner_system_failure
                    - stuck_or_timeout_failure
                artifacts:
                  expire_in: 1 week
                timeout: 1 hour
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
              stages:
                - build
                - test
              workflow:
                rules:
                  - if: $CI_PIPELINE_SOURCE == 'merge_request_event'
                  - if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS
                    when: never
                  - if: $CI_COMMIT_BRANCH
              default:
                interruptible: true
                retry:
                  max: 2
                  when:
                    - runner_system_failure
                    - stuck_or_timeout_failure
                artifacts:
                  expire_in: 1 week
                timeout: 1 hour
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }
}
