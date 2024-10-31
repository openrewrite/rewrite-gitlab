package org.openrewrite.gitlab.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class FindComponentTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindComponent("\\$CI_SERVER_FQDN/components/opentofu/full-pipeline"));
    }

    @DocumentExample
    @Test
    void exists() {
        //language=yaml
        rewriteRun(
            yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/components/opentofu/full-pipeline@0.10.0
                      inputs:
                        version: 0.10.0
                        opentofu_version: 1.6.1
                  """,
                """
                  include:
                    - ~~>component: $CI_SERVER_FQDN/components/opentofu/full-pipeline@0.10.0
                      inputs:
                        version: 0.10.0
                        opentofu_version: 1.6.1
                  """,
                source -> source.path(".gitlab-ci.yml")
            )
        );
    }

    @DocumentExample
    @Test
    void notExists() {
        //language=yaml
        rewriteRun(
            yaml(
                """
                  include:
                    - component: $CI_SERVER_FQDN/components/opentofu/job-templates@0.10.0
                      inputs:
                        version: 0.10.0
                        opentofu_version: 1.6.1
                  """,
                source -> source.path(".gitlab-ci.yml")
            )
        );
    }
}
