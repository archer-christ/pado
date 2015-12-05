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
package com.netcrest.pado.demo.bank.market.data.v;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.ArrayList;

import com.netcrest.pado.data.KeyType;
import com.netcrest.pado.data.KeyTypeManager;
import com.netcrest.pado.data.jsonlite.annotation.KeyReference;

/**
 * @pado 1:-1:0 3887315310445677729:-5436818777130160768
 * @version 1 dpark:10/03/12.22.29.39.EDT
 * @padocodegen Updated Wed Oct 03 22:29:39 EDT 2012
 */
public enum CompanyInfoKeyType_v1 implements KeyType {
	/**
	 * SecId: <b>String</b>
	 */
	KSecId("SecId", String.class, false, true),
	/**
	 * Company: <b>String</b>
	 */
	KCompany("Company", String.class, false, true),
	/**
	 * Country: <b>String</b>
	 */
	KCountry("Country", String.class, false, true),
	/**
	 * Gics: <b>Integer</b>
	 */
	KGics("Gics", Integer.class, false, true),
	/**
	 * Sector: <b>String</b>
	 */
	KSector("Sector", String.class, false, true);

	private static final Object ID = new UUID(3887315310445677729L,
			-5436818777130160768L);
	private static final int VERSION = 1;
	private static int keyIndex;
	private static boolean payloadKeepSerialized;

	private static int getNextIndex() {
		keyIndex++;
		return keyIndex - 1;
	}

	private CompanyInfoKeyType_v1(String name, Class type, boolean isDeprecated,
			boolean keyKeepSerialized) {
		this.index = getNextIndex();
		this.name = name;
		this.type = type;
		this.isDeprecated = isDeprecated;
		this.keyKeepSerialized = keyKeepSerialized;
		try {
			Field field = this.getClass().getField("K" + name);
			Annotation annotations[] = field.getAnnotations();
			for (Annotation annotation : annotations) {
				if (annotation.annotationType() == KeyReference.class) {
					isReference = true;
					break;
				}
			}
		} catch (Exception e) {
			// ignore
		}
	}

	private int index;
	private String name;
	private Class type;
	private boolean isDeprecated;
	private boolean keyKeepSerialized;
	private boolean isReference;

	public int getIndex() {
		return index;
	}

	public String getName() {
		return name;
	}

	public Class getType() {
		return type;
	}

	private static final Map<String, KeyType> keyNameMap;
	private static final int[] deprecatedIndexes;

	static {
		KeyType values[] = values();
		HashMap<String, KeyType> map = new HashMap<String, KeyType>(
				values.length + 1, 1f);
		ArrayList<Integer> list = new ArrayList(values.length);
		for (int i = 0; i < values.length; i++) {
			map.put(values[i].getName(), values[i]);
			if (values[i].isKeyKeepSerialized()) {
				payloadKeepSerialized = true;
			}
			if (values[i].isDeprecated()) {
				list.add(i);
			}
		}
		keyNameMap = Collections.unmodifiableMap(map);
		deprecatedIndexes = new int[list.size()];
		for (int i = 0; i < deprecatedIndexes.length; i++) {
			deprecatedIndexes[i] = list.get(i);
		}
	}

	public Object getId() {
		return ID;
	}

	public int getMergePoint() {
		return +-1;
	}

	public int getVersion() {
		return VERSION;
	}

	public int getKeyCount() {
		return values().length;
	}

	public KeyType[] getValues(int version) {
		return KeyTypeManager.getValues(this, version);
	}

	public static KeyType getKeyType() {
		return values()[0];
	}

	public KeyType getKeyType(String name) {
		return keyNameMap.get(name);
	}

	public KeyType[] getValues() {
		return values();
	}

	public boolean isDeltaEnabled() {
		return false;
	}

	public boolean isDeprecated() {
		return isDeprecated;
	}

	public int[] getDeprecatedIndexes() {
		return deprecatedIndexes;
	}

	public boolean isKeyKeepSerialized() {
		return keyKeepSerialized;
	}

	public boolean isCompressionEnabled() {
		return false;
	}

	public boolean isPayloadKeepSerialized() {
		return payloadKeepSerialized;
	}

	public Set<String> getNameSet() {
		return keyNameMap.keySet();
	}

	public boolean containsKey(String name) {
		return keyNameMap.containsKey(name);
	}

	@Override
	public Class getDomainClass()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public KeyType[] getReferences()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isReference()
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getQuery()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setQuery(String path)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getDepth()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setDepth(int depth)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setReferences(KeyType[] references)
	{
		// TODO Auto-generated method stub
		
	}

}
