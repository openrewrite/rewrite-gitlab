package org.openrewrite.gitlab.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class FindTemplateTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindTemplate("Gradle.gitlab-ci.yml"));
    }

    @DocumentExample
    @Test
    void exists() {
        //language=yaml
        rewriteRun(
                yaml(
                        """
                          include:
                            - template: Gradle.gitlab-ci.yml
                          """,
                        """
                          include:
                            - ~~>template: Gradle.gitlab-ci.yml
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
                            - template: Jobs/SAST.gitlab-ci.yml
                          """,
                        source -> source.path(".gitlab-ci.yml")
                )
        );
    }
}
