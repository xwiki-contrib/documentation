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
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationException;
import org.xwiki.contrib.documentation.DocumentationViolationGroup;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceProvider;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Marks the violations stored in documentation pages as false positives, i.e. as violations that the documentation
 * rules don't actually forbid, and reinstates them. A violation marked as a false positive keeps being stored, along
 * with who marked it, when and why, but isn't reported anymore.
 *
 * @version $Id$
 * @since 1.16
 */
@Component(roles = FalsePositives.class)
@Singleton
public class FalsePositives
{
    private static final String FALSE_POSITIVE = "falsePositive";

    private static final String FALSE_POSITIVE_BY = "falsePositiveBy";

    private static final String FALSE_POSITIVE_DATE = "falsePositiveDate";

    private static final String FALSE_POSITIVE_REASON = "falsePositiveReason";

    private static final String CHECK = "check";

    private static final String MESSAGE = "message";

    private static final String SEVERITY = "severity";

    private static final String QUERY = "select distinct doc.fullName from XWikiDocument doc, BaseObject obj "
        + "where doc.fullName = obj.name and obj.className = :className and doc.translation = 0 order by doc.fullName";

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private EntityReferenceProvider entityReferenceProvider;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    /**
     * Marks a violation of a documentation page as a false positive, on behalf of the current user.
     *
     * @param reference the documentation page holding the violation
     * @param number the number of the violation xobject
     * @param reason why the violation is a false positive, optional
     * @return true if the violation has been marked, false if there's no such violation or if it's already marked
     * @throws DocumentationException if the page fails to be loaded or saved
     */
    public boolean mark(DocumentReference reference, int number, String reason) throws DocumentationException
    {
        return update(reference, o -> o.getNumber() == number && !isFalsePositive(o), true, reason,
            "Marked a documentation violation as a false positive") > 0;
    }

    /**
     * Reinstates a violation of a documentation page that was marked as a false positive.
     *
     * @param reference the documentation page holding the violation
     * @param number the number of the violation xobject
     * @return true if the violation has been reinstated, false if there's no such violation or if it isn't marked
     * @throws DocumentationException if the page fails to be loaded or saved
     */
    public boolean unmark(DocumentReference reference, int number) throws DocumentationException
    {
        return update(reference, o -> o.getNumber() == number && isFalsePositive(o), false, null,
            "Reinstated a documentation violation marked as a false positive") > 0;
    }

