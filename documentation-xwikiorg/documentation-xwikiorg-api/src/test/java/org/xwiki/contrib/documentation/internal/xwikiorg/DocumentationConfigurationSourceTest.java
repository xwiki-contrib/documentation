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

import org.junit.jupiter.api.Test;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.AllComponents;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link DocumentationConfigurationSource}.
 *
 * @version $Id$
 */
@AllComponents
@OldcoreTest
class DocumentationConfigurationSourceTest
{
    private static final String PROPERTY = "oldestSupportedVersion";

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    private void saveConfiguration(String value) throws Exception
    {
        XWikiContext xcontext = this.oldcore.getXWikiContext();
        DocumentReference documentReference =
            new DocumentReference(DocumentationConfigurationSource.DOCUMENT_REFERENCE, xcontext.getWikiReference());
        XWikiDocument document = xcontext.getWiki().getDocument(documentReference, xcontext).clone();
        BaseObject object = document.getXObject(DocumentationConfigurationSource.CLASS_REFERENCE, true, xcontext);
        object.setStringValue(PROPERTY, value);
        xcontext.getWiki().saveDocument(document, xcontext);
    }

    @Test
    void getPropertyIsLive() throws Exception
    {
        // Saving the configuration page must send the events that clear the configuration cache.
        this.oldcore.notifyDocumentCreatedEvent(true);
        this.oldcore.notifyDocumentUpdatedEvent(true);

        ConfigurationSource source =
            this.oldcore.getMocker().getInstance(ConfigurationSource.class, DocumentationConfigurationSource.HINT);

        // No configuration page yet.
        assertNull(source.getProperty(PROPERTY, String.class));

        saveConfiguration("16.10.0");
        assertEquals("16.10.0", source.getProperty(PROPERTY, String.class));

        // Updating the configuration page is taken into account without restarting.
        saveConfiguration("17.10.0");
        assertEquals("17.10.0", source.getProperty(PROPERTY, String.class));

        // An empty value means no value.
        saveConfiguration("");
        assertNull(source.getProperty(PROPERTY, String.class));
    }
}
