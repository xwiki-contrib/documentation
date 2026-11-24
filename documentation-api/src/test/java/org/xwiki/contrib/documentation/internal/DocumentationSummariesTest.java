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
package org.xwiki.contrib.documentation.internal;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentationSummaries}.
 *
 * @version $Id$
 */
@OldcoreTest
@ComponentList(DocumentationSummaries.class)
@ReferenceComponentList
class DocumentationSummariesTest
{
    private static final DocumentReference PAGE = new DocumentReference("wiki", List.of("doc", "install"), "WebHome");

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private DocumentationSummaries summaries;

    @BeforeEach
    void setUp() throws Exception
    {
        this.summaries = this.oldcore.getMocker().getInstance(DocumentationSummaries.class);
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(Right.VIEW, PAGE)).thenReturn(true);
    }

    private void savePage(String summary) throws XWikiException
    {
        XWikiDocument document = new XWikiDocument(PAGE);
        if (summary != null) {
            BaseObject object = new BaseObject();
            object.setXClassReference(new DocumentReference("wiki", List.of("DocApp", "Code"), "DocumentationClass"));
            object.setStringValue("summary", summary);
            document.addXObject(object);
        }
        this.oldcore.getSpyXWiki().saveDocument(document, this.oldcore.getXWikiContext());
    }

    @Test
    void getSummary() throws Exception
    {
        savePage("  Make every code macro highlight with Prism.js. ");

        assertEquals("Make every code macro highlight with Prism.js.", this.summaries.getSummary(PAGE));
    }

    @Test
    void getSummaryWhenTheUserCannotViewThePage() throws Exception
    {
        savePage("Secret");
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(Right.VIEW, PAGE)).thenReturn(false);

        assertEquals("", this.summaries.getSummary(PAGE));
    }

    @Test
    void getSummaryWithoutRightCheckWhenTheUserCannotViewThePage() throws Exception
    {
        savePage("Make every code macro highlight with Prism.js.");
        when(this.oldcore.getMockContextualAuthorizationManager().hasAccess(Right.VIEW, PAGE)).thenReturn(false);

        assertEquals("Make every code macro highlight with Prism.js.",
            this.summaries.getSummaryWithoutRightCheck(PAGE));
    }

    @Test
    void getSummaryWhenNoDocumentationObject() throws Exception
    {
        savePage(null);

        assertEquals("", this.summaries.getSummary(PAGE));
    }

    @Test
    void getSummaryWhenThePageDoesNotExist()
    {
        assertEquals("", this.summaries.getSummary(PAGE));
    }

    @Test
    void getSummaryWhenThePageCannotBeLoaded() throws Exception
    {
        doThrow(new XWikiException()).when(this.oldcore.getSpyXWiki()).getDocument(eq(PAGE), any());

        assertEquals("", this.summaries.getSummary(PAGE));
        assertEquals("Failed to get the summary of the documentation page [wiki:doc.install.WebHome]. "
            + "Root cause: [XWikiException: Error number 0 in 0]", this.logCapture.getMessage(0));
    }
}
