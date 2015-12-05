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
package com.netcrest.pado.biz.gemfire.proxy.functions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import com.gemstone.gemfire.cache.execute.FunctionContext;
import com.netcrest.pado.IBizContextServer;
import com.netcrest.pado.annotation.BizMethod;
import com.netcrest.pado.biz.gemfire.proxy.GemfireBizUtil;
import com.netcrest.pado.exception.PadoException;
import com.netcrest.pado.exception.PadoServerException;
import com.netcrest.pado.log.Logger;

/**
 * Proxy function installed for each Gemfire IBiz method in the service
 * interface when the current VM can actually execute the service logic and will
 * instantiate the service implementation class.
 */
@SuppressWarnings("serial")
public class ServerProxyFunction extends ProxyFunction
{
	private Class<?> bizImplClass;
	private Object bizImpl;
	private Field gridContextField;
	private BizContextServerSingletonImpl bizContext = null;
	private Map<String, Method> methodMap = new HashMap<String, Method>();
//	private Map<String, Method> iBizMethodMap = new HashMap<String, Method>();

	/**
	 * Constructs a server-side proxy function for the specified IBiz class.
	 * 
	 * @param ibizClass
	 *            IBiz class
	 */
	public ServerProxyFunction(Class<?> ibizClass)
	{
		super(ibizClass);

		try {
			bizImplClass = ibizClass.getClassLoader().loadClass(GemfireBizUtil.getBizImplClassName(ibizClass));
			this.bizImpl = bizImplClass.newInstance();
			// this.targetMethod = targetImpl.getClass().getMethod(m.getName(),
			// m.getParameterTypes());
		} catch (Exception e) {
			Logger.error(e);
			throw new PadoServerException(e);
		}

		// Cache all valid methods in the target implementation class
		// for (Method m : ibizClass.getMethods()) {
		// if (m.isAnnotationPresent(BizMethod.class)) {
		// String methodName = GemfireBizUtil.getMethodName(ibizClass, m);
		// methodMap.put(methodName, m);
		// if (Logger.isFineEnabled()) {
		// Logger.fine("Added method [" + ibizClass.getName() + "::" +
		// m.getName() + "] as part of target object ["
		// + getId() + "]");
		// }
		// }
		// }

		// Collect server implementation methods in methodMap.
		Map<String, Method> bizMap = getBizMethodMap(ibizClass);
		Map<String, Method> bizImplMap = getBizMethodMap(bizImpl.getClass());
		Set<Map.Entry<String, Method>> set = bizMap.entrySet();
		for (Map.Entry<String, Method> entry : set) {
			String methodId = entry.getKey();
			Method implMethod = bizImplMap.get(methodId);
			if (implMethod != null) {
				methodMap.put(methodId, implMethod);
//				iBizMethodMap.put(methodId, entry.getValue());
			}
			
		}
		
		// this.bizContext is for SINGLETON only
		switch (bizStateType) {
		case SINGLETON:

			for (Field f : bizImpl.getClass().getDeclaredFields()) {
				if (f.getAnnotation(Resource.class) != null && f.getType() == IBizContextServer.class) {
					if (Logger.isFineEnabled()) {
						Logger.fine(bizImpl.getClass().getName() + ": Field [" + f.getName()
								+ "] annotated to be injected with function context");
					}
					f.setAccessible(true);
					gridContextField = f;
					try {
						bizContext = new BizContextServerSingletonImpl();
						gridContextField.set(bizImpl, bizContext);
					} catch (IllegalArgumentException e) {
						Logger.warning(bizImpl.getClass().getName() + ": Failed to set function context on field ["
								+ gridContextField.getName() + "]", e);
					} catch (IllegalAccessException e) {
						Logger.warning(bizImpl.getClass().getName() + ": Failed to set function context on field ["
								+ gridContextField.getName() + "]", e);
					}
				}
			}
			break;
		default:
			break;
		}
	}

	private Map<String, Method> getBizMethodMap(Class clazz)
	{
		HashMap<String, Method> map = new HashMap();
		for (Method m : clazz.getMethods()) {
			if (m.isAnnotationPresent(BizMethod.class)) {
				String methodName = GemfireBizUtil.getMethodName(m);
				map.put(methodName, m);
			}
		}
		return map;
	}

	/*
	 * Executes the service logic by forwarding the invocation to the service
	 * implementation class
	 * 
	 * @see ProxyFunction
	 */
	@Override
	public void execute(FunctionContext fc)
	{
		switch (bizStateType) {
		case SINGLETON:
			executeSingleton(fc);
			break;
		case STATELESS:
			executeStateless(fc);
			break;
		}
	}
	
	private void executeSingleton(FunctionContext fc) 
	{
		BizArguments bizArgs = (BizArguments) fc.getArguments();
		BizContextClientImpl client = (BizContextClientImpl) bizArgs.getBizContext();
		if (bizContext != null) {
			bizContext.addBizContextClient(client, fc, bizArgs.getAdditionalArgs(), bizArgs.getTransientData());
		}
		invokeMethod(fc);
		if (bizContext != null) {
			bizContext.removeBizContextClient();
		}
	}
	
