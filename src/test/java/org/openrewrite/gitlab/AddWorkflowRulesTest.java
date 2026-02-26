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

class AddWorkflowRulesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddWorkflowRules(
          """
          - if: $CI_PIPELINE_SOURCE == 'merge_request_event'
          - if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS
            when: never
          - if: $CI_COMMIT_BRANCH""",
          null));
    }

    @DocumentExample
    @Test
    void addWorkflowRules() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              """,
            """
              workflow:
                rules:
                  - if: $CI_PIPELINE_SOURCE == 'merge_request_event'
                  - if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS
                    when: never
                  - if: $CI_COMMIT_BRANCH
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }

    @Test
    void mergeIntoExistingWorkflow() {
        //language=yaml
        rewriteRun(
          yaml(
            """
              workflow:
                name: My Pipeline
              """,
            """
              workflow:
                name: My Pipeline
                rules:
                  - if: $CI_PIPELINE_SOURCE == 'merge_request_event'
                  - if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS
                    when: never
                  - if: $CI_COMMIT_BRANCH
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
              workflow:
                rules:
                  - if: $CI_PIPELINE_SOURCE == 'merge_request_event'
                  - if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS
                    when: never
                  - if: $CI_COMMIT_BRANCH
              """,
            source -> source.path(".gitlab-ci.yml")
          )
        );
    }
}
