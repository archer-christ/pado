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
package com.netcrest.pado.gemfire;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Resource;

import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionService;
import com.gemstone.gemfire.cache.client.Pool;
import com.netcrest.pado.IBiz;
import com.netcrest.pado.IBizInfo;
import com.netcrest.pado.IBizJson;
import com.netcrest.pado.IBizLocal;
import com.netcrest.pado.ICatalog;
import com.netcrest.pado.IPado;
import com.netcrest.pado.biz.gemfire.client.proxy.handlers.LocalInvocationHandler;
import com.netcrest.pado.biz.info.BizInfoFactory;
import com.netcrest.pado.data.jsonlite.JsonLiteArray;
import com.netcrest.pado.exception.PadoException;
import com.netcrest.pado.info.AppInfo;
import com.netcrest.pado.info.BizInfo;
import com.netcrest.pado.info.LoginInfo;
import com.netcrest.pado.internal.biz.util.BizUtil;
import com.netcrest.pado.internal.factory.BizManagerFactory;
import com.netcrest.pado.internal.impl.GridService;
import com.netcrest.pado.internal.impl.PadoClientManager;
import com.netcrest.pado.internal.server.BizManager;
import com.netcrest.pado.log.Logger;

public class CatalogImpl implements ICatalog
{
	private IPado pado;
	private String appId;
	private RegionService padoRegionService;
	private Object token;
	private GemfireGridService gridService;
	private Set<String> gridIdSet = new TreeSet();
	private Map<String, BizManager> bizManagerMap = new HashMap<String, BizManager>();
	private PadoClientManager clientManager = PadoClientManager.getPadoClientManager();

	public CatalogImpl(IPado pado, Properties credentials, LoginInfo loginInfo, AppInfo appInfo,
			RegionService padoRegionService, Pool padoPool, Pool indexMatrixPool, Region routerRegion)
	{
		this.pado = pado;
		this.appId = appInfo.getAppId();
		this.padoRegionService = padoRegionService;
		this.token = loginInfo.getToken();

		this.gridService = new GemfireGridService(appId, credentials, token, loginInfo.getUsername(), padoRegionService,
				padoPool, indexMatrixPool, routerRegion);

		// Register all login authorized IBiz classes
		registerBizClasses(loginInfo.getBizSet());
		String gridIds[] = appInfo.getGridIds();
		for (String gridId : gridIds) {
			addGridId(gridId);
		}
	}

	public String getAppId()
	{
		return this.appId;
	}

	public GridService getGridService()
	{
		return gridService;
	}

	public void registerBizClasses(Set<BizInfo> bizInfoSet)
	{
		if (bizInfoSet != null) {
			for (BizInfo bizInfo : bizInfoSet) {
				if (bizInfo.getBizInterfaceName() != null) {
					try {
						Class<?> bizClass = bizInfo.getClass().getClassLoader()
								.loadClass(bizInfo.getBizInterfaceName());
						BizManager<IBiz> bizManager = BizManagerFactory.getBizManagerFactory()
								.createBizManager(bizClass, true);
						bizManager.setAppId(appId);
						bizManager.setGridService(gridService);
						bizManager.init();
						bizManagerMap.put(bizManager.getTargetClass().getName(), bizManager);
					} catch (ClassNotFoundException ex) {
						Logger.error(ex);
					}
				}
			}
		}
	}

	/**
	 * Returns a new instance of the specified biz class. It returns null if the
	 * user does not have proper access rights.
	 */
	@Override
	public <T> T newInstance(Class<T> bizClass, Object... bizLocalArguments)
	{
		BizManager<IBiz> bizManager = getGemFireBizManager(bizClass);
		if (bizManager == null) {
			return null;
		}

		// If local implementation is defined then return a local instance
		String localImplClassName = BizUtil.getExplicitLocalImplClassName(bizClass);
		if (localImplClassName != null && localImplClassName.length() > 0) {
			try {
				IBizLocal bizLocal = (IBizLocal) bizManager.getTargetClass().getClassLoader()
						.loadClass(localImplClassName).newInstance();
				return newInstanceLocalProxy(bizManager, bizLocal, bizLocalArguments);
			} catch (Exception e) {
				Logger.error("Error occurred while instantiating IBizLocal class: " + localImplClassName + ".", e);
				throw new PadoException(
						"Error occurred while instantiating IBizLocal class: " + localImplClassName + ".", e);
			}
		} else {
			try {
				localImplClassName = BizUtil.getImplicitBizLocalImplClassName(bizClass);
				IBizLocal bizLocal = (IBizLocal) bizManager.getTargetClass().getClassLoader()
						.loadClass(localImplClassName).newInstance();
				return newInstanceLocalProxy(bizManager, bizLocal, bizLocalArguments);
			} catch (ClassNotFoundException e) {
				// No IBizLocal. Use proxy instead. This is normal.
				return (T) bizManager.newClientProxy();
			} catch (Exception e) {
				Logger.error("Error occurred while creating a IBizLocal object: " + localImplClassName
						+ ". Assigning a proxy object instead.", e);
				return (T) bizManager.newClientProxy();
			}
		}
	}

