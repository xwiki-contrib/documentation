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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SummaryCheck}.
 *
 * @version $Id$
 */
@ComponentTest
class SummaryCheckTest
{
    private static final String MISSING_MESSAGE = "Documentation pages should have a summary: one sentence saying "
        + "what the page gives that its title doesn't say.";

    private static final String TITLE_MESSAGE =
        "The summary must say what the title doesn't, while it's mostly made of the words of the title.";

    @InjectMockComponents
    private SummaryCheck check;

    private static XWikiDocument document(String title, String summary)
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getTitle()).thenReturn(title);
        if (summary != null) {
            BaseObject object = mock(BaseObject.class);
            when(object.getStringValue("summary")).thenReturn(summary);
            when(document.getXObject(new LocalDocumentReference(List.of("DocApp", "Code"), "DocumentationClass")))
                .thenReturn(object);
        }
        return document;
    }

    private List<DocumentationViolation> check(String title, String summary)
    {
        return this.check.check(document(title, summary));
    }

    private static void assertWarning(DocumentationViolation violation, String message, String context)
    {
        assertEquals(message, violation.getViolationMessage());
        assertEquals(context, violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violation.getViolationSeverity());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // The examples of the proposal (https://forum.xwiki.org/t/19107).
        "Its highlighting style and the largest attachment it accepts as source.",
        "Make every existing code macro highlight with Prism.js by installing one extension.",
        "The two ways a Java component can extend the code macro: new sources of code and new languages.",
        // Abbreviations don't end a sentence.
        "Script macros, e.g. Velocity and Groovy, vs. Python ones."
    })
    void checkWhenSummaryIsValid(String summary)
    {
        assertEquals(List.of(), check("Code Macro Configuration Properties", summary));
    }

    @Test
    void checkWhenNoDocumentationObject()
    {
        assertEquals(List.of(), check("Title", null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "  " })
    void checkWhenSummaryIsMissing(String summary)
    {
        List<DocumentationViolation> violations = check("Title", summary);

        assertEquals(1, violations.size());
        assertWarning(violations.get(0), MISSING_MESSAGE, "Summary: []");
    }

    @Test
    void checkWhenSummaryIsTooLong()
    {
        String summary = "a".repeat(150) + ".";

        List<DocumentationViolation> violations = check("Installation", summary);

        assertEquals(1, violations.size());
        assertWarning(violations.get(0), "The summary must not be longer than 150 characters, while it has "
            + summary.length() + ".", "Summary: [" + summary + "]");
    }

    @Test
    void checkWhenSummaryIsAtMaximumLength()
    {
        String summary = "a".repeat(149) + ".";

        assertEquals(List.of(), check("Installation", summary));
    }

    @Test
    void checkWhenSummaryHasSeveralSentences()
    {
        List<DocumentationViolation> violations =
            check("Installation", "Install XWiki on Tomcat. Then configure its database.");

        assertEquals(1, violations.size());
        assertWarning(violations.get(0), "The summary must be a single sentence.",
            "Summary: [Install XWiki on Tomcat. Then configure its database.]");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "See [[the guide>>Guide]] for the setup steps",
        "Uses the {{code}} macro for the setup steps",
        "Read https://www.xwiki.org for the setup steps"
    })
    void checkWhenSummaryHasMarkup(String summary)
    {
        List<DocumentationViolation> violations = check("Installation", summary);

        assertEquals(1, violations.size());
        assertWarning(violations.get(0), "The summary is plain text: links, macros and URLs are displayed as is.",
            "Summary: [" + summary + "]");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "This page describes the setup of the database",
        "learn how to set up the database"
    })
    void checkWhenSummaryDescribesThePage(String summary)
    {
        List<DocumentationViolation> violations = check("Installation", summary);

        assertEquals(1, violations.size());
        assertWarning(violations.get(0), "The summary must describe the content, not the page: don't start it with "
            + "\"This page\" or \"Learn how\".", "Summary: [" + summary + "]");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // The failing example of the proposal: plurals and stop words don't make a summary say more.
        "The configuration properties of the code macro.",
        "Code macro configuration property, and its macro."
    })
    void checkWhenSummaryIsMostlyTheTitle(String summary)
    {
        List<DocumentationViolation> violations = check("Code Macro Configuration Properties", summary);

        assertEquals(1, violations.size());
        assertWarning(violations.get(0), TITLE_MESSAGE,
            "Summary: [" + summary + "], Page title: [Code Macro Configuration Properties]");
    }

    @Test
    void checkWhenTitleOrSummaryHasNoWord()
    {
        assertEquals(List.of(), check(null, "Install XWiki on Tomcat."));
        assertEquals(List.of(), check("Installation", "The ..."));
    }

    @Test
    void checkReportsAllTheViolations()
    {
        String summary = "This page describes the code macros. " + "Code macros ".repeat(12) + "[[and more>>Page]].";

        assertEquals(5, check("Code Macros", summary).size());
    }
}
