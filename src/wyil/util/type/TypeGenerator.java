package wyil.util.type;

import java.io.*;
import java.util.*;

import wyil.lang.Type;
import wyjvm.io.*;
import wyautl.io.*;
import wyautl.lang.Automata;
import wyautl.lang.Automatas;
import wyautl.lang.DefaultInterpretation.Value;
import wyautl.util.*;
import wyautl.util.Generator.Config;
import wyautl.util.Generator.Kind;

public class TypeGenerator {
	
	public static class BinaryTypeWriter extends Type.BinaryWriter {
		public BinaryTypeWriter(BinaryOutputStream output) {
			super(output);
		}
		
		public void write(Automata automata) throws IOException {			
			if(!isContractive(automata)) {
				Type t = Type.construct(automata);
				if (t != Type.T_VOID) {
					super.write(automata);
					count++;
					if (verbose) {
						System.err.print("\rWrote " + count + " types.");					
					}
				}			
			}
		}
	}
	
	public static class TextTypeWriter implements GenericWriter<Automata> {
		private PrintStream output;

		public TextTypeWriter(PrintStream output) {
			this.output = output;
		}

		public void write(Automata automata) throws IOException {			
			if(!isContractive(automata)) {
				Type t = Type.construct(Automatas.extract(automata,0));
				if (t != Type.T_VOID) {
					output.println(t);
					count++;
					if (verbose) {
						System.err.print("\rWrote " + count + " types.");
					}
				} 			
			}
		}

		public void flush() throws IOException {
			output.flush();
		}

		public void close() throws IOException {
			output.close();
		}
	}	
	
	public static boolean isContractive(Automata automata) {		
		BitSet contractives = new BitSet(automata.size());
		// initially all nodes are considered contracive.
		contractives.set(0,contractives.size(),true);
		boolean changed = true;
		boolean contractive = false;
		while(changed) {
			changed=false;
			contractive = false;
			for(int i=0;i!=automata.size();++i) {
				boolean oldVal = contractives.get(i);
				boolean newVal = isContractive(i,contractives,automata);
				if(oldVal && !newVal) {
					contractives.set(i,newVal);
					changed = true;
				}
				contractive |= newVal;
			}
		}

		return contractive;
	}
	
	private static boolean isContractive(int index, BitSet contractives,
			Automata automata) {
		Automata.State state = automata.states[index];
		int[] children = state.children;
		if(children.length == 0) {
			return false;
		}
		if(state.deterministic) {
			for(int child : children) {
				if(child == index || contractives.get(child)) {
					return true;
				}
			}
			return false;
		} else {			
			boolean r = true;
			for(int child : children) {				
				if(child == index) { 
					return true;
				}
				r &= contractives.get(child);									
			}
			return r;
		}
	}
	
	private static final Generator.Data DATA_GENERATOR = new Generator.Data() {
		public List<Object> generate(Automata.State state) {
			if(state.kind == Type.K_RECORD) {
				return recordGenerator(state);
			} else {
				return Collections.EMPTY_LIST;
			}
		}
	};
	
	private static final Config config = new Config() {{		
		RECURSIVE = true;
		SIZE = 3;
		KINDS = new Kind[24];
		//KINDS[Type.K_VOID] = new Kind(true,0,0,null);
		//KINDS[Type.K_ANY] = new Kind(true,0,0,null);		
		KINDS[Type.K_NULL] = new Kind(true,0,0,null);
		//KINDS[Type.K_BOOL] = new Kind(true,0,0,null);
		//KINDS[Type.K_BYTE] = new Kind(true,0,0,null);
		//KINDS[Type.K_CHAR] = new Kind(true,0,0,null);
		//KINDS[Type.K_INT] = new Kind(true,0,0,null);
		//KINDS[Type.K_RATIONAL] = new Kind(true,0,0,null);
		//KINDS[Type.K_STRING] = new Kind(true,0,0,null);
		KINDS[Type.K_TUPLE] = new Kind(true,2,2,null);
		//KINDS[Type.K_SET] = new Kind(true,1,1,null);
		//KINDS[Type.K_LIST] = new Kind(true,1,1,null);
		//KINDS[Type.K_DICTIONARY] = new Kind(true,2,2,null);	
		//KINDS[Type.K_PROCESS] = new Kind(true,1,1,null);
		KINDS[Type.K_RECORD] = new Kind(true,1,1,DATA_GENERATOR);
		KINDS[Type.K_UNION] = new Kind(false,2,2,null);
		//KINDS[Type.K_NEGATION] = new Kind(true,1,1,null);
		//KINDS[Type.K_FUNCTION] = new Kind(true,2,3,null);
		//KINDS[Type.K_METHOD] = new Kind(true,1,1,null);
		//KINDS[Type.K_HEADLESS] = new Kind(true,1,1,null);
		//KINDS[Type.K_EXISTENTIAL] = new Kind(true,1,1,null);
	}};
	
	private static final String[] fields = {"f1","f2","f3","f4","f5"};	
	
	private static List<Object> recordGenerator(Automata.State state) {		
		ArrayList<String> data1 = new ArrayList();
		ArrayList<String> data2 = new ArrayList();
		for(int i=0;i!=state.children.length;++i) {
			data1.add(fields[i]);
			data2.add(fields[i+1]);
		}
		ArrayList<Object> datas = new ArrayList<Object>();		
		datas.add(data1);
		datas.add(data2);		
		return datas;
	}		
	
	private static void kindUpdate(int k, Kind kind) {
		if(config.KINDS[k] != null) {
			config.KINDS[k] = kind;
		}
	}
	
	private static boolean verbose = false;	
	private static int count = 0;
	
	public static void main(String[] args) {		
		boolean binary = false;
		GenericWriter<Automata> writer;
		PrintStream out = System.out;
		int minSize = 1;
		int maxSize = config.SIZE;
		
		try {
			int index = 0;
			while(index < args.length) {
				if(args[index].equals("-b")) {
					binary=true;
				} else if(args[index].equals("-o")) {
					String filename = args[++index];
					out = new PrintStream(new FileOutputStream(filename));
				} else if(args[index].equals("-s") || args[index].equals("-size")) {
					String arg = args[++index];
					if(arg.indexOf(':') >= 0) {
						String[] ss = arg.split(":");
						minSize = Integer.parseInt(ss[0]);
						maxSize = Integer.parseInt(ss[1]);
					} else {
						maxSize = Integer.parseInt(arg);						
					}
				} else if(args[index].equals("-v") || args[index].equals("-verbose")) {
					verbose = true;
				} else if(args[index].equals("-m") || args[index].equals("-model")) {
					config.RECURSIVE = false;
					maxSize++;
					kindUpdate(Type.K_UNION,null);
					kindUpdate(Type.K_NEGATION,null);
					kindUpdate(Type.K_SET,new Kind(true,0,2,null));
					kindUpdate(Type.K_LIST,new Kind(true,0,2,null));
					// could do more
				}
				index++;
			}
			
			if(binary) {
				BinaryOutputStream bos = new BinaryOutputStream(out);
				writer = new BinaryTypeWriter(bos);
			} else {
				writer = new TextTypeWriter(out);
			}				
					
			for(int i=minSize;i<=maxSize;++i) {
				config.SIZE = i;
				Generator.generate(writer,config);				
			}						
			System.err.println("\rWrote " + count + " types.");			
			writer.close();									
		} catch(IOException ex) {
			System.out.println("Exception: " + ex);
		}		
	}
}