	@Override
	public <T> T newInstanceLocal(Class<T> bizClass, IBizLocal bizLocal, Object... bizLocalArguments)
	{
		BizManager<IBiz> bizManager = this.getGemFireBizManager(bizClass);
		if (bizManager == null) {
			return null;
		}
		return newInstanceLocalProxy(bizManager, bizLocal, bizLocalArguments);
	}

	private <T> T newInstanceLocalProxy(BizManager<IBiz> bizManager, IBizLocal bizLocal, Object... bizLocalArguments)
	{
		IBiz biz = bizManager.newClientProxy();
		for (Field f : bizLocal.getClass().getDeclaredFields()) {
			if (f.getAnnotation(Resource.class) != null && f.getType() == bizManager.getTargetClass()) {
				f.setAccessible(true);
				try {
					f.set(bizLocal, biz);
				} catch (IllegalArgumentException e) {
					Logger.warning("Failed to set function context on field [" + f.getName() + "]", e);
				} catch (IllegalAccessException e) {
					Logger.warning("Failed to set function context on field [" + f.getName() + "]", e);
				}
			}
		}
		if (bizLocalArguments != null && bizLocalArguments.length == 1 && bizLocal instanceof IBizJson
				&& bizLocalArguments[0] instanceof JsonLiteArray) {
			((IBizJson) bizLocal).init(biz, pado, (JsonLiteArray) bizLocalArguments[0]);
		} else {
			bizLocal.init(biz, pado, bizLocalArguments);
		}
		biz.getBizContext().getBizStatistics().setLocal(true);
		Class ibizClass = bizManager.getTargetClass();
		InvocationHandler handler = new LocalInvocationHandler<T>(ibizClass, bizLocal);
		return (T) Proxy.newProxyInstance(ibizClass.getClassLoader(), new Class[] { ibizClass }, handler);
	}

	@Override
	public IBiz newInstance(String bizInterfaceName, Object... bizLocalArguments)
	{
		BizManager<IBiz> bizManager = this.getBizManager(bizInterfaceName);
		if (bizManager == null) {
			return null;
		}
		Class<?> bizClass = bizManager.getTargetClass();
		// If local implementation is defined then return a local instance
		String localImplClassName = BizUtil.getExplicitLocalImplClassName(bizClass);
		if (localImplClassName != null && localImplClassName.length() > 0) {
			try {
				Class<?> bizLocalClass = bizManager.getTargetClass().getClassLoader().loadClass(localImplClassName);
				IBizLocal bizLocal = (IBizLocal) bizLocalClass.newInstance();
				return (IBiz) newInstanceLocalProxy(bizManager, bizLocal, bizLocalArguments);
			} catch (Exception e) {
				Logger.error("Error occurred while instantiating IBizLocal class: " + localImplClassName + ".", e);
				throw new PadoException(
						"Error occurred while instantiating IBizLocal class: " + localImplClassName + ".", e);
			}
		} else {
			localImplClassName = BizUtil.getImplicitBizLocalImplClassName(bizClass);
			try {
				Class<?> bizLocalClass = bizManager.getTargetClass().getClassLoader().loadClass(localImplClassName);
				IBizLocal bizLocal = (IBizLocal) bizLocalClass.newInstance();
				return (IBiz) newInstanceLocalProxy(bizManager, bizLocal, bizLocalArguments);
			} catch (Exception e) {
				return bizManager.newClientProxy();
			}
		}
	}

	@Override
	public IBizLocal newInstanceLocal(String bizInterfaceName, String bizLocalClassName, Object... bizLocalArguments)
	{
		BizManager<IBiz> bizManager = this.getBizManager(bizInterfaceName);
		if (bizManager == null) {
			return null;
		}
		try {
			Class<?> bizLocalClass = bizManager.getTargetClass().getClassLoader().loadClass(bizLocalClassName);
			IBizLocal bizLocal = (IBizLocal) bizLocalClass.newInstance();
			return newInstanceLocalProxy(bizManager, bizLocal, bizLocalArguments);
		} catch (ClassNotFoundException ex) {
			Logger.warning("Undefined IBizLocal class, " + bizLocalClassName + ". Aborted.", ex);
		} catch (Exception ex) {
			Logger.warning(ex);
		}
		return null;
	}

