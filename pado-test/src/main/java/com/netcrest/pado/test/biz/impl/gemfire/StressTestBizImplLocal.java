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
package com.netcrest.pado.test.biz.impl.gemfire;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import com.netcrest.pado.IBiz;
import com.netcrest.pado.IBizContextClient;
import com.netcrest.pado.IBizLocal;
import com.netcrest.pado.IPado;
import com.netcrest.pado.biz.IPathBiz;
import com.netcrest.pado.biz.IPathBiz.PathType;
import com.netcrest.pado.data.jsonlite.JsonLite;
import com.netcrest.pado.test.biz.IStressTestBiz;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class StressTestBizImplLocal implements IStressTestBiz, IBizLocal
{
	@Resource
	private IStressTestBiz biz;
	private IPado pado;

	private HashMap<String, JsonLite> pathConfigMap = new HashMap<String, JsonLite>();
	private int threadCountPerDriver = 5;
	private int loopCount = 1;
	private int batchSize = 1000;
	private int updateIntervalInMsec = 0;
	private boolean isIncludeObjectCreationTime = false;

	@Override
	public IBizContextClient getBizContext()
	{
		return biz.getBizContext();
	}

	@Override
	public void init(IBiz biz, IPado pado, Object... args)
	{
		this.pado = pado;
	}

	@Override
	public List<String> __start(Map<String, JsonLite> pathConfigMap, int threadCountPerDriver, int loopCount, boolean isIncludeObjectCreationTime)
	{
		// First, create all paths
		IPathBiz pathBiz = pado.getCatalog().newInstance(IPathBiz.class);
		Collection<JsonLite> col = pathConfigMap.values();
		for (JsonLite pathConfig : col) {
			if (pathConfig.get("Refid") != null) {
				boolean isTemporal = pathConfig.get("IsTemporal") != null && (Boolean)pathConfig.get("IsTemporal");
				boolean isLuceneDynamic = pathConfig.get("IsLuceneDynamic") != null && (Boolean)pathConfig.get("IsLuceneDynamic");
				pathBiz.createPath(biz.getBizContext().getGridService().getDefaultGridId(),
						(String) pathConfig.get("Path"), (String) pathConfig.get("Refid"),
						isTemporal, isLuceneDynamic, true);
			} else {
				pathBiz.createPath(biz.getBizContext().getGridService().getDefaultGridId(),
						(String) pathConfig.get("Path"), (PathType) pathConfig.get("PathType"), true);
			}
		}
		return biz.__start(pathConfigMap, threadCountPerDriver, loopCount, isIncludeObjectCreationTime);
	}

	@Override
	public List<String> start()
	{
		List<String> list = __start(pathConfigMap, threadCountPerDriver, loopCount, isIncludeObjectCreationTime);
		if (list != null) {
			Collections.sort(list);
		}
		return list;
	}

	@Override
	public boolean isComplete()
	{
		return biz.isComplete();
	}

	@Override
	public List<String> getStatus()
	{
		return biz.getStatus();
	}
	
	@Override
	public boolean isIncludeObjectCreationTime()
	{
		return isIncludeObjectCreationTime;
	}

	@Override
	public void setIncludeObjectCreationTime(boolean isIncludeObjectCreationTime)
	{
		this.isIncludeObjectCreationTime = isIncludeObjectCreationTime;
	}

	@Override
	public void addPath(String path)
	{
		pathConfigMap.put(path, createPathConfig(path));
	}
	
	@Override
	public void addPath(String fullPath, int payloadSize, int fieldCount, int updateIntervalInMsec, int totalEntryCount,
			int batchSize)
	{
		pathConfigMap.put(fullPath,
				createPathConfig(fullPath, PathType.LOCAL, payloadSize, fieldCount, updateIntervalInMsec, totalEntryCount, batchSize));
	}

	@Override
	public void addPath(String path, PathType pathType, int payloadSize, int fieldCount, int updateIntervalInMsec,
			int totalEntryCount, int batchSize)
	{
		pathConfigMap.put(path,
				createPathConfig(path, pathType, payloadSize, fieldCount, updateIntervalInMsec, totalEntryCount, batchSize));
	}

	@Override
	public void addPath(String path, String refid, boolean isTemporal, boolean isLuceneDynamic, int payloadSize,
			int fieldCount, int updateIntervalInMsec, int totalEntryCount, int batchSize)
	{
		pathConfigMap.put(path, createPathConfig(path, refid, isTemporal, isLuceneDynamic, payloadSize, fieldCount,
				updateIntervalInMsec, totalEntryCount, batchSize));
	}
	
	@Override
	public void addPath(JsonLite pathConfig)
	{
		if (pathConfig == null) {
			return;
		}
		if (pathConfig.get("Path") == null) {
			pathConfig.put("Path", "stress/test");
		}
		if (pathConfig.get("PathType") == null && pathConfig.get("Refid") == null) {
			pathConfig.put("PathType", PathType.TEMPORAL);
		}
		if (pathConfig.get("PayloadSize") == null) {
			pathConfig.put("PayloadSize", 1024);
		}
		if (pathConfig.get("FieldCount") == null) {
			pathConfig.put("FieldCount", 20);
		}
		if (pathConfig.get("UpdateIntervalInMsec") == null) {
			pathConfig.put("UpdateIntervalInMsec", 0);
		}
		if (pathConfig.get("TotalEntryCount") == null) {
			pathConfig.put("TotalEntryCount", 10000);
		}
		if (pathConfig.get("BatchSize") == null) {
			pathConfig.put("BatchSize", 1000);
		}
		pathConfigMap.put((String)pathConfig.get("Path"), pathConfig);
	}
	
	@Override
	public int getPathCount()
	{
		return pathConfigMap.size();
	}
	
	@Override
	public Set<String> getPathSet()
	{
		return pathConfigMap.keySet(); 
	}

	@Override
	public void removePath(String path)
	{
		pathConfigMap.remove(path);
	}

	@Override
	public void removeAllPaths()
	{
		IPathBiz pathBiz = pado.getCatalog().newInstance(IPathBiz.class);
		Set<String> pathSet = pathConfigMap.keySet();
		for (String path : pathSet) {
			pathBiz.remove(biz.getBizContext().getGridService().getDefaultGridId(), path, false);
		}
		pathConfigMap.clear();
	}

	@Override
	public void clearPath(String path)
	{
		IPathBiz pathBiz = pado.getCatalog().newInstance(IPathBiz.class);
		pathBiz.clear(biz.getBizContext().getGridService().getDefaultGridId(), path, true);
	}

	@Override
	public void clearAllPaths()
	{
//		IPathBiz pathBiz = pado.getCatalog().newInstance(IPathBiz.class);
	}

	@Override
	public int getThreadCountPerDriver()
	{
		return this.threadCountPerDriver;
	}

	@Override
	public void setThreadCountPerDriver(int threadCountPerDriver)
	{
		this.threadCountPerDriver = threadCountPerDriver;
	}

	@Override
	public int getLoopCount()
	{
		return loopCount;
	}

	@Override
	public void setLoopCount(int loopCount)
	{
		if (loopCount < 1) {
			this.loopCount = 1;
		} else {
			this.loopCount = loopCount;
		}
	}
	
	@Override
	public int getBatchSize()
	{
		return batchSize;
	}

	@Override
	public void setBatchSize(int batchSize)
	{
		if (batchSize <= 0) {
			this.batchSize = 1000;
		} else {
			this.batchSize = batchSize;
		}
	}

	@Override
	public int getUpdateIntervalInMsec()
	{
		return updateIntervalInMsec;
	}

	@Override
	public void setUpdateIntervalInMsec(int updateIntervalInMsec)
	{
		if (updateIntervalInMsec < 0) {
			this.updateIntervalInMsec = 0;
		} else {
			this.updateIntervalInMsec = updateIntervalInMsec;
		}
	}
	
	private JsonLite createPathConfig(String path)
	{
		return createPathConfig(path, PathType.TEMPORAL, 1024, 20, updateIntervalInMsec, 10000, batchSize);
	}

	public JsonLite createPathConfig(String path, IPathBiz.PathType pathType, int payloadSize, int fieldCount,
			int updateIntervalInMsec, int totalEntryCount, int batchSize)
	{
		JsonLite jl = new JsonLite();
		jl.put("Path", path);
		jl.put("PathType", pathType);
		jl.put("PayloadSize", payloadSize);
		jl.put("FieldCount", fieldCount);
		jl.put("UpdateIntervalInMsec", updateIntervalInMsec);
		jl.put("TotalEntryCount", totalEntryCount);
		jl.put("BatchSize", batchSize);
		return jl;
	}

	public JsonLite createPathConfig(String path, String refid, boolean isTemporal, boolean isLuceneDynamic,
			int payloadSize, int fieldCount, int updateIntervalInMsec, int totalEntryCount, int batchSize)
	{
		JsonLite jl = new JsonLite();
		jl.put("Path", path);
		jl.put("Refid", refid);
		jl.put("IsTemporal", isTemporal);
		jl.put("IsLuceneDynamic", isLuceneDynamic);
		jl.put("PayloadSize", payloadSize);
		jl.put("FieldCount", fieldCount);
		jl.put("UpdateIntervalInMsec", updateIntervalInMsec);
		jl.put("TotalEntryCount", totalEntryCount);
		jl.put("BatchSize", batchSize);
		return jl;
	}
}
