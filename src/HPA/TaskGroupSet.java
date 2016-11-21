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

public class TaskGroupSet
{
	TaskGroupList taskGroupList = new TaskGroupList();	
	Integer baseId;
	Integer lastInstanceId=0;
	Integer lastTaskGroupId;
	Description desc;
	Double startOffset=0.0; // start offset value of first instance , FOR STATIC OFFSET
	Double startOffsetMin=0.0, startOffsetMax=0.0; // // start offset value of first instance , FOR DYNAMIC OFFSET
	Double resultOffset=0.0; // start offset + yang's offset
	Integer hyperPeriodStartTime=0;

	Double maxEnd=0.0;

	Integer maxExpandNumber=0; //including own task
	
	Integer firstNotForJitterTaskInstId = 0 ;
	
	Integer incCount = 0;
	
	Integer expandedTaskNumByJitter = 0;
	
	Long criticalPathLength = (long)-1;
	Integer maxJitter = 0;
	Integer wcetMinMax = -1; // getWCET() 함수가 호출 되면 결정 된다. MinMax 수행 결과 가장 WCET 값이 큰 task group 의 값을 저장한다
	
	Double innerOffset = 0.0;

	//Constructor
	public String ILPSTATUS=null; // 0: Optimal, 1: Feasible(Time out), 2: Can't found
	public String SOLSTATUS=null;

	public TaskGroupSet(Description desc, TaskGroup baseTask) 
	{		
		this.taskGroupList.add(baseTask);			
		this.lastTaskGroupId = baseTask.getId();
		this.baseId = baseTask.getBaseId();
		this.desc = desc;
	}
	
	public void setupForJitter()
	{
		for(TaskGroup t : taskGroupList)
		{
			if(t.getInstanceID() == this.lastInstanceId)
				t.setForJitter(false);
			else
				t.setForJitter(true);
		}
		
		this.firstNotForJitterTaskInstId =this.lastInstanceId;
	}
	
	public void setupForJitterOneMore()
	{
		for(int i = 0 ; i < taskGroupList.size(); i++)
		{
			TaskGroup t = taskGroupList.get(i);
			if(!t.isForJitter())
			{
				t.setForJitter(true);
				break;
			}			
		}
	}
	
	public TaskGroup addNextTaskInstance()
	{	
		int fin_task_id = desc.totalTaskGroupList.size() - 1;
		int fin_subtask_id = desc.totalTaskList.size() - 1;
		
		TaskGroup prev_task = this.taskGroupList.getFromId(this.lastTaskGroupId);
			
		desc.DEBUG_DESC.println("Adding one more task to task group["+this.getBaseId()+"]");	
		
		TaskGroup new_task=null;
		new_task = new TaskGroup(this.lastInstanceId+1, prev_task.getPeriod(), prev_task.getDeadline(), fin_task_id+1, this.desc);
		new_task.setBaseId(prev_task.getBaseId());
		new_task.setJitter(prev_task.getJitter());
		new_task.setInterDistance(prev_task.getInterDistance());
		new_task.setTaskGroupSet(this);
		new_task.setName(prev_task.getName());
	
		TaskList sl = prev_task.getTaskList();
		for(int i = 0 ; i < sl.size();i++)
		{
			Task s = sl.get(i);
			int new_id = fin_subtask_id+i+1;
			Task new_s;
			
			new_s = new Task(new_task
					, s.getComputationUpper(), s.getComputationLower()
/*					,s.getMemAccessUpper(), s.getMemAccessLower()
					,s.getBusUsageUpper(), s.getBusUsageLower()
*/					, new_id, s.getPeriod());
			
			new_s.setPreemptable(s.isPreemptable());
			new_s.setPriority(s.getPriority());
			new_s.setMappedProcID(s.getMappedProcID());
			new_s.setJitter(s.getJitter());
			new_s.setBaseId(s.getBaseId());
			new_s.setName(s.getName());
			new_s.setDistance(s.getDistance());
			
			Processor p = this.desc.totalProcessorList.getFromId(s.getMappedProcID());
			p.getMappedSubtasks().add(new_s);

			new_s.setPrevInstance(s);
			s.setNextInstance(new_s);
			
			new_task.getTaskList().add(new_s);			
		}
		

		this.desc.addTask(new_task);	

		
		/* copy incomming edges */
		for(int i = 0 ; i < prev_task.getTaskList().size(); i++)
		{
			Task s = prev_task.getTaskList().get(i);
			for(int j = 0 ; j < s.getIncommingEdges().size();j++)
			{
				Task is = s.getIncommingEdges().get(j);

				if(s.getParentTaskId() == is.getParentTaskId())
				{
					Task next = s.getNextInstance();
					// s.next = next
					// is.next = inext
					// is -> s					
					// inext? -> next
					Task inext = is.getNextInstance();
					next.getIncommingEdges().add(inext);
				}
			}
		}	
				
		/* copy outgoingcomming edges */
		for(int i = 0 ; i < prev_task.getTaskList().size(); i++)
		{
			Task s = prev_task.getTaskList().get(i);
			for(int j = 0 ; j < s.getOutgoingEdges().size();j++)
			{
				Task os = s.getOutgoingEdges().get(j);
				if(s.getParentTaskId() == os.getParentTaskId())
				{
					Task next = s.getNextInstance();
					Task onext = os.getNextInstance();					
					next.getOutgoingEdges().add(onext);
				}
			}
		}
		/* for a source node, make an dependency arc */
		for(int i = 0 ; i < prev_task.getTaskList().size(); i++)
		{
			Task s = prev_task.getTaskList().get(i);
//			if(s.getIncommingEdges().size() == 0)
			if(s.isItSource())
			{
				Task next = s.getNextInstance();
				s.getOutgoingEdges().add(next);
				next.getIncommingEdges().add(s);
				next.setSource(true);
			}
			if (s.isItSink())
			{
				Task next = s.getNextInstance();
				next.setSink(true);
			}
		}
		
		desc.DEBUG_DESC.println("===" + new_task);
		this.lastInstanceId = new_task.getInstanceID();
		this.lastTaskGroupId = new_task.getId();
		this.taskGroupList.add(new_task);
		
		return new_task;
	}
	
