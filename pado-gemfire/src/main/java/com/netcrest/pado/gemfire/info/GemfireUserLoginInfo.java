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
package com.netcrest.pado.gemfire.info;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Set;

import com.gemstone.gemfire.DataSerializable;
import com.gemstone.gemfire.DataSerializer;
import com.netcrest.pado.info.BizInfo;
import com.netcrest.pado.info.UserLoginInfo;
import com.netcrest.pado.internal.security.AESCipher;
import com.netcrest.pado.server.PadoServerManager;

public class GemfireUserLoginInfo extends UserLoginInfo implements DataSerializable
{
	private static final long serialVersionUID = 1L;

	private String gridRootRegionPath;
	private boolean singleHopEnabled = false;
	private boolean multiuserAuthenticationEnabled = false;

	public GemfireUserLoginInfo()
	{
	}
	
	public GemfireUserLoginInfo(GemfireLoginInfo loginInfo)
	{
		this.appId = loginInfo.getAppId();
		this.bizSet = loginInfo.getBizSet();
		this.childGridIds = loginInfo.getChildGridIds();
		this.gridId = loginInfo.getGridId();
		this.gridRootRegionPath = loginInfo.getGridRootRegionPath();
		this.locators = loginInfo.getLocators();
		this.multiuserAuthenticationEnabled = loginInfo.isMultiuserAuthenticationEnabled();
		this.connectionName = loginInfo.getConnectionName();
		this.sharedConnectionName = loginInfo.getSharedConnectionName();
		this.singleHopEnabled = loginInfo.isSingleHopEnabled();
		this.token = loginInfo.getToken();
		this.username = loginInfo.getUsername();
		if (PadoServerManager.getPadoServerManager().isEncryptionEnabled()) {
			this.privateKey = AESCipher.getPrivateKey();
		}
	}
	
	public GemfireUserLoginInfo(String appId, String domain, String username, Object token, Set<BizInfo> bizSet, byte[] privateKey)
	{
		super(appId, domain, username, token, bizSet, privateKey);
	}

	/**
	 * Returns the pado's root region path.
	 */
	public String getGridRootRegionPath()
	{
		return gridRootRegionPath;
	}

	public void setGridRootRegionPath(String gridRootRegionPath)
	{
		this.gridRootRegionPath = gridRootRegionPath;
	}

	public boolean isSingleHopEnabled()
	{
		return singleHopEnabled;
	}

	public void setSingleHopEnabled(boolean singleHopEnabled)
	{
		this.singleHopEnabled = singleHopEnabled;
	}

	public boolean isMultiuserAuthenticationEnabled()
	{
		return multiuserAuthenticationEnabled;
	}

	public void setMultiuserAuthenticationEnabled(boolean multiuserAuthenticationEnabled)
	{
		this.multiuserAuthenticationEnabled = multiuserAuthenticationEnabled;
	}

	@Override
	public void fromData(DataInput input) throws IOException, ClassNotFoundException
	{
		appId = DataSerializer.readString(input);
		username = DataSerializer.readString(input);
		token = DataSerializer.readObject(input);
		bizSet = DataSerializer.readObject(input);
		gridId = DataSerializer.readString(input);
		gridRootRegionPath = DataSerializer.readString(input);
		connectionName = DataSerializer.readString(input);
		sharedConnectionName = DataSerializer.readString(input);
		locators = DataSerializer.readString(input);
		singleHopEnabled = DataSerializer.readPrimitiveBoolean(input);
		multiuserAuthenticationEnabled = DataSerializer.readPrimitiveBoolean(input);
		childGridIds = DataSerializer.readStringArray(input);
		privateKey = DataSerializer.readByteArray(input);
	}

	@Override
	public void toData(DataOutput output) throws IOException
	{
		DataSerializer.writeString(appId, output);
		DataSerializer.writeString(username, output);
		DataSerializer.writeObject(token, output);
		DataSerializer.writeObject(bizSet, output);
		DataSerializer.writeString(gridId, output);
		DataSerializer.writeString(gridRootRegionPath, output);
		DataSerializer.writeString(connectionName, output);
		DataSerializer.writeString(sharedConnectionName, output);
		DataSerializer.writeString(locators, output);
		DataSerializer.writePrimitiveBoolean(singleHopEnabled, output);
		DataSerializer.writePrimitiveBoolean(multiuserAuthenticationEnabled, output);
		DataSerializer.writeStringArray(childGridIds, output);
		DataSerializer.writeByteArray(privateKey, output);
	}
}
