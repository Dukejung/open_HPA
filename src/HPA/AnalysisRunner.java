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

class HPARunner
{
	public Description descSim;
	public HPA hpa;
	public long[] WCRTs;
	public double TIME;
	
	public void go(String path) throws Exception
	{
		descSim = new Description(path);
		descSim.reconstructGraph();
		
		WCRTs = new long[descSim.totalTaskGroupNumOrignal];
		
		{
			hpa = new HPA(descSim);
			hpa.go();
				
			for(int i = 0 ; i < descSim.totalTaskGroupSetList.size() ; ++i)
				WCRTs[i] = hpa.getWCRT(i);
		}
	}
}

public class AnalysisRunner
{
	public static void main(String args[])
	{
		double start = System.nanoTime();
		try{
			if (args.length == 0)
			{
				Debug.ESTI.println("Path of the task description file needed.");
				return;
			}
			
			// HPA
			HPARunner entry = new HPARunner();
				
			entry.go(args[0]);
			for (int i = 0; i < entry.WCRTs.length; ++i)
				System.out.println("WCRT[" +i + "] : " + entry.WCRTs[i]);
		}
		catch(Exception e){
			e.printStackTrace();
		}
		System.out.println("Elapsed time : " + (System.nanoTime() - start) / 1000.0);
	}
}
