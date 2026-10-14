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

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.documentation.internal.DocumentationPages;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryFilter;
import org.xwiki.query.QueryManager;
import org.xwiki.query.QueryParameter;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViolationsLiveDataQueryHelper}.
 *
 * @version $Id$
 */
@ComponentTest
class ViolationsLiveDataQueryHelperTest
{
    private static final String CLASS_NAME = "DocApp.Code.DocumentationViolationClass";

    private static final String FROM = "from XWikiDocument doc, BaseObject obj";

    private static final String WHERE = " where obj.name = doc.fullName and obj.className = :className "
        + "and doc.translation = 0 and obj.id not in (select fp.id.id from IntegerProperty fp "
        + "where fp.id.name = 'falsePositive' and fp.value = 1)";

    private static final String ORDER_BY = " order by doc.fullName asc, obj.number asc";

    private static final String SELECT = "select doc.fullName, obj.number ";

    @InjectMockComponents
    private ViolationsLiveDataQueryHelper queryHelper;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    @Named("hidden/document")
    private Provider<QueryFilter> hiddenFilterProvider;

    @MockComponent
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    private final QueryFilter hiddenFilter = mock(QueryFilter.class);

    private final QueryParameter parameter = mock(QueryParameter.class, RETURNS_SELF);

    @BeforeEach
    void setUp()
    {
        when(this.localSerializer.serialize(DocumentationPages.VIOLATION_CLASS_REFERENCE)).thenReturn(CLASS_NAME);
        when(this.hiddenFilterProvider.get()).thenReturn(this.hiddenFilter);
    }

    @Test
    void count() throws Exception
    {
        Query query = mockQuery("select count(obj.id) " + FROM + WHERE);
        when(query.execute()).thenReturn(List.of(3L));

        assertEquals(3L, this.queryHelper.count(new LiveDataQuery()));

        verify(query).bindValue("className", CLASS_NAME);
        verify(query).addFilter(this.hiddenFilter);
    }

    @Test
    void getViolationsWithoutFilterNorSort() throws Exception
    {
        Query query = mockQuery(SELECT + FROM + WHERE + ORDER_BY);
        List<Object> results = List.of(new Object[] {"Space.Page", 1});
        when(query.execute()).thenReturn(results);
        LiveDataQuery liveDataQuery = new LiveDataQuery();
        liveDataQuery.setOffset(15L);
        liveDataQuery.setLimit(30);

        assertSame(results, this.queryHelper.getViolations(liveDataQuery));

        verify(query).setOffset(15);
        verify(query).setLimit(30);
    }

    @Test
    void getViolationsMarkedAsFalsePositives() throws Exception
    {
        Query query = mockQuery(SELECT + FROM + WHERE.replace("obj.id not in", "obj.id in") + ORDER_BY);
        LiveDataQuery liveDataQuery = new LiveDataQuery();
        liveDataQuery.setSource(new LiveDataQuery.Source(ViolationsLiveDataSource.ID));
        liveDataQuery.getSource().setParameter("falsePositives", "only");

        this.queryHelper.getViolations(liveDataQuery);

        verify(query).execute();
    }

    @Test
    void getViolationsWithFilters() throws Exception
    {
        String statement = SELECT + FROM + ", StringProperty prop0, StringProperty prop1, StringProperty prop2" + WHERE
            + " and (lower(doc.title) like :value0)"
            + " and prop0.id.id = obj.id and prop0.id.name = :prop0_name"
            + " and (prop0.value = :value2 or prop0.value = :value3)"
            + " and prop1.id.id = obj.id and prop1.id.name = :prop1_name"
            + " and (lower(prop1.value) like :value5 and lower(prop1.value) like :value6)"
            + " and prop2.id.id = obj.id and prop2.id.name = :prop2_name"
            + " and (lower(prop2.value) like :value8)" + ORDER_BY;
        Query query = mockQuery(statement);
        LiveDataQuery liveDataQuery = new LiveDataQuery();
        liveDataQuery.setFilters(List.of(
            new LiveDataQuery.Filter("doc.title", "contains", "Install"),
            new LiveDataQuery.Filter("severity", "equals", false, "Error", "Warning"),
            new LiveDataQuery.Filter("message", "startsWith", true, "Missing", "missing"),
            new LiveDataQuery.Filter("check", "equals", "imageMacroAlt")));

        this.queryHelper.getViolations(liveDataQuery);

        verify(query).bindValue("prop0_name", "severity");
        verify(query).bindValue("value2", "Error");
        verify(query).bindValue("value3", "Warning");
        verify(query).bindValue("prop1_name", "message");
        verify(query).bindValue("prop2_name", "check");
        // The text values are matched ignoring the case.
        verify(query).bindValue("value0");
        verify(query).bindValue("value5");
        verify(query).bindValue("value6");
        verify(query).bindValue("value8");
        verify(this.parameter).literal("install");
        verify(this.parameter, times(2)).literal("missing");
        verify(this.parameter).literal("imagemacroalt");
    }

