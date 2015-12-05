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
package com.netcrest.pado.temporal.test.junit;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.netcrest.pado.ICatalog;
import com.netcrest.pado.IPado;
import com.netcrest.pado.Pado;
import com.netcrest.pado.biz.ITemporalBiz;
import com.netcrest.pado.data.KeyMap;
import com.netcrest.pado.data.jsonlite.JsonLite;
import com.netcrest.pado.exception.PadoLoginException;
import com.netcrest.pado.temporal.TemporalEntry;

/**
 * TemporalReferenceGetAllEntrySetTest requires "mygrid". Note that this test
 * runs interactively.
 * 
 * @author dpark
 * 
 */
public class TemporalReferenceGetAllEntrySetTest
{
	private static IPado pado;
	@SuppressWarnings("rawtypes")
	private static ITemporalBiz<String, KeyMap> portfolioBiz;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@BeforeClass
	public static void loginPado() throws PadoLoginException
	{
		System.setProperty("gemfirePropertyFile", "etc/client/client.properties");
		Pado.connect("localhost:20000", true);
		pado = Pado.login("sys", "netcrest", "dpark", "dpark".toCharArray());
		ICatalog catalog = pado.getCatalog();
		portfolioBiz = catalog.newInstance(ITemporalBiz.class, "portfolio");
	}

	@AfterClass
	public static void closePado()
	{
		Pado.close();
	}

	@SuppressWarnings({ "rawtypes" })
	@Test
	public void testPortfolioReferenceGetAll() throws ParseException
	{
		SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
		// Date validAtDate = formatter.parse("8/26/2011");
		Date validAtDate = new Date();
		Date asOfDate = new Date();
		System.out.println("TemporalGetTest.testPortfolioReferenceGetAll()");
		System.out.println("----------------------------------------------");
		portfolioBiz.setReference(true);
		long startTime = System.currentTimeMillis();
		Set<TemporalEntry<String, KeyMap>> set = portfolioBiz.getAllEntrySet("aa", validAtDate.getTime());
		long delta = System.currentTimeMillis() - startTime;
		for (TemporalEntry<String, KeyMap> temporalEntry : set) {
			JsonLite jl = (JsonLite) temporalEntry.getValue();
			System.out.println(jl.toString(2, true, false));
			System.out.println();
		}
		System.out.println("Elapsed time (msec): " + delta);
		System.out.println();
	}

}
