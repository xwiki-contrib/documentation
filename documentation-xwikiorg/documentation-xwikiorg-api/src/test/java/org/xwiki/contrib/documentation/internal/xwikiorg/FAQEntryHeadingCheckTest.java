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

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.annotation.AllComponents;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link FAQEntryHeadingCheck}.
 *
 * @version $Id$
 */
@AllComponents
@OldcoreTest
class FAQEntryHeadingCheckTest
{
    private static final String LEVEL_MESSAGE = "FAQ entries must be level 2 headings.";

    private static final String QUESTION_MESSAGE =
        "FAQ entries must be phrased as questions, ending with a question mark.";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    private XWikiDocument createDocument(String content, String faqContent) throws Exception
    {
        Parser parser = this.oldcore.getMocker().getInstance(Parser.class, Syntax.XWIKI_2_1.toIdString());
        XDOM xdom = parser.parse(new StringReader(content));
        XWikiDocument document = new XWikiDocument(new DocumentReference("wiki", "space", "page"))
        {
            @Override
            public XDOM getXDOM()
            {
                return xdom;
            }

            @Override
            public Syntax getSyntax()
            {
                return Syntax.XWIKI_2_1;
            }
        };
        if (faqContent != null) {
            BaseObject faqObj = new BaseObject();
            faqObj.setXClassReference(
                new DocumentReference("wiki", Arrays.asList("DocApp", "Code"), "DocumentationClass"));
            faqObj.setLargeStringValue("faq", faqContent);
            document.addXObject(faqObj);
        }
        return document;
    }

    private DocumentationCheck getChecker() throws Exception
    {
        return this.oldcore.getMocker().getInstance(DocumentationCheck.class, "faqEntryHeading");
    }

    private void assertViolation(DocumentationViolation violation, String message, String heading)
    {
        assertEquals(message, violation.getViolationMessage());
        assertEquals("Heading : " + heading, violation.getViolationContext());
        assertEquals(DocumentationViolationSeverity.WARNING, violation.getViolationSeverity());
    }

    @Test
    void checkWhenNoDocumentationObject() throws Exception
    {
        assertEquals(0, getChecker().check(createDocument("= Not a question =", null)).size());
    }

    @Test
    void checkWhenFAQIsEmpty() throws Exception
    {
        assertEquals(0, getChecker().check(createDocument("", "")).size());
    }

    @Test
    void checkWhenFAQEntriesAreLevelTwoQuestions() throws Exception
    {
        XWikiDocument document = createDocument("= Content heading =",
            "== How do I **install** it? ==\n\nAnswer.\n\n== Why is it slow?  ==\n\nOther answer.");

        assertEquals(0, getChecker().check(document).size());
    }

    @Test
    void checkWhenFAQEntriesAreNotLevelTwoHeadings() throws Exception
    {
        XWikiDocument document = createDocument("",
            "= How do I install it? =\n\n=== Why is it slow? ===\n\n==== Details ====");

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(3, violations.size());
        assertViolation(violations.get(0), LEVEL_MESSAGE, "How do I install it?");
        assertViolation(violations.get(1), LEVEL_MESSAGE, "Why is it slow?");
        assertViolation(violations.get(2), LEVEL_MESSAGE, "Details");
    }

    @Test
    void checkWhenFAQEntriesAreNotQuestions() throws Exception
    {
        XWikiDocument document = createDocument("",
            "== Installation ==\n\nAnswer.\n\n== Is it **fast**? ==\n\n== Is it free? Yes ==");

        List<DocumentationViolation> violations = getChecker().check(document);

        assertEquals(2, violations.size());
        assertViolation(violations.get(0), QUESTION_MESSAGE, "Installation");
        assertViolation(violations.get(1), QUESTION_MESSAGE, "Is it free? Yes");
    }
}
