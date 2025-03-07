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

package org.apache.shardingsphere.proxy.backend.handler.distsql.rdl.storage.unit;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.distsql.handler.exception.storageunit.InvalidStorageUnitsException;
import org.apache.shardingsphere.distsql.handler.exception.storageunit.MissingRequiredStorageUnitsException;
import org.apache.shardingsphere.distsql.handler.exception.storageunit.StorageUnitInUsedException;
import org.apache.shardingsphere.distsql.statement.rdl.drop.UnregisterStorageUnitStatement;
import org.apache.shardingsphere.infra.exception.core.ShardingSpherePreconditions;
import org.apache.shardingsphere.infra.exception.core.external.server.ShardingSphereServerException;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.resource.unit.StorageUnit;
import org.apache.shardingsphere.infra.rule.ShardingSphereRule;
import org.apache.shardingsphere.proxy.backend.context.ProxyContext;
import org.apache.shardingsphere.proxy.backend.response.header.ResponseHeader;
import org.apache.shardingsphere.proxy.backend.response.header.update.UpdateResponseHeader;
import org.apache.shardingsphere.proxy.backend.session.ConnectionSession;
import org.apache.shardingsphere.single.rule.SingleRule;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unregister storage unit backend handler.
 */
@Slf4j
public final class UnregisterStorageUnitBackendHandler extends StorageUnitDefinitionBackendHandler<UnregisterStorageUnitStatement> {
    
    public UnregisterStorageUnitBackendHandler(final UnregisterStorageUnitStatement sqlStatement, final ConnectionSession connectionSession) {
        super(sqlStatement, connectionSession);
    }
    
    @Override
    public ResponseHeader execute(final String databaseName, final UnregisterStorageUnitStatement sqlStatement) {
        checkSQLStatement(databaseName, sqlStatement);
        try {
            ProxyContext.getInstance().getContextManager().getInstanceContext().getModeContextManager().unregisterStorageUnits(databaseName, sqlStatement.getStorageUnitNames());
        } catch (final SQLException | ShardingSphereServerException ex) {
            log.error("Unregister storage unit failed", ex);
            throw new InvalidStorageUnitsException(Collections.singleton(ex.getMessage()));
        }
        return new UpdateResponseHeader(sqlStatement);
    }
    
    @Override
    public void checkSQLStatement(final String databaseName, final UnregisterStorageUnitStatement sqlStatement) {
        if (!sqlStatement.isIfExists()) {
            checkExisted(databaseName, sqlStatement.getStorageUnitNames());
        }
        checkInUsed(databaseName, sqlStatement);
    }
    
    private void checkExisted(final String databaseName, final Collection<String> storageUnitNames) {
        Map<String, StorageUnit> storageUnits = ProxyContext.getInstance().getDatabase(databaseName).getResourceMetaData().getStorageUnits();
        Collection<String> notExistedStorageUnits = storageUnitNames.stream().filter(each -> !storageUnits.containsKey(each)).collect(Collectors.toList());
        ShardingSpherePreconditions.checkState(notExistedStorageUnits.isEmpty(), () -> new MissingRequiredStorageUnitsException(databaseName, notExistedStorageUnits));
    }
    
    private void checkInUsed(final String databaseName, final UnregisterStorageUnitStatement sqlStatement) {
        ShardingSphereDatabase database = ProxyContext.getInstance().getDatabase(databaseName);
        Map<String, Collection<Class<? extends ShardingSphereRule>>> inUsedStorageUnits = database.getRuleMetaData().getInUsedStorageUnitNameAndRulesMap();
        Collection<String> inUsedStorageUnitNames = inUsedStorageUnits.keySet();
        inUsedStorageUnitNames.retainAll(sqlStatement.getStorageUnitNames());
        if (!inUsedStorageUnitNames.isEmpty()) {
            if (sqlStatement.isIgnoreSingleTables()) {
                checkInUsedIgnoreSingleTables(new HashSet<>(inUsedStorageUnitNames), inUsedStorageUnits);
            } else {
                String firstResource = inUsedStorageUnitNames.iterator().next();
                throw new StorageUnitInUsedException(firstResource, inUsedStorageUnits.get(firstResource));
            }
        }
    }
    
    private void checkInUsedIgnoreSingleTables(final Collection<String> inUsedResourceNames, final Map<String, Collection<Class<? extends ShardingSphereRule>>> inUsedStorageUnits) {
        for (String each : inUsedResourceNames) {
            Collection<Class<? extends ShardingSphereRule>> inUsedRules = inUsedStorageUnits.get(each);
            inUsedRules.remove(SingleRule.class);
            ShardingSpherePreconditions.checkState(inUsedRules.isEmpty(), () -> new StorageUnitInUsedException(each, inUsedRules));
        }
    }
}
