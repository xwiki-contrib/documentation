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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.internal.DocumentationPages;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryFilter;
import org.xwiki.query.QueryManager;
import org.xwiki.query.QueryParameter;

import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.CHECK;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.CONTEXT;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.DOC_TITLE;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.FALSE_POSITIVE_BY;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.FALSE_POSITIVE_DATE;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.FALSE_POSITIVE_REASON;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.MESSAGE;
import static org.xwiki.contrib.documentation.internal.livedata.ViolationsLiveDataConfigurationProvider.SEVERITY;

/**
 * Queries the documentation violations matching the filters of a Live Data query, one result per violation xobject.
 *
 * @version $Id$
 * @since 1.17
 */
@Component(roles = ViolationsLiveDataQueryHelper.class)
@Singleton
public class ViolationsLiveDataQueryHelper
{
    /**
     * The source parameter telling which violations to list: the ones marked as false positives when set to
     * {@link #ONLY}, the reported ones otherwise.
     */
    static final String FALSE_POSITIVES_PARAMETER = "falsePositives";

    static final String ONLY = "only";

    private static final String STRING_PROPERTY = "StringProperty";

    private static final String LARGE_STRING_PROPERTY = "LargeStringProperty";

    /**
     * The table holding the value of each property of the violation xclass that can be filtered and sorted.
     */
    private static final Map<String, String> PROPERTY_TABLES = Map.of(
        MESSAGE, STRING_PROPERTY,
        CONTEXT, STRING_PROPERTY,
        CHECK, STRING_PROPERTY,
        SEVERITY, STRING_PROPERTY,
        FALSE_POSITIVE_REASON, LARGE_STRING_PROPERTY,
        FALSE_POSITIVE_BY, LARGE_STRING_PROPERTY,
        FALSE_POSITIVE_DATE, "DateProperty");

    private static final String CONTAINS = "contains";

    private static final String STARTS_WITH = "startsWith";

    private static final String EQUALS = "equals";

    private static final String BEFORE = "before";

    private static final String AFTER = "after";

    private static final String BETWEEN = "between";

    private static final String AND = " and ";

    private static final String COMMA = ", ";

    private static final String VALUE = "%s.value";

    @Inject
    private QueryManager queryManager;

    /**
     * Excludes the hidden pages, unless the current user displays them. It's instantiated for each query since it reads
     * the preference of the current user when it's instantiated.
     */
    @Inject
    @Named("hidden/document")
    private Provider<QueryFilter> hiddenFilterProvider;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    /**
     * @param query the Live Data query
     * @return the number of violations matching the filters of the query
     * @throws QueryException if the query fails
     */
    public long count(LiveDataQuery query) throws QueryException
    {
        Statement statement = new Statement(query);
        List<Long> results = createQuery("select count(obj.id) " + statement.getFromAndWhere(), statement).execute();
        return results.get(0);
    }

    /**
     * @param query the Live Data query
     * @return the violations matching the filters of the query, sorted and paginated as the query says, each one
     *     holding the name of the page holding the violation and the number of the violation xobject
     * @throws QueryException if the query fails
     */
    public List<Object[]> getViolations(LiveDataQuery query) throws QueryException
    {
        Statement statement = new Statement(query);
        Query hqlQuery = createQuery(
            "select doc.fullName, obj.number " + statement.getFromAndWhere() + statement.getOrderBy(), statement);
        if (query.getOffset() != null) {
            hqlQuery.setOffset(query.getOffset().intValue());
        }
        if (query.getLimit() != null) {
            hqlQuery.setLimit(query.getLimit());
        }
        return hqlQuery.execute();
    }

    private Query createQuery(String statement, Statement parts) throws QueryException
    {
        Query query = this.queryManager.createQuery(statement, Query.HQL)
            .bindValue("className", this.localSerializer.serialize(DocumentationPages.VIOLATION_CLASS_REFERENCE))
            .addFilter(this.hiddenFilterProvider.get());
        parts.values.forEach(query::bindValue);
        for (Map.Entry<String, Like> like : parts.likes.entrySet()) {
            QueryParameter parameter = query.bindValue(like.getKey());
            if (like.getValue().anyCharsBefore()) {
                parameter.anyChars();
            }
            parameter.literal(like.getValue().literal());
            if (like.getValue().anyCharsAfter()) {
                parameter.anyChars();
            }
        }
        return query;
    }

    /**
     * A value matched with the {@code like} operator: a literal, optionally preceded and followed by any characters.
     */
    private record Like(boolean anyCharsBefore, String literal, boolean anyCharsAfter)
    {
    }

    /**
     * The parts of the HQL statement listing the violations matching the filters of a Live Data query, along with the
     * values of its parameters.
     */
    private static final class Statement
    {
        private final StringBuilder from = new StringBuilder("from XWikiDocument doc, BaseObject obj");

        private final List<String> where = new ArrayList<>();

        private final List<String> orderBy = new ArrayList<>();

        /**
         * The alias of the table joined for each property used by a filter or by the sort.
         */
        private final Map<String, String> aliases = new LinkedHashMap<>();

        private final Map<String, Object> values = new LinkedHashMap<>();

        private final Map<String, Like> likes = new LinkedHashMap<>();

