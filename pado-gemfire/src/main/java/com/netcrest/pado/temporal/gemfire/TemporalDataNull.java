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
package com.netcrest.pado.temporal.gemfire;

import com.netcrest.pado.temporal.ITemporalDataNull;
import com.netcrest.pado.temporal.ITemporalValue;

/**
 * TemporalDataNull used as a null value in temporal regions.
 * @author dpark
 *
 */
public abstract class TemporalDataNull<K, V> implements ITemporalDataNull<K>
{
	protected ITemporalValue<K> temporalValue;

	public TemporalDataNull()
	{
	}
	
	public ITemporalValue<K> __getTemporalValue()
	{
		return temporalValue;
	}

	public void __setTemporalValue(ITemporalValue<K> temporalValue)
	{
		this.temporalValue = temporalValue;
	}
	
	/**
	 * Always returns null.
	 */
	public V getValue()
	{
		return null;
	}

	/**
	 * Always returns true. This method is provided as a workaround to a PDX bug
	 * in GemFire 6.6.1 that does not honor null comparison done with 
	 * recovered data. The GemFire bug is: If a persistent region is recovered 
	 * then the getValue() method's null value is treated as non-null. 
	 * <p>
	 * Note that TemporalDataNull is not PdxSerializable. This bug does not
	 * apply. However, in order to allow consistent query statements, this
	 * method is provided.
	 * 
	 */
	public boolean isNull()
	{
		return true;
	}
}
