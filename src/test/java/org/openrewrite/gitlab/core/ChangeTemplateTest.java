package org.openrewrite.gitlab.core;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.yaml.Assertions.yaml;

class ChangeTemplateTest implements RewriteTest {
    @DocumentExample
    @Test
    void updateTemplate() {
        rewriteRun(spec -> spec.recipe(
                        new ChangeTemplate(
                                "Terraform/Base.gitlab-ci.yml",
                                "OpenTofu/Base.gitlab-ci.yml"
                        )),
                //language=yaml
                yaml("""
                                include:
                                  - template: Terraform/Base.gitlab-ci.yml
                                """,
                        """
                                include:
                                  - template: OpenTofu/Base.gitlab-ci.yml
                                """,
                        source -> source.path(".gitlab-ci.yml"))
        );
    }
}