    /**
     * Groups the violations not marked as false positives of a given documentation page and, when it's a nested page,
     * of the pages located under it, by rule, i.e. by check and message.
     *
     * @param page the page whose violations to group, along with the violations of the pages located under it when
     *     it's a nested page
     * @return the groups of violations, the ones with the most violations first
     * @throws DocumentationException if the pages fail to be found or loaded
     */
    public List<DocumentationViolationGroup> getGroups(DocumentReference page) throws DocumentationException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        Map<List<String>, DocumentationViolationGroup> groups = new LinkedHashMap<>();
        for (DocumentReference reference : getPagesWithViolations(page)) {
            try {
                XWikiDocument document = xcontext.getWiki().getDocument(reference, xcontext);
                for (BaseObject object : document.getXObjects(DocumentationPages.VIOLATION_CLASS_REFERENCE)) {
                    // Removed xobjects are kept as null entries.
                    if (object != null && !isFalsePositive(object)) {
                        String check = object.getStringValue(CHECK);
                        String message = object.getStringValue(MESSAGE);
                        groups.computeIfAbsent(List.of(check, message),
                            key -> new DocumentationViolationGroup(check, message, object.getStringValue(SEVERITY)))
                            .addViolation(reference);
                    }
                }
            } catch (XWikiException e) {
                throw new DocumentationException(String.format("Failed to load the violations of [%s]", reference), e);
            }
        }
        List<DocumentationViolationGroup> sortedGroups = new ArrayList<>(groups.values());
        sortedGroups.sort(Comparator.comparingInt(DocumentationViolationGroup::getViolationCount).reversed());
        return sortedGroups;
    }

    /**
     * Marks as false positives the violations of a given rule, i.e. reported by a given check with a given message, in
     * a given documentation page and, when it's a nested page, in the pages located under it, on behalf of the current
     * user. Each page holding such violations is saved once.
     *
     * @param check the hint of the check that reported the violations to mark, empty for the violations stored before
     *     the check was recorded
     * @param message the message of the violations to mark
     * @param page the page whose violations to mark, along with the violations of the pages located under it when it's
     *     a nested page
     * @param reason why the violations are false positives, optional
     * @return the number of violations marked
     * @throws DocumentationException if the pages fail to be found, loaded or saved
     */
    public int markAll(String check, String message, DocumentReference page, String reason)
        throws DocumentationException
    {
        int count = 0;
        for (DocumentReference reference : getPagesWithViolations(page)) {
            count += update(reference, o -> check.equals(o.getStringValue(CHECK))
                && message.equals(o.getStringValue(MESSAGE)) && !isFalsePositive(o), true, reason,
                "Marked documentation violations as false positives");
        }
        return count;
    }

    /**
     * @param reference a documentation page
     * @return the pages in which the violations of a rule reported in the passed page can be marked as false positives
     *     along with the violations of the pages located under them: the passed page when it's a nested page, then the
     *     nested pages it's located under, the closest first
     */
    public List<DocumentReference> getScopes(DocumentReference reference)
    {
        String webHome = this.entityReferenceProvider.getDefaultReference(EntityType.DOCUMENT).getName();
        List<SpaceReference> spaces = reference.getSpaceReferences();
        List<DocumentReference> scopes = new ArrayList<>();
        for (int i = spaces.size() - 1; i >= 0; i--) {
            scopes.add(new DocumentReference(webHome, spaces.get(i)));
        }
        return scopes;
    }

    private List<DocumentReference> getPagesWithViolations(DocumentReference page) throws DocumentationException
    {
        List<String> pageNames;
        try {
            pageNames = this.queryManager.createQuery(QUERY, Query.HQL)
                .bindValue("className", this.localSerializer.serialize(DocumentationPages.VIOLATION_CLASS_REFERENCE))
                .setWiki(page.getWikiReference().getName())
                .execute();
        } catch (QueryException e) {
            throw new DocumentationException("Failed to find the pages holding documentation violations", e);
        }
        return pageNames.stream()
            .map(pageName -> this.resolver.resolve(pageName, page.getWikiReference()))
            .filter(reference -> isAt(reference, page))
            .toList();
    }

    /**
     * @return true if the passed reference is the passed page, or is located under it when it's a nested page
     */
    private boolean isAt(DocumentReference reference, DocumentReference page)
    {
        boolean isNestedPage =
            page.getName().equals(this.entityReferenceProvider.getDefaultReference(EntityType.DOCUMENT).getName());
        return reference.equals(page) || (isNestedPage && reference.hasParent(page.getLastSpaceReference()));
    }

    private static boolean isFalsePositive(BaseObject object)
    {
        return object.getIntValue(FALSE_POSITIVE) == 1;
    }

    private int update(DocumentReference reference, Predicate<BaseObject> filter, boolean falsePositive,
        String reason, String comment) throws DocumentationException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        try {
            // Clone the document since it's modified, and the document returned by getDocument() is shared.
            XWikiDocument document = xcontext.getWiki().getDocument(reference, xcontext).clone();
            int count = 0;
            for (BaseObject object : document.getXObjects(DocumentationPages.VIOLATION_CLASS_REFERENCE)) {
                // Removed xobjects are kept as null entries.
                if (object != null && filter.test(object)) {
                    if (falsePositive) {
                        object.setIntValue(FALSE_POSITIVE, 1);
                        object.setLargeStringValue(FALSE_POSITIVE_BY,
                            this.serializer.serialize(xcontext.getUserReference()));
                        object.setDateValue(FALSE_POSITIVE_DATE, new Date());
                        object.setLargeStringValue(FALSE_POSITIVE_REASON, reason != null ? reason : "");
                    } else {
                        // Blank the properties rather than removing them: removing a property and saving the
                        // violation xobject fails, since the stored property is still associated with the session.
                        object.setIntValue(FALSE_POSITIVE, 0);
                        object.setLargeStringValue(FALSE_POSITIVE_BY, "");
                        object.setDateValue(FALSE_POSITIVE_DATE, null);
                        object.setLargeStringValue(FALSE_POSITIVE_REASON, "");
                    }
                    count++;
                }
            }
            if (count > 0) {
                document.setAuthorReference(xcontext.getUserReference());
                xcontext.getWiki().saveDocument(document, comment, true, xcontext);
            }
            return count;
        } catch (XWikiException e) {
            throw new DocumentationException(
                String.format("Failed to update the false positive violations of [%s]", reference), e);
        }
    }
}
