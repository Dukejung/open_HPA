package SharedResource;

import java.io.File;
import java.util.*;

public class DemandCurve { @SuppressWarnings("rawtypes")
	//demand curve
	static class Position implements Comparable{
		public int x;
		public int y;
		public Position(int a, int b)
		{
			x=a;
			y=b;
		}
		public Position()
		{
			x=y=0;
		}
		public int compareTo(Object o)
		{
			Position tmp = (Position)o;
			return x>tmp.x?1:-1;
		}
	};
	static int limit_curve = 10000;
	static int calc_unit = 1;
	boolean repeat_flag;
	int curv_end; // 반복되는 구간을 제외한 커브가 끝나는 지점. 현재 구현에서는 Curve.get(repeat_end).x와 동일시한다.
	int repeat_st; // 반복되는 구간의 시작값 (vector에서의 위치) Curve.get(repeat_st) 로 가져올 수 있게.
	int repeat_end; // 반복되는 구간의 끝 
	Vector<Position> Curve;
	public static String USERDIR = System.getProperty("user.dir");
	public static String FS = System.getProperty("file.separator");
	public static String PATH_3 = "";
	final static String OUTPUT_PATH1 = USERDIR+FS+PATH_3+"result1.xls";
	final static String OUTPUT_PATH2 = USERDIR+FS+PATH_3+"result2.xls";
	final static String OUTPUT_PATH3 = USERDIR+FS+PATH_3+"result3.xls";
	final static String OUTPUT_PATH4 = USERDIR+FS+PATH_3+"result4.xls";
	
	public DemandCurve()
	{
		Curve = new Vector<Position>();
		repeat_flag=false;
		repeat_st=repeat_end=curv_end=0;
	}
	
	public DemandCurve(Vector<Position> curve, int end)
	{
		Curve = new Vector<Position>(curve);
		repeat_flag = false;
		curv_end=end;
		repeat_st=repeat_end=0;
	}
	
	public DemandCurve(Vector<Position> curve, int end, int rs, int re)
	{
		Curve = new Vector<Position>(curve);
		repeat_flag=true;
		curv_end=end;
		repeat_st = rs;
		repeat_end = re;
	}
	
	public DemandCurve(int period, int distance, int maxn, int mind, int maxpulse)
	{
		//distance : shifting 결과에 의해 나온 자신의 종료 시점과 자신의 시작 시점의 최소 거리
		//distance = period + MinS - MaxR?
		mind+=maxpulse;
		if(distance<0) distance = 0;
		if(distance>period-(maxn-1)*mind-maxpulse) distance = period-(maxn-1)*mind-maxpulse;
		if(maxn==0)
		{
			Curve = new Vector<Position>();
			repeat_flag=false;
			repeat_st=repeat_end=curv_end=0;
		}
		int crit=0;
		int value=0;
		Curve = new Vector<Position>();
		if(distance+maxpulse>mind)
		{
			crit = 0;
			for(int i=0;i<maxn;i++)
			{
				value+=maxpulse;
				Curve.add(new Position(i*mind,value));
			}
			crit=(maxn-1)*mind+distance+maxpulse;
			for(int i=0;i<maxn;i++)
			{
				value+=maxpulse;
				Curve.add(new Position(crit+i*mind,value));
			}
			curv_end=crit+period;
			Curve.add(new Position(curv_end,value+maxpulse));
			repeat_end=Curve.size()-1;
			repeat_st=repeat_end-maxn;
			repeat_flag=true;
		}
		else
		{
			if(distance<=0)
			{
				Curve.add(new Position(0,2*maxpulse));
				crit = maxpulse;
			}
			else
			{
				Curve.add(new Position(0,maxpulse));
				Curve.add(new Position(distance+maxpulse,2*maxpulse));
				crit = distance+maxpulse;
			}
			value=2*maxpulse;
			for(int i=1;i<=2*maxn-2;i++)
			{
				value+=maxpulse;
				Curve.add(new Position(crit+i*mind,value));
				if(i==maxn-1) repeat_st=Curve.size()-1;
			}
			curv_end=period+crit+mind*(maxn-1);
			Curve.add(new Position(curv_end,value+maxpulse));
			repeat_flag=true;
			repeat_end=Curve.size()-1;
		}
	}
	
