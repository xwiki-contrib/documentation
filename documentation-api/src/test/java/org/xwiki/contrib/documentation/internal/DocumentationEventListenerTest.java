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

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.bridge.event.DocumentCreatedEvent;
import org.xwiki.bridge.event.DocumentUpdatedEvent;
import org.xwiki.contrib.documentation.DocumentationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentationEventListener}.
 *
 * @version $Id$
 * @since 1.14
 */
@ComponentTest
class DocumentationEventListenerTest
{
    private static final LocalDocumentReference DOCUMENTATION_CLASS_REFERENCE =
        new LocalDocumentReference(List.of("DocApp", "Code"), "DocumentationClass");

    @InjectMockComponents
    private DocumentationEventListener listener;

    @MockComponent
    private DocumentationManager manager;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    private XWikiContext xcontext;

    private XWiki xwiki;

    /**
     * The copy of the saved document, loaded from the store, which is analysed.
     */
    private XWikiDocument storedDocumentCopy;

    @BeforeEach
    void setUp()
    {
        this.xcontext = mock(XWikiContext.class);
        this.xwiki = mock(XWiki.class);
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWiki()).thenReturn(this.xwiki);
        this.storedDocumentCopy = mock(XWikiDocument.class, "storedDocumentCopy");
    }

    private XWikiDocument mockDocument(boolean hasDocumentationClass, String comment, String space) throws Exception
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getXObject(DOCUMENTATION_CLASS_REFERENCE))
            .thenReturn(hasDocumentationClass ? mock(BaseObject.class) : null);
        when(document.getComment()).thenReturn(comment);
        DocumentReference reference = new DocumentReference("wiki", space, "Page");
        when(document.getDocumentReference()).thenReturn(reference);
        when(document.getDocumentReferenceWithLocale()).thenReturn(reference);

        XWikiDocument storedDocument = mock(XWikiDocument.class, "storedDocument");
        when(this.xwiki.getDocument(reference, this.xcontext)).thenReturn(storedDocument);
        when(storedDocument.clone()).thenReturn(this.storedDocumentCopy);
        return document;
    }

    @Test
    void analysisRunsWhenDocumentationClassPresent() throws Exception
    {
        XWikiDocument document = mockDocument(true, "edit", "Space");

        this.listener.onEvent(new DocumentUpdatedEvent(), document, null);

        verify(this.manager).analyse(this.storedDocumentCopy);
    }

    @Test
    void analysisSkippedWhenNoDocumentationClass() throws Exception
    {
        XWikiDocument document = mockDocument(false, "edit", "Space");

        this.listener.onEvent(new DocumentUpdatedEvent(), document, null);

        verify(this.manager, never()).analyse(any());
    }

    @Test
    void analysisSkippedWhenSaveIsFromAnalysis() throws Exception
    {
        XWikiDocument document = mockDocument(true, "Documentation analysis", "Space");

        this.listener.onEvent(new DocumentUpdatedEvent(), document, null);

        verify(this.manager, never()).analyse(any());
    }

    @Test
    void analysisSkippedWhenInDocAppSpace() throws Exception
    {
        XWikiDocument document = mockDocument(true, "edit", "DocApp");

        this.listener.onEvent(new DocumentUpdatedEvent(), document, null);

        verify(this.manager, never()).analyse(any());
    }

    @Test
    void analysisRunsOnDocumentCreatedEvent() throws Exception
    {
        XWikiDocument document = mockDocument(true, "edit", "Space");

        this.listener.onEvent(new DocumentCreatedEvent(), document, null);

        verify(this.manager).analyse(this.storedDocumentCopy);
    }

    @Test
    void analysisDoesNotSaveTheDocumentOfTheEvent() throws Exception
    {
        // When a document is renamed, the document of the event is a clone of the renamed document whose history is
        // already stored at its new location: saving it again fails.
        XWikiDocument document = mockDocument(true, "edit", "Space");

        this.listener.onEvent(new DocumentCreatedEvent(), document, null);

        verify(this.manager, never()).analyse(same(document));
        verify(this.manager).analyse(this.storedDocumentCopy);
    }

    @Test
    void analysisSkippedWhenDocumentNotStoredAnymore() throws Exception
    {
        XWikiDocument document = mockDocument(true, "edit", "Space");
        XWikiDocument deletedDocument = mock(XWikiDocument.class, "deletedDocument");
        when(deletedDocument.isNew()).thenReturn(true);
        when(this.xwiki.getDocument(document.getDocumentReferenceWithLocale(), this.xcontext))
            .thenReturn(deletedDocument);

        this.listener.onEvent(new DocumentUpdatedEvent(), document, null);

        verify(this.manager, never()).analyse(any());
    }
}
