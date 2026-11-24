/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.documentation.internal.xwikiorg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Verifies the summary of documentation pages (the {@code summary} property of their
 * {@code DocApp.Code.DocumentationClass} object), displayed as their first paragraph, in the More and Documentation
 * live data, and in the Highlights of their parent: it must be present, be a single plain-text sentence of at most
 * {@value #MAX_LENGTH} characters, describe the content rather than the page (no "This page describes…" or "Learn how
 * to…"), and say something that the title doesn't, so it must not be mostly made of the words of the title.
 * <p>
 * All the violations are warnings, so that existing pages can get a summary over time.
 *
 * @version $Id$
 * @since 1.21
 */
@Component
@Singleton
@Named("summary")
public class SummaryCheck implements DocumentationCheck
{
    /**
     * The maximum number of characters of a summary, short enough to be read at a glance in a table column.
     */
    static final int MAX_LENGTH = 150;

    private static final LocalDocumentReference DOCUMENTATION_CLASS_REFERENCE =
        new LocalDocumentReference(List.of("DocApp", "Code"), "DocumentationClass");

    private static final String SUMMARY_CONTEXT = "Summary: [%s]";

    /**
     * The end of a sentence followed by the start of another one. The period of a common abbreviation (for example
     * "e.g. Velocity") doesn't end a sentence.
     */
    private static final Pattern SENTENCE_BREAK =
        Pattern.compile("(?<!\\b(?i:e\\.g|i\\.e|vs|etc|cf))[.!?]\\s+\\p{Lu}");

    /**
     * The openings describing the page instead of its content.
     */
    private static final Pattern PAGE_DESCRIPTION =
        Pattern.compile("^(this (page|document|section|tutorial|guide)\\b|learn how\\b)", Pattern.CASE_INSENSITIVE);

    /**
     * Wiki syntax for links and macros, and URLs, which a plain-text summary displays as is.
     */
    private static final Pattern MARKUP = Pattern.compile("\\[\\[|\\{\\{|://");

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    /**
     * The words carrying no information, ignored when comparing the summary with the title.
     */
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "and", "are", "as", "at", "be", "by", "can",
        "for", "from", "how", "in", "into", "is", "it", "its", "of", "on", "or", "that", "the", "their", "this", "to",
        "with", "you", "your");

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        BaseObject object = document.getXObject(DOCUMENTATION_CLASS_REFERENCE);
        if (object == null) {
            return violations;
        }

        String summary = StringUtils.trimToEmpty(object.getStringValue("summary"));
        if (summary.isEmpty()) {
            violations.add(new DocumentationViolation(
                "Documentation pages should have a summary: one sentence saying what the page gives that its title "
                    + "doesn't say.",
                String.format(SUMMARY_CONTEXT, ""), DocumentationViolationSeverity.WARNING));
            return violations;
        }

        String context = String.format(SUMMARY_CONTEXT, summary);
        if (summary.length() > MAX_LENGTH) {
            violations.add(new DocumentationViolation(String.format(
                "The summary must not be longer than %s characters, while it has %s.", MAX_LENGTH, summary.length()),
                context, DocumentationViolationSeverity.WARNING));
        }
        if (SENTENCE_BREAK.matcher(summary).find()) {
            violations.add(new DocumentationViolation("The summary must be a single sentence.", context,
                DocumentationViolationSeverity.WARNING));
        }
        if (MARKUP.matcher(summary).find()) {
            violations.add(new DocumentationViolation(
                "The summary is plain text: links, macros and URLs are displayed as is.", context,
                DocumentationViolationSeverity.WARNING));
        }
        if (PAGE_DESCRIPTION.matcher(summary).find()) {
            violations.add(new DocumentationViolation(
                "The summary must describe the content, not the page: don't start it with \"This page\" or \"Learn "
                    + "how\".", context, DocumentationViolationSeverity.WARNING));
        }
        if (isMostlyTitle(summary, document.getTitle())) {
            violations.add(new DocumentationViolation(
                "The summary must say what the title doesn't, while it's mostly made of the words of the title.",
                String.format("%s, Page title: [%s]", context, document.getTitle()),
                DocumentationViolationSeverity.WARNING));
        }
        return violations;
    }

    /**
     * @return {@code true} if at least half of the words of the summary that carry information are words of the title
     */
    private boolean isMostlyTitle(String summary, String title)
    {
        Set<String> titleWords = getWords(title).stream().collect(Collectors.toSet());
        List<String> summaryWords = getWords(summary);
        if (titleWords.isEmpty() || summaryWords.isEmpty()) {
            return false;
        }
        long titleWordCount = summaryWords.stream().filter(titleWords::contains).count();
        return titleWordCount * 2 >= summaryWords.size();
    }

    /**
     * @return the words of the passed text that carry information, in lower case and without a plural "s", so that
     *     "Properties" and "property" don't count as different words
     */
    private List<String> getWords(String text)
    {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(StringUtils.defaultString(text));
        while (matcher.find()) {
            String word = matcher.group().toLowerCase(Locale.ROOT);
            if (!STOP_WORDS.contains(word)) {
                words.add(stem(word));
            }
        }
        return words;
    }

    private String stem(String word)
    {
        if (word.length() > 4 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + 'y';
        } else if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
