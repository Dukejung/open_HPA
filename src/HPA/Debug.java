/* Copyright (c) 2016, Codesign And Parallel processing Laboratory (CAPLab)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the Codesign And Parallel processing Laboratory (CAPLab) 
 * nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package HPA;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class Debug
{
	enum Category { DESCRIPTION, ESTIMATION, ANALYSIS };
	public static Debug.Stream DESC;
	public static Debug.Stream ESTI;
	public static Debug.Stream ANAL;

	private static Debug singleton;
	private Debug() { }
	
	static
	{
		singleton = new Debug();
		DESC = Create(Category.DESCRIPTION);
		ESTI = Create(Category.ESTIMATION);
		ANAL = Create(Category.ANALYSIS);		
	}	

	public static Debug.Stream Create(Debug.Category type)
	{
		switch (type)
		{
		case DESCRIPTION:
			return singleton.new Description();
		case ESTIMATION:
			return singleton.new Estimation();
		case ANALYSIS:
			return singleton.new Analysis();
		}
		
		return null;
	}
	
	public static Debug.Stream Create(Debug.Category type, String logName)
	{
		switch (type)
		{
		case DESCRIPTION:
			return singleton.new Description(logName);
		case ESTIMATION:
			return singleton.new Estimation(logName);
		case ANALYSIS:
			return singleton.new Analysis(logName);
		}
		
		return null;
	}
	
	public abstract class Stream
	{
		protected BufferedWriter writer;
		
		public abstract void print(Object obj);
		public abstract void println(Object obj);
		public abstract void println();
		
		protected void close()
		{
			if (writer != null)
			{
				try { writer.close(); }
				catch (IOException e) { e.printStackTrace(); }
			}
		}
		
		protected void _Constructor(String logName)
		{
			try { writer = new BufferedWriter(new FileWriter(logName, false)); }
			catch (IOException e) { e.printStackTrace(); }
		}
		
		protected void _print(Object obj)
		{	
			System.out.print(obj);
			
			if (writer != null)
			{
				try { writer.write(obj.toString());
				writer.flush();}
				catch (IOException e) { e.printStackTrace(); }
			}
		}
		
		protected void _println(Object obj)
		{
			System.out.println(obj);
			
			if (writer != null)
			{
				try { writer.write(obj.toString()); writer.newLine();writer.flush(); }
				catch (IOException e) { e.printStackTrace(); }
			}
		}
		
		protected void _println()
		{
			System.out.println();
			
			if (writer != null)
			{
				try { writer.newLine(); }
				catch (IOException e) { e.printStackTrace(); }
			}
		}
	}
	
	public class Description extends Stream
	{
		public Description()
		{
		}
		
		public Description(String logName)
		{
			if (Param.DEBUG_DESCRIPTION)
				_Constructor(logName);
		}
		
		public void print(Object obj)
		{
			if (Param.DEBUG_DESCRIPTION)
				_print(obj);
		}
		
		public void println(Object obj)
		{
			if (Param.DEBUG_DESCRIPTION)
				_println(obj);
		}
		
		public void println()
		{
			if (Param.DEBUG_DESCRIPTION)
				_println();
		}
	}
	
	public class Estimation extends Stream
	{
		public Estimation()
		{
		}
		
		public Estimation(String logName)
		{
			if (Param.DEBUG_ESTIMATION)
				_Constructor(logName);
		}
		
		public void print(Object obj)
		{
			if (Param.DEBUG_ESTIMATION)
				_print(obj);
		}
		
		public void println(Object obj)
		{
			if (Param.DEBUG_ESTIMATION)
				_println(obj);
		}
		
		public void println()
		{
			if (Param.DEBUG_ESTIMATION)
				_println();
		}
	}
	
	public class Analysis extends Stream
	{
		public Analysis()
		{
			
		}
		
		public Analysis(String logName)
		{
			if (Param.DEBUG_ANALYSIS)
				_Constructor(logName);
		}
		
		public void print(Object obj)
		{
			if (Param.DEBUG_ANALYSIS)
				_print(obj);
		}
		
		public void println(Object obj)
		{
			if (Param.DEBUG_ANALYSIS)
				_println(obj);
		}
		
		public void println()
		{
			if (Param.DEBUG_ANALYSIS)
				_println();
		}
	}
}