    @Test
    void getViolationsWithDateFilters() throws Exception
    {
        String statement = SELECT + FROM + ", DateProperty prop0" + WHERE
            + " and prop0.id.id = obj.id and prop0.id.name = :prop0_name"
            + " and (prop0.value between :value1 and :value2)"
            + " and (prop0.value < :value3)"
            + " and (prop0.value > :value4)" + ORDER_BY;
        Query query = mockQuery(statement);
        LiveDataQuery liveDataQuery = new LiveDataQuery();
        liveDataQuery.setFilters(List.of(
            new LiveDataQuery.Filter("falsePositiveDate", "between",
                "2026-10-01T00:00:00+02:00/2026-10-14T23:59:59+02:00"),
            new LiveDataQuery.Filter("falsePositiveDate", "before", "2026-10-14T00:00:00Z"),
            new LiveDataQuery.Filter("falsePositiveDate", "after", "2026-10-01T00:00:00Z")));

        this.queryHelper.getViolations(liveDataQuery);

        verify(query).bindValue("value1", Date.from(OffsetDateTime.parse("2026-10-01T00:00:00+02:00").toInstant()));
        verify(query).bindValue("value2", Date.from(OffsetDateTime.parse("2026-10-14T23:59:59+02:00").toInstant()));
        verify(query).bindValue("value3", Date.from(OffsetDateTime.parse("2026-10-14T00:00:00Z").toInstant()));
        verify(query).bindValue("value4", Date.from(OffsetDateTime.parse("2026-10-01T00:00:00Z").toInstant()));
    }

    @Test
    void getViolationsIgnoresTheConstraintsItCannotHandle() throws Exception
    {
        // The constraints with an empty value, on an unknown property, with an unsupported operator or with an invalid
        // date don't filter the violations. Only the joins of the known properties remain.
        String statement = SELECT + FROM + ", StringProperty prop0, DateProperty prop1" + WHERE
            + " and prop0.id.id = obj.id and prop0.id.name = :prop0_name"
            + " and prop1.id.id = obj.id and prop1.id.name = :prop1_name" + ORDER_BY;
        Query query = mockQuery(statement);
        LiveDataQuery liveDataQuery = new LiveDataQuery();
        liveDataQuery.setFilters(List.of(
            new LiveDataQuery.Filter("message", "contains", " "),
            new LiveDataQuery.Filter("unknown", "contains", "value"),
            new LiveDataQuery.Filter("doc.title", "contains", null),
            new LiveDataQuery.Filter("context", "regex", ".*"),
            new LiveDataQuery.Filter("falsePositiveDate", "between", "not a date/range"),
            new LiveDataQuery.Filter("falsePositiveDate", "between", "2026-10-01T00:00:00Z"),
            new LiveDataQuery.Filter("falsePositiveDate", "contains", "2026")));

        this.queryHelper.getViolations(liveDataQuery);

        verify(query).execute();
        verify(query, never()).bindValue(anyString());
    }

    @Test
    void getViolationsSorted() throws Exception
    {
        String statement = SELECT + FROM + ", LargeStringProperty prop0" + WHERE
            + " and prop0.id.id = obj.id and prop0.id.name = :prop0_name"
            + " order by prop0.value desc, doc.title asc, doc.fullName asc, obj.number asc";
        Query query = mockQuery(statement);
        LiveDataQuery liveDataQuery = new LiveDataQuery();
        liveDataQuery.setSort(List.of(new LiveDataQuery.SortEntry("falsePositiveReason", true),
            new LiveDataQuery.SortEntry("unknown"), new LiveDataQuery.SortEntry("doc.title")));

        this.queryHelper.getViolations(liveDataQuery);

        verify(query).bindValue("prop0_name", "falsePositiveReason");
    }

    private Query mockQuery(String statement) throws Exception
    {
        Query query = mock(Query.class, RETURNS_SELF);
        when(this.queryManager.createQuery(statement, Query.HQL)).thenReturn(query);
        when(query.bindValue(anyString())).thenReturn(this.parameter);
        return query;
    }
}
