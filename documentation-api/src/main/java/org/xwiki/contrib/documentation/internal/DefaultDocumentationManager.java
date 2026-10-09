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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

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
    /**
     * The comment of the saves done by the analysis, which don't trigger a new analysis.
     */
    public static final String ANALYSIS_COMMENT = "Documentation analysis";

    private static final String CHECK = "check";

    private static final String FALSE_POSITIVE = "falsePositive";

    private static final String MESSAGE = "message";

    private static final String CONTEXT = "context";

    private static final String SEVERITY = "severity";

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManagerProvider;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Override
    public boolean analyse(XWikiDocument document) throws IndexException
    {
        ComponentManager cm = this.componentManagerProvider.get();
        try {
            // Step 1: Call the various checkers, remembering which check reported each violation.
            XWikiContext xcontext = this.xcontextProvider.get();
            Map<String, DocumentationCheck> checkers = cm.getInstanceMap(DocumentationCheck.class);
            List<CheckedViolation> violations = new ArrayList<>();
            for (Map.Entry<String, DocumentationCheck> checker : checkers.entrySet()) {
                for (DocumentationViolation violation : checker.getValue().check(document)) {
                    violations.add(new CheckedViolation(checker.getKey(), violation));
                }
            }

            // Step 2: Save new violations when they don't already exist + remove violations that were stored but don't
            //         exist anymore.
            boolean hasChanges = saveAndDeleteXObjects(document, violations, xcontext);

            // Step 3: Save the document (only if there have been changes)
            if (hasChanges) {
                // Save as superadmin, representing the system user, to indicate that the changes are not from the
                // current author but by the system.
                document.setAuthor(SuperAdminUserReference.INSTANCE);
                xcontext.getWiki().saveDocument(document, ANALYSIS_COMMENT, true, xcontext);
            }
            return hasChanges;
        } catch (Exception e) {
            throw new IndexException(String.format(
                "Failed to perform documentation content validation for [%s]", document.getDocumentReference()), e);
        }
    }

    /**
     * Matches each violation found with at most one stored violation xobject, so that a stored violation keeps its
     * xobject, and thus its false positive mark, for as long as it's reported. A violation matches the xobject holding
     * the same message, context and severity, or else the xobject holding the same check and context, which then gets
     * the new message and severity: this way a false positive mark survives a check rewording its message or changing
     * its severity.
     */
    private boolean saveAndDeleteXObjects(XWikiDocument document, List<CheckedViolation> violations,
        XWikiContext xcontext) throws XWikiException
    {
        boolean hasChanges = false;
        // Removed xobjects are kept as null entries.
        List<BaseObject> unmatchedObjects = new ArrayList<>(document.getXObjects(
            DocumentationPages.VIOLATION_CLASS_REFERENCE).stream().filter(Objects::nonNull).toList());

        List<CheckedViolation> unmatchedViolations = new ArrayList<>();
        for (CheckedViolation violation : violations) {
            BaseObject object = removeFirst(unmatchedObjects, o -> isSameViolation(o, violation));
            if (object == null) {
                unmatchedViolations.add(violation);
            } else if (!violation.check().equals(object.getStringValue(CHECK))
                || object.safeget(FALSE_POSITIVE) == null)
            {
                // The violation was stored before the check and the false positive mark were recorded.
                object.set(CHECK, violation.check(), xcontext);
                object.setIntValue(FALSE_POSITIVE, object.getIntValue(FALSE_POSITIVE));
                hasChanges = true;
            }
        }

        for (CheckedViolation violation : unmatchedViolations) {
            BaseObject object = removeFirst(unmatchedObjects, o -> isSameCheckAndContext(o, violation));
            if (object == null) {
                object = document.newXObject(DocumentationPages.VIOLATION_CLASS_REFERENCE, xcontext);
                object.set(CHECK, violation.check(), xcontext);
                object.set(CONTEXT, violation.violation().getViolationContext(), xcontext);
                // Always stored, so that the violations can be listed depending on whether they're false positives.
                object.setIntValue(FALSE_POSITIVE, 0);
            }
            object.set(MESSAGE, violation.violation().getViolationMessage(), xcontext);
            object.set(SEVERITY, violation.violation().getViolationSeverity().toString(), xcontext);
            hasChanges = true;
        }

        // Remove all the stored violations that don't exist anymore.
        for (BaseObject object : unmatchedObjects) {
            document.removeXObject(object);
            hasChanges = true;
        }

        return hasChanges;
    }

    private BaseObject removeFirst(List<BaseObject> objects, Predicate<BaseObject> predicate)
    {
        Iterator<BaseObject> iterator = objects.iterator();
        while (iterator.hasNext()) {
            BaseObject object = iterator.next();
            if (predicate.test(object)) {
                iterator.remove();
                return object;
            }
        }
        return null;
    }

    private boolean isSameViolation(BaseObject object, CheckedViolation violation)
    {
        // A violation stored before the check was recorded has no check.
        String check = object.getStringValue(CHECK);
        return (check.isEmpty() || check.equals(violation.check())) && hasSameContent(object, violation.violation());
    }

    private boolean hasSameContent(BaseObject object, DocumentationViolation violation)
    {
        return violation.getViolationMessage().equals(object.getStringValue(MESSAGE))
            && violation.getViolationContext().equals(object.getStringValue(CONTEXT))
            && violation.getViolationSeverity().toString().equals(object.getStringValue(SEVERITY));
    }

    private boolean isSameCheckAndContext(BaseObject object, CheckedViolation violation)
    {
        return violation.check().equals(object.getStringValue(CHECK))
            && violation.violation().getViolationContext().equals(object.getStringValue(CONTEXT));
    }

    /**
     * A violation along with the hint of the check that reported it.
     *
     * @param check the hint of the check that reported the violation
     * @param violation the violation
     */
    private record CheckedViolation(String check, DocumentationViolation violation)
    {
    }
}
