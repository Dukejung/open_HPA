package Analysis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class pyCPA {
	Data.Description desc;
	public long [] WCRTs;

	public pyCPA(Data.Description desc)
	{
		this.desc = desc;
		WCRTs = new long[desc.GraphList.size()];
	}

    public long getWCRT(int graphID)
    {
    	return WCRTs[graphID];
    }
    
	public boolean go()
	{
		generate_pyCPA_input(desc.FilePath.replace(".xml","_PYCPA.py"));
		
		double start = System.nanoTime();
		run_pyCPA(desc.FilePath.replace(".xml","_PYCPA.py"));
		double end = (System.nanoTime() - start) / 1000.0;
		
		return true;
	}
	
	void generate_pyCPA_input(String filePath){
		String ss="";

		//license information
		ss+= "\"\"\"\n";
		ss+= "| Copyright (C) 2013 Shin-haeng Kang\n";
		ss+= "| Caplab, Republic of Korea\n";
		ss+= "| All rights reserved\n";
		ss+= "\n";
		ss+= ":Authors: Shin-haeng Kang\n";
		ss+= "\"\"\"\n";

		ss+="\n";

		//import
		ss+= "from pycpa import model\n";
		ss+= "from pycpa import analysis\n";
		ss+= "from pycpa import schedulers\n";
		ss+= "from pycpa import options\n";
		ss+= "from pycpa import path_analysis\n";

		ss+="\n";

		ss+= "def shkang_run():\n";

		ss+= "\toptions.init_pycpa()\n";
		ss+= "\ts = model.System()\n";

		//resource
		for (int i = 0; i < desc.PEList.size(); i++){
			if (i != desc.PEList.get(i).ID){
				System.err.println("!");
			}
			int pIdx = desc.PEList.get(i).ID;
			
			if (desc.PEList.get(pIdx).Preemptable){
				//ss+= "\tr"+pIdx+" = s.add_resource(\"R"+pIdx+"\", spp.w_spp, spp.spp_multi_activation_stopping_condition)\n";
				ss+= "\tr"+pIdx+" = s.bind_resource(model.Resource(\"R"+pIdx+"\", schedulers.SPPScheduler()))\n";
				
			}
			else {
				//ss+= "\tr"+pIdx+" = s.add_resource(\"R"+pIdx+"\", spnp.w_spnp, spnp.spnp_multi_activation_stopping_condition)\n";
				ss+= "\tr"+pIdx+" = s.bind_resource(model.Resource(\"R"+pIdx+"\", schedulers.SPNPScheduler()))\n";	
			}

		}

		//mapping tasks 
		for (int i = 0; i < desc.GraphList.size(); i++)
		{
			Data.Graph graph = desc.GraphList.get(i);
			int gIdx = graph.ID;
			for (int j = 0; j < graph.TaskList.size() ; j++){
				Data.Task task = graph.TaskList.get(j);
				int tIdx = task.ID;
				
				ss+= "\tt"+gIdx+""+tIdx+" = r"+task.MappedPE+".bind_task(";
				ss+= "model.Task(\"T"+gIdx+""+tIdx+"\", wcet = "+task.WCET+", bcet = "+task.BCET+", scheduling_parameter="+task.Priority+"))\n";
			}
		}
		
		//dependency
		for (int i = 0; i < desc.GraphList.size(); i++)
		{
			Data.Graph graph = desc.GraphList.get(i);
			int gIdx = graph.ID;
			
			for (int j = 0; j < graph.DependencyList.size(); j++)
			{
				Data.Dependency dependency = graph.DependencyList.get(j);
				ss+= "\tt"+gIdx+""+dependency.SourceID+".link_dependent_task(t"+gIdx+""+dependency.DestinationID+")\n";
			}
		}
		
		//event modeling
		ArrayList<Integer> sourceIDs = new ArrayList<Integer>();
		for (int i = 0; i < desc.GraphList.size(); i++)
		{
			Data.Graph graph = desc.GraphList.get(i);
			int gIdx = graph.ID;
			
			for (int dIdx1 = 0; dIdx1 < graph.DependencyList.size(); dIdx1++)
	    	{
	    		Data.Dependency dependency1 = graph.DependencyList.get(dIdx1);
	    		boolean isSource = true;
	    		
	    		for (int dIdx2 = 0; dIdx2 < graph.DependencyList.size(); dIdx2++)
	    		{
	    			if (dIdx1 == dIdx2)
	    				continue;
	    			
	    			Data.Dependency dependency2 = graph.DependencyList.get(dIdx2);
	    			if (dependency1.SourceID == dependency2.DestinationID)
	    			{
	    				isSource = false;
	    				break;
	    			}
	    		}
	    		
	    		if (isSource && !sourceIDs.contains(dependency1.SourceID))
	    		{
	    			sourceIDs.add(dependency1.SourceID);
	    			ss+= "\tt"+gIdx+""+dependency1.SourceID+".in_event_model = model.PJdEventModel(P = "
	    			+graph.Activation.Period+", J = "+graph.Activation.Jitter+", dmin="+graph.Activation.Distance+")\n";	
	    		}
	    	}
		}

		//task grouping.
		for (int i = 0; i < desc.GraphList.size() ; i++)
		{
			Data.Graph graph = desc.GraphList.get(i);
			ss+= "\ttg"+graph.ID+"= s.bind_path(model.Path(\"tg"+graph.ID+"\", [";

			ArrayList<Integer> order = topologicalOrder(graph);
			
			for (int j=0 ; j<order.size()-1 ; j++)
				ss+= "t"+graph.ID+""+order.get(i)+", ";
			ss+= "t"+graph.ID+""+order.get(order.size()-1);
			
			ss+="]))\n";			
		}

		//analyze the system
		ss+= "\tprint(\"analyzing...\")\n";
		ss+= "\ttaskresults=analysis.analyze_system(s)\n";
		
		for (int gIdx = 0; gIdx < desc.GraphList.size() ; gIdx++){
			ss+= "\tbest_case_latency, worst_case_latency = path_analysis.end_to_end_latency(tg"+gIdx+", taskresults, 1)\n";
			ss+= "\tprint(worst_case_latency)\n";
		}

		ss+= "if __name__ == \"__main__\":\n";
		ss+= "\tshkang_run()\n";

		//save the file
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(filePath));
			out.write(ss);
			out.close();
		} catch (IOException e) {
			System.err.println(e); 
			System.exit(1);
		}

	}

	ArrayList<Integer> topologicalOrder(Data.Graph graph)
	{
		int minID = Integer.MAX_VALUE;
		int maxID = Integer.MIN_VALUE;
		for (int i = 0; i < graph.TaskList.size(); ++i)
		{
			Data.Task task = graph.TaskList.get(i);
			minID = Math.min(minID, task.ID);
			maxID = Math.max(maxID, task.ID);
		}
		assert(graph.TaskList.size() == maxID - minID + 1);
		
		int[] visitableCount = new int[maxID - minID + 1];
		for (int i = 0; i < graph.DependencyList.size(); ++i)
		{
			Data.Dependency dependency = graph.DependencyList.get(i);
			visitableCount[dependency.DestinationID - minID]++;
		}
		
		Queue<Integer> visitQueue = new LinkedList<Integer>();
		for (int i = 0; i < maxID - minID + 1; ++i)
		{
			if (visitableCount[i] == 0)
				visitQueue.offer(i+minID);
		}

		ArrayList<Integer> order = new ArrayList<Integer>();
		while (!visitQueue.isEmpty())
		{
			int taskID = visitQueue.poll();
			order.add(taskID);
			
			for (int i = 0; i < graph.DependencyList.size(); ++i)
			{
				Data.Dependency dependency = graph.DependencyList.get(i);
				if (dependency.SourceID == taskID)
				{
					if (--visitableCount[dependency.DestinationID - minID] == 0)
						visitQueue.add(dependency.DestinationID);
				}
			}
		}
		assert(order.size() == graph.TaskList.size());
		
		return order;
	}
	
	public void run_pyCPA(String filePath){

		boolean exception = false;
		
		Runtime run = Runtime.getRuntime();
		boolean reading = false;
		int tgIdx =0;
		try {
			String path = "python " + filePath;
			Process oProcess = run.exec(path);
			
			if (oProcess.waitFor(120, TimeUnit.SECONDS) == false)
			{
				exception = true;
				oProcess.destroyForcibly();
			}
			else
			{
				InputStream stdin = oProcess.getInputStream();
				InputStreamReader isr = new InputStreamReader(stdin);
				BufferedReader br = new BufferedReader(isr);
				String s;
				while ((s = br.readLine()) != null){
					if (reading){
						WCRTs[tgIdx++] = Long.parseLong(s.trim());
					}
					
					if (!reading && s.trim().equals("analyzing...")){	//start signal.
						reading = true;
					}
				}
				
				InputStream stderr = oProcess.getErrorStream();
				isr = new InputStreamReader(stderr);
				br = new BufferedReader(isr);
				
				while ((s = br.readLine()) != null){
			
					if (s.startsWith("pycpa.analysis.NotSchedulableException"))
						exception = true;
				}
			}
		} catch (Exception e) {
			//e.printStackTrace();
			exception = true;
		}

		if (exception)
		{
			for (int j = 0; j < desc.GraphList.size(); ++j)
			{
			 WCRTs[j] = Long.MAX_VALUE;
			}
		}
		else
		{
			for (int j = 0; j < desc.GraphList.size(); ++j)
			{
			 WCRTs[j] += desc.GraphList.get(j).Activation.Jitter;
			}
		}
	}
}
