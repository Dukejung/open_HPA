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

public class Processor
{
	TaskList mappedSubtasks = new TaskList();
	TaskList prioritySortedList = new TaskList();
	TaskList scanOrder = new TaskList();
	Integer id;
	boolean preemtable = true;
	Integer coreCount;
	String name;
	
	public void reorder(TaskList prioritySortedListTotal) {
		scanOrder.clear();
		prioritySortedList.clear();
		for(int i = 0 ;i  < prioritySortedListTotal.size(); i++)
		{
			Task s = prioritySortedListTotal.get(i);
			if(s.getMappedProcID() == id)
				prioritySortedList.add(s);
		}
		do {
			boolean dirty = false;
			for (Task n: prioritySortedList) {
				boolean check = true;
				for (Task foo: n.getIncommingEdges()) {
					if (foo.getMappedProcID() == n.getMappedProcID() && ! scanOrder.contains(foo)) check = false;
				}
				if (check && !scanOrder.contains(n)) {
					scanOrder.add(n);
					if (dirty) break;
				}
				else dirty = true;
			}
		} while (scanOrder.size() < prioritySortedList.size());
	}
	
	public void initPriorityOrder(TaskList prioritySortedListTotal)
	{
		prioritySortedList.clear();
		for (Task task : prioritySortedListTotal)
		{
			if (task.getMappedProcID() == id)
				prioritySortedList.add(task);
		}
	}
	
	public void topologicalOrder()
	{
		scanOrder.clear();
		do {
			boolean dirty = false;
			for (Task n: prioritySortedList) {
				boolean check = true;
				for (Task foo: n.getIncommingEdges()) {
					if (foo.getMappedProcID() == n.getMappedProcID() && ! scanOrder.contains(foo)) check = false;
				}
				if (check && !scanOrder.contains(n)) {
					scanOrder.add(n);
					if (dirty) break;
				}
				else dirty = true;
			}
		} while (scanOrder.size() < prioritySortedList.size());
	}
	
	public TaskList getPriorityOrder()
	{
		return prioritySortedList;
	}
	
	public TaskList getScanOrder()
	{
		return scanOrder;
	}
	
	public boolean isPreemtable() {
		return preemtable;
	}

	public void setPreemtable(boolean preemtable) {
		this.preemtable = preemtable;
	}

	public Processor(Integer id)
	{
		this.id = id;
	}

	public Integer getId()
	{
		return id;
	}

	public void setId(Integer id)
	{
		this.id = id;
	}

	public TaskList getMappedSubtasks()
	{
		return mappedSubtasks;
	}

	public void setMappedSubtasks(TaskList mappedSubtask)
	{
		this.mappedSubtasks = mappedSubtask;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public boolean isSingleCore() {
		return coreCount == 1;
	}
	
	public Integer getCoreCount() {
		return coreCount;
	}
	
	public void setCoreCount(Integer coreCount) {
		this.coreCount = coreCount;
	}
}

