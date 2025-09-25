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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.index.IndexException;
import org.xwiki.index.TaskConsumer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Perform documentation content validation.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
@Named(DefaultDocumentationManager.DOCUMENTATION_TASK_ID)
public class DocumentationCheckerConsumer implements TaskConsumer
{
    private static final LocalDocumentReference VIOLATION_CLASS_REFERENCE =
        new LocalDocumentReference(List.of("Documentation", "Code"), "DocumentationViolationClass");

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Override
    public void consume(DocumentReference documentReference, String version) throws IndexException
    {
        ComponentManager cm = this.componentManagerProvider.get();
        try {
            // Step 1: Call the various checkers
            XWikiContext xcontext = this.xcontextProvider.get();
            XWikiDocument document = xcontext.getWiki().getDocument(documentReference, xcontext).clone();
            List<DocumentationCheck> checkers = cm.getInstanceList(DocumentationCheck.class);
            List<DocumentationViolation> violations = new ArrayList<>();
            for (DocumentationCheck checker : checkers) {
                violations.addAll(checker.check(document));
            }

            // Step 2: Remove all existing violations.
            boolean removed = document.removeXObjects(VIOLATION_CLASS_REFERENCE);

            // Step 3: Store the violation results in DocumentationViolationClass xobjects inside the passed document.
            for (DocumentationViolation violation : violations) {
                BaseObject object = document.newXObject(VIOLATION_CLASS_REFERENCE, xcontext);
                object.set("message", violation.getViolationMessage(), xcontext);
                object.set("context", violation.getViolationContext(), xcontext);
                object.set("severity", violation.getViolationSeverity().toString(), xcontext);
            }

            // Step 4: Save the document (only if there have been changes)
            if (removed || !violations.isEmpty()) {
                xcontext.getWiki().saveDocument(document, "Documentation analysis", true, xcontext);
            }
        } catch (Exception e) {
            throw new IndexException(String.format(
                "Failed to perform documentation content validation for [%s]", documentReference), e);
        }
    }
}