	public int get(int x)
	{
		if(Curve.size()==0) return 0;
		int tmp;
		int base=0;
		if(x>curv_end){
			if(!repeat_flag) return Curve.get(Curve.size()-1).y;
			else{
				tmp = x-curv_end;
				base = (Curve.get(repeat_end).y-Curve.get(repeat_st).y)*(tmp/(Curve.get(repeat_end).x-Curve.get(repeat_st).x));
				tmp%=(Curve.get(repeat_end).x-Curve.get(repeat_st).x);
				return base+get(tmp+Curve.get(repeat_st).x)-Curve.get(repeat_st).y+get(curv_end);
			}
		}
		return Bsearch(x, 0, Curve.size()-1);
	}
	
	public int findv(int x)
	{
		int tmp;
		int base=0;
		if(x>curv_end){
			if(!repeat_flag) return findv(curv_end);
			else{
				tmp = x-curv_end;
				base = (repeat_end-repeat_st)*(tmp/(Curve.get(repeat_end).x-Curve.get(repeat_st).x));
				tmp%=(Curve.get(repeat_end).x-Curve.get(repeat_st).x);
				return base+findv(tmp+Curve.get(repeat_st).x)-repeat_st+repeat_end;
			}
		}
		return Bsearchv(x, 0, Curve.size()-1);
	}
	
	private int Bsearchv(int x, int start, int end)
	{
		if(Curve.size()==0) return 0;
		if(Curve.get(end).x<=x) return end;
		int mid = (start+end)/2;
		Position midval = Curve.get(mid);
		if(x<midval.x) return Bsearchv(x,start,mid-1);
		else if(midval.x<=x&&x<Curve.get(mid+1).x) return mid;
		else return Bsearchv(x,mid+1,end);
	}
	
	private int Bsearch(int x, int start, int end)
	{
		if(Curve.size()==0) return 0;
		if(Curve.get(end).x<=x) return Curve.get(end).y;
		int mid = (start+end)/2;
		Position midval = Curve.get(mid);
		if(x<midval.x) return Bsearch(x,start,mid-1);
		else if(midval.x<=x&&x<Curve.get(mid+1).x) return midval.y;
		else return Bsearch(x,mid+1,end);
	}
	
