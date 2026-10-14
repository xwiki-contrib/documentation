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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.internal.DocumentationPages;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataEntryDescriptor;
import org.xwiki.livedata.LiveDataMeta;
import org.xwiki.livedata.LiveDataPaginationConfiguration;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.livedata.LiveDataPropertyDescriptor.DisplayerDescriptor;
import org.xwiki.livedata.LiveDataPropertyDescriptor.FilterDescriptor;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.PropertyClass;
import com.xpn.xwiki.objects.classes.StaticListClass;

/**
 * Provides the default configuration of the {@link ViolationsLiveDataSource documentation violations Live Data}: the
 * properties of an entry, how they're displayed and how they're filtered.
 *
 * @version $Id$
 * @since 1.17
 */
@Component
@Singleton
@Named(ViolationsLiveDataSource.ID)
public class ViolationsLiveDataConfigurationProvider implements Provider<LiveDataConfiguration>
{
    /**
     * The identifier of an entry: the name of the page holding the violation and the number of the violation xobject.
     */
    static final String ID = "id";

    static final String DOC_TITLE = "doc.title";

    /**
     * The raw title of the page holding the violation, when it differs from the displayed one (i.e. when it's
     * computed), so that the title displayer warns that filtering and sorting on it don't work as expected.
     */
    static final String DOC_TITLE_RAW = "doc.title_raw";

    static final String DOC_URL = "doc.url";

    static final String DOC_VIEWABLE = "doc.viewable";

    static final String MESSAGE = "message";

    static final String CONTEXT = "context";

    static final String SEVERITY = "severity";

    static final String CHECK = "check";

    static final String FALSE_POSITIVE_REASON = "falsePositiveReason";

    static final String FALSE_POSITIVE_BY = "falsePositiveBy";

    static final String FALSE_POSITIVE_BY_URL = "falsePositiveBy_url";

    static final String FALSE_POSITIVE_DATE = "falsePositiveDate";

    private static final String STRING_TYPE = "String";

    private static final String TEXT_FILTER = "text";

    private static final String EQUALS_OPERATOR = "equals";

    private static final String PROPERTY_HREF = "propertyHref";

    private static final String VALUE = "value";

    private static final String LABEL = "label";

    @Inject
    private ContextualLocalizationManager l10n;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private Logger logger;

    @Override
    public LiveDataConfiguration get()
    {
        LiveDataMeta meta = new LiveDataMeta();
        LiveDataEntryDescriptor entryDescriptor = new LiveDataEntryDescriptor();
        entryDescriptor.setIdProperty(ID);
        meta.setEntryDescriptor(entryDescriptor);
        LiveDataPaginationConfiguration pagination = new LiveDataPaginationConfiguration();
        pagination.setShowPageSizeDropdown(true);
        meta.setPagination(pagination);
        meta.setPropertyDescriptors(getPropertyDescriptors());

        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setMeta(meta);
        return configuration;
    }

    private List<LiveDataPropertyDescriptor> getPropertyDescriptors()
    {
        BaseClass violationClass = getViolationClass();
        List<LiveDataPropertyDescriptor> descriptors = new ArrayList<>();
        LiveDataPropertyDescriptor id = createDescriptor(ID, ID);
        id.setVisible(false);
        id.setSortable(false);
        id.setFilterable(false);
        descriptors.add(id);
        LiveDataPropertyDescriptor title =
            createDescriptor(DOC_TITLE, this.l10n.getTranslationPlain("docapp.doc.title"));
        DisplayerDescriptor titleDisplayer = new DisplayerDescriptor("docTitle");
        titleDisplayer.setParameter(PROPERTY_HREF, DOC_URL);
        title.setDisplayer(titleDisplayer);
        title.setFilter(new FilterDescriptor(TEXT_FILTER));
        descriptors.add(title);
        for (String property : List.of(MESSAGE, CONTEXT, CHECK, FALSE_POSITIVE_REASON)) {
            descriptors.add(createTextDescriptor(property, violationClass));
        }
        descriptors.add(createSeverityDescriptor(violationClass));
        LiveDataPropertyDescriptor markedBy = createTextDescriptor(FALSE_POSITIVE_BY, violationClass);
        DisplayerDescriptor markedByDisplayer = new DisplayerDescriptor("link");
        markedByDisplayer.setParameter(PROPERTY_HREF, FALSE_POSITIVE_BY_URL);
        markedBy.setDisplayer(markedByDisplayer);
        descriptors.add(markedBy);
        descriptors.add(createDateDescriptor(FALSE_POSITIVE_DATE, violationClass));
        return descriptors;
    }

    private LiveDataPropertyDescriptor createDateDescriptor(String property, BaseClass violationClass)
    {
        String date = "date";
        LiveDataPropertyDescriptor descriptor = createDescriptor(property, getPrettyName(violationClass, property));
        descriptor.setType("Date");
        descriptor.setDisplayer(new DisplayerDescriptor(date));
        descriptor.setFilter(new FilterDescriptor(date));
        return descriptor;
    }

    private LiveDataPropertyDescriptor createTextDescriptor(String property, BaseClass violationClass)
    {
        LiveDataPropertyDescriptor descriptor = createDescriptor(property, getPrettyName(violationClass, property));
        descriptor.setFilter(new FilterDescriptor(TEXT_FILTER));
        return descriptor;
    }

    /**
     * The severity is filtered among its allowed values, as defined by the violation xclass.
     */
    private LiveDataPropertyDescriptor createSeverityDescriptor(BaseClass violationClass)
    {
        LiveDataPropertyDescriptor descriptor = createDescriptor(SEVERITY, getPrettyName(violationClass, SEVERITY));
        List<Map<String, String>> options = new ArrayList<>();
        if (violationClass != null && violationClass.get(SEVERITY) instanceof StaticListClass severityClass) {
            for (String value : severityClass.getList(this.xcontextProvider.get())) {
                options.add(Map.of(VALUE, value, LABEL, value));
            }
        }
        FilterDescriptor filter = new FilterDescriptor("list");
        filter.setParameter("options", options);
        filter.addOperator(EQUALS_OPERATOR, null);
        filter.setDefaultOperator(EQUALS_OPERATOR);
        descriptor.setFilter(filter);
        return descriptor;
    }

    private LiveDataPropertyDescriptor createDescriptor(String id, String name)
    {
        LiveDataPropertyDescriptor descriptor = new LiveDataPropertyDescriptor();
        descriptor.setId(id);
        descriptor.setName(name);
        descriptor.setType(STRING_TYPE);
        descriptor.setVisible(true);
        descriptor.setEditable(false);
        descriptor.setSortable(true);
        descriptor.setFilterable(true);
        return descriptor;
    }

    /**
     * @return the pretty name of the passed property of the violation xclass, translated in the current locale, or the
     *     property name if the xclass can't be loaded
     */
    private String getPrettyName(BaseClass violationClass, String property)
    {
        if (violationClass != null && violationClass.get(property) instanceof PropertyClass propertyClass) {
            return propertyClass.getTranslatedPrettyName(this.xcontextProvider.get());
        }
        return property;
    }

    private BaseClass getViolationClass()
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        try {
            return xcontext.getWiki().getXClass(
                new DocumentReference(DocumentationPages.VIOLATION_CLASS_REFERENCE, xcontext.getWikiReference()),
                xcontext);
        } catch (XWikiException e) {
            this.logger.warn("Failed to load the documentation violation class to label the Live Data properties: "
                + "[{}]", ExceptionUtils.getRootCauseMessage(e));
            return null;
        }
    }
}
