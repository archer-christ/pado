package com.netcrest.pado.test.rpc.client.java;

import java.text.ParseException;

import org.junit.Test;

import com.netcrest.pado.rpc.client.biz.TemporalBiz;
import com.netcrest.pado.rpc.client.dna.RpcInvoker;
import com.netcrest.pado.test.rpc.client.TemporalBizQuery;

/**
 * TemporalBizQueryTest tests the RPC {@link TemporalBiz} query methods via
 * {@link RpcInvoker}. This test case requires the "nw" data distributed with
 * Pado. Make sure to first load that set of data in the "mygrid" environment.
 * 
 * @author dpark
 *
 */
public class TemporalBizQueryTest extends TemporalBizQuery
{
	public TemporalBizQueryTest()
	{
		super("java");
	}
	
	@Test
	public void testGetAllEntries()
	{
		super.testGetAllEntries();
	}

	@Test
	public void testGetEntry()
	{
		super.testGetEntry();
	}

	@Test
	public void testGet()
	{
		super.testGet();
	}

	@Test
	public void testSize()
	{
		super.testSize();
	}

	@Test
	public void testGetTemporalListCount()
	{super.testGetTemporalListCount();
	}

	@Test
	public void testGetEntryHistoryWrittenTimeRangeList() throws ParseException
	{
		super.testGetEntryHistoryWrittenTimeRangeList();
	}
}
