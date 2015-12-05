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
package com.netcrest.pado.gemfire.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;

public class InstantiatorClassLoader
{
	/**
	 * Loads the DataSerializable classes from the relative file path
	 * specified by the system property "dataSerializableFilePath". If not
	 * defined then the default relative file path, "etc/DataSerializables.txt",
	 * is read. The file must contain fully-qualified class names separated
	 * by a new line. Lines that begin with # or that have white spaces only 
	 * are ignored. For example,
	 * <p>
	 * <table cellpadding=0 cellspacing=0>
	 * <tr># Trade objects</tr>
	 * <tr>foo.data.Trade</tr>
	 * <tr>foo.data.Price</tr>
	 * <tr>foo.data.Order</tr>
	 * <tr>#
	 * <tr># Info objects</tr>
	 * <tr>foo.info.Company</tr>
	 * <tr>foo.info.Employee</tr>
	 * <tr>foo.info.Customer</tr>
	 * </table>
	 * 
	 * @throws IOException Thrown if unable to read the file.
	 * @throws ClassNotFoundException Thrown if any one of classes in the file is
	 *                     undefined.
	 * @return Returns a comma separated list of loaded class paths.
	 */
	public static String loadDataSerializables() throws IOException, ClassNotFoundException
	{
		String dataSerializableFilePath = System.getProperty("dataSerializableFilePath", "etc/DataSerializables.txt");
		return loadDataSerializables(dataSerializableFilePath);
	}
	
	/**
	 * Loads the DataSerializable classes from the specified relative file path.
	 * The file must contain fully-qualified class names separated
	 * by a new line. Lines that begin with # or that have white spaces only 
	 * are ignored. For example,
	 * <p>
	 * <table cellpadding=0 cellspacing=0>
	 * <tr># Trade objects</tr>
	 * <tr>foo.data.Trade</tr>
	 * <tr>foo.data.Price</tr>
	 * <tr>foo.data.Order</tr>
	 * <tr>#
	 * <tr># Info objects</tr>
	 * <tr>foo.info.Company</tr>
	 * <tr>foo.info.Employee</tr>
	 * <tr>foo.info.Customer</tr>
	 * </table>
	 * 
	 * @param filePath The relative or absolute file path.
	 * @return Returns a comma separated list of loaded class paths.
	 * @throws IOException Thrown if unable to read the file.
	 * @throws ClassNotFoundException Thrown if any one of classes in the file is
	 *                     undefined.
	 */
	public static String loadDataSerializables(String filePath) throws IOException, ClassNotFoundException
	{
		filePath = filePath.trim();
		File file;
		if (filePath.startsWith("/") || filePath.indexOf(':') >= 0) {
			// absolute path
			file = new File(filePath);
		} else {
			String userDir = System.getProperty("user.dir");
			file = new File(userDir, filePath);
		}
		
		LineNumberReader reader = new LineNumberReader(new FileReader(file));
		String line = reader.readLine();
		String className;
		StringBuffer buffer = new StringBuffer(1000);
		while (line != null) {
			className = line.trim();
			if (className.length() > 0 && className.startsWith("#") == false) {
				Class.forName(className);
				buffer.append(className);
				buffer.append(", ");
			}
			line = reader.readLine();
		}
		reader.close();
		String classList;
		int endIndex = buffer.lastIndexOf(", "); // 8
		if (endIndex == buffer.length() - 2) {
			classList = buffer.substring(0, endIndex);
		} else {
			classList = buffer.toString();
		}
		return classList;
	}

}
