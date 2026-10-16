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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.bridge.event.DocumentCreatedEvent;
import org.xwiki.bridge.event.DocumentUpdatedEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationManager;
import org.xwiki.index.IndexException;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Trigger a documentation analysis when a page is created or updated.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
@Named("DocumentationEventListener")
public class DocumentationEventListener extends AbstractEventListener
{
    @Inject
    private Logger logger;

    @Inject
    private DocumentationManager manager;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    /**
     * Default constructor.
     */
    public DocumentationEventListener()
    {
        super("DocumentationEventListener", new DocumentCreatedEvent(), new DocumentUpdatedEvent());
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        this.logger.debug("Event [{}] received from [{}] with data [{}].", event.getClass().getName(), source,
            data);

        XWikiDocument document = (XWikiDocument) source;

        // 1) Only validate documentation pages (see DocumentationPages#isAnalysable)
        // 2) Protection for infinite recursion: don't trigger the analysis when the save is done by the Documentation
        //    checker. We identify this by the save message.
        if (DocumentationPages.isAnalysable(document)
            && !DefaultDocumentationManager.ANALYSIS_COMMENT.equals(document.getComment()))
        {
            try {
                analyseStoredDocument(document);
            } catch (IndexException | XWikiException e) {
                this.logger.error("Failed to perform documentation checks on the document [{}] for revision [{}].",
                    document.getDocumentReference(), document.getVersion(), e);
            }
        }
    }

    /**
     * Analyses a copy of the saved document loaded from the store, rather than the instance passed with the event,
     * since the analysis saves the document it analyses. The event instance can't be saved again: when a document is
     * renamed, it's a clone of the renamed document whose history was already stored at its new location, and saving
     * it fails on inserting that history again. The copy is a clone, so that the cached document isn't modified.
     */
    private void analyseStoredDocument(XWikiDocument document) throws XWikiException, IndexException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument storedDocument =
            xcontext.getWiki().getDocument(document.getDocumentReferenceWithLocale(), xcontext);
        // The document can be deleted by another listener of the save.
        if (!storedDocument.isNew()) {
            this.manager.analyse(storedDocument.clone());
        }
    }
}
