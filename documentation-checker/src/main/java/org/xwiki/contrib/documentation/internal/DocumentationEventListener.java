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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.bridge.event.DocumentCreatedEvent;
import org.xwiki.bridge.event.DocumentUpdatedEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationManager;
import org.xwiki.index.IndexException;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

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
    private static final LocalDocumentReference DOCUMENTATION_CLASS_REFERENCE =
        new LocalDocumentReference(List.of("Documentation", "Code"), "DocumentationClass");

    @Inject
    private Logger logger;

    @Inject
    private DocumentationManager manager;

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

        // 1) Only validate pages containing a DocumentationClass xobject
        // 2) Protection for infinite recursion: don't trigger the analysis when the save is done by the Documentation
        // checker. We identify this by the save message.
        if (document.getXObject(DOCUMENTATION_CLASS_REFERENCE) != null
            && !"Documentation analysis".equals(document.getComment()))
        {
            try {
                this.manager.analyse(document);
            } catch (IndexException e) {
                this.logger.error("Failed to perform documentation checks on the document [{}] for revision [{}].",
                    document.getDocumentReference(), document.getVersion(), e);
            }
        }
    }
}