	//public void visualize(File out)//excel file(.xls), draw with lined-scatter graph option 
	//{	
	//	//System.out.println(out.getName()+"- Period:" + (Curve.get(repeat_end).x-Curve.get(repeat_st).x));
	//	try{
	//		WritableWorkbook wb = Workbook.createWorkbook(out);
	//		WritableSheet sh = wb.createSheet("X-Y scatter curve", 0);
	//		for(int i = 0 ; i<2; i++)
	//		{
	//			sh.setColumnView(i,20);
	//		}
	//		WritableCellFormat textFormat = new WritableCellFormat();
	//		WritableCellFormat integerFormat = new WritableCellFormat(NumberFormats.INTEGER);
	//
	//		textFormat.setAlignment(Alignment.CENTRE);
	//		textFormat.setBorder(Border.ALL, BorderLineStyle.THIN);
	//
	//		int row = 0;
	//
	//		Label label;
	//		label = new jxl.write.Label(0, row, "time", textFormat);
	//		sh.addCell(label);
	//		label = new jxl.write.Label(1, row, "demand", textFormat);
	//		sh.addCell(label);
	//		row++;
	//		int c1 = 0;
	//		int c2 = 1;
	//		jxl.write.Number num;
	//		int length = Curve.size();
	//		num = new jxl.write.Number(c1,row,Curve.get(0).x,integerFormat);
	//		sh.addCell(num);
	//		num = new jxl.write.Number(c2,row,Curve.get(0).y,integerFormat);
	//		sh.addCell(num);
	//		row++;
	//		for (int i = 1 ; i<length; i++)
	//		{
	//			num = new jxl.write.Number(c1,row,Curve.get(i).x,integerFormat);
	//			sh.addCell(num);
	//			num = new jxl.write.Number(c2,row,Curve.get(i-1).y,integerFormat);
	//			sh.addCell(num);
	//			row++;
	//			if(row >= 65536){
	//				row = 0;
	//				c1+=2;
	//				c2+=2;
	//			}
	//			num = new jxl.write.Number(c1,row,Curve.get(i).x,integerFormat);
	//			sh.addCell(num);
	//			num = new jxl.write.Number(c2,row,Curve.get(i).y,integerFormat);
	//			sh.addCell(num);
	//			row++;
	//			if(row >= 65536){
	//				row = 0;
	//				c1+=2;
	//				c2+=2;
	//			}
	//		}
	//		wb.write();
	//		wb.close();
	//	}
	//	catch(Exception e)
	//	{
	//		System.out.println(e.toString());
	//		e.printStackTrace();
	//	}
	//}
	
	
	public Position getv(int x) // get position with vertex no x (x may be bigger than no of curv_end
	{
		if(curv_end==0) return new Position();
		if(repeat_flag){
	
			if(x<=repeat_end) return Curve.get(x);
			else
			{
				int temp = x-repeat_end;
				Position base = new Position();
				base.x = curv_end;
				base.y = Curve.get(repeat_end).y;
				int rcount = temp/(repeat_end-repeat_st);
				temp%=(repeat_end-repeat_st);
				base.x+=(Curve.get(repeat_end).x-Curve.get(repeat_st).x)*rcount+Curve.get(repeat_st+temp).x-Curve.get(repeat_st).x;
				base.y+=(Curve.get(repeat_end).y-Curve.get(repeat_st).y)*rcount+Curve.get(repeat_st+temp).y-Curve.get(repeat_st).y;
				return base;
			}
		}
		else
		{
			if(x>Curve.size()-1){
				System.out.println("Suck ERROR");
				return Curve.get(Curve.size()-1);
			}
			else return Curve.get(x);
		}
	}
	
