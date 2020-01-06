package Data;

import javax.xml.bind.annotation.XmlAttribute;

public class Activation {
    @XmlAttribute(name="Period")   		public long Period;
    @XmlAttribute(name="Jitter")   		public long Jitter;
    @XmlAttribute(name="Distance")   	public long Distance;
    @XmlAttribute(name="StartOffset")   public long StartOffset;
    @XmlAttribute(name="Deadline")   	public long Deadline;
    
    public Activation()
    {
    	Period = 0;
    	Jitter = 0;
    	Distance = 0;
    	StartOffset = 0;
    	Deadline = 0;
    }
    
    public Activation clone()
    {
    	Activation clone = new Activation();
    	clone.Period = this.Period;
    	clone.Jitter = this.Jitter;
    	clone.Distance = this.Distance;
    	clone.StartOffset = this.StartOffset;
    	clone.Deadline = this.Deadline;
    	return clone;
    }
}
