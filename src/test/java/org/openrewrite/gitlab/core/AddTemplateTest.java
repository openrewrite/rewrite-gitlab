package org.openrewrite.gitlab.core;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class AddTemplateTest implements RewriteTest {
    @DocumentExample
    @Test
    void addToExistingList() {
        rewriteRun(spec -> spec.recipe(
                new AddTemplate("Jobs/SAST.gitlab-ci.yml")),
                //language=yaml
                yaml("""
                        include:
                          - template: Gradle.gitlab-ci.yml
                        """,
                        """
                        include:
                          - template: Gradle.gitlab-ci.yml
                          - template: Jobs/SAST.gitlab-ci.yml
                        """,
                        source -> source.path(".gitlab-ci.yml")
                )
        );
    }

    @DocumentExample
    @Test
    void addToExistingListPreservingFormat() {
        rewriteRun(spec -> spec.recipe(
                        new AddTemplate("Jobs/SAST.gitlab-ci.yml")),
                //language=yaml
                yaml("""
                        include:
                          - template: Gradle.gitlab-ci.yml
                        """,
                        """
                        include:
                          - template: Gradle.gitlab-ci.yml
                          - template: Jobs/SAST.gitlab-ci.yml
                        """,
                        source -> source.path(".gitlab-ci.yml")
                )
        );
    }

    @DocumentExample
    @Test
    void addNew() {
        rewriteRun(spec -> spec.recipe(
                        new AddTemplate("Jobs/SAST.gitlab-ci.yml")),
                //language=yaml
                yaml("",
                        """
                        include:
                          - template: Jobs/SAST.gitlab-ci.yml
                        """,
                        source -> source.path(".gitlab-ci.yml")
                )
        );
    }
}