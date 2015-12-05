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
package com.netcrest.pado.biz.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.netcrest.pado.IPado;
import com.netcrest.pado.biz.IGridMapBiz;
import com.netcrest.pado.biz.ITemporalAdminBiz;
import com.netcrest.pado.data.KeyMap;
import com.netcrest.pado.data.KeyType;
import com.netcrest.pado.index.provider.lucene.DateTool;
import com.netcrest.pado.internal.util.ClassUtil;
import com.netcrest.pado.log.Logger;
import com.netcrest.pado.server.PadoServerManager;
import com.netcrest.pado.temporal.ITemporalAdminBizLink;
import com.netcrest.pado.temporal.ITemporalBulkLoader;
import com.netcrest.pado.temporal.ITemporalData;
import com.netcrest.pado.temporal.ITemporalKey;
import com.netcrest.pado.temporal.gemfire.impl.GemfireTemporalData;
import com.netcrest.pado.temporal.gemfire.impl.GemfireTemporalKey;
import com.netcrest.pado.util.IBulkLoader;
import com.netcrest.pado.util.IBulkLoaderListener;
import com.netcrest.pado.util.ObjectUtil;

/**
 * CsvFileLoader loads CSV file contents in the form of a specified data class
 * into a IBulkLoader object. The data class must provide public members or
 * setters (set methods) that match the column names found in the CSV file. The
 * column names can be case sensitive or case insensitive. If case sensitive
 * (default) then the column names must exactly match the setter (method) names.
 * <p>
 * <b>IMPORTANT:</b> The load() methods are not thread safe. To change
 * properties, load() must return first.
 * <p>
 * The current implementation treats empty column values in the CSV file as null
 * values. This may change in the future release by introducing a null indicator
 * in the schema file.
 * <p>
 * The following property types are supported:
 * <ul>
 * <li>primitives</li>
 * <li>String</li>
 * <li>java.util.Date</li>
 * <li>org.joda.time.DateTime</li>
 * </ul>
 * <p>
 * 
 * @author dpark
 * 
 */
public class CsvFileLoader implements IFileLoader
{
	private IPado pado;

	private SchemaInfo schemaInfo;
	private SimpleDateFormat dateFormatter;
	private DateTimeFormatter jodaFormatter;
	private StringBuffer buffer = new StringBuffer(100);
	private boolean verbose = false;
	private String verboseTag = "";
	private char delimiter;

	public CsvFileLoader(IPado pado)
	{
		this.pado = pado;
	}

	public void setVerbose(boolean verbose)
	{
		this.verbose = verbose;
	}

	public boolean isVerbose()
	{
		return verbose;
	}

	public String getVerboseTag()
	{
		return verboseTag;
	}

