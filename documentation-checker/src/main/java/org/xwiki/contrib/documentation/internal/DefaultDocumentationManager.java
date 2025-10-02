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
import org.xwiki.contrib.documentation.DocumentationManager;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.index.IndexException;
import org.xwiki.index.TaskManager;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.user.SuperAdminUserReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Perform documentation analysis using the {@link TaskManager} API (i.e. asynchronously).
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultDocumentationManager implements DocumentationManager
{
    private static final List<String> SPACES = List.of("Documentation", "Code");

    private static final LocalDocumentReference VIOLATION_CLASS_REFERENCE =
        new LocalDocumentReference(SPACES, "DocumentationViolationClass");

    private static final String MESSAGE = "message";

    private static final String CONTEXT = "context";

    private static final String SEVERITY = "severity";

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Override
    public void analyse(XWikiDocument document) throws IndexException
    {
        ComponentManager cm = this.componentManagerProvider.get();
        try {
            // Step 1: Call the various checkers
            XWikiContext xcontext = this.xcontextProvider.get();
            List<DocumentationCheck> checkers = cm.getInstanceList(DocumentationCheck.class);
            List<DocumentationViolation> violations = new ArrayList<>();
            for (DocumentationCheck checker : checkers) {
                violations.addAll(checker.check(document));
            }

            // Step 2: Save new violations when they don't already exist + remove violations that were stored but don't
            //         exist anymore.
            boolean hasChanges = saveAndDeleteXObjects(document, violations, xcontext);

            // Step 3: Save the document (only if there have been changes)
            if (hasChanges) {
                // Save as superadmin, representing the system user, to indicate that the changes are not from the
                // current author but by the system.
                document.setAuthor(SuperAdminUserReference.INSTANCE);
                xcontext.getWiki().saveDocument(document, "Documentation analysis", true, xcontext);
            }
        } catch (Exception e) {
            throw new IndexException(String.format(
                "Failed to perform documentation content validation for [%s]", document.getDocumentReference()), e);
        }
    }

    private boolean saveAndDeleteXObjects(XWikiDocument document, List<DocumentationViolation> violations,
        XWikiContext xcontext) throws XWikiException
    {
        boolean hasChanges = false;
        List<BaseObject> existingViolationObjects = new ArrayList<>(document.getXObjects(VIOLATION_CLASS_REFERENCE));

        // Remove all existing violations that don't exist anymore.
        for (BaseObject existingViolationObject : existingViolationObjects) {
            // If we don't find the violation in the new list, remove it.
            if (!exists(existingViolationObject, violations)) {
                document.removeXObject(existingViolationObject);
                hasChanges = true;
            }
        }

        // Add all new violations that don't already exist.'
        for (DocumentationViolation violation : violations) {
            // If we don't already have this violation as an xobject, add it.
            if (!exists(violation, existingViolationObjects)) {
                BaseObject object = document.newXObject(VIOLATION_CLASS_REFERENCE, xcontext);
                object.set(MESSAGE, violation.getViolationMessage(), xcontext);
                object.set(CONTEXT, violation.getViolationContext(), xcontext);
                object.set(SEVERITY, violation.getViolationSeverity().toString(), xcontext);
                hasChanges = true;
            }
        }

        return hasChanges;
    }

    private boolean exists(BaseObject existingViolationObject, List<DocumentationViolation> violations)
    {
        boolean exists = false;
        for (DocumentationViolation violation : violations) {
            if (isEqual(existingViolationObject, violation)) {
                exists = true;
                break;
            }
        }
        return exists;
    }

    private boolean exists(DocumentationViolation violation, List<BaseObject> existingViolationObjects)
    {
        boolean exists = false;
        for (BaseObject existingViolationObject : existingViolationObjects) {
            if (isEqual(existingViolationObject, violation)) {
                exists = true;
                break;
            }
        }
        return exists;
    }

    private boolean isEqual(BaseObject existingViolationObject, DocumentationViolation violation)
    {
        String message = existingViolationObject.getStringValue(MESSAGE);
        String context = existingViolationObject.getStringValue(CONTEXT);
        String severity = existingViolationObject.getStringValue(SEVERITY);
        return violation.getViolationMessage().equals(message)
            && violation.getViolationContext().equals(context)
            && violation.getViolationSeverity().toString().equals(severity);
    }
}
