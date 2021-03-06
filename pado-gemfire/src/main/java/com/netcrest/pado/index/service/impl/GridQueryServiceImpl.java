/*
 * Copyright (c) 2013-2015 Netcrest Technologies, LLC. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netcrest.pado.index.service.impl;

import java.util.concurrent.ConcurrentHashMap;

import com.gemstone.gemfire.cache.InterestResultPolicy;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionService;
import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.cache.client.PoolManager;
import com.netcrest.pado.gemfire.GemfirePado;
import com.netcrest.pado.index.exception.GridQueryException;
import com.netcrest.pado.index.exception.GridQueryResultSetExpiredException;
import com.netcrest.pado.index.gemfire.function.IndexMatrixBuildFunction;
import com.netcrest.pado.index.helper.FunctionExecutor;
import com.netcrest.pado.index.helper.FunctionExecutor.Realm;
import com.netcrest.pado.index.internal.Constants;
import com.netcrest.pado.index.internal.IndexMatrix;
import com.netcrest.pado.index.internal.IndexMatrixListener;
import com.netcrest.pado.index.internal.IndexMatrixManager;
import com.netcrest.pado.index.result.ClientResults;
import com.netcrest.pado.index.result.IndexServerResultsCollector;
import com.netcrest.pado.index.service.GridQuery;
import com.netcrest.pado.index.service.IGridQueryService;
import com.netcrest.pado.index.service.IScrollableResultSet;

public class GridQueryServiceImpl implements IGridQueryService
{
	// indexMatrixCountMap keeps track of the index matrix count to unregister
	// interest upon reaching the count of 0.
	private final static ConcurrentHashMap<Object, Integer> indexMatrixCountMap = new ConcurrentHashMap<Object, Integer>();

	protected Pool pool;
	protected RegionService regionService;

	/**
	 * For distributed system, i.e., members
	 */
	public GridQueryServiceImpl()
	{
		this(null, null);
	}

	/**
	 * Constructs an instance of GridQueryServiceImpl.
	 * 
	 * @param pool
	 *            Pool to use for client to server communications. regionService
	 *            takes predecence. If null, then the default pool,
	 *            {@link Constants#INDEX_POOL} is assigned.
	 * @param regionService
	 *            RegionService to use for client server communications. If null
	 *            then the pool is assigned instead.
	 */
	public GridQueryServiceImpl(Pool pool, RegionService regionService)
	{
		this.pool = pool;
		this.regionService = regionService;
		if (this.pool == null) {
			this.pool = PoolManager.find(Constants.INDEX_POOL);
			if (this.pool == null) {
				this.pool = GemfirePado.getIndexMatrixPool();
			}
		}
		IndexMatrixListener<Object, IndexMatrix> indexMatrixListener = new IndexMatrixListener<Object, IndexMatrix>(
				this);
		IndexMatrixManager.getIndexMatrixManager().getIndexMatrixRegion().getAttributesMutator()
				.addCacheListener(indexMatrixListener);
	}

	public RegionService getRegionService()
	{
		return regionService;
	}

	/**
	 * 
	 * @param queryString
	 * @param fetchSize
	 * @param isOrderBy
	 * @return
	 */
	// public IResultSet queryDirect(String queryString, int fetchSize, boolean
	// isOrderBy, QueryType queryType)
	// {
	// String regionPath = getRegionPath(queryString);
	//
	// Region region = CacheFactory.getAnyInstance().getRegion(regionPath);
	// if (region == null) {
	// return null;
	// }
	//
	// MemberRequest request = new MemberRequest();
	// // request.setId(UUID.randomUUID().toString());
	// request.setId(queryString);
	// request.setQueryString(queryString);
	// // int targetSize = (fetchSize / serverCount) + 1;
	// request.setFetchSize(fetchSize);
	// request.setTargetSize(targetSize);
	// request.setStartIndex(0);
	// request.setOrderBy(isOrderBy);
	//
	// try {
	// GridResults results = (GridResults)
	// FunctionService.onRegion(region).withArgs(request)
	// .withCollector(new MemberResultsCollector(request,
	// region)).execute(GridSearchFunction.Id)
	// .getResult();
	// results.buildIndexMatrix();
	// return new ResultSetImpl(results);
	// } catch (Exception ex) {
	// throw new GridQueryException(ex);
	// }
	// }

	public IScrollableResultSet query(GridQuery criteria) throws GridQueryException
	{
		IndexMatrix indexMatrix = getIndexMatrix(criteria);
		IScrollableResultSet results = null;

		// Register region interest for the index matrix
		// so that IndexMatrix updates occur automatically
		// in the region.
		// synchronized (indexMatrixCountMap) {
		registerInterestInIndexRegion(criteria.getId());
		// }
		if (indexMatrix == null) {
			results = queryIndexMatrixServer(criteria);
			((ClientResults) results).setGridQueryService(this);
		} else {
			results = directResultSet(criteria);
			((ClientResults) results).setGridQueryService(this);
		}
		return results;
	}

	protected synchronized void registerInterestInIndexRegion(Object id)
	{
		Region<Object, IndexMatrix> indexRegion = IndexMatrixManager.getIndexMatrixManager().getIndexMatrixRegion();
		if (indexMatrixCountMap.containsKey(id) == false) {
			indexRegion.registerInterest(id, InterestResultPolicy.KEYS_VALUES);
		}
	}

	protected synchronized void unregisterInterestInIndexRegion(Object id)
	{
		Region<Object, IndexMatrix> indexRegion = IndexMatrixManager.getIndexMatrixManager().getIndexMatrixRegion();
		indexRegion.unregisterInterest(id);
	}

	private IScrollableResultSet directResultSet(GridQuery criteria)
	{
		ClientResults results = null;
		try {
			results = newClientResultSet(criteria);
			results.goToSet(0);
		} catch (GridQueryResultSetExpiredException expiredEx) {
			// IndexMatrix expired, query again
			return query(criteria);

		} catch (Exception ex) {
			throw new GridQueryException(ex);
		}
		return results;
	}

	protected ClientResults newClientResultSet(GridQuery criteria)
	{
		return new ClientResults(criteria, this);
	}

	private ClientResults queryIndexMatrixServer(GridQuery criteria) throws GridQueryException
	{
		ClientResults results = null;
		try {
			Integer count = indexMatrixCountMap.get(criteria.getId());
			if (count == null) {
				count = 0;
			} else {
				count++;
			}
			indexMatrixCountMap.put(criteria.getId(), count);
			results = executeIndexBuildFunction(criteria);

			// if the result set is empty then the index matrix may
			// have been created during the request time by another
			// client's request. Get the index matrix from the
			// __index region and send the request directly to the
			// data grid.
			// if (results.getValueResultList() == null ||
			// results.getValueResultList().size() == 0) {
			// indexMatrix = manager.getIndexMatrix(query);
			// results = new ClientResults(request, region);
			// results.nextSet();
			// }
		} catch (Exception ex) {
			throw new GridQueryException(ex);
		}
		return results;
	}

	protected ClientResults executeIndexBuildFunction(GridQuery criteria) throws Exception
	{
		IndexServerResultsCollector collector = (IndexServerResultsCollector) FunctionExecutor.execute(Realm.SERVER,
				null, pool, regionService, null, criteria, new IndexServerResultsCollector(criteria),
				IndexMatrixBuildFunction.Id, null);
		return collector.getResult();
	}

	/**
	 * Returns the index matrix for the specified criteria if it already exists.
	 * It returns null if the criteria is forced-build or the index matrix is
	 * not found.
	 * 
	 * @param criteria
	 *            Grid search criteria
	 */
	protected IndexMatrix getIndexMatrix(GridQuery criteria)
	{
		IndexMatrixManager manager = IndexMatrixManager.getIndexMatrixManager();
		IndexMatrix indexMatrix = manager.getIndexMatrix(criteria.getId());
		if (indexMatrix != null) {
			// remove it if it is complete and force-rebuild
			if (indexMatrix.isComplete()) {
				if (criteria.isForceRebuildIndex()) {
					manager.removeIndexMatrix(criteria.getId());
					indexMatrix = null;
				}
			}
		}
		return indexMatrix;
	}

	/**
	 * Closes the specified index matrix id by releasing resources.
	 * 
	 * @param id
	 *            The index matrix id to close.
	 */
	public synchronized void close(Object id)
	{
		if (id == null) {
			return;
		}
		// synchronized (indexMatrixCountMap) {
		Integer count = indexMatrixCountMap.get(id);
		if (count == null) {
			return;
		}
		count--;
		if (count <= 0) {
			indexDestroyed(id);
		} else {
			indexMatrixCountMap.put((String) id, count);
		}
		// }

	}

	/**
	 * Returns true if the index matrix has not been created or has been closed.
	 * 
	 * @param id
	 *            Index matrix id
	 */
	public boolean isClosed(Object id)
	{
		return indexMatrixCountMap.containsKey(id);
	}

	/**
	 * Closes the specified index matrix id by releasing resources.
	 * 
	 * @param id
	 *            The index matrix id to close.
	 */
	public synchronized void indexDestroyed(Object id)
	{
		if (id == null) {
			return;
		}
		indexMatrixCountMap.remove(id);
		unregisterInterestInIndexRegion(id);
	}

	/**
	 * Returns if the indexMatrixCountMap has the query
	 */
	public boolean isIndexBuildingInProgress(String id)
	{
		if (indexMatrixCountMap.get(id) != null) {
			return true;
		} else {
			return false;
		}
	}
}