	public void setVerboseTag(String verboseTag)
	{
		this.verboseTag = verboseTag;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int load(SchemaInfo schemaInfo, String dataText) throws FileLoaderException
	{
		validateSchemaInfo(schemaInfo);

		this.schemaInfo = schemaInfo;
		this.delimiter = schemaInfo.getDelimiter();

		StringReader reader = null;
		int count = 0;
		try {
			reader = new StringReader(dataText);
			count = loadData(reader, -1);
			reader.close();
		} catch (Exception ex) {
			throw new FileLoaderException(ex);
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
		return count;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int load(SchemaInfo schemaInfo, File dataFile) throws FileLoaderException
	{
		validateSchemaInfo(schemaInfo);

		this.schemaInfo = schemaInfo;
		this.delimiter = schemaInfo.getDelimiter();

		if (dataFile == null) {
			throw new FileLoaderException("The passed in file is null");
		}
		Reader csvReader = null;
		int count = 0;
		try {
			// Extract the timestamp from the file name. This time stamp is
			// used as temporal start valid and written time if temporal data.
			long temporalTime = -1;
			int index = dataFile.getName().lastIndexOf(".v");
			if (index > 0) {
				String timeStr = dataFile.getName().substring(index + 2);
				index = timeStr.indexOf(".");
				if (index > 0) {
					timeStr = timeStr.substring(0, index);
				}
				temporalTime = DateTool.stringToTime(timeStr, DateTool.Resolution.SECOND);
				timeStr = DateTool.timeToString(temporalTime, schemaInfo.getTemporalTimeResolution());
				temporalTime = DateTool.stringToTime(timeStr, schemaInfo.getTemporalTimeResolution());
			}
			InputStream inputStream = new FileInputStream(dataFile);
			Reader reader = new InputStreamReader(inputStream, Charset.forName("US-ASCII"));
			csvReader = new BufferedReader(reader);
			count = loadData(csvReader, temporalTime);
			csvReader.close();
		} catch (Exception ex) {
			throw new FileLoaderException("File load failed. " + ex.getMessage() + ". Data file: "
					+ dataFile.getAbsolutePath(), ex);
		} finally {
			if (csvReader != null) {
				try {
					csvReader.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}

		return count;
	}

	private void validateSchemaInfo(SchemaInfo schemaInfo) throws FileLoaderException
	{
		if (schemaInfo == null) {
			throw new FileLoaderException("SchemaInfo cannnot be null.");
		}
		if (schemaInfo.getValueColumnNames() == null) {
			throw new FileLoaderException("The passed in colum name arrary is null");
		}
		// Primary key property name of the data class. It is expected
		// that the specified data class has the primary key getter
		// method which returns the key that uniquely maps the data
		// object.
		if (schemaInfo.getPkColumnNames() != null) {
			if (schemaInfo.getKeyClass() == null) {
				throw new FileLoaderException(
						"The passed in primary key class is null and the number of primary key column names is greater than 1");
			}
		} else {
			throw new FileLoaderException("The passed in primary key column name is null");
		}
	}

	/**
	 * Loads data into the grid by reading each row from the specified text
	 * reader.
	 * 
	 * @param textReader
	 *            Text reader.
	 * @param temporalTime
	 *            Temporal time to be used to create "forever" records if
	 *            schemaInfo.isHistory() and schemaInfo.isTemporal() are true.
	 *            If -1 then the current time.
	 * @return Number of entries put into the grid. Note that the returned value
	 *         is not the actual number of entries in the file. For example, if
	 *         IsHistory is true then only non-duplicate entries will be put
	 *         into the grid and therefore the number of entries will be less
	 *         than the number of records in the file.
	 * @throws FileLoaderException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private int loadData(Reader textReader, long temporalTime) throws FileLoaderException, InstantiationException,
			IllegalAccessException
	{
		IBulkLoader bulkLoader;
		ITemporalAdminBizLink temporalAdminBiz = null;
		
		// For server-side, do not use IBiz which fails due to class loader
		// conflicts.
		if (PadoServerManager.getPadoServerManager() != null) {
			bulkLoader = schemaInfo.createBulkLoader();
			bulkLoader.setPath(schemaInfo.getGridPath());
			bulkLoader.setBatchSize(schemaInfo.getBatchSize());
		} else {
			if (schemaInfo.isTemporal()) {
				temporalAdminBiz = pado.getCatalog().newInstance(ITemporalAdminBiz.class,
						schemaInfo.getGridPath());
				bulkLoader = temporalAdminBiz.createBulkLoader(schemaInfo.getBatchSize());
				((ITemporalBulkLoader)bulkLoader).setDiffEnabled(schemaInfo.isHistory());
				((ITemporalBulkLoader)bulkLoader).setDiffTemporalTime(temporalTime);
			} else {
				IGridMapBiz gridMapBiz = pado.getCatalog().newInstance(IGridMapBiz.class, schemaInfo.getGridPath());
				bulkLoader = gridMapBiz.getBulkLoader(schemaInfo.getBatchSize());
			}	
		}
		EntryCountListener entryCountListener = new EntryCountListener();
		bulkLoader.addBulkLoaderListener(entryCountListener);
		
		String pkPropertyName = null;
		if (schemaInfo.getPkColumnNames().length == 1) {
			pkPropertyName = schemaInfo.getPkColumnNames()[0];
		}
		Field pkValueField = getField(schemaInfo.getValueClass(), pkPropertyName);
		Method pkDataMethod = getGetterMethod(schemaInfo.getValueClass(), pkPropertyName);

		if (pkValueField == null && pkDataMethod == null && schemaInfo.getPkColumnNames() == null) {
			throw new FileLoaderException("Primary key schema information undefined.");
		}
		if (pkValueField == null && pkDataMethod == null && schemaInfo.getKeyClass() == null) {
			throw new FileLoaderException(
					"The passed in primary key field, method, and class are null. One of them must be non-null.");
		}
		dateFormatter = new SimpleDateFormat(schemaInfo.getDateFormat());
		jodaFormatter = DateTimeFormat.forPattern(schemaInfo.getDateFormat());

		Object[] valueClassSetters = getFieldMethodList(schemaInfo.getValueClass(), schemaInfo.getValueColumnNames(),
				"set", 1);
		Object[] pkClassSetters = getFieldMethodList(schemaInfo.getKeyClass(), schemaInfo.getPkColumnNames(), "set", 1);
		Object[] valueClassGetters = getFieldMethodList(schemaInfo.getValueClass(), schemaInfo.getValueColumnNames(),
				"get", 0);
		boolean isKeyColumns = schemaInfo.isKeyColumns();
		int keyStartIndex = schemaInfo.getKeyStartIndex();
		int valueStartIndex = schemaInfo.getValueStartIndex();
		int temporalStartIndex = schemaInfo.getTemporalStartIndex();

		// Temporal attributes
		long startValidTime = schemaInfo.getTemporalStartTime().getTime();
		long endValidTime = schemaInfo.getTemporalEndTime().getTime();
		long writtenTime;
		if (schemaInfo.getTemporalWrittenTime() == null) {
			writtenTime = System.currentTimeMillis();
		} else {
			writtenTime = schemaInfo.getTemporalWrittenTime().getTime();
		}

		// User
		String username;
		if (schemaInfo.getUsername() == null) {
			username = System.getProperty("user.name");
		} else {
			username = schemaInfo.getUsername();
		}

		IRowFilter rowFilter = null;
		IEntryFilter entryFilter = null;
		Class clazz = schemaInfo.getRowFilterClass();
		if (clazz != null) {
			if (IRowFilter.class.isAssignableFrom(clazz)) {
				rowFilter = (IRowFilter) clazz.newInstance();
			} else {
				if (verbose) {
					System.err.println("Invalid row filter type. " + clazz.getName() + " does not implement "
							+ IRowFilter.class.getName() + ". Row filter ignored.");
				}
			}
		}
		clazz = schemaInfo.getEntryFilterClass();
		if (clazz != null) {
			if (IEntryFilter.class.isAssignableFrom(clazz)) {
				entryFilter = (IEntryFilter) clazz.newInstance();
			} else {
				if (verbose) {
					System.err.println("Invalid entry filter type. " + clazz.getName() + " does not implement "
							+ IEntryFilter.class.getName() + ". Entry filter ignored.");
				}
			}
		}
		
		int lineNumber = 0;
		int count = 0;
		BufferedReader reader = null;
		String line = null;
		try {
			reader = new BufferedReader(textReader);
			// skip the rows before the start row
			for (int i = 1; i < schemaInfo.getStartRow(); i++) {
				line = reader.readLine();
				lineNumber++;
			}
			while ((line = readLine(reader)) != null) {
				lineNumber++;
				String line2 = line.trim();
				if (line2.startsWith("#") || line2.length() == 0) {
					continue;
				}
				// String[] tokens =
				// line.split(String.valueOf(schemaInfo.getDelimiter()));
				String tokens[] = getTokens(line, schemaInfo.getDelimiter());
				if (tokens == null) {
					continue;
				}
				if (tokens.length < 0) {
					continue;
				}

				Object keyObject = null;
				Object dataObject = null;

				if (rowFilter != null) {
					keyObject = rowFilter.filterKey(tokens);
					if (keyObject == null) {
						continue;
					}
					dataObject = rowFilter.filterValue(tokens);
					if (dataObject == null) {
						continue;
					}
				} else {
					if (KeyMap.class.isAssignableFrom(schemaInfo.getValueClass())) {
						dataObject = createKeyMap(schemaInfo.getKeyType(), schemaInfo.getValueClass(),
								schemaInfo.getValueColumnNames(), schemaInfo.getValueColumnTypes(), tokens,
								valueStartIndex);
					} else {
						dataObject = createObject(schemaInfo.getValueClass(), valueClassSetters, tokens,
								valueStartIndex);
					}
					keyObject = null;
					// If valueStartIndex begins from the first index then
					// the key object must be obtained from the value object.
					if (valueStartIndex == 0) {
						if (dataObject instanceof IPrimaryKey) {
							keyObject = ((IPrimaryKey) dataObject).getPrimaryKey();
						} else if (schemaInfo.getPkColumnNames().length > 0) {
							if (isKeyColumns) {
								keyObject = createObject(schemaInfo.getKeyClass(), pkClassSetters, tokens,
										keyStartIndex);
							} else {
								// IsKeyAutoGen overrides primary keys
								if (schemaInfo.isKeyAutoGen()) {
									keyObject = UUID.randomUUID().toString();
								} else {
									if (dataObject instanceof KeyMap) {
										keyObject = createKey(schemaInfo.getKeyClass(), schemaInfo.getPkColumnNames(),
												(KeyMap) dataObject);
									} else {
										keyObject = createKey(schemaInfo.getKeyClass(), pkClassSetters,
												schemaInfo.getPkColumnNames(), schemaInfo.getValueClass(),
												valueClassGetters, dataObject);
									}
								}
							}
						} else {
							if (schemaInfo.isKeyAutoGen()) {
								keyObject = UUID.randomUUID().toString();
							} else {
								throw new FileLoaderException(
										"Unable to determine key object. Check the schema file format.");
							}
						}
					} else {
						keyObject = createObject(schemaInfo.getKeyClass(), pkClassSetters, tokens,
								schemaInfo.getKeyStartIndex());
					}
				}

				if (schemaInfo.isTemporal()) {
					ITemporalKey tk;
					ITemporalData td;
					// If the temporal start index is a non-negative number then
					// the temporal attributes are part of the the columns.
					if (temporalStartIndex != -1) {
						tk = createTemporalKey(keyObject, tokens, temporalStartIndex);
					} else {
						tk = new GemfireTemporalKey(keyObject, startValidTime, endValidTime, writtenTime, username);
					}
					td = new GemfireTemporalData(tk, dataObject);
					if (entryFilter != null) {
						IEntryFilter.Entry entry = entryFilter.filterEntry(new IEntryFilter.Entry(tk, td));
						if (entry != null && entry.getKey() != null && entry.getValue() != null) {
							Object key = entry.getKey();
							Object data = entry.getValue();
							if (key instanceof ITemporalKey && data instanceof ITemporalData) {
								bulkLoader.put((ITemporalKey)key, (ITemporalData)data);
							} else {
								bulkLoader.put(key, data);
							}
						}
					} else {
						bulkLoader.put(tk, td);
					}
				} else {
					if (entryFilter != null) {
						IEntryFilter.Entry entry = entryFilter
								.filterEntry(new IEntryFilter.Entry(keyObject, dataObject));
						if (entry != null && entry.getKey() != null && entry.getValue() != null) {
							bulkLoader.put(entry.getKey(), entry.getValue());
						}
					} else {
						bulkLoader.put(keyObject, dataObject);
					}
				}
				count++;

				if (verbose) {
					if (count % bulkLoader.getBatchSize() == 0) {
						System.out.println("   " + verboseTag + " Read: " + count + ", Loaded: " + entryCountListener.getTotalCount());
					}
				}
			}

			bulkLoader.flush();

		} catch (Exception ex) {
			Logger.error(ex);
			throw new FileLoaderException("Error occured while loading data: " + ex.getClass() + ", line=" + lineNumber
					+ ": " + line, ex);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}

		return entryCountListener.getTotalCount();
	}

	private ITemporalKey createTemporalKey(Object identityKey, String[] tokens, int temporalStartIndex)
			throws ParseException
	{
		int index = temporalStartIndex;
		Date startValidTime = dateFormatter.parse(tokens[index++]);
		Date endValidTime = dateFormatter.parse(tokens[index++]);
		Date writtenTime = dateFormatter.parse(tokens[index++]);
		String username = tokens[index++];
		GemfireTemporalKey tk = new GemfireTemporalKey(identityKey, startValidTime.getTime(), endValidTime.getTime(),
				writtenTime.getTime(), username);
		return tk;
	}

	// TODO: Support other than String
	@SuppressWarnings("rawtypes")
	private Object createKey(Class<?> keyClass, String[] keyNames, KeyMap keyMap)
	{
		Object key = null;
		if (keyNames.length == 1) {
			key = keyMap.get(keyNames[0]);
		} else {
			if (keyClass == String.class) {
				String strKey = null;
				for (String keyName : keyNames) {
					Object val = keyMap.get(keyName);
					if (val != null) {
						if (strKey == null) {
							strKey = val.toString();
						} else {
							strKey += "." + val;
						}
					}
				}
				key = strKey;
			}
		}
		return key;
	}

	private Object createKey(Class<?> keyClass, Object[] pkClassSetters, String[] pkColumnNames, Class<?> valueClass,
			Object[] valueClassGetters, Object dataObject)
	{
		return null;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private KeyMap createKeyMap(KeyType keyType, Class keyMapClass, String[] keyNames, Class<?>[] tokenTypes,
			String[] tokens, int startTokenIndex) throws Exception
	{
		KeyMap keyMap = (KeyMap) keyMapClass.newInstance();
		if (keyType != null) {
			// This registers the key type.
			keyMap.put(keyType.getValues()[0], null);
		}
		for (int i = 0; i < keyNames.length; i++) {
			if (schemaInfo.isSkipColumn(keyNames[i]) == false) {
				ObjectUtil.updateKeyMap(keyMap, keyNames[i], tokens[startTokenIndex + i], dateFormatter, true,
						tokenTypes[i]);
			}
		}
		return keyMap;
	}

	/**
	 * Creates an object with the token values.
	 * 
	 * @param clazz
	 *            Class to create the object.
	 * @param clazzSetters
	 *            Fields and methods of the same order as the token values. If
	 *            clazz is primitive or String, then clazzSetters is not
	 *            required.
	 * @param tokens
	 *            Tokens that are assigned to the clazzSetters.
	 * @return A new instance of the specified clazz set to the token values.
	 * 
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws ParseException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	private Object createObject(Class<?> clazz, Object[] clazzSetters, String[] tokens, int startTokenIndex)
			throws InstantiationException, IllegalAccessException, ParseException, IllegalArgumentException,
			InvocationTargetException
	{
		// Handle primitives
		if (ClassUtil.isPrimitiveBase(clazz)) {
			if (tokens.length > startTokenIndex) {
				return ClassUtil.newPrimitive(clazz, tokens[startTokenIndex]);
			} else {
				return null;
			}
		}

		// Non-primitives
		Object dataObject = clazz.newInstance();
		int tokenIndex = startTokenIndex;
		for (int i = 0; i < clazzSetters.length; i++) {
			Object object = clazzSetters[i];
			if (object == null) {
				continue;
			}
			// If the number of columns in the line is greater
			// than the number of tokens then silently ignore.
			if (tokenIndex >= tokens.length) {
				break;
			}

			// Invoke setter
			String stringValue = tokens[tokenIndex++];
			Object value;
			if (object instanceof Field) {
				Field field = (Field) object;
				value = getValue(stringValue, field);
				field.set(dataObject, value);
			} else {
				Method method = (Method) object;
				value = getValue(stringValue, method);
				method.invoke(dataObject, value);
			}
		}

		return dataObject;
	}

	private Object getValue(String strVal, Field field) throws ParseException
	{
		Class<?> clazz = field.getType();
		return getValue(strVal, clazz);
	}

	private Object getValue(String strVal, Method method) throws ParseException
	{
		Class<?> clazz = method.getParameterTypes()[0];
		return getValue(strVal, clazz);
	}

	/**
	 * Converts the specified string value to an instance of the specified
	 * class. It supports primitives, String, java.util.Date,
	 * org.joda.time.DateTime, and BigDecimal. If the specified class is not
	 * supported then it returns the specified string.
	 * 
	 * @param strVal
	 *            String value to convert
	 * @param clazz
	 *            Class to convert the string value
	 * @throws ParseException
	 */
	private Object getValue(String strVal, Class<?> clazz) throws ParseException
	{
		if (strVal == null || strVal.length() == 0) {
			return null;
		}
		if (clazz == Boolean.class || clazz == boolean.class) {
			return new Boolean(strVal);
		} else if (clazz == Byte.class || clazz == byte.class) {
			return new Byte(strVal);
		} else if (clazz == Character.class || clazz == char.class) {
			return new Character(strVal.charAt(0));
		} else if (clazz == Integer.class || clazz == int.class) {
			return new Integer(strVal);
		} else if (clazz == Short.class || clazz == short.class) {
			return new Short(strVal);
		} else if (clazz == Long.class || clazz == long.class) {
			return new Long(strVal);
		} else if (clazz == Float.class || clazz == float.class) {
			return new Float(strVal);
		} else if (clazz == Double.class || clazz == double.class) {
			return new Double(strVal);
		} else if (clazz == String.class) {
			return new String(strVal);
		} else if (clazz == Date.class) {
			return dateFormatter.parse(strVal);
		} else if (clazz == DateTime.class) {
			return jodaFormatter.parseDateTime(strVal);
		} else if (clazz == BigDecimal.class) {
			return new BigDecimal(strVal);
		} else {
			return strVal;
		}
	}

	/**
	 * Returns public Fields and Methods that begin with the specified method
	 * prefix. Methods override Fields if they have the same name.
	 * 
	 * @param clazz
	 *            The class to introspect.
	 * @return List of public Fields and Methods in the same order as the
	 *         specified column names. Undefined fields/methods are set to null.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected Object[] getFieldMethodList(Class<?> clazz, String columnNames[], String methodPrefix, int parameterCount)
	{
		if (clazz == null) {
			return null;
		}
		if (ClassUtil.isPrimitiveBase(clazz)) {
			return null;
		}

		Field[] fields = clazz.getDeclaredFields();
		Method[] methods = clazz.getMethods();

		HashMap map = new HashMap();

		// scan fields
		for (int i = 0; i < fields.length; i++) {
			int modifiers = fields[i].getModifiers();
			if (!Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)) {
				// columns.add(fields[i].getName());
				String propertyName = fields[i].getName();
				if (schemaInfo.isColumnNamesCaseSensitive()) {
					// Support lowercase and uppercase for the first letter of
					// property name
					map.put(getAlternatePropertyName(propertyName), fields[i]);
					map.put(propertyName, fields[i]);
				} else {
					map.put(propertyName.toLowerCase(), fields[i]);
				}
			}
		}
		// scan methods
		for (int i = 0; i < methods.length; i++) {
			Method method = methods[i];
			int modifiers = method.getModifiers();
			String methodName = methods[i].getName();
			if (!Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers) && methodName.startsWith(methodPrefix)
					&& (method.getParameterTypes().length == parameterCount) && !methodName.equals("getClass")) {
				String propertyName = methodName.substring(3);
				if (propertyName.length() > 0) {
					if (schemaInfo.isColumnNamesCaseSensitive()) {
						// Support lowercase and uppercase for the first letter
						// of property name
						map.put(getAlternatePropertyName(propertyName), methods[i]);
						map.put(propertyName, methods[i]);
					} else {
						map.put(propertyName.toLowerCase(), methods[i]);
					}
				}
			}
		}

		ArrayList list = new ArrayList();
		if (columnNames != null) {
			for (int i = 0; i < columnNames.length; i++) {
				Object obj;
				if (schemaInfo.isColumnNamesCaseSensitive()) {
					obj = map.get(columnNames[i]);
				} else {
					obj = map.get(columnNames[i].toLowerCase());
				}
				list.add(obj);
			}
		} else {
			Set<Map.Entry> set = map.entrySet();
			for (Map.Entry entry : set) {
				entry.getKey();
				list.add(entry.getValue());
			}
		}
		return list.toArray();
	}

	/**
	 * Returns the class field that matches the specified column name. It
	 * returns null if the matching field is not found.
	 * 
	 * @param clazz
	 *            The class in which the field exists
	 * @param columnName
	 *            The column name
	 */
	protected Field getField(Class<?> clazz, String columnName)
	{
		if (clazz == null) {
			return null;
		}
		try {
			Field field = null;
			if (schemaInfo.isColumnNamesCaseSensitive()) {
				try {
					field = clazz.getField(columnName);
				} catch (Exception ex) {
					// ignore
				}
				if (field == null) {
					field = clazz.getField(getAlternatePropertyName(columnName));
				}
			} else {
				Field fields[] = clazz.getFields();
				for (Field field2 : fields) {
					if (columnName.equalsIgnoreCase(field2.getName())) {
						field = field2;
						break;
					}
				}
			}
			return field;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Returns the getter method that matches the specified column name.It
	 * returns null if the matching method is not found.
	 * 
	 * @param clazz
	 *            The class in which the getter method exists.
	 * @param columnName
	 *            The column name.
	 */
	protected Method getGetterMethod(Class<?> clazz, String columnName)
	{
		if (clazz == null || columnName == null || columnName.length() == 0) {
			return null;
		}
		try {
			Method method = null;
			if (schemaInfo.isColumnNamesCaseSensitive()) {
				try {
					method = clazz.getMethod("get" + columnName);
				} catch (Exception ex) {
					// ignore
				}
				if (method == null) {
					method = clazz.getMethod("get" + getAlternatePropertyName(columnName));
				}
			} else {
				Method methods[] = clazz.getMethods();
				String getter = "get" + columnName;
				for (Method method2 : methods) {
					if (getter.equalsIgnoreCase(method2.getName())) {
						method = method2;
						break;
					}
				}
			}
			return method;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Returns the alternate property name. If the first letter is lower case
	 * then it turns the upper case, and vice versa. It returns null if the
	 * specified property is null or empty.
	 * 
	 * @param property
	 *            Property name
	 */
	private String getAlternatePropertyName(String property)
	{
		if (property == null || property.length() == 0) {
			return null;
		}
		if (Character.isUpperCase(property.charAt(0))) {
			return Character.toLowerCase(property.charAt(0)) + property.substring(1);
		} else {
			return Character.toUpperCase(property.charAt(0)) + property.substring(1);
		}
	}

	protected String readLine(BufferedReader reader) throws IOException
	{
		boolean endOfLine = false;
		boolean oddQuote = false;
		int c;
		buffer.delete(0, buffer.length());
		while ((c = reader.read()) != -1) {
			// TODO: The following supports enclosed quotes for ','. All
			// other delimiters treat the double quote as a regular character.
			// We may need to support delimiters other than ',' for enclosed
			// quotes.
			if (c == delimiter && c != ',') {
				oddQuote = false;
				buffer.append((char) c);
			} else {
				switch (c) {
				case '"':
					oddQuote = !oddQuote;
					buffer.append((char) c);
					break;
				case '\r':
					break;
				case '\n':
					if (oddQuote) {
						buffer.append((char) c);
					} else {
						endOfLine = true;
					}
					break;
				default:
					buffer.append((char) c);
					break;
				}
				if (endOfLine) {
					break;
				}
			}
		}
		if (c == -1) {
			return null;
		}
		return buffer.toString();
	}

	/**
	 * Returns an array of tokens extracted from the specified line parameter.
	 * 
	 * @param line
	 *            The string to be tokenized.
	 * @param delimiter
	 *            Token separator.
	 */
	protected String[] getTokens(String line, char delimiter)
	{
		if (line.length() == 0) {
			return null;
		}
		// HBAN,23.82,300,23.79,800,"Thu, ""test"", 'hello' Jun 08 09:41:19 EDT 2006",99895,1094931009,82,99895,8,HBAN
		ArrayList<String> list = new ArrayList<String>();
		boolean openQuote = false;
		String value = "";
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (c == delimiter) {
				if (openQuote == false) {
					value = value.trim();
					if (value.startsWith("\"") && (value.endsWith("\"") || value.indexOf(" ") != -1)) {
						value = value.substring(1);
						if (value.endsWith("\"")) {
							value = value.substring(0, value.length() - 1);
						}
					}

					list.add(value);
					value = "";
					continue;
				}
			} else if (c == '"' && delimiter == ',') {
				openQuote = !openQuote;
			}
			value += c;
		}
		if (value.startsWith("\"") && value.endsWith("\"")) {
			value = value.substring(1);
			value = value.substring(0, value.length() - 1);
		}
		list.add(value);
		return (String[]) list.toArray(new String[0]);
	}

	class EntryCountListener implements IBulkLoaderListener
	{
		int totalCount;

		@Override
		public void flushed(int count)
		{
			totalCount += count;
		}

		public int getTotalCount()
		{
			return totalCount;
		}

	}
}
