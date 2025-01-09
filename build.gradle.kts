plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Rewrite GitLab recipes."

val rewriteVersion = "latest.release"
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:8.41.1"))
    implementation("org.openrewrite:rewrite-core")
    implementation("org.openrewrite:rewrite-yaml")

    testImplementation("org.openrewrite:rewrite-test")
}