	private void executeStateless(FunctionContext fc) 
	{
		BizArguments bizArgs = (BizArguments) fc.getArguments();
		BizContextClientImpl client = (BizContextClientImpl) bizArgs.getBizContext();
		BizContextServerImpl bizContext = new BizContextServerImpl(client, fc, bizArgs.getAdditionalArgs(), bizArgs.getTransientData());
		try {
			Object bizImpl = bizImplClass.newInstance();
			gridContextField.set(bizImpl, bizContext);
			invokeMethod(fc);
		} catch (Exception ex) {
			throw new PadoServerException(ex);
		}
	}
	
	private void invokeMethod(FunctionContext fc)
	{
		Object retval = null;

		// Invoke the method
		try {
			BizArguments bizArgs = (BizArguments) fc.getArguments();
			BizContextClientImpl client = (BizContextClientImpl) bizArgs.getBizContext();
			Method method = methodMap.get(bizArgs.getMethodName());
			if (method == null) {
				if (Logger.isInfoEnabled()) {
					Logger.info("The specified method does not exist [" + bizImpl.getClass().getName() + "."
							+ bizArgs.getMethodName());
				}
				throw new PadoServerException("The specified method does not exist [" + bizImpl.getClass().getName()
						+ "." + bizArgs.getMethodName());
			} else {
				if (Logger.isFineEnabled()) {
					Logger.info("Invoking method [" + bizImpl.getClass().getName() + "." + method.getName()
							+ "] with args [" + bizArgs.getArgs() + "]");
				}
				
				// For pure clients and non-IPadoBiz calls, validate the
				// token.
//				if (client != null && client.getGridId() == null && targetClass.getName().equals(IPadoBiz.class.getName()) == false) {
//					if (client.getUserContext() == null) {
//						throw new PadoServerException("Access denied. Invalid session. IUserContext not provided.");
//					} else {
//						boolean isTokenValid = PadoServerManager.getPadoServerManager().isValidToken(
//								client.getUserContext().getToken());
//						if (isTokenValid == false) {
//							LoginInfo loginInfo = PadoServerManager.getPadoServerManager().getLoginInfo(
//									client.getUserContext().getToken());
//							String appId = null;
//							String username = null;
//							if (loginInfo != null) {
//								appId = loginInfo.getAppId();
//								username = loginInfo.getUsername();
//							}
//
//							throw new PadoServerException(
//									"Access denied. Invalid session. Please login with a valid account. " + "[app-id="
//											+ appId + ", user=" + username + ", token="
//											+ client.getUserContext().getToken() + "]");
//						} else {
//							client.getUserContext().getUserInfo().setUserPrincipal(principal);
//						}
//					}
//				}
				
				// TODO: Provide a way to send intermittent results
//				Method ibizMethod = iBizMethodMap.get(bizArgs.getMethodName());
//				if (ibizMethod != null && ibizMethod.getReturnType() == Future.class) {
//					fc.getResultSender().sendResult(null);
//				}
				
				retval = method.invoke(bizImpl, bizArgs.getArgs());
			}
			// return result
			if (Logger.isFineEnabled()) {
				String message = "Completed " + bizImpl.getClass().getName() + ". " + method.getName()
						+ "] returning [";
				if (method.getReturnType() == void.class) {
					message += "void]";
				} else {
					message += retval + "]";
				}
				Logger.fine(message);
			}
			if (method.getReturnType() != void.class) {
				fc.getResultSender().lastResult(retval);
			} else {
				// Due to GemFire restrictions, we must send something for void
				// typed methods.
				fc.getResultSender().lastResult(null);
			}
		} catch (SecurityException ex) {
			Logger.error(ex);
			throw new PadoServerException(ex);
		} catch (IllegalArgumentException ex) {
			Logger.error(ex);
			throw new PadoServerException(ex);
		} catch (IllegalAccessException ex) {
			Logger.error(ex);
			throw new PadoServerException(ex);

		} catch (InvocationTargetException ex) {

			// InvocationTargetException wraps the exception raised by the
			// method
			// Do NOT log. Logging is not necessary since the exception is
			// handled
			// by the client.
			Throwable cause = ex.getCause();
			if (PadoException.class.isAssignableFrom(cause.getClass())) {
				throw (PadoException) cause;
			} else {
				throw new PadoServerException(cause);
			}

		} catch (Throwable th) {

			Logger.error(th);

			// Send only PadoException. Wrap the exception if not PadoException.
			Throwable cause = th;
			Throwable lastThrowable = cause;
			while (cause != null) {
				lastThrowable = cause;
				if (PadoException.class.isAssignableFrom(lastThrowable.getClass())) {
					throw (PadoException) lastThrowable;
				}
				cause = cause.getCause();
			}
			throw new PadoServerException(th);

		}
	}

	@Override
	public boolean isHA()
	{
		return false;
	}

	@Override
	public boolean optimizeForWrite()
	{
		return true;
	}
}
