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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class TaskGroup
{
	TaskList taskList = new TaskList();
	Integer instanceID;
	long period;
	Long deadline;
	Integer id;
	Integer baseId;
	Description desc;
	double WCETResult;
	double startTime;
	
	boolean forJitter = false;
	Integer jitter=0;
	Integer interDistance=0;
	
	Integer wcetMinMax = -1;
	
	TaskGroupSet taskGroupSet;
	
	String name;
	
	//Constructor
	public TaskGroup(Integer instanceID, long period, long deadline, Integer id, Description desc)
	{
		this.instanceID = instanceID;
		this.period = period;
		this.deadline = deadline;
		this.id = id;
		this.baseId = id;
		this.desc = desc;
	}
	
/*	public Task addNextTaskInstance(int instanceID, boolean forJitter)
	{
		int fin_task_id = desc.totalTaskGroupList.size() - 1;
		int fin_subtask_id = desc.totalTaskList.size() - 1;
		
		Task task = null;
		task = new Task(instanceID, this.period, fin_task_id+1, this.desc);
		task.setBaseId(this.getBaseId());
		task.setForJitter(forJitter);
		task.setJitter(this.getJitter());
		task.setInterDistance(this.getInterDistance());
		
		SubtaskList sl = this.getSubtaskList();
		for(int i = 0 ; i < sl.size();i++)
		{
			Subtask s = sl.get(i);
			int new_id = fin_subtask_id+i+1;
			Subtask new_s;
			
			new_s = new Subtask(task
					,s.getComputationUpper(), s.getComputationLower()
					,s.getMemAccessUpper(), s.getMemAccessLower()
					,s.getBusUsageUpper(), s.getBusUsageLower(), new_id
					,s.getPeriod(), s.isPreemptable()
			);
			
			new_s.setPriority(s.getPriority());
			new_s.setMappedProcID(s.getMappedProcID());
			
			Processor p = this.desc.totalProcessorList.getFromId(s.getMappedProcID());
			p.getMappedSubtasks().add(new_s);

			new_s.setPrevInstance(s);
			s.setNextInstance(new_s);
			
			task.getSubtaskList().add(new_s);			
		}
		
		this.desc.addTask(task);	
		
		for(int i = 0 ; i < this.getSubtaskList().size(); i++)
		{
			Subtask s = this.getSubtaskList().get(i);
			for(int j = 0 ; j < s.getIncommingEdges().size();j++)
			{
				Subtask is = s.getIncommingEdges().get(j);

				if(s.getParentTaskId() == is.getParentTaskId())
				{
					Subtask next = s.getNextInstance();
					
					int diff = is.getId() - s.getId();

					Subtask inext = desc.totalTaskList.getFromId(next.getId()+diff);
					next.getIncommingEdges().add(inext);
				}
			}
		}	
		
		for(int i = 0 ; i < this.getSubtaskList().size(); i++)
		{
			Subtask s = this.getSubtaskList().get(i);
			for(int j = 0 ; j < s.getOutgoingEdges().size();j++)
			{
				Subtask os = s.getOutgoingEdges().get(j);

				if(s.getParentTaskId() == os.getParentTaskId())
				{
					Subtask next = s.getNextInstance();
					int diff = os.getId() - s.getId();
					Subtask onext = desc.totalTaskList.getFromId(next.getId()+diff);
					next.getOutgoingEdges().add(onext);
				}
			}
		}
		
		return task;
	}*/	
	
	
	
	public Integer calCriticalPath()
	{
		Queue<Task> visitQueue = new LinkedList<Task>();
		for (Task task : taskList)
		{
			task.setCriticalPathLength(0);
			task.resetVisitableCount(task.getOutgoingEdges().size());
			
			if (task.isItSink())
			{
				task.setCriticalPathLength(task.getComputationUpper());
				visitQueue.offer(task);
			}
		}
		
		while (!visitQueue.isEmpty())
		{
			Task task = visitQueue.poll();
			
			for (Task predecessor : task.getIncommingEdges())
			{
				int criticalPathLength = predecessor.getComputationUpper()
										+ task.getCriticalPathLength();
				if (predecessor.getCriticalPathLength() < criticalPathLength)
					predecessor.setCriticalPathLength(criticalPathLength);
				
				if (predecessor.decVisitableCount())
					visitQueue.offer(predecessor);
			}
		}
		
		int criticalPathLength = 0;
		for (Task task : taskList)
		{
			if (task.isItSource())
			{
				if (criticalPathLength < task.getCriticalPathLength())
					criticalPathLength = task.getCriticalPathLength();
			}
		}
		return criticalPathLength;
	}

	public Integer getWcetMinMax() {
		return wcetMinMax;
	}

	public void setWcetMinMax(Integer wcetMinMax) {
		this.wcetMinMax = wcetMinMax;
	}
	
	Integer sumComputationUpper()
	{
		Integer sum=0;
		for(Task st : this.taskList)
		{
			sum += st.getComputationUpper();
		}
		return sum;
	}
	
	@Override
	public String toString()
	{
		return "TaskGroup id["+id+"] BaseId["+baseId+"] instanceID["+instanceID+"] p["+period+"] j["+
      jitter+"] d["+interDistance+"] taskSize["+taskList.size()+"]"+" WCET["+String.format("%.2f", WCETResult)+"] forJitter[" + forJitter +"]";
	}

	// getter and setter
	
	public TaskList getTaskList()
	{
		return taskList;
	}
	
	public double getStartTime()
	{
		return startTime;
	}
	
	public boolean isForJitter()
	{
		return forJitter;
	}

	public void setForJitter(boolean forJitter)
	{
		this.forJitter = forJitter;
	}

	public void setStartTime(double startTime)
	{
		this.startTime = startTime;
	}

	public double getWCETResult()
	{
		return WCETResult;
	}

	public void setWCETResult(double wCETResult)
	{
		WCETResult = wCETResult;
	}

	public Integer getBaseId()
	{
		return baseId;
	}

	public void setBaseId(Integer baseId)
	{
		this.baseId = baseId;
	}

	public Description getDesc()
	{
		return desc;
	}

	public void setDesc(Description desc)
	{
		this.desc = desc;
	}

	public void setTaskList(TaskList subtaskList)
	{
		this.taskList = subtaskList;
	}
	
	public Integer getInstanceID()
	{
		return instanceID;
	}
	
	public void setInstanceID(Integer instanceID)
	{
		this.instanceID = instanceID;
	}
	
	public long getPeriod()
	{
		return period;
	}
	
	public void setPeriod(long period)
	{
		this.period = period;
	}
	
	public Long getDeadline()
	{
		return deadline;
	}
	
	public void setDeadline(Long deadline)
	{
		this.deadline = deadline;
	}
	
	boolean violated = false;
	public void violateDeadline()
	{
		violated = true;
	}
	public boolean isDeadlineViolated()
	{
		return violated;
	}
	public Integer getId()
	{
		return id;
	}
	
	public void setId(Integer id)
	{
		this.id = id;
	}
	
	public Integer getJitter()
	{
		return jitter;
	}

	public void setJitter(Integer jitter)
	{
		this.jitter = jitter;
	}
	
	public Integer getInterDistance()
	{
		return interDistance;
	}

	public void setInterDistance(Integer interDistance)
	{
		this.interDistance = interDistance;
	}

	public TaskGroupSet getTaskGroupSet()
	{
		return taskGroupSet;
	}

	public void setTaskGroupSet(TaskGroupSet taskGroup)
	{
		this.taskGroupSet = taskGroup;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}	
}