        Statement(LiveDataQuery query)
        {
            this.where.add("obj.name = doc.fullName and obj.className = :className and doc.translation = 0");
            // The violations that have never been marked as false positives don't have a falsePositive property.
            Object falsePositives = query.getSource() != null
                ? query.getSource().getParameters().get(FALSE_POSITIVES_PARAMETER) : null;
            this.where.add(String.format("obj.id %sin (select fp.id.id from IntegerProperty fp "
                + "where fp.id.name = 'falsePositive' and fp.value = 1)", ONLY.equals(falsePositives) ? "" : "not "));
            if (query.getFilters() != null) {
                query.getFilters().forEach(this::addFilter);
            }
            if (query.getSort() != null) {
                for (LiveDataQuery.SortEntry sortEntry : query.getSort()) {
                    String expression = getExpression(sortEntry.getProperty());
                    if (expression != null) {
                        this.orderBy.add(expression + (sortEntry.isDescending() ? " desc" : " asc"));
                    }
                }
            }
            // List the violations in a stable order.
            this.orderBy.add("doc.fullName asc");
            this.orderBy.add("obj.number asc");
        }

        String getFromAndWhere()
        {
            return this.from + " where " + String.join(AND, this.where);
        }

        String getOrderBy()
        {
            return " order by " + String.join(COMMA, this.orderBy);
        }

        /**
         * @return the HQL expression of the value of the passed property, joining the table holding it if needed, or
         *     {@code null} if the property can't be filtered nor sorted
         */
        private String getExpression(String property)
        {
            if (DOC_TITLE.equals(property)) {
                return DOC_TITLE;
            }
            String table = PROPERTY_TABLES.get(property);
            if (table == null) {
                return null;
            }
            return String.format(VALUE, this.aliases.computeIfAbsent(property, key -> join(key, table)));
        }

        /**
         * Joins the table holding the value of the passed property.
         *
         * @return the alias of the joined table
         */
        private String join(String property, String table)
        {
            String alias = "prop" + this.aliases.size();
            this.from.append(COMMA).append(table).append(' ').append(alias);
            this.where.add(String.format("%1$s.id.id = obj.id and %1$s.id.name = :%1$s_name", alias));
            this.values.put(alias + "_name", property);
            return alias;
        }

        private void addFilter(LiveDataQuery.Filter filter)
        {
            List<String> clauses = new ArrayList<>();
            for (LiveDataQuery.Constraint constraint : filter.getConstraints()) {
                String value = constraint.getValue() == null ? "" : String.valueOf(constraint.getValue());
                if (StringUtils.isNotBlank(value)) {
                    String clause = getClause(filter.getProperty(), constraint.getOperator(), value);
                    if (clause != null) {
                        clauses.add(clause);
                    }
                }
            }
            if (!clauses.isEmpty()) {
                this.where.add("(" + String.join(filter.isMatchAll() ? AND : " or ", clauses) + ")");
            }
        }

        /**
         * @return the HQL clause matching the passed constraint, or {@code null} if the property can't be filtered or
         *     if the operator isn't supported for it
         */
        private String getClause(String property, String operator, String value)
        {
            if (FALSE_POSITIVE_DATE.equals(property)) {
                return getDateClause(operator, value);
            }
            String expression = getExpression(property);
            if (expression == null) {
                return null;
            }
            String parameter = newParameter();
            if (SEVERITY.equals(property)) {
                // The severity is one of the allowed values of the xclass.
                this.values.put(parameter, value);
                return expression + " = :" + parameter;
            }
            String lowerCaseValue = value.toLowerCase(Locale.ROOT);
            String clause = String.format("lower(%s) like :%s", expression, parameter);
            if (EQUALS.equals(operator)) {
                this.likes.put(parameter, new Like(false, lowerCaseValue, false));
            } else if (STARTS_WITH.equals(operator)) {
                this.likes.put(parameter, new Like(false, lowerCaseValue, true));
            } else if (operator == null || CONTAINS.equals(operator)) {
                this.likes.put(parameter, new Like(true, lowerCaseValue, true));
            } else {
                clause = null;
            }
            return clause;
        }

        /**
         * @param value an ISO 8601 date, or an ISO 8601 time interval for the {@code between} operator
         */
        private String getDateClause(String operator, String value)
        {
            String expression = getExpression(FALSE_POSITIVE_DATE);
            String clause = null;
            try {
                if (BETWEEN.equals(operator)) {
                    String[] range = StringUtils.split(value, '/');
                    if (range.length == 2) {
                        String start = addDate(range[0]);
                        clause = String.format("%s between :%s and :%s", expression, start, addDate(range[1]));
                    }
                } else if (BEFORE.equals(operator)) {
                    clause = expression + " < :" + addDate(value);
                } else if (AFTER.equals(operator)) {
                    clause = expression + " > :" + addDate(value);
                }
            } catch (DateTimeParseException e) {
                // Ignore the constraint, as the Live Data does with the values it can't handle.
                clause = null;
            }
            return clause;
        }

        /**
         * @return the name of a new parameter of the statement
         */
        private String newParameter()
        {
            return "value" + (this.values.size() + this.likes.size());
        }

        /**
         * @return the name of the parameter holding the passed date
         */
        private String addDate(String date)
        {
            String parameter = newParameter();
            this.values.put(parameter, Date.from(OffsetDateTime.parse(date).toInstant()));
            return parameter;
        }
    }
}
