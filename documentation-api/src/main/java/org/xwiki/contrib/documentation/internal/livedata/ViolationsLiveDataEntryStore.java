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
package org.xwiki.contrib.documentation.internal.livedata;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.internal.DocumentationPages;
import org.xwiki.livedata.LiveData;
import org.xwiki.livedata.LiveDataEntryStore;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.query.QueryException;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.CHECK;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.CONTEXT;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.DOC_TITLE;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.DOC_TITLE_RAW;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.DOC_URL;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.DOC_VIEWABLE;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.FALSE_POSITIVE_BY;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.FALSE_POSITIVE_BY_URL;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.FALSE_POSITIVE_DATE;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.FALSE_POSITIVE_REASON;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.ID;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.MESSAGE;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.SEVERITY;

/**
 * Provides the entries of the {@link ViolationsLiveDataSource documentation violations Live Data}: one entry per
 * violation xobject, holding the values of that xobject.
 *
 * @version $Id$
 * @since 1.17
 */
@Component
@Singleton
@Named(ViolationsLiveDataSource.ID)
public class ViolationsLiveDataEntryStore implements LiveDataEntryStore
{
    /**
     * Separates the name of the page holding the violation from the number of the violation xobject, in the entry
     * identifier.
     */
    private static final char ID_SEPARATOR = '/';

    private static final String VIEW = "view";

    @Inject
    private ViolationsLiveDataQueryHelper queryHelper;

    @Inject
    private ContextualAuthorizationManager authorization;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Override
    public Optional<Map<String, Object>> get(Object entryId) throws LiveDataException
    {
        String id = String.valueOf(entryId);
        int separatorIndex = id.lastIndexOf(ID_SEPARATOR);
        if (separatorIndex < 0 || !StringUtils.isNumeric(id.substring(separatorIndex + 1))) {
            return Optional.empty();
        }
        Map<String, Object> entry =
            getEntry(id.substring(0, separatorIndex), Integer.parseInt(id.substring(separatorIndex + 1)));
        return entry.containsKey(MESSAGE) ? Optional.of(entry) : Optional.empty();
    }

    @Override
    public LiveData get(LiveDataQuery query) throws LiveDataException
    {
        LiveData liveData = new LiveData();
        try {
            List<Object[]> violations = this.queryHelper.getViolations(query);
            liveData.setCount(violations.size());
            int start = query.getOffset() != null ? (int) Math.min(Math.max(query.getOffset(), 0), violations.size())
                : 0;
            int end = query.getLimit() != null ? Math.min(start + Math.max(query.getLimit(), 0), violations.size())
                : violations.size();
            for (Object[] violation : violations.subList(start, end)) {
                liveData.getEntries().add(getEntry((String) violation[0], ((Number) violation[1]).intValue()));
            }
        } catch (QueryException e) {
            throw new LiveDataException("Failed to list the documentation violations", e);
        }
        return liveData;
    }

    /**
     * @param pageName the name of the page holding the violation
     * @param number the number of the violation xobject
     * @return the entry of the violation, holding only whether its page is viewable when the current user can't view
     *     its page (and thus not its identifier, which holds the page name)
     */
    private Map<String, Object> getEntry(String pageName, int number) throws LiveDataException
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        DocumentReference reference = this.resolver.resolve(pageName);
        boolean viewable = this.authorization.hasAccess(Right.VIEW, reference);
        entry.put(DOC_VIEWABLE, viewable);
        if (viewable) {
            entry.put(ID, pageName + ID_SEPARATOR + number);
            XWikiContext xcontext = this.xcontextProvider.get();
            XWiki xwiki = xcontext.getWiki();
            XWikiDocument document;
            try {
                document = xwiki.getDocument(reference, xcontext);
            } catch (XWikiException e) {
                throw new LiveDataException(String.format("Failed to load the page [%s]", reference), e);
            }
            String title = document.getRenderedTitle(Syntax.PLAIN_1_0, xcontext);
            entry.put(DOC_TITLE, title);
            if (!title.equals(document.getTitle())) {
                entry.put(DOC_TITLE_RAW, document.getTitle());
            }
            entry.put(DOC_URL, xwiki.getURL(reference, VIEW, xcontext));
            BaseObject violation = document.getXObject(DocumentationPages.VIOLATION_CLASS_REFERENCE, number);
            if (violation != null) {
                addViolation(violation, entry, xcontext);
            }
        }
        return entry;
    }

    private void addViolation(BaseObject violation, Map<String, Object> entry, XWikiContext xcontext)
    {
        for (String property : List.of(MESSAGE, CONTEXT, SEVERITY, CHECK)) {
            entry.put(property, violation.getStringValue(property));
        }
        entry.put(FALSE_POSITIVE_REASON, violation.getLargeStringValue(FALSE_POSITIVE_REASON));
        String markedBy = violation.getLargeStringValue(FALSE_POSITIVE_BY);
        if (StringUtils.isNotBlank(markedBy)) {
            DocumentReference userReference = this.resolver.resolve(markedBy);
            entry.put(FALSE_POSITIVE_BY, xcontext.getWiki().getPlainUserName(userReference, xcontext));
            entry.put(FALSE_POSITIVE_BY_URL, xcontext.getWiki().getURL(userReference, VIEW, xcontext));
        }
        Date markedOn = violation.getDateValue(FALSE_POSITIVE_DATE);
        if (markedOn != null) {
            entry.put(FALSE_POSITIVE_DATE, markedOn.getTime());
        }
    }
}
