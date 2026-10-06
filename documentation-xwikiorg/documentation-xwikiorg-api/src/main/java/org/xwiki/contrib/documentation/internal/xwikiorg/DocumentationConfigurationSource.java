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

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.internal.AbstractDocumentConfigurationSource;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;

/**
 * Configuration of the documentation checks, stored in the {@code DocApp.Code.DocumentationConfiguration} page of the
 * current wiki and editable from the wiki Administration. Values are cached and the cache is cleared whenever the
 * configuration object changes, so a new value is used without restarting XWiki.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Named(DocumentationConfigurationSource.HINT)
@Singleton
public class DocumentationConfigurationSource extends AbstractDocumentConfigurationSource
{
    /**
     * The hint of this component.
     */
    public static final String HINT = "documentation";

    private static final List<String> CODE_SPACE = List.of("DocApp", "Code");

    static final LocalDocumentReference CLASS_REFERENCE =
        new LocalDocumentReference(CODE_SPACE, "DocumentationConfigurationClass");

    static final LocalDocumentReference DOCUMENT_REFERENCE =
        new LocalDocumentReference(CODE_SPACE, "DocumentationConfiguration");

    @Override
    protected DocumentReference getDocumentReference()
    {
        return new DocumentReference(DOCUMENT_REFERENCE, getCurrentWikiReference());
    }

    @Override
    protected LocalDocumentReference getClassReference()
    {
        return CLASS_REFERENCE;
    }

    @Override
    protected String getCacheId()
    {
        return "configuration.documentation";
    }
}
