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
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package HPA;

public class Param {
	public static String USERDIR = System.getProperty("user.dir");
	public static String FS = System.getProperty("file.separator");
	public static String LS = System.getProperty("line.separator");
	public static String OS = System.getProperty("os.name");
	
	enum TaskGraphExpType {Yang, HyperPeriod, PS, NonHyperPeriod};
	enum SchedulingOptType {MinMax, FinishStar};
	enum ObjectiveType {DiffMax, ExecutionTimeMax};
	
	static TaskGraphExpType TASK_GRAPH_EXPANSION_TYPE = TaskGraphExpType.NonHyperPeriod;
	static SchedulingOptType SCHEDULING_OPT_TYPE = SchedulingOptType.MinMax;
	
	static boolean SCHEDULING_OPT = true;

	static boolean NEW_NP = true;
	static boolean NEW_P = false;
	
	static boolean TOTAL_TARGET = true;
	
	static boolean HEURISTIC_ONLY = false;
	
	static boolean DEBUG_DESCRIPTION = false;
	static boolean DEBUG_ESTIMATION = false;
	static boolean DEBUG_ANALYSIS = false;
	
	static ObjectiveType OBJECTIVE_TYPE = ObjectiveType.DiffMax;
	
	static double BOUND_FACTOR = 3;
	static double ILP_TIME_BOUND = 300;
	
	static boolean EXPANSION_PARTIAL = false;
	
	static boolean SAVE_GANTT = false;
	
	static boolean ALL_TASK_GROUP = false;	
	
	static int START_OFFSET_TYPE = 0;
	
	static boolean MULTICORE_SCHEDULING = false;
}