	public TaskGroup addNextTaskInstanceNoSrcSrcEdge()
	{	
		int fin_task_id = desc.totalTaskGroupList.size() - 1;
		int fin_subtask_id = desc.totalTaskList.size() - 1;
		
		TaskGroup prev_task = this.taskGroupList.getFromId(this.lastTaskGroupId);
			
		desc.DEBUG_DESC.println("Adding one more task to task group["+this.getBaseId()+"]");	
		
		TaskGroup new_task=null;
		new_task = new TaskGroup(this.lastInstanceId+1, prev_task.getPeriod(), prev_task.getDeadline(), fin_task_id+1, this.desc);
		new_task.setBaseId(prev_task.getBaseId());
		new_task.setJitter(prev_task.getJitter());
		new_task.setInterDistance(prev_task.getInterDistance());
		new_task.setTaskGroupSet(this);
		new_task.setName(prev_task.getName());
	
		TaskList sl = prev_task.getTaskList();
		for(int i = 0 ; i < sl.size();i++)
		{
			Task s = sl.get(i);
			int new_id = fin_subtask_id+i+1;
			Task new_s;
			
			new_s = new Task(new_task
					, s.getComputationUpper(), s.getComputationLower()
/*					,s.getMemAccessUpper(), s.getMemAccessLower()
					,s.getBusUsageUpper(), s.getBusUsageLower()
*/					, new_id, s.getPeriod());
			
			new_s.setPreemptable(s.isPreemptable());
			new_s.setPriority(s.getPriority());
			new_s.setMappedProcID(s.getMappedProcID());
			new_s.setJitter(s.getJitter());
			new_s.setBaseId(s.getBaseId());
			new_s.setName(s.getName());
			new_s.setDistance(s.getDistance());
			
			Processor p = this.desc.totalProcessorList.getFromId(s.getMappedProcID());
			p.getMappedSubtasks().add(new_s);

			new_s.setPrevInstance(s);
			s.setNextInstance(new_s);
			
			new_task.getTaskList().add(new_s);			
		}
		

		this.desc.addTask(new_task);	

		
		/* copy incomming edges */
		for(int i = 0 ; i < prev_task.getTaskList().size(); i++)
		{
			Task s = prev_task.getTaskList().get(i);
			for(int j = 0 ; j < s.getIncommingEdges().size();j++)
			{
				Task is = s.getIncommingEdges().get(j);

				if(s.getParentTaskId() == is.getParentTaskId())
				{
					Task next = s.getNextInstance();
					// s.next = next
					// is.next = inext
					// is -> s					
					// inext? -> next
					Task inext = is.getNextInstance();
					next.getIncommingEdges().add(inext);
				}
			}
		}	
				
		/* copy outgoingcomming edges */
		for(int i = 0 ; i < prev_task.getTaskList().size(); i++)
		{
			Task s = prev_task.getTaskList().get(i);
			for(int j = 0 ; j < s.getOutgoingEdges().size();j++)
			{
				Task os = s.getOutgoingEdges().get(j);
				if(s.getParentTaskId() == os.getParentTaskId())
				{
					Task next = s.getNextInstance();
					Task onext = os.getNextInstance();					
					next.getOutgoingEdges().add(onext);
				}
			}
		}
		/* for a source node, make an dependency arc */
		for(int i = 0 ; i < prev_task.getTaskList().size(); i++)
		{
			Task s = prev_task.getTaskList().get(i);
//			if(s.getIncommingEdges().size() == 0)
			if(s.isItSource())
			{
				Task next = s.getNextInstance();
//				s.getOutgoingEdges().add(next);
//				next.getIncommingEdges().add(s);
				next.setSource(true);
			}
			if (s.isItSink())
			{
				Task next = s.getNextInstance();
				next.setSink(true);
			}
		}
		
		desc.DEBUG_DESC.println("===" + new_task);
		this.lastInstanceId = new_task.getInstanceID();
		this.lastTaskGroupId = new_task.getId();
		this.taskGroupList.add(new_task);
		
		return new_task;
	}

