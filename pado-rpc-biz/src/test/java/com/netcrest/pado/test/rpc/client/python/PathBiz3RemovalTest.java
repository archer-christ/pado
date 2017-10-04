package com.netcrest.pado.test.rpc.client.python;

import org.junit.Test;

import com.netcrest.pado.rpc.client.biz.PathBiz;
import com.netcrest.pado.rpc.client.dna.RpcInvoker;
import com.netcrest.pado.test.rpc.client.PathBiz1Put;
import com.netcrest.pado.test.rpc.client.PathBiz3Remove;

/**
 * PathBiz3RemovalTest tests the RPC {@link PathBiz} "remove" methods via
 * {@link RpcInvoker}. You must run {@link PathBiz1Put} first to insert data
 * into the grid.
 * 
 * @author dpark
 *
 */
public class PathBiz3RemovalTest extends PathBiz3Remove
{
	public PathBiz3RemovalTest()
	{
		super("python");
	}

	/**
	 * Tests removal of all keys inserted by the test case {@link PathBiz1Put}.
	 */
	@Test
	public void testRemove()
	{
		super.testRemove();
	}
}
