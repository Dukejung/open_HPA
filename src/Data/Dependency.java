package Data;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

public class Dependency {
    @XmlAttribute(name="Source")   		public String Source;
    @XmlAttribute(name="Destination")   public String Destination;
    
    @XmlTransient	public int SourceID;
    @XmlTransient	public int DestinationID;
    
    public Dependency()
    {
    	Source = "";
    	Destination = "";
    	SourceID = 0;
    	DestinationID = 0;
    }
    
    public Dependency clone()
    {
    	Dependency clone = new Dependency();
    	clone.Source = this.Source;
    	clone.Destination = this.Destination;
    	clone.SourceID = this.SourceID;
    	clone.DestinationID = this.DestinationID;
    	return clone;
    }
}
