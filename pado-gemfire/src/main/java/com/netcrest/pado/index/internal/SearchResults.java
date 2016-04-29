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
package com.netcrest.pado.index.internal;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

import com.gemstone.gemfire.DataSerializable;
import com.gemstone.gemfire.DataSerializer;

public class SearchResults implements DataSerializable
{
	private static final long serialVersionUID = 1L;
	private List<?> results;

	public SearchResults()
	{
	}
	
	public SearchResults(List<?> results)
	{
		this.results = results;
	}

	public List<?> getResults()
	{
		return results;
	}

	public void setResults(List<?> results)
	{
		this.results = results;
	}

	/**
	 * Reads the state of this object from the given <code>DataInput</code>.
	 * @gfcodegen This code is generated by gfcodegen.
	 */
	public void fromData(DataInput input) throws IOException, ClassNotFoundException
	{
		results = DataSerializer.readObject(input);
	}

	/**
	 * Writes the state of this object to the given <code>DataOutput</code>.
	 * @gfcodegen This code is generated by gfcodegen.
	 */
	public void toData(DataOutput output) throws IOException
	{
		DataSerializer.writeObject(results, output);
	}
}
