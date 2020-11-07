/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.hmily.tac.sqlcompute.impl;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import lombok.RequiredArgsConstructor;
import org.dromara.hmily.repository.spi.entity.HmilyDataSnapshot;
import org.dromara.hmily.repository.spi.entity.HmilySQLTuple;
import org.dromara.hmily.tac.metadata.HmilyMetaDataManager;
import org.dromara.hmily.tac.metadata.model.TableMetaData;
import org.dromara.hmily.tac.sqlcompute.exception.SQLComputeException;
import org.dromara.hmily.tac.sqlparser.model.segment.generic.table.HmilySimpleTableSegment;
import org.dromara.hmily.tac.sqlparser.model.statement.dml.HmilyUpdateStatement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Hmily update SQL compute engine.
 *
 * @author zhaojun
 */
@RequiredArgsConstructor
public final class HmilyUpdateSQLComputeEngine extends AbstractHmilySQLComputeEngine {
    
    private static final String UPDATE_COLUMN_SUFFIX = "$UPDATED$";
    
    private final HmilyUpdateStatement sqlStatement;
    
    @Override
    // FIXME fixture dataSnapshot for poc test
    public HmilyDataSnapshot generateSnapshot(final String sql, final List<Object> parameters, final Connection connection, final String resourceId) throws SQLComputeException {
        Map<String, Object> beforeImage = new LinkedHashMap<>();
        Map<String, Object> afterImage = new LinkedHashMap<>();
        HmilyDataSnapshot result = new HmilyDataSnapshot();
        if (sql.contains("order")) {
            beforeImage.put("status", 3);
            afterImage.put("number", sql.substring(sql.indexOf("'") + 1, sql.length() - 1));
            result.getTuples().add(new HmilySQLTuple("order", "update", beforeImage, afterImage));
        } else if (sql.contains("account")) {
            beforeImage.put("balance", 100);
            afterImage.put("user_id", 10000);
            result.getTuples().add(new HmilySQLTuple("account", "update", beforeImage, afterImage));
        } else {
            beforeImage.put("total_inventory", 100);
            afterImage.put("product_id", 1);
            result.getTuples().add(new HmilySQLTuple("inventory", "update", beforeImage, afterImage));
        }
        return result;
    }
    
    @Override
    Collection<HmilySQLTuple> createTuples(final String sql, final List<Object> parameters, final Connection connection, final String resourceId) throws SQLException {
        Collection<HmilySQLTuple> result = new LinkedList<>();
        Preconditions.checkState(sqlStatement.getTables().size() == 1, "Do not support multiple tables in update statement");
        HmilySimpleTableSegment tableSegment = sqlStatement.getTables().iterator().next();
        String tableName = sql.substring(tableSegment.getStartIndex(), tableSegment.getStopIndex());
        String selectSQL = String.format("SELECT %s FROM %s %s", Joiner.on(",").join(getAllColumns(tableSegment), getUpdatedColumns(parameters)), tableName, getWhereCondition(sql));
        Collection<Map<String, Object>> records = executeQuery(connection, selectSQL, parameters);
        result.addAll(doConvert(records, tableName, resourceId));
        return result;
    }
    
    private String getAllColumns(final HmilySimpleTableSegment segment) {
        String result;
        if (segment.getAlias().isPresent()) {
            result = String.format("%s.*", segment.getAlias().get());
        } else if (segment.getOwner().isPresent()) {
            result = String.format("%s.%s.*", segment.getOwner(), segment.getTableName().getIdentifier().getValue());
        } else {
            result = String.format("%s.*", segment.getTableName().getIdentifier().getValue());
        }
        return result;
    }
    
    private List<String> getUpdatedColumns(final List<Object> parameters) {
        List<String> result = new LinkedList<>();
        sqlStatement.getSetAssignment().getAssignments().forEach(assignment -> result.add(
            String.format("%s AS %s", ExpressionHandler.getValue(parameters, assignment.getValue()), assignment.getColumn().getIdentifier().getValue() + UPDATE_COLUMN_SUFFIX)));
        return result;
    }
    
    private String getWhereCondition(final String sql) {
        return sqlStatement.getWhere().map(segment -> sql.substring(segment.getStartIndex(), segment.getStopIndex()))
            .orElseThrow(() -> new SQLComputeException("DML SQL should contain where condition"));
    }
    
    private Collection<Map<String, Object>> executeQuery(final Connection connection, final String sql, final List<Object> parameters) throws SQLException {
        Collection<Map<String, Object>> result = new LinkedList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int parameterIndex = 1;
            for (Object each : parameters) {
                preparedStatement.setObject(parameterIndex, each);
                parameterIndex++;
            }
            ResultSet resultSet = preparedStatement.executeQuery();
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            while (resultSet.next()) {
                Map<String, Object> record = new LinkedHashMap<>();
                for (int columnIndex = 1; columnIndex <= resultSetMetaData.getColumnCount(); columnIndex++) {
                    record.put(resultSetMetaData.getColumnLabel(columnIndex), resultSet.getObject(columnIndex));
                    result.add(record);
                }
            }
        }
        return result;
    }
    
    private Collection<HmilySQLTuple> doConvert(final Collection<Map<String, Object>> records, final String tableName, final String resourceId) {
        Collection<HmilySQLTuple> result = new LinkedList<>();
        TableMetaData tableMetaData = HmilyMetaDataManager.get(resourceId).getTableMetaDataMap().get(tableName);
        for (Map<String, Object> record : records) {
            Map<String, Object> before = new LinkedHashMap<>();
            Map<String, Object> after = new LinkedHashMap<>();
            record.forEach((key, value) -> {
                if (!key.contains(UPDATE_COLUMN_SUFFIX)) {
                    before.put(key, value);
                } else {
                    // TODO skip date column here
                    after.put(key.replace(UPDATE_COLUMN_SUFFIX, ""), value);
                }
            });
            result.add(new HmilySQLTuple(tableName, "update", before, after));
        }
        return result;
    }
}