	public static DemandCurve convolution(DemandCurve c1, DemandCurve c2) // convolution
	{
		int bmax ,max, tmp;
		int t = 0;
		Vector<Position> result = new Vector<Position>();
		max = 0;
		/*
			while(t<5*(c1.curv_end+c2.curv_end))
			{
				bmax = max;
				max = 0;
				for(int i =0;i<=t;i+=calc_unit)
				{
					tmp = c1.get(i) + c2.get(t-i);
					max=tmp>max?tmp:max;
				}
				if(bmax<max) result.add(new Position(t,max));
				t+=calc_unit;
			}
			/*이전 구현 */
		int crit, remain;
		if(DemandCurve.limit_curve<5*(c1.curv_end+c2.curv_end))
		{
			while(t<DemandCurve.limit_curve)
			{
				bmax=max;
				max=0;
				crit = c1.findv(t);
				remain = t-c1.getv(crit).x;
				int i=crit;
				int j=0;
				while(i>=0)
				{
					if(c2.getv(j+1).x-c2.getv(j).x<=remain && j<=c2.findv(t))
					{
						remain-=c2.getv(j+1).x-c2.getv(j).x;
						j++;
					}
					else
					{
						tmp=c1.getv(i).y+c2.getv(j).y;
						max=tmp>max?tmp:max;
						if(i>0) remain+=c1.getv(i).x-c1.getv(i-1).x;
						i--;
					}
				}
				if(bmax<max) result.add(new Position(t,max));
				t+=calc_unit;
			}
			return new DemandCurve(result,result.get(result.size()-1).x);
		}
		else
		{
			while(t<5*(c1.curv_end+c2.curv_end))
			{
				bmax=max;
				max=0;
				crit = c1.findv(t);
				remain = t-c1.getv(crit).x;
				int i=crit;
				int j=0;
				while(i>=0)
				{
					if(c2.getv(j+1).x-c2.getv(j).x<=remain && j<=c2.findv(t))
					{
						remain-=c2.getv(j+1).x-c2.getv(j).x;
						j++;
					}
					else
					{
						tmp=c1.getv(i).y+c2.getv(j).y;
						max=tmp>max?tmp:max;
						if(i>0) remain+=c1.getv(i).x-c1.getv(i-1).x;
						i--;
					}
				}
				if(bmax<max) result.add(new Position(t,max));
				t+=calc_unit;
			}
			int maxp = max(c1.getv(c1.repeat_end).x-c1.getv(c1.repeat_st).x,c2.getv(c2.repeat_end).x-c2.getv(c2.repeat_st).x);
			int minp = min(c1.getv(c1.repeat_end).x-c1.getv(c1.repeat_st).x,c2.getv(c2.repeat_end).x-c2.getv(c2.repeat_st).x);
			if(maxp<2*minp) maxp=minp;
			int rm = detectRepeat(result, maxp);
			if(rm!=-1){
				//System.out.println("new_period:"+(result.get(result.size()-1).x-result.get(result.size()-1-rm).x));
				return new DemandCurve(result, result.get(result.size()-1).x, result.size()-1-rm ,result.size()-1);
			}
			else return new DemandCurve(result,result.get(result.size()-1).x);
		}
	}
	static int max(int a, int b)
	{
		return a>b?a:b;
	}
	static int min(int a,int b)
	{
		return a<b?a:b;
	}
	
	public static int detectRepeat(Vector<Position> cv, int minp)
	{
		if(cv.size()==0) return -1;
		int end = cv.size()-1;
		int t = end;
		while(cv.get(end).x-cv.get(t).x<minp && t>=0)
			t--;
		if(t<0) return -1;
		for(;end<=2*t;t--)
		{
			if(cv.get(end).x-cv.get(t).x==cv.get(t).x-cv.get(2*t-end).x
					&&cv.get(end).y-cv.get(t).y==cv.get(t).y-cv.get(2*t-end).y)
			{
				int i;
				for(i=1;i<end-t;i++)
				{
					if(cv.get(end).x-cv.get(end-i).x!=cv.get(t).x-cv.get(t-i).x
							&& cv.get(end).y-cv.get(end-i).y!=cv.get(t).y-cv.get(t-i).y) break;
				}
				if(i==end-t) break;
			}
		}
		if(end>2*t) return -1;
		else
		{
			int period = end-t;
			while(t>=2*period)
			{
				if(cv.get(t).x-cv.get(t-period).x==cv.get(t-period).x-cv.get(t-2*period).x
						&&cv.get(t).y-cv.get(t-period).y==cv.get(t-period).y-cv.get(t-2*period).y)
				{
					int i;
					for(i=1;i<end-t;i++)
					{
						if(cv.get(end).x-cv.get(end-i).x!=cv.get(t).x-cv.get(t-i).x
								&& cv.get(end).y-cv.get(end-i).y!=cv.get(t).y-cv.get(t-i).y) break;
					}
					if(i!=end-t) break;
					end-=period;
					t-=period;
				}
				else break;
			}
			Vector<Position> cv2 = new Vector<Position>(cv);
			cv.clear();
			for(int i=0;i<=end;i++)
			{
				cv.add(cv2.get(i));
			}
			return end-t;
		}
	}
//Convolution -> 최적화 가능(2배이상) 일단 정의에 따라서 구현.
}
