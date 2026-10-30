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

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.With;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.style.Autodetect;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Comparator.comparingInt;

@EqualsAndHashCode(callSuper = false)
@Value
public class OrderKeywords extends Recipe {

    private static final List<String> GLOBAL_KEYWORDS = asList(
            "include", "stages", "variables", "workflow", "default");

    private static final Set<String> NON_JOB_KEYS = new HashSet<>(asList(
            "include", "stages", "variables", "workflow", "spec",
            "image", "services", "cache", "before_script", "after_script"));

    private static final List<String> JOB_KEYWORDS = asList(
            "<<", "extends", "inherit",
            "stage", "needs", "dependencies",
            "rules", "only", "except", "when", "start_in", "manual_confirmation", "allow_failure",
            "trigger", "image", "services", "tags", "resource_group", "environment",
            "parallel", "interruptible", "timeout", "retry",
            "inputs", "variables", "id_tokens", "identity", "secrets", "cache",
            "hooks", "before_script", "script", "run", "after_script",
            "coverage", "artifacts", "pages", "release", "dast_configuration");

    private static final Pattern REST_OF_LINE = Pattern.compile("[^\n]*?(\r?\n|\\z)");

    String displayName = "Order GitLab CI keywords";

    String description = "Order the keywords in `.gitlab-ci.yml` consistently. Global keywords come first, " +
            "in the order `include`, `stages`, `variables`, `workflow` and `default`, followed by the jobs in their " +
            "original order. Within `default` and each job, keywords follow the order in which they take effect: " +
            "inheritance, scheduling, execution environment, scripts and finally outputs such as `artifacts`. " +
            "Unrecognized keys keep their relative order after the recognized ones. " +
            "A mapping is left unchanged when reordering would place a YAML alias before its anchor.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles("**/.gitlab-ci.yml"),
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                        if (!(document.getBlock() instanceof Yaml.Mapping)) {
                            return document;
                        }
                        Cursor cursor = getCursor();
                        String newline = Autodetect.generalFormat(document).newLine();
                        Yaml.Mapping root = (Yaml.Mapping) document.getBlock();
                        List<Yaml.Mapping.Entry> rootEntries = root.getEntries();
                        String[] prefixes = new String[rootEntries.size() + 1];
                        for (int i = 0; i < rootEntries.size(); i++) {
                            prefixes[i] = rootEntries.get(i).getPrefix();
                        }
                        prefixes[rootEntries.size()] = document.getEnd().getPrefix();
                        root = root.withEntries(ListUtils.map(rootEntries, (i, entry) -> {
                            entry = entry.withPrefix(prefixes[i]);
                            Reordered job = isJob(entry) ?
                                    order((Yaml.Mapping) entry.getValue(), JOB_KEYWORDS, prefixes[i + 1], newline, cursor) : null;
                            if (job == null) {
                                return entry;
                            }
                            prefixes[i + 1] = job.getFollowing();
                            return entry.withValue(job.getMapping());
                        }));
                        String documentEnd = prefixes[rootEntries.size()];
                        Reordered ordered = order(root, GLOBAL_KEYWORDS, documentEnd, newline, cursor);
                        if (ordered != null) {
                            root = ordered.getMapping();
                            documentEnd = ordered.getFollowing();
                        }
                        return document.withBlock(root).withEnd(document.getEnd().withPrefix(documentEnd));
                    }
                }
        );
    }

    private static boolean isJob(Yaml.Mapping.Entry entry) {
        return entry.getValue() instanceof Yaml.Mapping && !NON_JOB_KEYS.contains(entry.getKey().getValue());
    }

    @Value
    private static class Reordered {
        Yaml.Mapping mapping;
        String following;
    }

    @Value
    @With
    private static class Slot {
        String ownLines;
        String lineEnd;
    }

    private static @Nullable Reordered order(Yaml.Mapping mapping, List<String> keywords, String following, String newline, Cursor cursor) {
        if (mapping.getOpeningBracePrefix() != null) {
            return null;
        }
        List<Yaml.Mapping.Entry> entries = mapping.getEntries();
        List<Yaml.Mapping.Entry> ordered = new ArrayList<>(entries);
        ordered.sort(comparingInt(entry -> rank(entry, keywords)));
        if (ordered.equals(entries) || aliasPrecedesAnchor(ordered)) {
            return null;
        }
        return relayout(mapping, ordered, following, newline, cursor);
    }

    private static int rank(Yaml.Mapping.Entry entry, List<String> keywords) {
        int index = keywords.indexOf(entry.getKey().getValue());
        return index < 0 ? keywords.size() : index;
    }

    // An entry's prefix starts with the rest of the previous entry's last line (a trailing comment and the line
    // break), unless that entry ended in a block scalar that already holds the line break. That line end travels
    // with the previous entry, the remaining comment lines travel with the entry itself, and the leading whitespace
    // in front of the first entry stays in first position as it separates the mapping from its parent key. The text
    // following the mapping likewise ends the line of its last entry, so it is relaid out together with the entries.
    private static Reordered relayout(Yaml.Mapping mapping, List<Yaml.Mapping.Entry> ordered, String following,
                                      String newline, Cursor cursor) {
        List<Yaml.Mapping.Entry> entries = mapping.getEntries();
        String firstPrefix = entries.get(0).getPrefix();
        String parentLineEnd = firstPrefix.substring(0, firstPrefix.indexOf('\n') + 1);
        String ownLines = firstPrefix.substring(parentLineEnd.length());
        boolean endsWithoutLineBreak = false;
        Map<Yaml.Mapping.Entry, Slot> slots = new IdentityHashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            Yaml.Mapping.Entry entry = entries.get(i);
            String next = i + 1 < entries.size() ? entries.get(i + 1).getPrefix() : following;
            String lineEnd = "";
            int ownLinesStart = 0;
            if (!entry.getValue().print(cursor).endsWith("\n")) {
                Matcher restOfLine = REST_OF_LINE.matcher(next);
                restOfLine.lookingAt();
                endsWithoutLineBreak = restOfLine.group(1).isEmpty();
                lineEnd = endsWithoutLineBreak ? restOfLine.group() + newline : restOfLine.group();
                ownLinesStart = restOfLine.end();
            }
            slots.put(entry, new Slot(ownLines, lineEnd));
            ownLines = next.substring(ownLinesStart);
        }

        Yaml.Mapping.Entry previousFirst = entries.get(0);
        Yaml.Mapping.Entry newFirst = ordered.get(0);
        String previousFirstWhitespace = leadingWhitespace(slots.get(previousFirst).getOwnLines());
        String newFirstWhitespace = leadingWhitespace(slots.get(newFirst).getOwnLines());
        slots.computeIfPresent(previousFirst, (entry, slot) -> slot.withOwnLines(
                newFirstWhitespace + slot.getOwnLines().substring(previousFirstWhitespace.length())));
        slots.computeIfPresent(newFirst, (entry, slot) -> slot.withOwnLines(
                previousFirstWhitespace + slot.getOwnLines().substring(newFirstWhitespace.length())));

        List<Yaml.Mapping.Entry> relaidOut = new ArrayList<>(entries.size());
        String previousLineEnd = parentLineEnd;
        for (Yaml.Mapping.Entry entry : ordered) {
            Slot slot = slots.get(entry);
            relaidOut.add(entry.withPrefix(previousLineEnd + slot.getOwnLines()));
            previousLineEnd = slot.getLineEnd();
        }
        if (endsWithoutLineBreak && previousLineEnd.endsWith(newline)) {
            previousLineEnd = previousLineEnd.substring(0, previousLineEnd.length() - newline.length());
        }
        return new Reordered(mapping.withEntries(relaidOut), previousLineEnd + ownLines);
    }

    private static String leadingWhitespace(String prefix) {
        int end = StringUtils.indexOfNonWhitespace(prefix);
        return end < 0 ? prefix : prefix.substring(0, end);
    }

    private static boolean aliasPrecedesAnchor(List<Yaml.Mapping.Entry> ordered) {
        List<Set<String>> anchorsPerEntry = new ArrayList<>(ordered.size());
        Set<String> siblingAnchors = new HashSet<>();
        for (Yaml.Mapping.Entry entry : ordered) {
            Set<String> anchors = YamlAnchors.anchorsIn(entry);
            anchorsPerEntry.add(anchors);
            siblingAnchors.addAll(anchors);
        }
        Set<String> defined = new HashSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            defined.addAll(anchorsPerEntry.get(i));
            for (String alias : YamlAnchors.aliasesIn(ordered.get(i))) {
                if (siblingAnchors.contains(alias) && !defined.contains(alias)) {
                    return true;
                }
            }
        }
        return false;
    }
}