	// getter and setter
	
	public TaskGroup getWorstTaskGroup()
	{
		double worst = -1.0;
		TaskGroup wt = taskGroupList.get(0);
		for(TaskGroup t : taskGroupList)
		{
			double min = Double.MAX_VALUE;
			for(Task st : t.getTaskList())
			{
				if(st.getResultRelease() < min)
				{
					min = st.getResultRelease();
				}
			}

			double max = -1;
			for(Task st : t.getTaskList())
			{
				if(st.getResultEnd() > max)
				{
					max = st.getResultEnd();
				}
			}
			if((max-min) > worst)
			{
				worst = (max-min);
				wt = t;
			}
		}
		return wt;
	}

	public TaskGroupList getTaskGroupList()
	{
		return taskGroupList;
	}

	public void setTaskGroupList(TaskGroupList taskList)
	{
		this.taskGroupList = taskList;
	}

	public Integer getFirstNotForJitterTaskInstId()
	{
		return firstNotForJitterTaskInstId;
	}

	public void setFirstNotForJitterTaskInstId(Integer firstNotForJitterTaskInstId)
	{
		this.firstNotForJitterTaskInstId = firstNotForJitterTaskInstId;
	}
	
	public Integer getBaseId()
	{
		return baseId;
	}

	public void setBaseId(Integer baseId)
	{
		this.baseId = baseId;
	}

	public Integer getLastInstanceId()
	{
		return lastInstanceId;
	}

	public void setLastInstanceId(Integer lastInstanceId)
	{
		this.lastInstanceId = lastInstanceId;
	}

	public Integer getLastTaskGroupId()
	{
		return lastTaskGroupId;
	}

	public void setLastTaskGroupId(Integer lastTaskId)
	{
		this.lastTaskGroupId = lastTaskId;
	}

	public Description getDesc()
	{
		return desc;
	}

	public void setDesc(Description desc)
	{
		this.desc = desc;
	}

	public Double getStartOffset()
	{
		return startOffset;
	}

	public void setStartOffset(Double startOffset)
	{
		this.startOffset = startOffset;
	}	
	
	public Double getInnerOffset()
	{
		return this.innerOffset;
	}
	
	public void setInnerOffset(Double innerOffset)
	{
		this.innerOffset = innerOffset;
	}
	
	public Double getStartOffsetMin() {
		return startOffsetMin;
	}

	public void setStartOffsetMin(Double startOffsetMin) {
		this.startOffsetMin = startOffsetMin;
	}

	public Double getStartOffsetMax() {
		return startOffsetMax;
	}

	public void setStartOffsetMax(Double startOffsetMax) {
		this.startOffsetMax = startOffsetMax;
	}

	public Double getMaxEnd() {
		return maxEnd;
	}

	public void setMaxEnd(Double maxEnd) {
		this.maxEnd = maxEnd;
	}

	public Integer getMaxExpandNumber() {
		return maxExpandNumber;
	}

	public void setMaxExpandNumber(Integer maxExpandNumber) {
		this.maxExpandNumber = maxExpandNumber;
	}

	public Double getResultOffset() {
		return resultOffset;
	}

	public void setResultOffset(Double resultOffset) {
		this.resultOffset = resultOffset;
	}
	
	public Integer getIncCount() {
		return incCount;
	}

	public void incIncCount() {
		this.incCount++;
	}

	public Integer getExpandedNumTaskByJitter() {
		return expandedTaskNumByJitter;
	}

	public void setExpandedTaskNumByJitter(Integer expandedTaskByJitter) {
		this.expandedTaskNumByJitter = expandedTaskByJitter;
	}	
	
	public Double getTotalJitter()
	{
		double total=0;
		for(TaskGroup t : taskGroupList)
		{
			for(Task st : t.getTaskList())
			{
				total+=st.getResultJitter();
			}
		}
		return total;
	}

	public Long getCriticalPathLength() {
		return criticalPathLength;
	}

	public void setCriticalPathLength(Long criticalPathLength) {
		this.criticalPathLength = criticalPathLength;
	}

	public Integer getMaxJitter() {
		return maxJitter;
	}

	public void setMaxJitter(Integer maxJitter) {
		this.maxJitter = maxJitter;
	}

	public Integer getWcetMinMax() {
		return wcetMinMax;
	}

	public void setWcetMinMax(Integer wcetMinMax) {
		this.wcetMinMax = wcetMinMax;
	}
}

