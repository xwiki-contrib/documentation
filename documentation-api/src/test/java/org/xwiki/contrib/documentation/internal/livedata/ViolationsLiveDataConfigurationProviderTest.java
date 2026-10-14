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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.PropertyClass;
import com.xpn.xwiki.objects.classes.StaticListClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViolationsLiveDataConfigurationProvider}.
 *
 * @version $Id$
 */
@ComponentTest
class ViolationsLiveDataConfigurationProviderTest
{
    private static final DocumentReference CLASS_REFERENCE =
        new DocumentReference("wiki", List.of("DocApp", "Code"), "DocumentationViolationClass");

    @InjectMockComponents
    private ViolationsLiveDataConfigurationProvider provider;

    @MockComponent
    private ContextualLocalizationManager l10n;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private final XWikiContext xcontext = mock(XWikiContext.class);

    private final XWiki xwiki = mock(XWiki.class);

    @BeforeEach
    void setUp()
    {
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWiki()).thenReturn(this.xwiki);
        when(this.xcontext.getWikiReference()).thenReturn(new WikiReference("wiki"));
        when(this.l10n.getTranslationPlain("docapp.doc.title")).thenReturn("Page Title");
    }

    @Test
    void get() throws Exception
    {
        BaseClass violationClass = mock(BaseClass.class);
        when(this.xwiki.getXClass(CLASS_REFERENCE, this.xcontext)).thenReturn(violationClass);
        for (String property : List.of("message", "context", "check", "falsePositiveReason", "falsePositiveBy",
            "falsePositiveDate")) {
            PropertyClass propertyClass = mock(PropertyClass.class);
            when(propertyClass.getTranslatedPrettyName(this.xcontext)).thenReturn(property + " label");
            when(violationClass.get(property)).thenReturn(propertyClass);
        }
        StaticListClass severityClass = mock(StaticListClass.class);
        when(severityClass.getTranslatedPrettyName(this.xcontext)).thenReturn("Severity");
        when(severityClass.getList(this.xcontext)).thenReturn(List.of("Warning", "Error"));
        when(violationClass.get("severity")).thenReturn(severityClass);

        LiveDataConfiguration configuration = this.provider.get();

        assertEquals("id", configuration.getMeta().getEntryDescriptor().getIdProperty());
        assertTrue(configuration.getMeta().getPagination().getShowPageSizeDropdown());
        Map<String, LiveDataPropertyDescriptor> descriptors = getDescriptors(configuration);
        assertEquals(List.of("id", "doc.title", "message", "context", "check", "falsePositiveReason", "severity",
            "falsePositiveBy", "falsePositiveDate"), List.copyOf(descriptors.keySet()));
        assertFalse(descriptors.get("id").isVisible());
        assertFalse(descriptors.get("id").isFilterable());

        LiveDataPropertyDescriptor title = descriptors.get("doc.title");
        assertEquals("Page Title", title.getName());
        assertEquals("docTitle", title.getDisplayer().getId());
        assertEquals("doc.url", title.getDisplayer().getParameters().get("propertyHref"));
        assertEquals("text", title.getFilter().getId());

        assertEquals("message label", descriptors.get("message").getName());
        assertEquals("text", descriptors.get("message").getFilter().getId());

        LiveDataPropertyDescriptor severity = descriptors.get("severity");
        assertEquals("Severity", severity.getName());
        assertEquals("list", severity.getFilter().getId());
        assertEquals(List.of(Map.of("value", "Warning", "label", "Warning"), Map.of("value", "Error", "label", "Error")),
            severity.getFilter().getParameters().get("options"));
        assertEquals("equals", severity.getFilter().getDefaultOperator());

        LiveDataPropertyDescriptor markedBy = descriptors.get("falsePositiveBy");
        assertEquals("link", markedBy.getDisplayer().getId());
        assertEquals("falsePositiveBy_url", markedBy.getDisplayer().getParameters().get("propertyHref"));

        LiveDataPropertyDescriptor markedOn = descriptors.get("falsePositiveDate");
        assertEquals("falsePositiveDate label", markedOn.getName());
        assertEquals("Date", markedOn.getType());
        assertEquals("date", markedOn.getDisplayer().getId());
        assertEquals("date", markedOn.getFilter().getId());
        assertTrue(markedOn.isSortable());
    }

    @Test
    void getWhenTheViolationClassCannotBeLoaded() throws Exception
    {
        when(this.xwiki.getXClass(any(), any())).thenThrow(new XWikiException());

        Map<String, LiveDataPropertyDescriptor> descriptors = getDescriptors(this.provider.get());

        // The properties are labeled with their names, and the severity can't be filtered among its values.
        assertEquals("message", descriptors.get("message").getName());
        assertEquals(List.of(), descriptors.get("severity").getFilter().getParameters().get("options"));
        assertTrue(this.logCapture.getMessage(0).startsWith(
            "Failed to load the documentation violation class to label the Live Data properties"));
    }

    private Map<String, LiveDataPropertyDescriptor> getDescriptors(LiveDataConfiguration configuration)
    {
        return configuration.getMeta().getPropertyDescriptors().stream().collect(Collectors.toMap(
            LiveDataPropertyDescriptor::getId, Function.identity(), (first, second) -> first,
            LinkedHashMap::new));
    }
}