	/**
	 * Returns the RPC manager that manages the specified IBiz class.
	 * 
	 * @param bizClass
	 *            IBiz interface class
	 */
	private BizManager<IBiz> getGemFireBizManager(Class<?> bizClass)
	{
		BizManager<IBiz> bizManager = bizManagerMap.get(bizClass.getName());
		if (bizManager == null) {
			if (BizUtil.isFuture(bizClass)) {
				if (bizManagerMap.containsKey(BizUtil.getBizInterfaceName(bizClass))) {
					bizManager = BizManagerFactory.getBizManagerFactory().createBizManager(bizClass, true);
					bizManager.setAppId(appId);
					bizManager.setGridService(gridService);
					bizManager.init();
					bizManagerMap.put(bizManager.getTargetClass().getName(), bizManager);
				}
			}
		}
		return bizManager;
	}

	private BizManager<IBiz> getBizManager(String bizInterfaceName)
	{
		BizManager<IBiz> bizManager = bizManagerMap.get(bizInterfaceName);
		if (bizManager == null) {
			// isFuture() assumes all Future class names end with "Future".
			if (BizUtil.isFuture(bizInterfaceName)) {
				// Load the future class using the same IBiz target class
				// loader.
				bizManager = bizManagerMap.get(BizUtil.getBizInterfaceNameOfFuture(bizInterfaceName));
				if (bizManager != null) {
					Class<?> bizClass = bizManager.getTargetClass();
					try {
						Class<?> futureBizClass = bizClass.getClassLoader().loadClass(bizInterfaceName);
						bizManager = BizManagerFactory.getBizManagerFactory().createBizManager(futureBizClass, true);
						bizManager.init();
						bizManagerMap.put(bizInterfaceName, bizManager);
					} catch (ClassNotFoundException e) {
						Logger.warning(
								"Attempted to load undefined Future biz class, " + bizInterfaceName + ". Aborted.");
					}
				}
			}
		}
		return bizManager;
	}

	@Override
	public String[] getAllBizClassNames()
	{
		return getBizClassNames(null);
	}

	@Override
	public String[] getBizClassNames(String regex)
	{
		ArrayList<String> list = new ArrayList<String>(bizManagerMap.keySet());
		if (regex != null) {
			Iterator<String> iterator = list.iterator();
			while (iterator.hasNext()) {
				String className = iterator.next();
				if (className.matches(regex) == false) {
					iterator.remove();
				}
			}
		}
		Collections.sort(list);
		return list.toArray(new String[list.size()]);
	}

	@Override
	public Class<?>[] getAllBizClasses()
	{
		return getBizClasses(null);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Class<?>[] getBizClasses(String regex)
	{
		Collection<BizManager> col = bizManagerMap.values();
		if (regex != null) {
			ArrayList<Class<?>> list = new ArrayList<Class<?>>(col.size());
			for (BizManager manager : col) {
				Class<?> clazz = manager.getTargetClass();
				if (clazz.getName().matches(regex)) {
					list.add(clazz);
				}
			}
			return list.toArray(new Class<?>[list.size()]);
		} else {
			Class<?>[] classes = new Class<?>[col.size()];
			int i = 0;
			for (BizManager manager : col) {
				classes[i++] = manager.getTargetClass();
			}
			return classes;
		}
	}

	@Override
	public IBizInfo[] getAllBizInfos()
	{
		return getBizInfos(null);
	}

	@Override
	public IBizInfo[] getBizInfos(String regex)
	{
		Class<?>[] bizClasses = getBizClasses(regex);
		IBizInfo[] bizInfos = new IBizInfo[bizClasses.length];
		for (int i = 0; i < bizClasses.length; i++) {
			bizInfos[i] = BizInfoFactory.createBizInfo(bizClasses[i]);
		}
		return bizInfos;
	}

	/**
	 * Removes all biz object classes and grid IDs
	 */
	public void clear()
	{
		Collection<BizManager> col = bizManagerMap.values();
		for (BizManager gemfireRpcServiceMgr : col) {
			gemfireRpcServiceMgr.close();
		}
		bizManagerMap.clear();
		gridIdSet.clear();
	}

	@Override
	public void refresh()
	{
		gridService.refresh();
	}

	/**
	 * Close the catalog. Upon return of this call, the catalog object is no
	 * longer valid. This method is invoked when the user logs out from Pado.
	 */
	@Override
	public void close()
	{
		clear();
		padoRegionService = null;
		token = null;
		gridService = null;
	}

	@Override
	public String[] getGridIds()
	{
		return gridIdSet.toArray(new String[gridIdSet.size()]);
	}

	public void addGridId(String gridId)
	{
		gridIdSet.add(gridId);
	}

	public void removeGridId(String gridId)
	{
		gridIdSet.remove(gridId);
	}
}
