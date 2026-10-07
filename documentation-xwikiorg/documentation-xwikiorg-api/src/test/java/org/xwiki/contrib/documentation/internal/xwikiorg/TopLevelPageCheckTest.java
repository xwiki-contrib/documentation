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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TopLevelPageCheck}.
 *
 * @version $Id$
 * @since 1.15
 */
@ComponentTest
class TopLevelPageCheckTest
{
    private static final String TYPE_MESSAGE =
        "Top-level documentation pages must be of Explanation type, for consistency.";

    private static final String WORDS_MESSAGE = "Top-level page titles must be concise: 3 words maximum.";

    private static final String QUALIFIER_MESSAGE = "Top-level page titles must not use parenthetical qualifiers.";

    private static final String EXPLANATION = "explanation";

    @InjectMockComponents
    private TopLevelPageCheck check;

    private XWikiDocument createDocument(String type, String title, String name, String... spaces)
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getDocumentReference())
            .thenReturn(new DocumentReference("xwiki", Arrays.asList(spaces), name));
        when(document.getTitle()).thenReturn(title);
        if (type != null) {
            BaseObject docObject = mock(BaseObject.class);
            when(docObject.getStringValue("type")).thenReturn(type);
            when(document.getXObject(any(EntityReference.class))).thenReturn(docObject);
        }
        return document;
    }

    private XWikiDocument createTopLevelDocument(String type, String title)
    {
        return createDocument(type, title, "WebHome", "documentation", "xs", "user", "like");
    }

    private void assertViolation(String expectedMessage, String expectedContext, DocumentationViolation violation)
    {
        assertEquals(expectedMessage, violation.getViolationMessage());
        assertEquals(expectedContext, violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violation.getViolationSeverity());
    }

    @Test
    void checkWhenTopLevelPageIsValid()
    {
        assertEquals(0, this.check.check(createTopLevelDocument(EXPLANATION, "Like")).size());
        assertEquals(0, this.check.check(createDocument(EXPLANATION, "Office Importer Application", "WebHome",
            "documentation", "extensions", "admin", "office-importer")).size());
        assertEquals(0, this.check.check(createDocument(EXPLANATION, " Rights  Management ", "WebHome",
            "documentation", "xs", "dev", "rights")).size());
    }

    @Test
    void checkWhenTopLevelPageIsNotExplanation()
    {
        List<DocumentationViolation> violations = this.check.check(createTopLevelDocument("howto", "Like"));

        assertEquals(1, violations.size());
        assertViolation(TYPE_MESSAGE, "Type: [howto]", violations.get(0));
    }

    @Test
    void checkWhenTopLevelPageHasNoDocumentationObject()
    {
        List<DocumentationViolation> violations = this.check.check(createTopLevelDocument(null, "Like"));

        assertEquals(1, violations.size());
        assertViolation(TYPE_MESSAGE, "Type: []", violations.get(0));
    }

    @Test
    void checkWhenTopLevelPageTitleIsTooLong()
    {
        List<DocumentationViolation> violations =
            this.check.check(createTopLevelDocument(EXPLANATION, "Like and Unlike Pages"));

        assertEquals(1, violations.size());
        assertViolation(WORDS_MESSAGE, "Page title: [Like and Unlike Pages]", violations.get(0));
    }

    @Test
    void checkWhenTopLevelPageTitleHasParentheticalQualifier()
    {
        List<DocumentationViolation> violations =
            this.check.check(createTopLevelDocument(EXPLANATION, "Like (Users)"));

        assertEquals(1, violations.size());
        assertViolation(QUALIFIER_MESSAGE, "Page title: [Like (Users)]", violations.get(0));
    }

    @Test
    void checkWhenTopLevelPageBreaksAllRules()
    {
        List<DocumentationViolation> violations =
            this.check.check(createTopLevelDocument("reference", "Office Importer (for Administrator)"));

        assertEquals(3, violations.size());
        assertViolation(TYPE_MESSAGE, "Type: [reference]", violations.get(0));
        assertViolation(WORDS_MESSAGE, "Page title: [Office Importer (for Administrator)]", violations.get(1));
        assertViolation(QUALIFIER_MESSAGE, "Page title: [Office Importer (for Administrator)]", violations.get(2));
    }

    @Test
    void checkWhenTopLevelPageTitleIsEmpty()
    {
        assertEquals(0, this.check.check(createTopLevelDocument(EXPLANATION, "")).size());
        assertEquals(0, this.check.check(createTopLevelDocument(EXPLANATION, null)).size());
    }

    @Test
    void checkWhenPageIsNotTopLevel()
    {
        String title = "How to Like a Page (Users)";
        // Below a top-level page.
        assertEquals(0, this.check.check(createDocument("howto", title, "WebHome",
            "documentation", "xs", "user", "like", "like-page")).size());
        // Terminal page right under an audience page.
        assertEquals(0, this.check.check(createDocument("howto", title, "like",
            "documentation", "xs", "user")).size());
        // Not under an audience page.
        assertEquals(0, this.check.check(createDocument("howto", title, "WebHome",
            "documentation", "xs", "users", "like")).size());
        assertEquals(0, this.check.check(createDocument("howto", title, "WebHome",
            "documentation", "contrib", "user", "like")).size());
        assertEquals(0, this.check.check(createDocument("howto", title, "WebHome",
            "Documentation", "xs", "user", "like")).size());
    }
}
