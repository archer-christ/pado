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
package com.netcrest.pado.exception;

/**
 * GridAccessNotAllowedException is thrown if a target grid is not available.
 * 
 * @author dpark
 * 
 */
public class GridAccessNotAllowedException extends PadoException
{
	private static final long serialVersionUID = 1L;

	private String gridId;

	/**
	 * Constructs a new GridAccessNotAllowedException exception with
	 * <code>null</code> as its detail message. The cause is not initialized,
	 * and may subsequently be initialized by a call to {@link #initCause}.
	 */
	public GridAccessNotAllowedException()
	{
		super();
	}

	/**
	 * Constructs a new GridAccessNotAllowedException exception with the
	 * specified detail message.
	 * <p>
	 * Note that the detail message associated with <code>cause</code> is
	 * <i>not</i> automatically incorporated in this exception's detail message.
	 * 
	 * @param message
	 *            the detail message (which is saved for later retrieval by the
	 *            {@link #getMessage()} method).
	 */
	public GridAccessNotAllowedException(String message)
	{
		super(message);
	}

	/**
	 * Constructs a new GridAccessNotAllowedException exception with the
	 * specified detail message and cause.
	 * <p>
	 * Note that the detail message associated with <code>cause</code> is
	 * <i>not</i> automatically incorporated in this exception's detail message.
	 * 
	 * @param message
	 *            the detail message (which is saved for later retrieval by the
	 *            {@link #getMessage()} method).
	 * @param cause
	 *            the cause (which is saved for later retrieval by the
	 *            {@link #getCause()} method). (A <tt>null</tt> value is
	 *            permitted, and indicates that the cause is nonexistent or
	 *            unknown.)
	 */
	public GridAccessNotAllowedException(String message, Throwable cause)
	{
		super(message, cause);
	}

	/**
	 * Constructs a new GridAccessNotAllowedException exception with the
	 * specified cause and a detail message of
	 * <tt>(cause==null ? null : cause.toString())</tt> (which typically
	 * contains the class and detail message of <tt>cause</tt>). This
	 * constructor is useful for exceptions that are little more than wrappers
	 * for other throwables.
	 * 
	 * @param cause
	 *            the cause (which is saved for later retrieval by the
	 *            {@link #getCause()} method). (A <tt>null</tt> value is
	 *            permitted, and indicates that the cause is nonexistent or
	 *            unknown.)
	 */
	public GridAccessNotAllowedException(Throwable cause)
	{
		super(cause);
	}

	/**
	 * Returns the ID of the grid that is not available.
	 */
	public String getGridId()
	{
		return gridId;
	}

	/**
	 * Sets the ID of the grid that is not available.
	 * 
	 * @param gridId
	 */
	public void setGridId(String gridId)
	{
		this.gridId = gridId;
	}
}
