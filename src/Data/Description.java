package Data;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlRootElement(name="Description")
public class Description {
    @XmlAttribute(name="SRcount")			public int SRcount;
    @XmlElement(name="Graph")				public ArrayList<Graph> GraphList;
    @XmlElement(name="ProcessingElement")	public ArrayList<ProcessingElement> PEList;

	@XmlTransient	public String FilePath;
	
	public Description()
	{
		SRcount = 0;
		GraphList = new ArrayList<Graph>();
		PEList = new ArrayList<ProcessingElement>();
	}
	
    public static Description load(String path)
    {
    	Description result = null;
    	
    	try {
    		InputStream xmlInput = new FileInputStream(path);
    		JAXBContext jaxbContext = JAXBContext.newInstance(Description.class);
    		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
    		Description desc = (Description) unmarshaller.unmarshal(xmlInput);
    		
    		if (desc.assignID() && desc.assignDependency() && desc.assignMapping())
    		{
    			result = desc;
    			result.FilePath = path;
    		}
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	return result;
    }
    
    public void save(String path)
    {
        try {
        	OutputStream xmlOutput = new FileOutputStream(path);
			JAXBContext jaxbContext = JAXBContext.newInstance(Description.class);
			Marshaller marshaller = jaxbContext.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true); 
			marshaller.marshal(this, xmlOutput);
        } catch (Exception e) {
        }
    }
 
    public Description clone()
    {
    	Description clone = new Description();
    	clone.SRcount = this.SRcount;
    	clone.GraphList = new ArrayList<Graph>();
    	for (Graph graph : this.GraphList)
    		clone.GraphList.add(graph.clone());
    	clone.PEList = new ArrayList<ProcessingElement>();
    	for (ProcessingElement pe : this.PEList)
    		clone.PEList.add(pe.clone());
    	clone.FilePath = this.FilePath;
    	return clone;
    }
    
    private boolean assignID()
    {
		int taskID = 0;
		int graphID = 0;
		int peID = 0;
		
		for (Graph graph : GraphList)
		{
			graph.ID = graphID++;
			for (Task task : graph.TaskList)
				task.ID = taskID++;
		}
		for (ProcessingElement PE : PEList)
			PE.ID = peID++;
		
		return true;
    }
    
    private boolean assignDependency()
    {
		for (Graph graph : GraphList)
		{
			for (Dependency dependency : graph.DependencyList)
			{
				Task source = null;
				Task destination = null;
				
				for (Task task : graph.TaskList)
				{
					if (task.Name.equals(dependency.Source))
						source = task;
					if (task.Name.equals(dependency.Destination))
						destination = task;
					if (source != null && destination != null)
						break;
				}
				if (source == null || destination == null)
					return false;
				
				dependency.SourceID = source.ID;
				dependency.DestinationID = destination.ID;
			}
		}
		return true;
    }
    
    private boolean assignMapping()
    {
		for (ProcessingElement PE : PEList)
		{
			if (PE.MappingList == null)
				PE.MappingList = new ArrayList<Mapping>();
			
			for (Mapping mapping : PE.MappingList)
			{
				if (mapping.Replica != 0)
					continue;
				
				Task mappedTask = null;
				for (Graph graph : GraphList)
				for (Task task : graph.TaskList)
					if (task.Name.equals(mapping.Task))
					{
							mappedTask = task;
							break;
					}
				if (mappedTask == null)
					return false;
				
				mappedTask.MappedPE = PE.ID;
				mapping.TaskID = mappedTask.ID;
			}
		}
		return true;
    }
}
