package Analysis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class MAST {
	
	Data.Description desc;
    String MastPath = "D:\\work\\Research\\WCRT_Analysis\\mast-bin-win-1-4-0-0\\mast_analysis.exe";
	public long [] WCRTs;
	boolean exception;
	
    public MAST(Data.Description desc)
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
		generate_MAST_input(desc.FilePath.replace(".xml","_MAST.txt"));
		
		// run MAST with three options: offset_based_optimized, offset_approximate_with_precedence_relations,
		// and offset_based_slanted. We task the best estimation among them.
		
		long[] bestWCRTs = new long[desc.GraphList.size()];
		for (int i = 0; i < desc.GraphList.size(); ++i)
			bestWCRTs[i] = Long.MAX_VALUE;
		
		double start = System.nanoTime();
		run_MAST(desc.FilePath.replace(".xml","_MAST.txt"), "offset_based_optimized");
		double end = (System.nanoTime() - start) / 1000.0;
		
		read_MAST_result(desc.FilePath.replace(".xml","_MAST.out"));
    	
		for (int i = 0; i < WCRTs.length; ++i)
		{
			if (bestWCRTs[i] > WCRTs[i])
				bestWCRTs[i] = WCRTs[i];
		}
		
		start = System.nanoTime();
		run_MAST(desc.FilePath.replace(".xml","_MAST.txt"), "offset_approximate_with_precedence_relations");
		end = (System.nanoTime() - start) / 1000.0;
		
		read_MAST_result(desc.FilePath.replace(".xml","_MAST.out"));
    	
		for (int i = 0; i < WCRTs.length; ++i)
		{
			if (bestWCRTs[i] > WCRTs[i])
				bestWCRTs[i] = WCRTs[i];
		}
		
		start = System.nanoTime();
		run_MAST(desc.FilePath.replace(".xml","_MAST.txt"), "offset_based_slanted");
		end = (System.nanoTime() - start) / 1000.0;
		
		read_MAST_result(desc.FilePath.replace(".xml","_MAST.out"));
    	
		for (int i = 0; i < WCRTs.length; ++i)
		{
			if (bestWCRTs[i] > WCRTs[i])
				bestWCRTs[i] = WCRTs[i];
		}
		
		// copy the best result into WCRTs
		for (int i = 0; i < WCRTs.length; ++i)
			WCRTs[i] = bestWCRTs[i];
		
		if (exception)
		{
			for (int i = 0; i < WCRTs.length; ++i)
				WCRTs[i] = Long.MAX_VALUE;
			return false;
		}
		return true;
	}
    
    void generate_MAST_input(String filePath)
    {
    	String output ="Model (Model_Name          => Unknown,Model_Date          => 2012-11-03T04:45:51, System_Pip_Behaviour=> STRICT);\n\n";
    	
    	String procs = "";
    	
        /* make processor */
        for(int i= 0 ; i < desc.PEList.size(); i++) {
        	Data.ProcessingElement pe = desc.PEList.get(i);        	
        	procs += "Processing_Resource (\n";
        	procs += " Type                   => Regular_Processor,\n";
        	procs += " Name                   => pe"+pe.ID+",\n";
        	procs += " Max_Interrupt_Priority => 32767,\n";
        	procs += " Min_Interrupt_Priority => 1,\n";
        	procs += " Worst_ISR_Switch       => 0.00,\n";
        	procs += " Avg_ISR_Switch         => 0.00,\n";
        	procs += " Best_ISR_Switch        => 0.00,\n";
        	procs += " Speed_Factor           => 1.00);\n";
        	procs += "\n";        
        }
        output += procs;
        output +="\n";
        
        String scheduler = "";
        for(int i= 0 ; i < desc.PEList.size(); i++) {
        	Data.ProcessingElement pe = desc.PEList.get(i);        	

        	scheduler+="Scheduler (\n";
        	scheduler+="	Type            => Primary_Scheduler,\n";
        	scheduler+="	Name            => pe"+pe.ID+",\n";
        	scheduler+="	Host            => pe"+pe.ID+",\n";
        	scheduler+="	Policy          => \n";
        	scheduler+="	( Type                 => Fixed_Priority,\n";
        	scheduler+="		Worst_Context_Switch => 0.00,\n";
        	scheduler+="		Avg_Context_Switch   => 0.00,\n";
        	scheduler+="		Best_Context_Switch  => 0.00,\n";
        	scheduler+="		Max_Priority         => 32767,\n";
        	scheduler+="		Min_Priority         => 1));\n";
        	scheduler+="\n";
        }
        output += scheduler;
        output +="\n";
        
        int maxPriority = 0;
        for (int i = 0; i < desc.GraphList.size(); ++i)
        for (int j = 0; j < desc.GraphList.get(i).TaskList.size(); ++j)
        {
        	Data.Task task = desc.GraphList.get(i).TaskList.get(j);
        	if (maxPriority < task.Priority)
        		maxPriority = task.Priority;
        }
        
        String ss = "";
        for (int i = 0; i < desc.GraphList.size(); ++i)
        for (int j = 0; j < desc.GraphList.get(i).TaskList.size(); ++j)
        {
        	Data.Task task = desc.GraphList.get(i).TaskList.get(j);
        	
        	ss += "Scheduling_Server (\n";
        	ss += "   Type                       => Regular,\n";
        	ss += "   Name                       => t"+task.ID+",\n";
        	ss += "   Server_Sched_Parameters    => \n";
        	
        	if(desc.PEList.get(task.MappedPE).Preemptable)
        		ss += "      ( Type         => Fixed_Priority_Policy,\n";
        	else
        		ss += "      ( Type         => Non_Preemptible_FP_Policy,\n";
        	ss += "        The_Priority => "+(maxPriority - task.Priority)+",\n";
        	ss += "        Preassigned  => YES),\n";
        	ss += "   Scheduler                  => pe"+task.MappedPE+");\n";
        	ss+="\n";
        }
        output += ss;
        output +="\n";
        
        String op = "";
        for (int i = 0; i < desc.GraphList.size(); ++i)
        for (int j = 0; j < desc.GraphList.get(i).TaskList.size(); ++j)
        {
            Data.Task task = desc.GraphList.get(i).TaskList.get(j);
            
        	op +="";
        	op +="Operation (\n";
        	op +="   Type                       => Simple,\n";
        	op +="   Name                       => t" + task.ID + ",\n";
        	op +="   Worst_Case_Execution_Time  => " + task.WCET + ".00,\n";
        	op +="   Avg_Case_Execution_Time    => " + ((double)(task.BCET + task.WCET)/2.0) + ",\n";
        	op +="   Best_Case_Execution_Time   => " + task.BCET + ".00);\n";
        	op +="\n";
        }
        output += op;
        output +="\n";
       
        
        String tr="";
        for(int i = 0 ; i < desc.GraphList.size(); i ++) {
        	Data.Graph graph = desc.GraphList.get(i);
        	
        	tr+="Transaction (\n";
        	tr+="   Type            => regular,\n";
        	tr+="   Name            => e_tg"+graph.ID+",\n";
        	tr+="   External_Events => \n";
        	tr+="      ( ( Type       => Periodic,\n";
        	tr+="          Name       => e_tg"+graph.ID+",\n";
        	tr+="          Period     => "+graph.Activation.Period+".00,\n";
        	tr+="          Max_Jitter => "+graph.Activation.Jitter+",\n";
        	tr+="          Phase      => 0.000)),\n";
        			          
        	tr+="   Internal_Events => (\n";
        	
        	/* MAST support linear Transaction only. Here assumes linear transaction */
        	ArrayList<Integer> linearTransaction = getLinearTransaction(graph);
        	
        	for (int j = 0; j < linearTransaction.size(); ++j)
        	{
        		tr+=" 		( Type => Regular,\n";
        		if(j < linearTransaction.size() - 1) {
        			tr+="  		 Name => t"+ linearTransaction.get(j)+"_t"+linearTransaction.get(j+1)+"),\n";  
        		}
        		else {
        			tr+="  		 Name => t"+ linearTransaction.get(j)+"_out)),\n";
        		}
        	}
        	
        	tr+="Event_Handlers  => (\n";
        	for (int j = 0; j < linearTransaction.size(); ++j)
        	{
        		tr+="	(Type               => Activity,\n";
        		if(j == 0)
        			tr+="         Input_Event        => e_tg"+graph.ID+",\n";
        		else
        			tr+="         Input_Event        => t"+linearTransaction.get(j-1)+"_t"+linearTransaction.get(j)+",\n";
        		
        		if(j < linearTransaction.size() - 1)
            		tr+="         Output_Event       => t"+linearTransaction.get(j)+"_t"+linearTransaction.get(j+1)+",\n";
        		else			
            		tr+="         Output_Event       => t"+linearTransaction.get(j)+"_out,\n";
        		
        		tr+="         Activity_Operation => t"+linearTransaction.get(j)+",\n";
        		tr+="         Activity_Server    => t"+linearTransaction.get(j)+")";
        		
        		if(j == linearTransaction.size() - 1)
        			tr+=")\n";
        		else
        			tr+=",\n";
        	}
        	tr+=");";
        	tr+="\n";
        }        
        output +=tr;
        output +="\n";
        
        try {
        	BufferedWriter bw = new BufferedWriter(new FileWriter(filePath));
        	bw.write(output);
			bw.close();
        } catch (Exception e) {
        	e.printStackTrace();
        }
    }
    
    ArrayList<Integer> getLinearTransaction(Data.Graph graph)
    {
    	ArrayList<Integer> linearTransaction = new ArrayList<Integer>();
    	for (int j = 0; j < graph.DependencyList.size(); ++j)
    	{
    		Data.Dependency dependency1 = graph.DependencyList.get(j);
    		boolean isSource = true;
    		
    		for (int k = 0; k < graph.DependencyList.size(); ++k)
    		{
    			if (j == k)
    				continue;
    			
    			Data.Dependency dependency2 = graph.DependencyList.get(k);
    			if (dependency1.SourceID == dependency2.DestinationID)
    			{
    				isSource = false;
    				break;
    			}
    		}
    		
    		if (isSource)
    		{
    			linearTransaction.add(dependency1.SourceID);
    			linearTransaction.add(dependency1.DestinationID);
    			break;
    		}
    	}
    	if(linearTransaction.isEmpty()) {
    		System.err.println("There is no src task in TG"+graph.ID);
    		System.exit(1);
    	}
    
    	boolean isSink = false;
    	while (!isSink)
    	{
    		isSink = true;
        	for (int j = 0; j < graph.DependencyList.size(); ++j)
        	{
        		Data.Dependency dependency = graph.DependencyList.get(j);
        		if (linearTransaction.get(linearTransaction.size()-1) == dependency.SourceID)
        		{
        			isSink = false;
        			linearTransaction.add(dependency.DestinationID);
        			break;
        		}
        	}
    	}
    	if (linearTransaction.size() != graph.DependencyList.size() + 1)
    	{
    		System.err.println("MASK doesn't support non-linear transaction");
			System.exit(1);
    	}
    	return linearTransaction;
    }
    
    void run_MAST(String filePath, String option)
    {
    	exception = false;
    	
    	Runtime run = Runtime.getRuntime();
		long start = System.currentTimeMillis();
		try {
			Process oProcess = run.exec(MastPath+ " " + option + " " + filePath + " " + filePath.replace(".txt", ".out"));

			if (oProcess.waitFor(20, TimeUnit.SECONDS) == false)
			{
				exception = true;
				
				oProcess.destroyForcibly();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    void read_MAST_result(String path)
    {
    	if (exception)
    	{
    		for (int i = 0; i < WCRTs.length; ++i)
    			WCRTs[i] = Long.MAX_VALUE;
    		
    		return;
    	}
    	try {
    		BufferedReader br = new BufferedReader(new FileReader(path));
    		
    		if(br.readLine() == null)
    			return;
    	
    		String s;
    		while ((s = br.readLine()) != null){
				if (s.indexOf("Transaction")!=-1){
					String a=null, b=null,c=null;
					while ((s = br.readLine()) != null){						
						if(s.indexOf("Worst_Global_Response_Times")!=-1) {
							a=s;
							b=br.readLine();
							c=br.readLine();								
						}
						if(s.indexOf(";") !=-1) 
							break;
					}
					
					int id = Integer.parseInt(b.split("e_tg",2)[1].split(",")[0]);
					double wcrt = Double.parseDouble(c.split("=> ")[1].split("[)]")[0]);	
					WCRTs[id] = (Long)Math.round(wcrt);
				}
    		}
    		
    		br.close();
    		
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	
    }
}
